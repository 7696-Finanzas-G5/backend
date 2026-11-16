package pe.edu.upc.credibarrio.credits.domain.services;

import pe.edu.upc.credibarrio.credits.domain.model.Compra;
import pe.edu.upc.credibarrio.credits.domain.model.CondicionesFinancieras;
import pe.edu.upc.credibarrio.credits.domain.model.CondicionesFinancieras.ProductoOfertado;
import pe.edu.upc.credibarrio.credits.domain.model.Cuota;
import pe.edu.upc.credibarrio.shared.domain.Enums.ModalidadCompra;
import pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath;
import pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath.CronogramaFrances;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath.round2;

/**
 * Servicio de dominio: originación de una venta al crédito. Valida la condición de aceptación (6.3.5)
 * y proyecta el pago a fin de mes o el cronograma francés.
 */
public final class OriginacionCredito {

    private OriginacionCredito() {
    }

    public record SolicitudCompra(Long idCliente, Long idProducto, int cantidad, LocalDateTime fechaHoraCompra,
                                  ModalidadCompra modalidad, Integer n) {
    }

    public record FinMes(int dias, double interes, double total) {
    }

    public record SimulacionCompra(List<String> errores, double monto, double disponible, LocalDate fechaPago,
                                   LocalDate fechaCorte, boolean esAnteriorAlCorte, FinMes finMes,
                                   CronogramaFrances cronograma) {
        public boolean valida() {
            return errores.isEmpty();
        }
    }

    public static SimulacionCompra simular(SolicitudCompra sol, ProductoOfertado producto, CondicionesFinancieras cond,
                                           double disponible) {
        double monto = round2(producto.precioCredito() * sol.cantidad());
        List<String> errores = new ArrayList<>(FinancialMath.validarCompra(producto.modalidadPermitida(), sol.modalidad(),
                sol.n(), cond.plazoMaxMeses(), monto, disponible));
        if (sol.cantidad() <= 0) errores.add("La cantidad debe ser mayor a cero.");
        if (!producto.activo()) errores.add("El producto está dado de baja.");

        LocalDate fechaCompra = sol.fechaHoraCompra().toLocalDate();
        LocalDate fechaPago = FinancialMath.fechaPagoCiclo(fechaCompra, cond.diaCorte(), cond.diaPago());
        LocalDate fechaCorte = FinancialMath.fechaCorteDeCiclo(fechaPago, cond.diaCorte());
        // Compras hasta las 23:59 del día de corte de su mes entran al ciclo de ese mes
        boolean anteriorAlCorte = fechaCompra.getDayOfMonth() <= cond.diaCorte();

        FinMes finMes = null;
        CronogramaFrances cronograma = null;
        if (sol.modalidad() == ModalidadCompra.FIN_MES) {
            int dias = FinancialMath.dias360(fechaCompra, fechaPago);
            double interes = FinancialMath.interesPorDias(monto, cond.tedCompensatoria(), dias);
            finMes = new FinMes(dias, interes, round2(monto + interes));
        } else if (sol.n() != null && sol.n() >= 1 && monto > 0) {
            cronograma = FinancialMath.cronogramaFrances(monto, cond.tedCompensatoria(), fechaCompra, fechaPago, sol.n());
        }
        return new SimulacionCompra(errores, monto, disponible, fechaPago, fechaCorte, anteriorAlCorte, finMes, cronograma);
    }

    /** Deuda vigente: capital de compras a fin de mes no canceladas + amortización pendiente de cuotas. */
    public static double deudaVigente(List<Compra> compras, List<Cuota> cuotas) {
        List<Compra> vigentes = compras.stream().filter(Compra::vigente).toList();
        Set<Long> idsCuotas = vigentes.stream().filter(c -> c.getModalidad() == ModalidadCompra.CUOTAS)
                .map(Compra::getId).collect(Collectors.toSet());
        double finMes = vigentes.stream().filter(c -> c.getModalidad() == ModalidadCompra.FIN_MES)
                .mapToDouble(c -> c.getCapital().doubleValue()).sum();
        double pendiente = cuotas.stream().filter(q -> idsCuotas.contains(q.getIdCompra()) && q.pendiente())
                .mapToDouble(q -> q.getAmortizacion().doubleValue()).sum();
        return round2(finMes + pendiente);
    }
}
