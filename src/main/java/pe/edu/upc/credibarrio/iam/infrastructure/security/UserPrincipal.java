package pe.edu.upc.credibarrio.iam.infrastructure.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import pe.edu.upc.credibarrio.shared.domain.Enums.Rol;

import java.util.Collection;
import java.util.List;

/** Usuario autenticado, reconstruido a partir de los claims del JWT. */
public record UserPrincipal(Long id, String username, Rol rol, Long idTienda, Long idCliente) {

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
    }
}
