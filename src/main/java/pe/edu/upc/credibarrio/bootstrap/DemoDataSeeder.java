package pe.edu.upc.credibarrio.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.credibarrio.audit.domain.model.RegistroAuditoria;
import pe.edu.upc.credibarrio.audit.domain.repositories.BitacoraRepository;
import pe.edu.upc.credibarrio.catalog.domain.model.ProductoServicio;
import pe.edu.upc.credibarrio.catalog.domain.model.ProductoServicio.TipoItem;
import pe.edu.upc.credibarrio.catalog.domain.repositories.ProductoRepository;
import pe.edu.upc.credibarrio.credits.domain.model.Compra;
import pe.edu.upc.credibarrio.credits.domain.model.Cuota;
import pe.edu.upc.credibarrio.credits.domain.model.DetalleCompra;
import pe.edu.upc.credibarrio.credits.domain.repositories.CompraRepository;
import pe.edu.upc.credibarrio.credits.domain.repositories.CuotaRepository;
import pe.edu.upc.credibarrio.customers.domain.model.Cliente;
import pe.edu.upc.credibarrio.customers.domain.model.CondicionCredito;
import pe.edu.upc.credibarrio.customers.domain.model.TerminosCredito;
import pe.edu.upc.credibarrio.customers.domain.repositories.ClienteRepository;
import pe.edu.upc.credibarrio.customers.domain.repositories.CondicionCreditoRepository;
import pe.edu.upc.credibarrio.iam.domain.model.Usuario;
import pe.edu.upc.credibarrio.iam.domain.repositories.UsuarioRepository;
import pe.edu.upc.credibarrio.shared.domain.Enums.*;
import pe.edu.upc.credibarrio.shared.domain.finance.FinancialMath;
import pe.edu.upc.credibarrio.stores.domain.model.Tienda;
import pe.edu.upc.credibarrio.stores.domain.model.Tienda.EstadoTienda;
import pe.edu.upc.credibarrio.stores.domain.repositories.TiendaRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Datos de demostración (mismos que {@code bootstrap/demo-seed.ts} del frontend). Reproducen los mockups (6.2)
 * y los juegos de datos de prueba (6.4). Solo se cargan si la base de datos está vacía.
 *
 * <p>Credenciales: admin / Admin2026 · bodega.sanpedro / Negocio2026 · juan.perez / Cliente2026
 * (maria.torres, jorge.ramirez y ana.perez también usan Negocio2026).</p>
 */
