package pe.edu.upc.credibarrio.dashboard.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.credibarrio.audit.application.AuditService;
import pe.edu.upc.credibarrio.audit.application.AuditService.RegistroView;
import pe.edu.upc.credibarrio.billing.application.EstadoCuentaService;
import pe.edu.upc.credibarrio.catalog.domain.repositories.ProductoRepository;
import pe.edu.upc.credibarrio.credits.application.CreditoService;
import pe.edu.upc.credibarrio.credits.domain.repositories.CompraRepository;
import pe.edu.upc.credibarrio.customers.domain.model.Cliente;
import pe.edu.upc.credibarrio.customers.domain.repositories.ClienteRepository;
import pe.edu.upc.credibarrio.iam.domain.repositories.UsuarioRepository;
import pe.edu.upc.credibarrio.iam.infrastructure.security.CurrentUser;
import pe.edu.upc.credibarrio.shared.domain.Enums.EstadoActivo;
import pe.edu.upc.credibarrio.stores.domain.model.Tienda;
import pe.edu.upc.credibarrio.stores.domain.repositories.TiendaRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath.round2;

/** Modelo de lectura (query side) que combina información de varios bounded contexts. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    public record ResumenSistema(long tiendas, long tiendasActivas, long usuarios, long clientes, long compras,
                                 List<RegistroView> bitacoraReciente) {
    }

    public record Vencimiento(String key, LocalDate fecha, Long idCliente, String cliente, String concepto, double monto,
                              boolean vencido) {
    }

    public record ResumenNegocio(String tienda, long clientes, long productos, double deuda, double disponible,
                                 List<Vencimiento> proximosVencimientos, List<RegistroView> bitacoraReciente) {
    }

    private final TiendaRepository tiendas;
    private final UsuarioRepository usuarios;
    private final ClienteRepository clientes;
    private final CompraRepository compras;
    private final ProductoRepository productos;
    private final CreditoService creditos;
    private final EstadoCuentaService estadoCuenta;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public DashboardService(TiendaRepository tiendas, UsuarioRepository usuarios, ClienteRepository clientes,
                            CompraRepository compras, ProductoRepository productos, CreditoService creditos,
                            EstadoCuentaService estadoCuenta, AuditService audit, CurrentUser currentUser) {
        this.tiendas = tiendas;
        this.usuarios = usuarios;
        this.clientes = clientes;
        this.compras = compras;
        this.productos = productos;
        this.creditos = creditos;
        this.estadoCuenta = estadoCuenta;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    public ResumenSistema resumenSistema() {
        return new ResumenSistema(tiendas.count(), tiendas.countByEstado(Tienda.EstadoTienda.ACTIVA), usuarios.count(),
                clientes.count(), compras.count(), audit.recientes(8));
    }

    public ResumenNegocio resumenNegocio() {
        Long idTienda = currentUser.tiendaId();
        List<Cliente> todos = clientes.findByIdTiendaOrderById(idTienda);
        List<Cliente> activos = todos.stream().filter(Cliente::activo).toList();
        double deuda = round2(todos.stream().mapToDouble(c -> creditos.deudaVigente(c.getId())).sum());
        double disponible = round2(activos.stream().mapToDouble(c -> creditos.disponible(c.getId())).sum());
        String nombre = tiendas.findById(idTienda).map(Tienda::getNombreComercial).orElse("—");
        return new ResumenNegocio(nombre, activos.size(), productos.countByIdTiendaAndEstado(idTienda, EstadoActivo.ACTIVO),
                deuda, disponible, proximosVencimientos(todos), audit.recientes(8));
    }

    private List<Vencimiento> proximosVencimientos(List<Cliente> delNegocio) {
        LocalDate hoy = LocalDate.now();
        List<Vencimiento> items = new ArrayList<>();
        for (Cliente c : delNegocio) {
            String nombre = c.nombreCompleto();
            for (LocalDate fp : estadoCuenta.ciclosPendientes(c.getId())) {
                var e = estadoCuenta.estadoCiclo(c.getId(), fp, fp);
                if (e != null) {
                    items.add(new Vencimiento("c" + c.getId() + "-" + fp, fp, c.getId(), nombre,
                            "Listado fin de mes (" + e.liquidacion().lineas().size() + " compras)",
                            e.liquidacion().totalExigible(), fp.isBefore(hoy)));
                }
            }
            for (var q : creditos.cuotasPendientes(c.getId())) {
                if (!q.pagable()) continue;
                LocalDate f = q.cuota().getFechaVencimiento();
                items.add(new Vencimiento("q" + q.cuota().getId(), f, c.getId(), nombre,
                        "Cuota " + q.cuota().getNumeroCuota() + "/" + q.compra().getNumeroCuotas() + " · " + q.descripcion(),
                        q.cuota().getMontoCuota().doubleValue(), f.isBefore(hoy)));
            }
        }
        return items.stream().sorted(Comparator.comparing(Vencimiento::fecha)).limit(10).toList();
    }
}
