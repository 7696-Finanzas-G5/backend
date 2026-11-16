package pe.edu.upc.credibarrio.iam.application;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.credibarrio.audit.application.AuditService;
import pe.edu.upc.credibarrio.iam.domain.model.Usuario;
import pe.edu.upc.credibarrio.iam.domain.repositories.UsuarioRepository;
import pe.edu.upc.credibarrio.iam.infrastructure.security.CurrentUser;
import pe.edu.upc.credibarrio.iam.infrastructure.security.JwtService;
import pe.edu.upc.credibarrio.iam.infrastructure.security.UserPrincipal;
import pe.edu.upc.credibarrio.shared.domain.DomainException;
import pe.edu.upc.credibarrio.shared.domain.Enums.Rol;
import pe.edu.upc.credibarrio.shared.domain.NotFoundException;

/** Caso de uso: autenticación y sesión del usuario actual. */
@Service
@Transactional
public class AuthService {

    public record LoginResult(String token, Usuario usuario) {
    }

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt, AuditService audit,
                       CurrentUser currentUser) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    public LoginResult login(Rol rol, String username, String clave) {
        Usuario user = usuarios.findByUsernameIgnoreCase(username == null ? "" : username.trim())
                .filter(u -> rol == null || u.getRol() == rol)
                .orElse(null);
        if (user == null || clave == null || !encoder.matches(clave, user.getClaveHash())) {
            audit.registrarComo(null, "LOGIN_FALLIDO", "usuario", user == null ? null : user.getId(),
                    "Intento con usuario \"" + username + "\"");
            throw new DomainException("Usuario, contraseña o rol incorrectos.");
        }
        if (!user.activo()) throw new DomainException("El usuario se encuentra dado de baja.");
        user.registrarAcceso();
        audit.registrarComo(user.getId(), "LOGIN", "usuario", user.getId(), null);
        String token = jwt.generate(new UserPrincipal(user.getId(), user.getUsername(), user.getRol(),
                user.getIdTienda(), user.getIdCliente()));
        return new LoginResult(token, user);
    }

    @Transactional(readOnly = true)
    public Usuario actual() {
        return usuarios.findById(currentUser.require().id())
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado."));
    }

    public void cambiarClave(String actual, String nueva) {
        Usuario user = actual();
        if (actual == null || !encoder.matches(actual, user.getClaveHash())) {
            throw new DomainException("La contraseña actual no es correcta.");
        }
        Usuario.validarClave(nueva);
        user.cambiarClaveHash(encoder.encode(nueva));
        audit.registrar("CAMBIAR_CLAVE", "usuario", user.getId(), null);
    }
}
