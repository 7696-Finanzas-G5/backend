package pe.edu.upc.credibarrio.iam.application;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.credibarrio.audit.application.AuditService;
import pe.edu.upc.credibarrio.iam.domain.model.Usuario;
import pe.edu.upc.credibarrio.iam.domain.repositories.UsuarioRepository;
import pe.edu.upc.credibarrio.iam.infrastructure.security.CurrentUser;
import pe.edu.upc.credibarrio.shared.domain.DomainException;
import pe.edu.upc.credibarrio.shared.domain.Enums.EstadoActivo;
import pe.edu.upc.credibarrio.shared.domain.Enums.Rol;
import pe.edu.upc.credibarrio.shared.domain.NotFoundException;

import java.util.List;
import java.util.Optional;

/** Casos de uso de administración de usuarios (también usados por otros contextos). */
@Service
@Transactional
public class UsuarioService {

    public record RegistrarUsuarioCommand(String username, String clave, Rol rol, Long idTienda, Long idCliente,
                                          EstadoActivo estado) {
    }

    /** {@code clave} vacía o nula conserva la contraseña actual. */
    public record ActualizarCredencialesCommand(String username, String clave, EstadoActivo estado) {
    }

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public UsuarioService(UsuarioRepository repo, PasswordEncoder encoder, AuditService audit, CurrentUser currentUser) {
        this.repo = repo;
        this.encoder = encoder;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public Usuario obtener(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Usuario no encontrado."));
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> adminDeTienda(Long idTienda) {
        return repo.findFirstByIdTiendaAndRol(idTienda, Rol.ADMIN_NEGOCIO);
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> usuarioDeCliente(Long idCliente) {
        return repo.findByIdCliente(idCliente);
    }

    @Transactional(readOnly = true)
    public boolean isUsernameTaken(String username, Long exceptId) {
        if (username == null || username.isBlank()) return false;
        return repo.findByUsernameIgnoreCase(username.trim()).filter(u -> !u.getId().equals(exceptId)).isPresent();
    }

    public Usuario registrar(RegistrarUsuarioCommand cmd) {
        String username = Usuario.validarUsername(cmd.username());
        Usuario.validarClave(cmd.clave());
        if (isUsernameTaken(username, null)) throw new DomainException("El usuario \"" + username + "\" ya está en uso.");
        Usuario u = repo.save(new Usuario(username, encoder.encode(cmd.clave()), cmd.rol(), cmd.idTienda(),
                cmd.idCliente(), cmd.estado()));
        audit.registrar("ALTA", "usuario", u.getId(), username + " (" + cmd.rol() + ")");
        return u;
    }

    public Usuario actualizarCredenciales(Long id, ActualizarCredencialesCommand cmd) {
        Usuario u = obtener(id);
        String username = Usuario.validarUsername(cmd.username());
        if (isUsernameTaken(username, id)) throw new DomainException("El usuario \"" + username + "\" ya está en uso.");
        u.cambiarUsername(username);
        u.cambiarEstado(cmd.estado());
        if (cmd.clave() != null && !cmd.clave().isEmpty()) {
            Usuario.validarClave(cmd.clave());
            u.cambiarClaveHash(encoder.encode(cmd.clave()));
        }
        audit.registrar("EDITAR", "usuario", id, username);
        return u;
    }

    public Usuario cambiarEstado(Long id, EstadoActivo estado) {
        if (id.equals(currentUser.idOrNull())) throw new DomainException("No puede desactivar su propio usuario.");
        Usuario u = obtener(id);
        u.cambiarEstado(estado);
        audit.registrar(estado == EstadoActivo.ACTIVO ? "ACTIVAR" : "DESACTIVAR", "usuario", id, u.getUsername());
        return u;
    }

    public void restablecerClave(Long id, String nueva) {
        Usuario.validarClave(nueva);
        Usuario u = obtener(id);
        u.cambiarClaveHash(encoder.encode(nueva));
        audit.registrar("RESTABLECER_CLAVE", "usuario", id, u.getUsername());
    }

    /** Mantiene el acceso del administrador de una tienda alineado con el estado de la tienda. */
    public void sincronizarEstadoAdminTienda(Long idTienda, EstadoActivo estado) {
        adminDeTienda(idTienda).ifPresent(a -> a.cambiarEstado(estado));
    }

    /** Mantiene el acceso del cliente alineado con su estado de alta/baja. */
    public void sincronizarEstadoCliente(Long idCliente, EstadoActivo estado) {
        usuarioDeCliente(idCliente).ifPresent(u -> u.cambiarEstado(estado));
    }
}
