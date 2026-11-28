package pe.edu.upc.credibarrio.billing.application;

import org.springframework.beans.factory.annotation.Autowired;
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
import pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath.LineaFinDeMes;
import pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath.ResultadoMora;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath.money;
import static pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath.round2;

/** Casos de uso: estado de cuenta al corte, liquidación con mora y registro de pagos. */
@Service
@Transactional
public class EstadoCuentaService {

    /** {@code listado}: listado de pago persistido al corte para este ciclo (null si aún no se generó). */
    public record EstadoCiclo(LocalDate fechaCorte, LocalDate fechaPago, LocalDate fechaEfectiva,
                              LiquidacionFinDeMes liquidacion, ResultadoMora mora, CondicionesFinancieras condiciones,
                              Liquidacion listado, boolean condicionesMixtas) {
    }

    /** Resultado del proceso de corte: listados generados y listados que pasaron a vencidos. */
    public record ResultadoCorte(LocalDateTime ejecutadoEn, int listadosGenerados, int listadosVencidos) {
    }

    public record CuotaConMora(Cuota cuota, Compra compra, String descripcion, boolean pagable, ResultadoMora mora) {
    }

    public record ResultadoPago(Pago pago, Imputacion imputacion) {
    }

    /** Zona horaria del negocio: el corte se evalúa en hora de Lima, no en la del servidor. */
    public static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final Set<EstadoLiquidacion> POR_COBRAR = EnumSet.of(EstadoLiquidacion.PENDIENTE, EstadoLiquidacion.VENCIDA);

    private final Clock clock;
    private final CreditoService creditos;
    private final ClienteService clientes;
    private final LiquidacionRepository liquidaciones;
    private final PagoRepository pagos;
    private final CurrentUser currentUser;
    private final AuditService audit;

    @Autowired
    public EstadoCuentaService(CreditoService creditos, ClienteService clientes, LiquidacionRepository liquidaciones,
                               PagoRepository pagos, CurrentUser currentUser, AuditService audit) {
        this(creditos, clientes, liquidaciones, pagos, currentUser, audit, Clock.system(ZONA));
    }

    EstadoCuentaService(CreditoService creditos, ClienteService clientes, LiquidacionRepository liquidaciones,
                        PagoRepository pagos, CurrentUser currentUser, AuditService audit, Clock clock) {
        this.creditos = creditos;
        this.clientes = clientes;
        this.liquidaciones = liquidaciones;
        this.pagos = pagos;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock.withZone(ZONA);
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
        return calcularCiclo(idCliente, fechaPago, fechaEfectiva);
    }

    /**
     * Genera (o devuelve, si ya existe) el listado de pago del ciclo. Solo procede pasada la fecha y hora de corte,
     * porque hasta ese momento el ciclo sigue abierto a nuevas compras.
     */
    public Liquidacion generarListado(Long idCliente, LocalDate fechaPago) {
        clientes.obtener(idCliente);
        EstadoCiclo e = calcularCiclo(idCliente, fechaPago, fechaPago);
        if (e == null) throw new DomainException("No hay compras pendientes para este ciclo.");
        DomainException.ensure(corteCerrado(e, ahora()), "Aún no llega la fecha y hora de corte de este ciclo.");
        return e.listado() != null ? e.listado() : guardarListado(idCliente, e, currentUser.idOrNull());
    }

    /**
     * Proceso de corte (lo ejecuta {@code CorteScheduler}): genera el listado de pago de cada ciclo cuyo corte ya
     * pasó y marca como vencidos los listados no pagados en la fecha pactada. Es idempotente.
     */
    public ResultadoCorte ejecutarCorte() {
        LocalDateTime ahora = ahora();
        int generados = 0;
        Map<Long, Set<LocalDate>> ciclos = new LinkedHashMap<>();
        for (Compra c : creditos.comprasFinDeMesPendientesDeTodos()) {
            ciclos.computeIfAbsent(c.getIdCliente(), k -> new TreeSet<>()).add(c.getFechaPago());
        }
        for (var entry : ciclos.entrySet()) {
            for (LocalDate fechaPago : entry.getValue()) {
                EstadoCiclo e = calcularCiclo(entry.getKey(), fechaPago, fechaPago);
                if (e != null && e.listado() == null && corteCerrado(e, ahora)) {
                    guardarListado(entry.getKey(), e, null);
                    generados++;
                }
            }
        }
        List<Liquidacion> vencidos = liquidaciones.findByEstadoAndFechaVencimientoBefore(EstadoLiquidacion.PENDIENTE,
                ahora.toLocalDate());
        vencidos.forEach(Liquidacion::marcarVencida);
        if (generados > 0 || !vencidos.isEmpty()) {
            audit.registrarComo(null, "CORTE_AUTOMATICO", "liquidacion", null, String.format(Locale.ROOT,
                    "%d listado(s) generado(s), %d vencido(s)", generados, vencidos.size()));
        }
        return new ResultadoCorte(ahora, generados, vencidos.size());
    }

