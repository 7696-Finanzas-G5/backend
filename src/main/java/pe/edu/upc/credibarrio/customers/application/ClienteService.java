package pe.edu.upc.credibarrio.customers.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.credibarrio.audit.application.AuditService;
import pe.edu.upc.credibarrio.customers.domain.model.Cliente;
import pe.edu.upc.credibarrio.customers.domain.model.CondicionCredito;
import pe.edu.upc.credibarrio.customers.domain.model.TerminosCredito;
import pe.edu.upc.credibarrio.customers.domain.repositories.ClienteRepository;
import pe.edu.upc.credibarrio.customers.domain.repositories.CondicionCreditoRepository;
import pe.edu.upc.credibarrio.iam.application.UsuarioService;
import pe.edu.upc.credibarrio.iam.application.UsuarioService.ActualizarCredencialesCommand;
import pe.edu.upc.credibarrio.iam.application.UsuarioService.RegistrarUsuarioCommand;
import pe.edu.upc.credibarrio.iam.domain.model.Usuario;
import pe.edu.upc.credibarrio.iam.infrastructure.security.CurrentUser;
import pe.edu.upc.credibarrio.iam.infrastructure.security.UserPrincipal;
import pe.edu.upc.credibarrio.shared.domain.DomainException;
import pe.edu.upc.credibarrio.shared.domain.Enums.EstadoActivo;
import pe.edu.upc.credibarrio.shared.domain.Enums.Rol;
import pe.edu.upc.credibarrio.shared.domain.NotFoundException;

import java.util.List;
import java.util.Optional;

/** Casos de uso: alta/baja de clientes y configuración de su línea de crédito. */
@Service
@Transactional
public class ClienteService {

    /** Acceso opcional del cliente con el rol Cliente / Vecino. */
    public record Acceso(String username, String clave) {
    }

    public record GuardarClienteCommand(String nombres, String apellidos, String dni, String direccion, String telefono,
                                        TerminosCredito terminos, boolean activo, Acceso acceso) {
    }

