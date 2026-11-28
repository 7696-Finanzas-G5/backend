package pe.edu.upc.credibarrio.credits.application.acl;

import org.springframework.stereotype.Component;
import pe.edu.upc.credibarrio.billing.domain.model.Liquidacion.EstadoLiquidacion;
import pe.edu.upc.credibarrio.billing.domain.repositories.LiquidacionRepository;
import pe.edu.upc.credibarrio.catalog.domain.model.ProductoServicio;
import pe.edu.upc.credibarrio.catalog.domain.repositories.ProductoRepository;
import pe.edu.upc.credibarrio.credits.domain.model.CondicionesFinancieras;
import pe.edu.upc.credibarrio.credits.domain.model.CondicionesFinancieras.ProductoOfertado;
import pe.edu.upc.credibarrio.customers.domain.model.CondicionCredito;
import pe.edu.upc.credibarrio.customers.domain.repositories.CondicionCreditoRepository;
import pe.edu.upc.credibarrio.shared.domain.Enums.FinalidadTasa;

import java.util.Optional;
import java.time.LocalDate;

/**
 * Capa anticorrupción: traduce Customers, Catalog y el cierre de Billing al contexto de Créditos.
 */
@Component
public class CreditContextAdapter {

    private final CondicionCreditoRepository condiciones;
    private final ProductoRepository productos;
    private final LiquidacionRepository liquidaciones;

    public CreditContextAdapter(CondicionCreditoRepository condiciones, ProductoRepository productos,
                                LiquidacionRepository liquidaciones) {
        this.condiciones = condiciones;
        this.productos = productos;
        this.liquidaciones = liquidaciones;
    }

    public Optional<CondicionesFinancieras> condicionesVigentes(Long idCliente) {
        return condiciones.findVigente(idCliente).map(CreditContextAdapter::traducir);
    }

    public Optional<CondicionesFinancieras> condicionesPorId(Long idCondicion) {
        return condiciones.findById(idCondicion).map(CreditContextAdapter::traducir);
    }

    public Optional<ProductoOfertado> producto(Long id) {
        return productos.findById(id).map(CreditContextAdapter::traducir);
    }

    public boolean cicloFinDeMesEmitido(Long idCliente, LocalDate fechaPago) {
        return liquidaciones.existsByIdClienteAndFechaVencimientoAndEstadoNot(idCliente, fechaPago,
                EstadoLiquidacion.ANULADA);
    }

    private static ProductoOfertado traducir(ProductoServicio p) {
        return new ProductoOfertado(p.getId(), p.getIdTienda(), p.getDescripcion(), p.getPrecioCredito().doubleValue(),
                p.getModalidadPermitida(), p.activo());
    }

    private static CondicionesFinancieras traducir(CondicionCredito c) {
        return new CondicionesFinancieras(c.getId(), c.getMoneda(), c.getLimiteCredito().doubleValue(),
                c.getPlazoMaxMeses(), c.getDiaCorte(), c.getDiaPago(), c.tedCompensatoria(), c.tedMoratoria(),
                c.tasaLabel(FinalidadTasa.COMPENSATORIA), c.tasaLabel(FinalidadTasa.MORATORIA), c.getHoraCorte());
    }
}