    /** Registra el pago total del ciclo (prelación mora → interés compensatorio → capital). */
    public ResultadoPago pagarCiclo(Long idCliente, LocalDate fechaPago, LocalDate fechaEfectiva, double monto) {
        EstadoCiclo e = estadoCiclo(idCliente, fechaPago, fechaEfectiva);
        if (e == null) throw new DomainException("No hay compras pendientes para este ciclo.");
        Imputacion imp = FinancialMath.imputarPago(monto, e.mora().interesMoratorio(), e.liquidacion().interesTotal(),
                e.mora().totalExigible());

        // Si el listado ya se generó al corte se cancela ese mismo; si no, se genera en este momento
        Liquidacion liq = e.listado() != null ? e.listado() : guardarListado(idCliente, e, currentUser.idOrNull());
        liq.marcarPagada();
        liquidaciones.save(liq);
        Pago pago = pagos.save(new Pago(idCliente, liq.getId(), null, currentUser.idOrNull(), e.fechaEfectiva(),
                e.mora().diasMora(), imp));
        creditos.cancelarCompras(e.liquidacion().lineas().stream().map(FinancialMath.LineaFinDeMes::id).toList());
        audit.registrar("REGISTRAR_PAGO", "liquidacion", liq.getId(),
                String.format(Locale.ROOT, "Total %.2f (mora %.2f)", imp.total(), imp.mora()));
        return new ResultadoPago(pago, imp);
    }

    /** Cálculo del ciclo sin control de acceso (lo usan los casos de uso ya autorizados y el proceso de corte). */
    private EstadoCiclo calcularCiclo(Long idCliente, LocalDate fechaPago, LocalDate fechaEfectiva) {
        List<Compra> compras = creditos.comprasFinDeMesPendientes(idCliente).stream()
                .filter(c -> c.getFechaPago().equals(fechaPago)).toList();
        if (compras.isEmpty()) return null;
        Liquidacion listado = liquidaciones.findFirstByIdClienteAndFechaVencimientoAndEstadoInOrderByIdDesc(idCliente,
                fechaPago, POR_COBRAR).orElse(null);
        Map<Long, Compra> porId = compras.stream().collect(Collectors.toMap(Compra::getId, Function.identity()));
        // Un listado emitido es una instantánea: ni una compra retroactiva ni un recálculo deben cambiar su cobro.
        if (listado != null) {
            compras = listado.getDetalle().stream().map(d -> {
                Compra compra = porId.get(d.getIdCompra());
                DomainException.ensure(compra != null, "El listado contiene una compra que ya no está pendiente.");
                return compra;
            }).toList();
        }
        Map<Long, CondicionesFinancieras> pactadas = compras.stream().collect(Collectors.toMap(Compra::getId,
                creditos::condicionesDeCompra));
        // Distintas versiones pueden tener el mismo vencimiento: el ciclo cierra al último corte pactado.
        CondicionesFinancieras cond = pactadas.values().stream().max(Comparator.comparing(c -> corteDeCiclo(fechaPago, c)))
                .orElseThrow();
        LocalDate efectiva = fechaEfectiva != null ? fechaEfectiva : fechaPago;
        List<LineaFinDeMes> lineas;
        if (listado == null) {
            lineas = compras.stream().map(c -> FinancialMath.liquidarFinDeMes(List.of(new ItemFinDeMes(c.getId(),
                            c.descripcion(), c.getFechaHoraCompra().toLocalDate(), c.getCapital().doubleValue())),
                    pactadas.get(c.getId()).tedCompensatoria(), fechaPago).lineas().getFirst()).toList();
        } else {
            lineas = listado.getDetalle().stream().map(d -> new LineaFinDeMes(d.getIdCompra(),
                    porId.get(d.getIdCompra()).descripcion(), porId.get(d.getIdCompra()).getFechaHoraCompra().toLocalDate(),
                    d.getCapital().doubleValue(), d.getDiasFinanciamiento(), d.getInteresCompensatorio().doubleValue(),
                    d.getTotalLinea().doubleValue())).toList();
        }
        lineas = lineas.stream().sorted(Comparator.comparing(LineaFinDeMes::fechaCompra).thenComparing(LineaFinDeMes::id))
                .toList();
        double capital = listado != null ? listado.getCapitalTotal().doubleValue()
                : round2(lineas.stream().mapToDouble(LineaFinDeMes::capital).sum());
        double interes = listado != null ? listado.getInteresCompensatorioTotal().doubleValue()
                : round2(lineas.stream().mapToDouble(LineaFinDeMes::interes).sum());
        double total = listado != null ? listado.getTotalExigible().doubleValue() : round2(capital + interes);
        LiquidacionFinDeMes liq = new LiquidacionFinDeMes(fechaPago, lineas, capital, interes, total);
        // La mora grava el total exigible: se acumulan las bases de igual tasa antes de redondear cada grupo.
        Map<Double, Double> basesPorTasa = lineas.stream().collect(Collectors.groupingBy(
                l -> pactadas.get(l.id()).tedMoratoria(), Collectors.summingDouble(LineaFinDeMes::total)));
        double interesMora = round2(basesPorTasa.entrySet().stream().mapToDouble(g -> FinancialMath.calcularMora(
                round2(g.getValue()), g.getKey(), fechaPago, efectiva).interesMoratorio()).sum());
        ResultadoMora mora = new ResultadoMora(Math.max(0, FinancialMath.dias360(fechaPago, efectiva)), interesMora,
                total, round2(total + interesMora));
        LocalDate fechaCorte = listado != null ? listado.getFechaCorte() : corteDeCiclo(fechaPago, cond).toLocalDate();
        return new EstadoCiclo(fechaCorte, fechaPago, efectiva, liq, mora, cond, listado,
                pactadas.values().stream().map(CondicionesFinancieras::idCondicion).distinct().count() > 1);
    }