    private final ClienteRepository clientes;
    private final CondicionCreditoRepository condiciones;
    private final UsuarioService usuarios;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public ClienteService(ClienteRepository clientes, CondicionCreditoRepository condiciones, UsuarioService usuarios,
                          CurrentUser currentUser, AuditService audit) {
        this.clientes = clientes;
        this.condiciones = condiciones;
        this.usuarios = usuarios;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    // --------------------------------------------------------------- consultas

    /** Clientes visibles: todos para el admin del sistema, los de su tienda para el negocio. */
    @Transactional(readOnly = true)
    public List<Cliente> listar() {
        return currentUser.is(Rol.ADMIN_SISTEMA) ? clientes.findAll() : clientes.findByIdTiendaOrderById(currentUser.tiendaId());
    }

    @Transactional(readOnly = true)
    public List<Cliente> deTienda(Long idTienda) {
        return clientes.findByIdTiendaOrderById(idTienda);
    }

    /**
     * Cliente accesible por el usuario en sesión: el admin del sistema ve todos, el negocio solo los
     * de su tienda y el cliente / vecino solo a sí mismo. En otro caso responde 404.
     */
    @Transactional(readOnly = true)
    public Cliente obtener(Long id) {
        Cliente c = clientes.findById(id).orElseThrow(() -> new NotFoundException("Cliente no encontrado."));
        UserPrincipal u = currentUser.require();
        boolean permitido = switch (u.rol()) {
            case ADMIN_SISTEMA -> true;
            case ADMIN_NEGOCIO -> c.getIdTienda().equals(u.idTienda());
            case CLIENTE -> c.getId().equals(u.idCliente());
        };
        if (!permitido) throw new NotFoundException("Cliente no encontrado.");
        return c;
    }

    @Transactional(readOnly = true)
    public Optional<CondicionCredito> condicionVigente(Long idCliente) {
        return condiciones.findVigente(idCliente);
    }

    @Transactional(readOnly = true)
    public Optional<CondicionCredito> condicionPorId(Long idCondicion) {
        return condiciones.findById(idCondicion);
    }

    @Transactional(readOnly = true)
    public List<CondicionCredito> historialCondiciones(Long idCliente) {
        obtener(idCliente);
        return condiciones.findByIdClienteOrderByIdDesc(idCliente);
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> usuarioDe(Long idCliente) {
        return usuarios.usuarioDeCliente(idCliente);
    }

    @Transactional(readOnly = true)
    public boolean isDniTaken(String dni, Long exceptId) {
        return clientes.findByIdTiendaAndDni(currentUser.tiendaId(), dni).filter(c -> !c.getId().equals(exceptId)).isPresent();
    }

    // ---------------------------------------------------------------- comandos

    public Cliente registrar(GuardarClienteCommand cmd) {
        Long idTienda = currentUser.tiendaId();
        TerminosCredito terminos = cmd.terminos().validar();
        if (isDniTaken(cmd.dni(), null)) throw new DomainException("Ya existe un cliente con este DNI en la tienda.");
        if (tieneAcceso(cmd) && usuarios.isUsernameTaken(cmd.acceso().username(), null)) {
            throw new DomainException("El nombre de usuario ya está en uso.");
        }
        EstadoActivo estado = cmd.activo() ? EstadoActivo.ACTIVO : EstadoActivo.INACTIVO;
        Cliente cliente = clientes.save(new Cliente(idTienda, cmd.nombres(), cmd.apellidos(), cmd.dni(), cmd.direccion(),
                cmd.telefono(), estado));
        condiciones.save(new CondicionCredito(cliente.getId(), terminos));
        if (tieneAcceso(cmd)) {
            usuarios.registrar(new RegistrarUsuarioCommand(cmd.acceso().username(), cmd.acceso().clave(), Rol.CLIENTE,
                    idTienda, cliente.getId(), estado));
        }
        audit.registrar("ALTA", "cliente", cliente.getId(), cliente.nombreCompleto());
        return cliente;
    }

    public Cliente actualizar(Long id, GuardarClienteCommand cmd) {
        Cliente cliente = obtener(id);
        TerminosCredito terminos = cmd.terminos().validar();
        if (isDniTaken(cmd.dni(), id)) throw new DomainException("Ya existe un cliente con este DNI en la tienda.");
        EstadoActivo estado = cmd.activo() ? EstadoActivo.ACTIVO : EstadoActivo.INACTIVO;

        Optional<Usuario> usuario = usuarios.usuarioDeCliente(id);
        if (tieneAcceso(cmd)) {
            if (usuario.isPresent()) {
                usuarios.actualizarCredenciales(usuario.get().getId(),
                        new ActualizarCredencialesCommand(cmd.acceso().username(), cmd.acceso().clave(), estado));
            } else {
                usuarios.registrar(new RegistrarUsuarioCommand(cmd.acceso().username(), cmd.acceso().clave(), Rol.CLIENTE,
                        cliente.getIdTienda(), id, estado));
            }
        } else {
            usuarios.sincronizarEstadoCliente(id, estado);
        }

        cliente.actualizarDatos(cmd.nombres(), cmd.apellidos(), cmd.dni(), cmd.direccion(), cmd.telefono());
        cliente.cambiarEstado(estado);

        Optional<CondicionCredito> actual = condiciones.findVigente(id);
        if (actual.isEmpty() || !actual.get().mismosTerminos(terminos)) {
            // Versionado: las compras ya registradas conservan la condición con la que fueron pactadas.
            actual.ifPresent(CondicionCredito::pasarAHistorica);
            condiciones.save(new CondicionCredito(id, terminos));
        }
        audit.registrar("EDITAR", "cliente", id, cliente.nombreCompleto());
        return cliente;
    }

    public Cliente cambiarEstado(Long id, boolean activo) {
        Cliente c = obtener(id);
        EstadoActivo estado = activo ? EstadoActivo.ACTIVO : EstadoActivo.INACTIVO;
        c.cambiarEstado(estado);
        usuarios.sincronizarEstadoCliente(id, estado);
        audit.registrar(activo ? "ALTA" : "BAJA", "cliente", id, c.nombreCompleto());
        return c;
    }

    private static boolean tieneAcceso(GuardarClienteCommand cmd) {
        return cmd.acceso() != null && cmd.acceso().username() != null && !cmd.acceso().username().isBlank();
    }
}
