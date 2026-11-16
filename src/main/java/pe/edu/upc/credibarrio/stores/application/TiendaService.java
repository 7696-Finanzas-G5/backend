package pe.edu.upc.credibarrio.stores.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.credibarrio.audit.application.AuditService;
import pe.edu.upc.credibarrio.iam.application.UsuarioService;
import pe.edu.upc.credibarrio.iam.application.UsuarioService.ActualizarCredencialesCommand;
import pe.edu.upc.credibarrio.iam.application.UsuarioService.RegistrarUsuarioCommand;
import pe.edu.upc.credibarrio.iam.domain.model.Usuario;
import pe.edu.upc.credibarrio.shared.domain.DomainException;
import pe.edu.upc.credibarrio.shared.domain.Enums.EstadoActivo;
import pe.edu.upc.credibarrio.shared.domain.Enums.Rol;
import pe.edu.upc.credibarrio.shared.domain.NotFoundException;
import pe.edu.upc.credibarrio.stores.domain.model.Tienda;
import pe.edu.upc.credibarrio.stores.domain.model.Tienda.EstadoTienda;
import pe.edu.upc.credibarrio.stores.domain.repositories.TiendaRepository;

import java.util.List;
import java.util.Optional;

/** Casos de uso del Administrador del Sistema: alta y baja de tiendas multi-giro. */
@Service
@Transactional
public class TiendaService {

    /** {@code adminClave} es obligatoria al registrar y opcional al editar. */
    public record GuardarTiendaCommand(String nombreComercial, String ruc, String direccion, String giroNegocio,
                                       boolean activa, String adminUsername, String adminClave) {
    }

    private final TiendaRepository repo;
    private final UsuarioService usuarios;
    private final AuditService audit;

    public TiendaService(TiendaRepository repo, UsuarioService usuarios, AuditService audit) {
        this.repo = repo;
        this.usuarios = usuarios;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Tienda> listar() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public Tienda obtener(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Tienda no encontrada."));
    }

    @Transactional(readOnly = true)
    public Optional<Usuario> adminDe(Long idTienda) {
        return usuarios.adminDeTienda(idTienda);
    }

    @Transactional(readOnly = true)
    public boolean isRucTaken(String ruc, Long exceptId) {
        return repo.findByRuc(ruc).filter(t -> !t.getId().equals(exceptId)).isPresent();
    }

    public Tienda registrar(GuardarTiendaCommand cmd) {
        if (isRucTaken(cmd.ruc(), null)) throw new DomainException("Ya existe una tienda con este RUC.");
        if (usuarios.isUsernameTaken(cmd.adminUsername(), null)) {
            throw new DomainException("El usuario administrador ya está en uso.");
        }
        Tienda tienda = repo.save(new Tienda(cmd.nombreComercial(), cmd.ruc(), cmd.direccion(), cmd.giroNegocio(),
                cmd.activa() ? EstadoTienda.ACTIVA : EstadoTienda.INACTIVA));
        usuarios.registrar(new RegistrarUsuarioCommand(cmd.adminUsername(), cmd.adminClave(), Rol.ADMIN_NEGOCIO,
                tienda.getId(), null, estadoUsuario(cmd.activa())));
        audit.registrar("ALTA", "tienda", tienda.getId(), tienda.getNombreComercial());
        return tienda;
    }

    public Tienda actualizar(Long id, GuardarTiendaCommand cmd) {
        Tienda tienda = obtener(id);
        if (isRucTaken(cmd.ruc(), id)) throw new DomainException("Ya existe una tienda con este RUC.");
        tienda.actualizarDatos(cmd.nombreComercial(), cmd.ruc(), cmd.direccion(), cmd.giroNegocio());
        tienda.cambiarEstado(cmd.activa() ? EstadoTienda.ACTIVA : EstadoTienda.INACTIVA);

        Optional<Usuario> admin = usuarios.adminDeTienda(id);
        if (admin.isPresent()) {
            usuarios.actualizarCredenciales(admin.get().getId(),
                    new ActualizarCredencialesCommand(cmd.adminUsername(), cmd.adminClave(), estadoUsuario(cmd.activa())));
        } else if (cmd.adminClave() != null && !cmd.adminClave().isEmpty()) {
            usuarios.registrar(new RegistrarUsuarioCommand(cmd.adminUsername(), cmd.adminClave(), Rol.ADMIN_NEGOCIO,
                    id, null, estadoUsuario(cmd.activa())));
        }
        audit.registrar("EDITAR", "tienda", id, tienda.getNombreComercial());
        return tienda;
    }

    public Tienda cambiarEstado(Long id, boolean activa) {
        Tienda t = obtener(id);
        t.cambiarEstado(activa ? EstadoTienda.ACTIVA : EstadoTienda.INACTIVA);
        usuarios.sincronizarEstadoAdminTienda(id, estadoUsuario(activa));
        audit.registrar(activa ? "ALTA" : "BAJA", "tienda", id, t.getNombreComercial());
        return t;
    }

    private static EstadoActivo estadoUsuario(boolean activa) {
        return activa ? EstadoActivo.ACTIVO : EstadoActivo.INACTIVO;
    }
}