    /** Persiste el listado de pago del ciclo (sin mora: la mora depende de la fecha efectiva de pago). */
    private Liquidacion guardarListado(Long idCliente, EstadoCiclo e, Long idUsuario) {
        List<LiquidacionDetalle> detalle = e.liquidacion().lineas().stream()
                .map(l -> new LiquidacionDetalle(l.id(), l.dias(), money(l.capital()), money(l.interes()), money(l.total())))
                .toList();
        EstadoLiquidacion estado = ahora().toLocalDate().isAfter(e.fechaPago()) ? EstadoLiquidacion.VENCIDA
                : EstadoLiquidacion.PENDIENTE;
        Liquidacion liq = liquidaciones.save(new Liquidacion(idCliente,
                e.condiciones().idCondicion(), e.fechaCorte(), e.fechaPago(), money(e.liquidacion().capitalTotal()),
                money(e.liquidacion().interesTotal()), money(e.liquidacion().totalExigible()), estado, detalle));
        audit.registrarComo(idUsuario, "GENERAR_LISTADO", "liquidacion", liq.getId(), String.format(Locale.ROOT,
                "Corte %s · vence %s · total %.2f", e.fechaCorte(), e.fechaPago(), e.liquidacion().totalExigible()));
        return liq;
    }

    /** El ciclo se cierra al pasar la hora de corte del día de corte. */
    private static boolean corteCerrado(EstadoCiclo e, LocalDateTime ahora) {
        LocalTime hora = e.condiciones().horaCorte() != null ? e.condiciones().horaCorte() : FinancialMath.HORA_CORTE_DEFECTO;
        return !ahora.isBefore(e.fechaCorte().atTime(hora));
    }

    private static LocalDateTime corteDeCiclo(LocalDate fechaPago, CondicionesFinancieras condiciones) {
        LocalTime hora = condiciones.horaCorte() != null ? condiciones.horaCorte() : FinancialMath.HORA_CORTE_DEFECTO;
        return FinancialMath.fechaCorteDeCiclo(fechaPago, condiciones.diaCorte()).atTime(hora);
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock);
    }

    // ------------------------------------------------------------- cuotas

    /** Cuotas pendientes con la mora que tendrían si se pagan en {@code fechaEfectiva}. */
    @Transactional(readOnly = true)
    public List<CuotaConMora> cuotasPendientes(Long idCliente, LocalDate fechaEfectiva) {
        clientes.obtener(idCliente);
        LocalDate efectiva = fechaEfectiva != null ? fechaEfectiva : LocalDate.now(clock);
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
        LocalDate efectiva = fechaEfectiva != null ? fechaEfectiva : LocalDate.now(clock);
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
