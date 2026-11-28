package pe.edu.upc.credibarrio.billing.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pe.edu.upc.credibarrio.audit.application.AuditService;
import pe.edu.upc.credibarrio.billing.domain.model.Liquidacion;
import pe.edu.upc.credibarrio.billing.domain.model.Liquidacion.EstadoLiquidacion;
import pe.edu.upc.credibarrio.billing.domain.repositories.LiquidacionRepository;
import pe.edu.upc.credibarrio.billing.domain.repositories.PagoRepository;
import pe.edu.upc.credibarrio.credits.application.CreditoService;
import pe.edu.upc.credibarrio.credits.domain.model.Compra;
import pe.edu.upc.credibarrio.credits.domain.model.CondicionesFinancieras;
import pe.edu.upc.credibarrio.customers.application.ClienteService;
import pe.edu.upc.credibarrio.iam.infrastructure.security.CurrentUser;
import pe.edu.upc.credibarrio.shared.domain.DomainException;
import pe.edu.upc.credibarrio.shared.domain.Enums.Moneda;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EstadoCuentaCorteTest {
    private static final Long CLIENTE = 1L;
    private static final LocalDate PAGO = LocalDate.of(2026, 9, 26);
    private final CreditoService creditos = mock(CreditoService.class);
    private final ClienteService clientes = mock(ClienteService.class);
    private final LiquidacionRepository liquidaciones = mock(LiquidacionRepository.class);
    private final PagoRepository pagos = mock(PagoRepository.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final AuditService audit = mock(AuditService.class);

    @BeforeEach
    void prepararCompraPendiente() {
        Compra compra = mock(Compra.class);
        when(compra.getId()).thenReturn(1L);
        when(compra.getIdCliente()).thenReturn(CLIENTE);
        when(compra.getIdCondicion()).thenReturn(2L);
        when(compra.getFechaPago()).thenReturn(PAGO);
        when(compra.getFechaHoraCompra()).thenReturn(LocalDateTime.of(2026, 9, 15, 10, 0));
        when(compra.getCapital()).thenReturn(new BigDecimal("100.00"));
        when(compra.descripcion()).thenReturn("Compra de prueba");
        when(creditos.comprasFinDeMesPendientes(CLIENTE)).thenReturn(List.of(compra));
        when(creditos.comprasFinDeMesPendientesDeTodos()).thenReturn(List.of(compra));
        when(creditos.condicionesDeCompra(compra)).thenReturn(new CondicionesFinancieras(
                2L, Moneda.PEN, 500, 3, 20, 26, 0.001, 0.001,
                "TEA de prueba", "TEA de prueba", LocalTime.of(18, 0)));
        when(liquidaciones.findFirstByIdClienteAndFechaVencimientoAndEstadoInOrderByIdDesc(eq(CLIENTE), eq(PAGO), any()))
                .thenReturn(Optional.empty());
        when(liquidaciones.findByEstadoAndFechaVencimientoBefore(eq(EstadoLiquidacion.PENDIENTE), any()))
                .thenReturn(List.of());
        when(liquidaciones.save(any(Liquidacion.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void noCierraElCicloAntesDeLaHoraPactada() {
        EstadoCuentaService service = servicioEn("2026-09-20T17:59:59.999999999");

        assertThat(service.ejecutarCorte().listadosGenerados()).isZero();
        assertThatThrownBy(() -> service.generarListado(CLIENTE, PAGO))
                .isInstanceOf(DomainException.class).hasMessageContaining("Aún no llega");
        verify(liquidaciones, never()).save(any());
    }

    @Test
    void generaElListadoEnElInstanteExactoDeCorteEnLima() {
        EstadoCuentaService service = servicioEn("2026-09-20T18:00:00");

        assertThat(service.ejecutarCorte().listadosGenerados()).isEqualTo(1);
        ArgumentCaptor<Liquidacion> captor = ArgumentCaptor.forClass(Liquidacion.class);
        verify(liquidaciones).save(captor.capture());
        Liquidacion listado = captor.getValue();

        assertThat(listado.getFechaCorte()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(listado.getFechaVencimiento()).isEqualTo(PAGO);
        assertThat(listado.getEstado()).isEqualTo(EstadoLiquidacion.PENDIENTE);
    }

    @Test
    void permiteLaGeneracionManualEnElInstanteExactoDeCorte() {
        assertThat(servicioEn("2026-09-20T18:00:00").generarListado(CLIENTE, PAGO)).isNotNull();
    }

    @Test
    void recuperaUnCorteAtrasadoSinDuplicarElListado() {
        EstadoCuentaService service = servicioEn("2026-09-28T08:00:00");
        assertThat(service.ejecutarCorte().listadosGenerados()).isEqualTo(1);
        ArgumentCaptor<Liquidacion> captor = ArgumentCaptor.forClass(Liquidacion.class);
        verify(liquidaciones).save(captor.capture());
        Liquidacion existente = captor.getValue();
        assertThat(existente.getEstado()).isEqualTo(EstadoLiquidacion.VENCIDA);
        when(liquidaciones.findFirstByIdClienteAndFechaVencimientoAndEstadoInOrderByIdDesc(eq(CLIENTE), eq(PAGO), any()))
                .thenReturn(Optional.of(existente));

        assertThat(service.ejecutarCorte().listadosGenerados()).isZero();
        assertThat(service.generarListado(CLIENTE, PAGO)).isSameAs(existente);
        verify(liquidaciones).save(any(Liquidacion.class));
    }

    private EstadoCuentaService servicioEn(String horaLima) {
        // El reloj está expresado en UTC: el servicio debe convertir el instante a la zona del negocio.
        Clock clock = Clock.fixed(LocalDateTime.parse(horaLima).atZone(EstadoCuentaService.ZONA).toInstant(), ZoneOffset.UTC);
        return new EstadoCuentaService(creditos, clientes, liquidaciones, pagos, currentUser, audit, clock);
    }
}
