package pe.edu.upc.credibarrio.credits.application;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.credibarrio.audit.application.AuditService;
import pe.edu.upc.credibarrio.credits.application.acl.CreditContextAdapter;
import pe.edu.upc.credibarrio.credits.domain.model.Compra;
import pe.edu.upc.credibarrio.credits.domain.model.Compra.EstadoCompra;
import pe.edu.upc.credibarrio.credits.domain.model.CondicionesFinancieras;
import pe.edu.upc.credibarrio.credits.domain.model.CondicionesFinancieras.ProductoOfertado;
import pe.edu.upc.credibarrio.credits.domain.model.Cuota;
import pe.edu.upc.credibarrio.credits.domain.model.DetalleCompra;
import pe.edu.upc.credibarrio.credits.domain.repositories.CompraRepository;
import pe.edu.upc.credibarrio.credits.domain.repositories.CuotaRepository;
import pe.edu.upc.credibarrio.credits.domain.services.OriginacionCredito;
import pe.edu.upc.credibarrio.credits.domain.services.OriginacionCredito.SimulacionCompra;
import pe.edu.upc.credibarrio.credits.domain.services.OriginacionCredito.SolicitudCompra;
import pe.edu.upc.credibarrio.customers.application.ClienteService;
import pe.edu.upc.credibarrio.customers.domain.model.Cliente;
import pe.edu.upc.credibarrio.shared.domain.DomainException;
import pe.edu.upc.credibarrio.shared.domain.Enums.ModalidadCompra;
import pe.edu.upc.credibarrio.shared.domain.NotFoundException;
import pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Casos de uso: registro de compras al crédito, deuda vigente y crédito disponible. */
@Service
@Transactional
public class CreditoService {

    /** Cuota pendiente de una compra; solo la cuota más antigua de cada compra puede pagarse. */
    public record CuotaPendiente(Cuota cuota, Compra compra, String descripcion, boolean pagable) {
    }

    public record ResumenCredito(Long idCliente, CondicionesFinancieras condiciones, double deudaVigente,
                                 double disponible) {
    }

    private final CompraRepository compras;
    private final CuotaRepository cuotas;
    private final CreditContextAdapter acl;
    private final ClienteService clientes;
    private final AuditService audit;

    public CreditoService(CompraRepository compras, CuotaRepository cuotas, CreditContextAdapter acl,
                          ClienteService clientes, AuditService audit) {
        this.compras = compras;
        this.cuotas = cuotas;
        this.acl = acl;
        this.clientes = clientes;
        this.audit = audit;
    }

