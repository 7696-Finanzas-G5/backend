package pe.edu.upc.credibarrio.billing.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pe.edu.upc.credibarrio.audit.application.AuditService;
import pe.edu.upc.credibarrio.billing.domain.model.Liquidacion;
import pe.edu.upc.credibarrio.billing.domain.model.LiquidacionDetalle;
import pe.edu.upc.credibarrio.billing.domain.model.Pago;
import pe.edu.upc.credibarrio.billing.domain.repositories.LiquidacionRepository;
import pe.edu.upc.credibarrio.billing.domain.repositories.PagoRepository;
import pe.edu.upc.credibarrio.credits.application.CreditoService;
import pe.edu.upc.credibarrio.credits.domain.model.Compra;
import pe.edu.upc.credibarrio.credits.domain.model.CondicionesFinancieras;
import pe.edu.upc.credibarrio.customers.application.ClienteService;
import pe.edu.upc.credibarrio.iam.infrastructure.security.CurrentUser;
import pe.edu.upc.credibarrio.shared.domain.DomainException;
import pe.edu.upc.credibarrio.shared.domain.Enums.ModalidadCompra;
import pe.edu.upc.credibarrio.shared.domain.Enums.Moneda;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath.money;

@ExtendWith(MockitoExtension.class)
class EstadoCuentaCondicionesTest {
    private static final Long CLIENTE = 10L;
    private static final LocalDate VENCIMIENTO = LocalDate.of(2026, 9, 26);

    @Mock CreditoService creditos;
    @Mock ClienteService clientes;
    @Mock LiquidacionRepository liquidaciones;
    @Mock PagoRepository pagos;
    @Mock CurrentUser usuario;
    @Mock AuditService audit;
    private EstadoCuentaService service;

    @BeforeEach
    void preparar() {
        service = new EstadoCuentaService(creditos, clientes, liquidaciones, pagos, usuario, audit,
                Clock.fixed(Instant.parse("2026-09-23T15:00:00Z"), EstadoCuentaService.ZONA));
    }

    @Test
    void calculaCadaCompraConSuPactoHistoricoYAcumulaLaMoraPorTasa() {
        Compra primera = compra(1L, 11L, "2026-09-05", 100);
        Compra segunda = compra(2L, 12L, "2026-09-12", 80);
        prepararCompras(List.of(segunda, primera), Map.of(11L, condicion(11L, .001, .003, 20),
                12L, condicion(12L, .002, .004, 22)));

        var e = service.estadoCiclo(CLIENTE, VENCIMIENTO, LocalDate.of(2026, 10, 1));

        assertThat(e.liquidacion().lineas()).extracting(l -> l.id()).containsExactly(1L, 2L);
        assertThat(e.liquidacion().lineas()).extracting(l -> l.interes()).containsExactly(2.12, 2.27);
        assertThat(e.liquidacion().capitalTotal()).isEqualTo(180);
        assertThat(e.liquidacion().interesTotal()).isEqualTo(4.39);
        assertThat(e.liquidacion().totalExigible()).isEqualTo(184.39);
        assertThat(e.mora().diasMora()).isEqualTo(5);
        assertThat(e.mora().interesMoratorio()).isEqualTo(3.20);
        assertThat(e.mora().totalExigible()).isEqualTo(187.59);
        assertThat(e.fechaCorte()).isEqualTo(LocalDate.of(2026, 9, 22));
        assertThat(e.condiciones().idCondicion()).isEqualTo(12L);
        assertThat(e.condicionesMixtas()).isTrue();
    }

    @Test
    void redondeaLaMoraSobreLaBaseTotalCuandoLaTasaEsLaMisma() {
        prepararCompras(List.of(compra(1L, 11L, "2026-09-05", 50.01), compra(2L, 12L, "2026-09-12", 50.01)),
                Map.of(11L, condicion(11L, .00000001, .0001, 20), 12L, condicion(12L, .00000001, .0001, 20)));

        var e = service.estadoCiclo(CLIENTE, VENCIMIENTO, LocalDate.of(2026, 9, 29));

        assertThat(e.liquidacion().totalExigible()).isEqualTo(100.02);
        assertThat(e.mora().interesMoratorio()).isEqualTo(.03);
        assertThat(e.mora().totalExigible()).isEqualTo(100.05);
    }

