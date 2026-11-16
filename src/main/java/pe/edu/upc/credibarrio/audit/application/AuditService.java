package pe.edu.upc.credibarrio.audit.application;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.credibarrio.audit.domain.model.RegistroAuditoria;
import pe.edu.upc.credibarrio.audit.domain.repositories.BitacoraRepository;
import pe.edu.upc.credibarrio.iam.domain.model.Usuario;
import pe.edu.upc.credibarrio.iam.domain.repositories.UsuarioRepository;
import pe.edu.upc.credibarrio.iam.infrastructure.security.CurrentUser;
import pe.edu.upc.credibarrio.shared.domain.Enums.Rol;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AuditService {

    public record RegistroView(Long id, Long idUsuario, String usuario, String accion, String entidad, Long idRegistro,
                               String detalle, LocalDateTime fechaHora) {
    }

    private final BitacoraRepository repo;
    private final UsuarioRepository usuarios;
    private final CurrentUser currentUser;

    public AuditService(BitacoraRepository repo, UsuarioRepository usuarios, CurrentUser currentUser) {
        this.repo = repo;
        this.usuarios = usuarios;
        this.currentUser = currentUser;
    }

    /** Registra una acción del usuario en sesión. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String accion, String entidad, Long idRegistro, String detalle) {
        repo.save(new RegistroAuditoria(currentUser.idOrNull(), accion, entidad, idRegistro, detalle));
    }

    /** Registra una acción a nombre de un usuario concreto (p. ej. login). Se confirma aunque la operación falle. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarComo(Long idUsuario, String accion, String entidad, Long idRegistro, String detalle) {
        repo.save(new RegistroAuditoria(idUsuario, accion, entidad, idRegistro, detalle));
    }

    /** Últimos registros: todos para el admin del sistema, solo los de su tienda para el negocio. */
    @Transactional(readOnly = true)
    public List<RegistroView> recientes(int limite) {
        var page = PageRequest.of(0, Math.max(1, Math.min(limite, 500)));
        List<RegistroAuditoria> registros;
        if (currentUser.is(Rol.ADMIN_SISTEMA)) {
            registros = repo.findAllByOrderByIdDesc(page);
        } else {
            List<Long> propios = usuarios.findByIdTienda(currentUser.tiendaId()).stream().map(Usuario::getId).toList();
            registros = propios.isEmpty() ? List.of() : repo.findByIdUsuarioInOrderByIdDesc(propios, page);
        }
        Map<Long, String> nombres = usuarios.findAll().stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getUsername, (a, b) -> a));
        Function<Long, String> nombre = id -> id == null ? "sistema" : nombres.getOrDefault(id, "—");
        return registros.stream()
                .map(r -> new RegistroView(r.getId(), r.getIdUsuario(), nombre.apply(r.getIdUsuario()), r.getAccion(),
                        r.getEntidad(), r.getIdRegistro(), r.getDetalle(), r.getFechaHora()))
                .toList();
    }
}