    // --------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public CondicionesFinancieras condicionesVigentes(Long idCliente) {
        return acl.condicionesVigentes(idCliente)
                .orElseThrow(() -> new DomainException("El cliente no tiene una condición de crédito vigente."));
    }

    /** Condiciones con las que se pactó la compra (no necesariamente las vigentes). */
    @Transactional(readOnly = true)
    public CondicionesFinancieras condicionesDeCompra(Compra compra) {
        return acl.condicionesPorId(compra.getIdCondicion())
                .or(() -> acl.condicionesVigentes(compra.getIdCliente()))
                .orElseThrow(() -> new DomainException("La compra no tiene condiciones de crédito asociadas."));
    }

    @Transactional(readOnly = true)
    public double deudaVigente(Long idCliente) {
        List<Compra> delCliente = compras.findByIdClienteAndEstado(idCliente, EstadoCompra.REGISTRADA);
        List<Long> ids = delCliente.stream().map(Compra::getId).toList();
        List<Cuota> qs = ids.isEmpty() ? List.of() : cuotas.findByIdCompraInOrderByNumeroCuota(ids);
        return OriginacionCredito.deudaVigente(delCliente, qs);
    }

    @Transactional(readOnly = true)
    public double disponible(Long idCliente) {
        return acl.condicionesVigentes(idCliente)
                .map(c -> FinancialMath.creditoDisponible(c.limiteCredito(), deudaVigente(idCliente)))
                .orElse(0.0);
    }

    @Transactional(readOnly = true)
    public ResumenCredito resumen(Long idCliente) {
        clientes.obtener(idCliente);
        return new ResumenCredito(idCliente, acl.condicionesVigentes(idCliente).orElse(null), deudaVigente(idCliente),
                disponible(idCliente));
    }

    @Transactional(readOnly = true)
    public List<Compra> comprasDe(Long idCliente) {
        clientes.obtener(idCliente);
        return compras.findByIdClienteOrderByFechaHoraCompraDesc(idCliente);
    }

    /** Compras más recientes de los clientes de la tienda en sesión. */
    @Transactional(readOnly = true)
    public List<Compra> recientesDeMiTienda(Long idTienda, int limite) {
        List<Long> ids = clientes.deTienda(idTienda).stream().map(Cliente::getId).toList();
        if (ids.isEmpty()) return List.of();
        return compras.findByIdClienteInOrderByFechaHoraCompraDesc(ids, PageRequest.of(0, Math.max(1, Math.min(limite, 200))));
    }

    @Transactional(readOnly = true)
    public Compra obtenerCompra(Long idCompra) {
        Compra c = compras.findById(idCompra).orElseThrow(() -> new NotFoundException("Compra no encontrada."));
        clientes.obtener(c.getIdCliente()); // control de acceso
        return c;
    }

    @Transactional(readOnly = true)
    public List<Cuota> cuotasDeCompra(Long idCompra) {
        obtenerCompra(idCompra);
        return cuotas.findByIdCompraOrderByNumeroCuota(idCompra);
    }

    // ------------------------------------------------------ simulación y alta

    @Transactional(readOnly = true)
    public SimulacionCompra simular(SolicitudCompra sol) {
        Cliente cliente = clientes.obtener(sol.idCliente());
        CondicionesFinancieras cond = condicionesVigentes(sol.idCliente());
        ProductoOfertado producto = productoDeLaTienda(sol.idProducto(), cliente);
        return simularCompra(normalizar(sol), producto, cond, disponible(sol.idCliente()));
    }

    public Compra registrarCompra(SolicitudCompra solicitud) {
        SolicitudCompra sol = normalizar(solicitud);
        Cliente cliente = clientes.obtener(sol.idCliente());
        if (!cliente.activo()) throw new DomainException("El cliente está dado de baja.");
        CondicionesFinancieras cond = condicionesVigentes(sol.idCliente());
        ProductoOfertado producto = productoDeLaTienda(sol.idProducto(), cliente);
        SimulacionCompra sim = simularCompra(sol, producto, cond, disponible(sol.idCliente()));
        if (!sim.valida()) throw new DomainException(String.join(" ", sim.errores()));

        BigDecimal monto = FinancialMath.money(sim.monto());
        DetalleCompra detalle = new DetalleCompra(producto.id(), producto.descripcion(), sol.cantidad(),
                FinancialMath.money(producto.precioCredito()), monto);
        Compra compra = compras.save(new Compra(sol.idCliente(), cond.idCondicion(), sol.fechaHoraCompra(),
                sol.modalidad(), sol.n(), monto, sim.fechaPago(), List.of(detalle)));
        if (sim.cronograma() != null) {
            List<Cuota> nuevas = sim.cronograma().filas().stream().map(f -> Cuota.desdeFila(compra.getId(), f)).toList();
            cuotas.saveAll(nuevas);
        }
        audit.registrar("REGISTRAR_COMPRA", "compra", compra.getId(), String.format(Locale.ROOT, "%s · %s · %.2f",
                producto.descripcion(), sol.modalidad(), sim.monto()));
        return compra;
    }

    // ---------------------------------------- consultas y comandos usados por Billing

    @Transactional(readOnly = true)
    public List<Compra> comprasFinDeMesPendientes(Long idCliente) {
        return compras.findByIdClienteAndModalidadAndEstadoOrderByFechaHoraCompra(idCliente, ModalidadCompra.FIN_MES,
                EstadoCompra.REGISTRADA);
    }

    /** Compras a fin de mes pendientes de todos los clientes (proceso automático de corte; sin control de acceso). */
    @Transactional(readOnly = true)
    public List<Compra> comprasFinDeMesPendientesDeTodos() {
        return compras.findByModalidadAndEstadoOrderByFechaHoraCompra(ModalidadCompra.FIN_MES, EstadoCompra.REGISTRADA);
    }

    @Transactional(readOnly = true)
    public List<CuotaPendiente> cuotasPendientes(Long idCliente) {
        List<CuotaPendiente> result = new ArrayList<>();
        for (Compra compra : compras.findByIdClienteAndModalidadAndEstadoOrderByFechaHoraCompra(idCliente,
                ModalidadCompra.CUOTAS, EstadoCompra.REGISTRADA)) {
            List<Cuota> pendientes = cuotas.findByIdCompraOrderByNumeroCuota(compra.getId()).stream()
                    .filter(Cuota::pendiente).toList();
            for (int i = 0; i < pendientes.size(); i++) {
                result.add(new CuotaPendiente(pendientes.get(i), compra, compra.descripcion(), i == 0));
            }
        }
        result.sort(Comparator.comparing(p -> p.cuota().getFechaVencimiento()));
        return result;
    }

    @Transactional(readOnly = true)
    public Cuota obtenerCuota(Long idCuota) {
        return cuotas.findById(idCuota).orElseThrow(() -> new NotFoundException("Cuota no encontrada."));
    }

    public void cancelarCompras(List<Long> ids) {
        compras.findAllById(ids).forEach(Compra::cancelar);
    }

    /** Marca la cuota como pagada; exige pagar en orden y cancela la compra al pagar la última. */
    public void marcarCuotaPagada(Long idCuota) {
        Cuota cuota = obtenerCuota(idCuota);
        if (!cuota.pendiente()) throw new DomainException("La cuota no está pendiente.");
        List<Cuota> deLaCompra = cuotas.findByIdCompraOrderByNumeroCuota(cuota.getIdCompra());
        if (deLaCompra.stream().anyMatch(q -> q.getNumeroCuota() < cuota.getNumeroCuota() && q.pendiente())) {
            throw new DomainException("Debe pagar primero las cuotas anteriores.");
        }
        cuota.marcarPagada();
        if (deLaCompra.stream().noneMatch(q -> !q.getId().equals(cuota.getId()) && q.pendiente())) {
            compras.findById(cuota.getIdCompra()).ifPresent(Compra::cancelar);
        }
    }

    // ---------------------------------------------------------------- privados

    private SimulacionCompra simularCompra(SolicitudCompra sol, ProductoOfertado producto,
                                           CondicionesFinancieras cond, double disponible) {
        SimulacionCompra sim = OriginacionCredito.simular(sol, producto, cond, disponible);
        if (sol.modalidad() != ModalidadCompra.FIN_MES || !acl.cicloFinDeMesEmitido(sol.idCliente(), sim.fechaPago())) {
            return sim;
        }
        List<String> errores = new ArrayList<>(sim.errores());
        errores.add("El ciclo ya tiene un listado de pago emitido; no admite nuevas compras a fin de mes.");
        return new SimulacionCompra(errores, sim.monto(), sim.disponible(), sim.fechaPago(), sim.fechaCorte(),
                sim.esAnteriorAlCorte(), sim.finMes(), sim.cronograma());
    }

    private ProductoOfertado productoDeLaTienda(Long idProducto, Cliente cliente) {
        return acl.producto(idProducto)
                .filter(p -> p.idTienda().equals(cliente.getIdTienda()))
                .orElseThrow(() -> new NotFoundException("Producto no encontrado."));
    }

    private static SolicitudCompra normalizar(SolicitudCompra s) {
        DomainException.ensure(s.modalidad() != null, "La modalidad de compra es obligatoria.");
        LocalDateTime fecha = s.fechaHoraCompra() != null ? s.fechaHoraCompra() : LocalDateTime.now().withSecond(0).withNano(0);
        return new SolicitudCompra(s.idCliente(), s.idProducto(), s.cantidad(), fecha, s.modalidad(),
                s.modalidad() == ModalidadCompra.CUOTAS ? s.n() : null);
    }
}