    @Test
    void guardaElListadoConElImporteDeCadaCompraYConservaLaReferenciaAlPacto() {
        prepararCompras(List.of(compra(1L, 11L, "2026-09-05", 100), compra(2L, 12L, "2026-09-12", 80)),
                Map.of(11L, condicion(11L, .001, .003, 20), 12L, condicion(12L, .002, .004, 22)));
        when(liquidaciones.save(any(Liquidacion.class))).thenAnswer(i -> i.getArgument(0));

        Liquidacion listado = service.generarListado(CLIENTE, VENCIMIENTO);

        assertThat(listado.getTotalExigible()).isEqualByComparingTo("184.39");
        assertThat(listado.getInteresCompensatorioTotal()).isEqualByComparingTo("4.39");
        assertThat(listado.getDetalle()).extracting(LiquidacionDetalle::getIdCompra).containsExactly(1L, 2L);
        assertThat(listado.getDetalle()).extracting(LiquidacionDetalle::getInteresCompensatorio)
                .containsExactly(money(2.12), money(2.27));
        assertThat(listado.getIdCondicion()).isEqualTo(12L);
    }

    @Test
    void noEmiteUnCicloMixtoHastaElUltimoCortePactado() {
        prepararCompras(List.of(compra(1L, 11L, "2026-09-05", 100), compra(2L, 12L, "2026-09-12", 80)),
                Map.of(11L, condicion(11L, .001, .003, 20), 12L, condicion(12L, .002, .004, 22)));
        service = new EstadoCuentaService(creditos, clientes, liquidaciones, pagos, usuario, audit,
                Clock.fixed(Instant.parse("2026-09-21T15:00:00Z"), EstadoCuentaService.ZONA));

        assertThatThrownBy(() -> service.generarListado(CLIENTE, VENCIMIENTO))
                .isInstanceOf(DomainException.class).hasMessageContaining("Aún no llega");
        verify(liquidaciones, never()).save(any());
    }

    @Test
    void cobraLaInstantaneaPersistidaYNoCancelaUnaCompraRetroactivaAjenaAlListado() {
        prepararCompras(List.of(compra(1L, 11L, "2026-09-05", 100), compra(2L, 12L, "2026-09-12", 80)),
                Map.of(11L, condicion(11L, .01, .003, 20), 12L, condicion(12L, .02, .004, 22)));
        Liquidacion listado = new Liquidacion(CLIENTE, 11L, LocalDate.of(2026, 9, 20), VENCIMIENTO,
                money(100), money(1.26), money(101.26), Liquidacion.EstadoLiquidacion.VENCIDA,
                List.of(new LiquidacionDetalle(1L, 21, money(100), money(1.26), money(101.26))));
        when(liquidaciones.findFirstByIdClienteAndFechaVencimientoAndEstadoInOrderByIdDesc(
                eq(CLIENTE), eq(VENCIMIENTO), any())).thenReturn(Optional.of(listado));
        when(pagos.save(any(Pago.class))).thenAnswer(i -> i.getArgument(0));

        var e = service.estadoCiclo(CLIENTE, VENCIMIENTO, LocalDate.of(2026, 10, 1));
        assertThat(e.liquidacion().totalExigible()).isEqualTo(101.26);
        assertThat(e.liquidacion().lineas()).hasSize(1);
        assertThat(e.mora().interesMoratorio()).isEqualTo(1.53);
        assertThat(e.condicionesMixtas()).isFalse();

        var pago = service.pagarCiclo(CLIENTE, VENCIMIENTO, LocalDate.of(2026, 10, 1), 102.79);

        assertThat(pago.imputacion().capital()).isEqualTo(100);
        assertThat(pago.imputacion().compensatorio()).isEqualTo(1.26);
        assertThat(listado.getEstado()).isEqualTo(Liquidacion.EstadoLiquidacion.PAGADA);
        verify(liquidaciones).save(listado);
        verify(creditos).cancelarCompras(List.of(1L));
    }

    private void prepararCompras(List<Compra> compras, Map<Long, CondicionesFinancieras> condiciones) {
        when(creditos.comprasFinDeMesPendientes(CLIENTE)).thenReturn(compras);
        when(creditos.condicionesDeCompra(any())).thenAnswer(i -> condiciones.get(((Compra) i.getArgument(0)).getIdCondicion()));
    }

    private static Compra compra(Long id, Long idCondicion, String fecha, double capital) {
        Compra compra = new Compra(CLIENTE, idCondicion, LocalDate.parse(fecha).atTime(10, 0), ModalidadCompra.FIN_MES,
                null, money(capital), VENCIMIENTO, List.of());
        ReflectionTestUtils.setField(compra, "id", id);
        return compra;
    }

    private static CondicionesFinancieras condicion(Long id, double tedC, double tedM, int diaCorte) {
        return new CondicionesFinancieras(id, Moneda.PEN, 1500, 6, diaCorte, 26, tedC, tedM,
                "Compensatoria", "Moratoria", LocalTime.of(18, 0));
    }
}
