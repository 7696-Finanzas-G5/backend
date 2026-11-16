package pe.edu.upc.credibarrio.billing.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.credibarrio.audit.application.AuditService;
import pe.edu.upc.credibarrio.billing.domain.model.Liquidacion;
import pe.edu.upc.credibarrio.billing.domain.model.Liquidacion.EstadoLiquidacion;
import pe.edu.upc.credibarrio.billing.domain.model.LiquidacionDetalle;
import pe.edu.upc.credibarrio.billing.domain.model.Pago;
import pe.edu.upc.credibarrio.billing.domain.repositories.LiquidacionRepository;
import pe.edu.upc.credibarrio.billing.domain.repositories.PagoRepository;
import pe.edu.upc.credibarrio.credits.application.CreditoService;
import pe.edu.upc.credibarrio.credits.application.CreditoService.CuotaPendiente;
import pe.edu.upc.credibarrio.credits.domain.model.Compra;
import pe.edu.upc.credibarrio.credits.domain.model.CondicionesFinancieras;
import pe.edu.upc.credibarrio.credits.domain.model.Cuota;
import pe.edu.upc.credibarrio.customers.application.ClienteService;
import pe.edu.upc.credibarrio.iam.infrastructure.security.CurrentUser;
import pe.edu.upc.credibarrio.shared.domain.DomainException;
import pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath;
import pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath.Imputacion;
import pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath.ItemFinDeMes;
import pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath.LiquidacionFinDeMes;
import pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath.ResultadoMora;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath.money;

/** Casos de uso: estado de cuenta al corte, liquidación con mora y registro de pagos. */
@Service
@Transactional
public class EstadoCuentaService {

    public record EstadoCiclo(LocalDate fechaCorte, LocalDate fechaPago, LocalDate fechaEfectiva,
                              LiquidacionFinDeMes liquidacion, ResultadoMora mora, CondicionesFinancieras condiciones) {
    }

    public record CuotaConMora(Cuota cuota, Compra compra, String descripcion, boolean pagable, ResultadoMora mora) {
    }

    public record ResultadoPago(Pago pago, Imputacion imputacion) {
    }

