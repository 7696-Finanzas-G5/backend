package pe.edu.upc.credibarrio.credits.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.upc.credibarrio.audit.application.AuditService;
import pe.edu.upc.credibarrio.credits.application.acl.CreditContextAdapter;
import pe.edu.upc.credibarrio.credits.domain.model.CondicionesFinancieras;
import pe.edu.upc.credibarrio.credits.domain.model.CondicionesFinancieras.ProductoOfertado;
import pe.edu.upc.credibarrio.credits.domain.repositories.CompraRepository;
import pe.edu.upc.credibarrio.credits.domain.repositories.CuotaRepository;
import pe.edu.upc.credibarrio.credits.domain.services.OriginacionCredito.SolicitudCompra;
import pe.edu.upc.credibarrio.customers.application.ClienteService;
import pe.edu.upc.credibarrio.customers.domain.model.Cliente;
import pe.edu.upc.credibarrio.shared.domain.DomainException;
import pe.edu.upc.credibarrio.shared.domain.Enums.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompraCicloEmitidoTest {
    @Mock CompraRepository compras;
    @Mock CuotaRepository cuotas;
    @Mock CreditContextAdapter acl;
    @Mock ClienteService clientes;
    @Mock AuditService audit;
    private CreditoService service;

    @BeforeEach
    void preparar() {
        service = new CreditoService(compras, cuotas, acl, clientes, audit);
        when(clientes.obtener(10L)).thenReturn(new Cliente(1L, "Nombre", "Prueba", "00000000", null, null, EstadoActivo.ACTIVO));
        when(acl.condicionesVigentes(10L)).thenReturn(Optional.of(new CondicionesFinancieras(11L, Moneda.PEN,
                1500, 6, 20, 26, .001, .003, "Compensatoria", "Moratoria", LocalTime.of(18, 0))));
        when(acl.producto(20L)).thenReturn(Optional.of(new ProductoOfertado(20L, 1L, "Producto", 100,
                ModalidadPermitida.AMBAS, true)));
    }

    @Test
    void laSimulacionAdvierteQueNoAdmiteComprasEnUnCicloEmitido() {
        when(acl.cicloFinDeMesEmitido(10L, LocalDate.of(2026, 9, 26))).thenReturn(true);

        var sim = service.simular(solicitud(ModalidadCompra.FIN_MES));

        assertThat(sim.errores()).singleElement().asString().contains("listado de pago emitido");
    }

    @Test
    void elRegistroRechazaLaCompraSinModificarElListado() {
        when(acl.cicloFinDeMesEmitido(10L, LocalDate.of(2026, 9, 26))).thenReturn(true);

        assertThatThrownBy(() -> service.registrarCompra(solicitud(ModalidadCompra.FIN_MES)))
                .isInstanceOf(DomainException.class).hasMessageContaining("listado de pago emitido");
        verify(compras, never()).save(any());
    }

    @Test
    void conservaLaSimulacionHistoricaDeCuotas() {
        var sim = service.simular(solicitud(ModalidadCompra.CUOTAS));

        assertThat(sim.errores()).isEmpty();
        assertThat(sim.cronograma().filas()).hasSize(3);
        verify(acl, never()).cicloFinDeMesEmitido(any(), any());
    }

    private static SolicitudCompra solicitud(ModalidadCompra modalidad) {
        return new SolicitudCompra(10L, 20L, 1, LocalDateTime.of(2026, 9, 15, 10, 0), modalidad,
                modalidad == ModalidadCompra.CUOTAS ? 3 : null);
    }
}
