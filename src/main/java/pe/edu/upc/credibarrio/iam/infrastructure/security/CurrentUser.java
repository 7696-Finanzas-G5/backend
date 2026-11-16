package pe.edu.upc.credibarrio.iam.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pe.edu.upc.credibarrio.shared.domain.DomainException;
import pe.edu.upc.credibarrio.shared.domain.Enums.Rol;

import java.util.Optional;

/** Acceso al usuario autenticado de la petición actual. */
@Component
public class CurrentUser {

    public Optional<UserPrincipal> get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof UserPrincipal p ? Optional.of(p) : Optional.empty();
    }

    public UserPrincipal require() {
        return get().orElseThrow(() -> new DomainException("Sesión no iniciada."));
    }

    public Long idOrNull() {
        return get().map(UserPrincipal::id).orElse(null);
    }

    public Rol rol() {
        return require().rol();
    }

    public boolean is(Rol rol) {
        return get().map(p -> p.rol() == rol).orElse(false);
    }

    /** Tienda del administrador del negocio en sesión. */
    public Long tiendaId() {
        Long id = require().idTienda();
        if (id == null) throw new DomainException("El usuario no pertenece a una tienda.");
        return id;
    }
}