    private final CreditoService creditos;
    private final ClienteService clientes;
    private final LiquidacionRepository liquidaciones;
    private final PagoRepository pagos;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public EstadoCuentaService(CreditoService creditos, ClienteService clientes, LiquidacionRepository liquidaciones,
                               PagoRepository pagos, CurrentUser currentUser, AuditService audit) {
        this.creditos = creditos;
        this.clientes = clientes;
        this.liquidaciones = liquidaciones;
        this.pagos = pagos;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    // --------------------------------------------------------- fin de mes

    /** Fechas de pago (ciclos) que tienen compras a fin de mes pendientes, en orden cronológico. */
    @Transactional(readOnly = true)
    public List<LocalDate> ciclosPendientes(Long idCliente) {
        clientes.obtener(idCliente);
        return creditos.comprasFinDeMesPendientes(idCliente).stream().map(Compra::getFechaPago).distinct().sorted().toList();
    }

    /** Liquidación del ciclo con la mora que corresponde si se paga en {@code fechaEfectiva}. */
    @Transactional(readOnly = true)
    public EstadoCiclo estadoCiclo(Long idCliente, LocalDate fechaPago, LocalDate fechaEfectiva) {
        clientes.obtener(idCliente);
        List<Compra> compras = creditos.comprasFinDeMesPendientes(idCliente).stream()
                .filter(c -> c.getFechaPago().equals(fechaPago)).toList();
        if (compras.isEmpty()) return null;
        CondicionesFinancieras cond = creditos.condicionesDeCompra(compras.getFirst());
        LocalDate efectiva = fechaEfectiva != null ? fechaEfectiva : fechaPago;
        List<ItemFinDeMes> items = compras.stream()
                .map(c -> new ItemFinDeMes(c.getId(), c.descripcion(), c.getFechaHoraCompra().toLocalDate(),
                        c.getCapital().doubleValue()))
                .toList();
        LiquidacionFinDeMes liq = FinancialMath.liquidarFinDeMes(items, cond.tedCompensatoria(), fechaPago);
        ResultadoMora mora = FinancialMath.calcularMora(liq.totalExigible(), cond.tedMoratoria(), fechaPago, efectiva);
        return new EstadoCiclo(FinancialMath.fechaCorteDeCiclo(fechaPago, cond.diaCorte()), fechaPago, efectiva, liq,
                mora, cond);
    }

    /** Registra el pago total del ciclo (prelación mora → interés compensatorio → capital). */
    public ResultadoPago pagarCiclo(Long idCliente, LocalDate fechaPago, LocalDate fechaEfectiva, double monto) {
        EstadoCiclo e = estadoCiclo(idCliente, fechaPago, fechaEfectiva);
        if (e == null) throw new DomainException("No hay compras pendientes para este ciclo.");
        Imputacion imp = FinancialMath.imputarPago(monto, e.mora().interesMoratorio(), e.liquidacion().interesTotal(),
                e.mora().totalExigible());

        List<LiquidacionDetalle> detalle = e.liquidacion().lineas().stream()
                .map(l -> new LiquidacionDetalle(l.id(), l.dias(), money(l.capital()), money(l.interes()), money(l.total())))
                .toList();
        Liquidacion liq = liquidaciones.save(new Liquidacion(idCliente, e.condiciones().idCondicion(), e.fechaCorte(),
                fechaPago, money(e.liquidacion().capitalTotal()), money(e.liquidacion().interesTotal()),
                money(e.liquidacion().totalExigible()), EstadoLiquidacion.PAGADA, detalle));
        Pago pago = pagos.save(new Pago(idCliente, liq.getId(), null, currentUser.idOrNull(), e.fechaEfectiva(),
                e.mora().diasMora(), imp));
        creditos.cancelarCompras(e.liquidacion().lineas().stream().map(FinancialMath.LineaFinDeMes::id).toList());
        audit.registrar("REGISTRAR_PAGO", "liquidacion", liq.getId(),
                String.format(Locale.ROOT, "Total %.2f (mora %.2f)", imp.total(), imp.mora()));
        return new ResultadoPago(pago, imp);
    }

    // ------------------------------------------------------------- cuotas

    /** Cuotas pendientes con la mora que tendrían si se pagan en {@code fechaEfectiva}. */
    @Transactional(readOnly = true)
    public List<CuotaConMora> cuotasPendientes(Long idCliente, LocalDate fechaEfectiva) {
        clientes.obtener(idCliente);
        LocalDate efectiva = fechaEfectiva != null ? fechaEfectiva : LocalDate.now();
        return creditos.cuotasPendientes(idCliente).stream()
                .map(p -> new CuotaConMora(p.cuota(), p.compra(), p.descripcion(), p.pagable(), moraCuota(p, efectiva)))
                .toList();
    }

    public ResultadoPago pagarCuota(Long idCuota, LocalDate fechaEfectiva, double monto) {
        Cuota cuota = creditos.obtenerCuota(idCuota);
        Compra compra = creditos.obtenerCompra(cuota.getIdCompra()); // incluye control de acceso
        CuotaPendiente pendiente = creditos.cuotasPendientes(compra.getIdCliente()).stream()
                .filter(p -> Objects.equals(p.cuota().getId(), idCuota))
                .findFirst()
                .orElseThrow(() -> new DomainException("La cuota no está pendiente."));
        LocalDate efectiva = fechaEfectiva != null ? fechaEfectiva : LocalDate.now();
        ResultadoMora mora = moraCuota(pendiente, efectiva);
        Imputacion imp = FinancialMath.imputarPago(monto, mora.interesMoratorio(),
                pendiente.cuota().getInteresPeriodo().doubleValue(), mora.totalExigible());
        creditos.marcarCuotaPagada(idCuota);
        Pago pago = pagos.save(new Pago(compra.getIdCliente(), null, idCuota, currentUser.idOrNull(),
                fechaPagoCuota(pendiente, efectiva), mora.diasMora(), imp));
        audit.registrar("REGISTRAR_PAGO", "cuota", idCuota, String.format(Locale.ROOT, "Cuota %d · %.2f",
                pendiente.cuota().getNumeroCuota(), imp.total()));
        return new ResultadoPago(pago, imp);
    }

    // ---------------------------------------------------------- historial

    @Transactional(readOnly = true)
    public List<Pago> pagosDe(Long idCliente) {
        clientes.obtener(idCliente);
        return pagos.findByIdClienteOrderByIdDesc(idCliente);
    }

    @Transactional(readOnly = true)
    public List<Liquidacion> liquidacionesDe(Long idCliente) {
        clientes.obtener(idCliente);
        return liquidaciones.findByIdClienteOrderByIdDesc(idCliente);
    }

    // ----------------------------------------------------------- privados

    /** Si se paga antes del vencimiento no hay mora: se toma la fecha de vencimiento. */
    private static LocalDate fechaPagoCuota(CuotaPendiente p, LocalDate fechaEfectiva) {
        return fechaEfectiva.isAfter(p.cuota().getFechaVencimiento()) ? fechaEfectiva : p.cuota().getFechaVencimiento();
    }

    private ResultadoMora moraCuota(CuotaPendiente p, LocalDate fechaEfectiva) {
        CondicionesFinancieras cond = creditos.condicionesDeCompra(p.compra());
        return FinancialMath.calcularMora(p.cuota().getMontoCuota().doubleValue(), cond.tedMoratoria(),
                p.cuota().getFechaVencimiento(), fechaPagoCuota(p, fechaEfectiva));
    }
}