@Component
@ConditionalOnProperty(name = "credibarrio.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final TiendaRepository tiendas;
    private final UsuarioRepository usuarios;
    private final ProductoRepository productos;
    private final ClienteRepository clientes;
    private final CondicionCreditoRepository condiciones;
    private final CompraRepository compras;
    private final CuotaRepository cuotas;
    private final BitacoraRepository bitacora;
    private final PasswordEncoder encoder;

    public DemoDataSeeder(TiendaRepository tiendas, UsuarioRepository usuarios, ProductoRepository productos,
                          ClienteRepository clientes, CondicionCreditoRepository condiciones, CompraRepository compras,
                          CuotaRepository cuotas, BitacoraRepository bitacora, PasswordEncoder encoder) {
        this.tiendas = tiendas;
        this.usuarios = usuarios;
        this.productos = productos;
        this.clientes = clientes;
        this.condiciones = condiciones;
        this.compras = compras;
        this.cuotas = cuotas;
        this.bitacora = bitacora;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarios.count() > 0) {
            completarImagenesDemo();
            return;
        }
        log.info("Cargando datos de demostración de CrediBarrio…");

        String hAdmin = encoder.encode("Admin2026");
        String hNeg = encoder.encode("Negocio2026");
        String hCli = encoder.encode("Cliente2026");

        Tienda sanPedro = tiendas.save(new Tienda("Bodega San Pedro", "20123456789", "Urb. San Martín", "Bodega", EstadoTienda.ACTIVA));
        Tienda frutas = tiendas.save(new Tienda("Frutas del Valle", "20567890123", "Av. Los Olivos", "Frutería", EstadoTienda.ACTIVA));
        Tienda panaderia = tiendas.save(new Tienda("Panadería Don Luis", "20678901234", "Calle Central", "Panadería", EstadoTienda.ACTIVA));
        Tienda spa = tiendas.save(new Tienda("Spa Bella Vida", "20456789012", "Urb. Miraflores", "Spa", EstadoTienda.INACTIVA));

        usuarios.save(new Usuario("admin", hAdmin, Rol.ADMIN_SISTEMA, null, null, EstadoActivo.ACTIVO));
        usuarios.save(new Usuario("bodega.sanpedro", hNeg, Rol.ADMIN_NEGOCIO, sanPedro.getId(), null, EstadoActivo.ACTIVO));
        usuarios.save(new Usuario("maria.torres", hNeg, Rol.ADMIN_NEGOCIO, frutas.getId(), null, EstadoActivo.ACTIVO));
        usuarios.save(new Usuario("jorge.ramirez", hNeg, Rol.ADMIN_NEGOCIO, panaderia.getId(), null, EstadoActivo.ACTIVO));
        usuarios.save(new Usuario("ana.perez", hNeg, Rol.ADMIN_NEGOCIO, spa.getId(), null, EstadoActivo.INACTIVO));

        Long t1 = sanPedro.getId();
        ProductoServicio canasta = productos.save(producto(t1, "P-001", TipoItem.PRODUCTO, "Alicorp", "Primor", "Canasta Familiar Mensual", "Pack x 12 unidades", 550, 600, ModalidadPermitida.AMBAS, EstadoActivo.ACTIVO));
        productos.save(producto(t1, "P-002", TipoItem.PRODUCTO, "Distribuidora Norte", "Primor", "Aceite Vegetal x Caja", "Caja x 12", 180, 210, ModalidadPermitida.FIN_MES, EstadoActivo.ACTIVO));
        productos.save(producto(t1, "S-001", TipoItem.SERVICIO, "Operador Local", "CrediBarrio Express", "Servicio de Delivery Barrial", "Servicio", 12, 15, ModalidadPermitida.CUOTAS, EstadoActivo.ACTIVO));
        productos.save(producto(t1, "P-003", TipoItem.PRODUCTO, "Mayorista Centro", "Sapolio", "Combo Aseo Hogar", "Pack", 75, 89, ModalidadPermitida.AMBAS, EstadoActivo.INACTIVO));
        ProductoServicio abarrotes = productos.save(producto(t1, "P-004", TipoItem.PRODUCTO, null, "Varios", "Compra de Abarrotes", "Bolsa", 92, 100, ModalidadPermitida.FIN_MES, EstadoActivo.ACTIVO));
        ProductoServicio lacteos = productos.save(producto(t1, "P-005", TipoItem.PRODUCTO, "Gloria S.A.", "Gloria", "Compra de Lácteos", "Pack", 74, 80, ModalidadPermitida.FIN_MES, EstadoActivo.ACTIVO));

        Cliente juan = clientes.save(new Cliente(t1, "Juan", "Pérez Gómez", "74859612", "Calle Los Cedros 240, San Miguel", "987654321", EstadoActivo.ACTIVO));
        Cliente maria = clientes.save(new Cliente(t1, "María", "Castillo Ruiz", "46782915", "Jr. Las Flores 120", null, EstadoActivo.ACTIVO));
        Cliente luis = clientes.save(new Cliente(t1, "Luis", "Rojas Vargas", "70345621", "Av. Principal 455", null, EstadoActivo.ACTIVO));
        Cliente carmen = clientes.save(new Cliente(t1, "Carmen", "Aquino Flores", "42890177", "Psje. Los Pinos 12", null, EstadoActivo.INACTIVO));

        CondicionCredito condJuan = condiciones.save(condicion(juan.getId(), 1500, 3, "0.24", "0.12", 20, 26));
        condiciones.save(condicion(maria.getId(), 2000, 6, "0.22", "0.10", 15, 25));
        condiciones.save(condicion(luis.getId(), 1000, 3, "0.20", "0.10", 10, 25));
        condiciones.save(condicion(carmen.getId(), 800, 2, "0.24", "0.12", 20, 26));

        usuarios.save(new Usuario("juan.perez", hCli, Rol.CLIENTE, t1, juan.getId(), EstadoActivo.ACTIVO));

        // Juego de datos 2: compras a fin de mes antes del corte del 20/09/2026
        compra(juan, condJuan, LocalDateTime.of(2026, 9, 5, 9, 15), ModalidadCompra.FIN_MES, null, abarrotes, LocalDate.of(2026, 9, 26));
        compra(juan, condJuan, LocalDateTime.of(2026, 9, 12, 18, 40), ModalidadCompra.FIN_MES, null, lacteos, LocalDate.of(2026, 9, 26));

        // Juego de datos 1: compra en 3 cuotas con gracia total
        LocalDateTime fechaCuotas = LocalDateTime.of(2026, 9, 15, 11, 30);
        LocalDate pago1 = FinancialMath.fechaPagoCiclo(fechaCuotas.toLocalDate(), 20, 26);
        Compra enCuotas = compra(juan, condJuan, fechaCuotas, ModalidadCompra.CUOTAS, 3, canasta, pago1);
        var cronograma = FinancialMath.cronogramaFrances(600, condJuan.tedCompensatoria(), fechaCuotas.toLocalDate(), pago1, 3);
        cuotas.saveAll(cronograma.filas().stream().map(f -> Cuota.desdeFila(enCuotas.getId(), f)).toList());

        completarImagenesDemo();
        bitacora.save(new RegistroAuditoria(null, "INICIALIZAR", "sistema", null, "Carga de datos de demostración"));
        log.info("Datos de demostración cargados. Usuarios: admin/Admin2026, bodega.sanpedro/Negocio2026, juan.perez/Cliente2026");
    }

    /** Ilustración de cada producto de demostración ("código|descripción" → emoji, fondo y etiqueta). */
    private static final Map<String, String[]> IMAGENES_DEMO = Map.of(
            "P-001|Canasta Familiar Mensual", new String[]{"🧺", "#FDE7C7", "Canasta familiar"},
            "P-002|Aceite Vegetal x Caja", new String[]{"🫒", "#E4F2D7", "Aceite vegetal"},
            "S-001|Servicio de Delivery Barrial", new String[]{"🛵", "#DCEBFA", "Delivery barrial"},
            "P-003|Combo Aseo Hogar", new String[]{"🧴", "#EDE3F7", "Aseo hogar"},
            "P-004|Compra de Abarrotes", new String[]{"🛒", "#FBE0DC", "Abarrotes"},
            "P-005|Compra de Lácteos", new String[]{"🥛", "#E3F1F6", "Lácteos"});

    /**
     * Completa la imagen de los productos de demostración que no la tengan (el enunciado exige imagen). Se ejecuta
     * también sobre una base ya inicializada, sin tocar imágenes que el negocio haya cargado.
     */
    private void completarImagenesDemo() {
        int n = 0;
        for (ProductoServicio p : productos.findAll()) {
            String[] img = IMAGENES_DEMO.get(p.getCodigo() + "|" + p.getDescripcion());
            if (img != null && (p.getImagenUrl() == null || p.getImagenUrl().isBlank())) {
                p.asignarImagenSiFalta(imagenSvg(img[0], img[1], img[2]));
                productos.save(p);
                n++;
            }
        }
        if (n > 0) log.info("Imágenes de demostración asignadas a {} producto(s).", n);
    }

    private static String imagenSvg(String emoji, String fondo, String etiqueta) {
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='240' height='240' viewBox='0 0 240 240'>"
                + "<rect width='240' height='240' rx='28' fill='" + fondo + "'/>"
                + "<text x='120' y='138' font-size='110' text-anchor='middle'>" + emoji + "</text>"
                + "<text x='120' y='206' font-size='20' font-family='Arial, sans-serif' fill='#334' text-anchor='middle'>"
                + etiqueta + "</text></svg>";
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    private static ProductoServicio producto(Long idTienda, String codigo, TipoItem tipo, String proveedor, String marca,
                                             String descripcion, String unidad, double contado, double credito,
                                             ModalidadPermitida modalidad, EstadoActivo estado) {
        return new ProductoServicio(idTienda, codigo, new ProductoServicio.Datos(tipo, proveedor, marca, descripcion, unidad,
                null, BigDecimal.valueOf(contado), BigDecimal.valueOf(credito), modalidad, estado), false);
    }

    private static CondicionCredito condicion(Long idCliente, double limite, int nMax, String comp, String mora,
                                              int corte, int pago) {
        var terminos = new TerminosCredito(Moneda.PEN, BigDecimal.valueOf(limite), nMax, corte, LocalTime.of(23, 59, 59), pago,
                List.of(new TerminosCredito.Tasa(FinalidadTasa.COMPENSATORIA, TipoTasa.EFECTIVA, new BigDecimal(comp), 360, null),
                        new TerminosCredito.Tasa(FinalidadTasa.MORATORIA, TipoTasa.EFECTIVA, new BigDecimal(mora), 360, null)));
        CondicionCredito c = new CondicionCredito(idCliente, terminos);
        c.vigenteDesde(LocalDate.of(2026, 9, 1));
        return c;
    }

    private Compra compra(Cliente cliente, CondicionCredito cond, LocalDateTime fecha, ModalidadCompra modalidad, Integer n,
                          ProductoServicio producto, LocalDate fechaPago) {
        BigDecimal precio = producto.getPrecioCredito();
        Compra c = new Compra(cliente.getId(), cond.getId(), fecha, modalidad, n, precio, fechaPago,
                List.of(new DetalleCompra(producto.getId(), producto.getDescripcion(), 1, precio, precio)));
        c.fechaRegistro(fecha);
        return compras.save(c);
    }
}
