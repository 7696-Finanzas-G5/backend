package pe.edu.upc.credibarrio.iam.infrastructure.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.edu.upc.credibarrio.iam.domain.model.Usuario;
import pe.edu.upc.credibarrio.iam.domain.repositories.UsuarioRepository;
import pe.edu.upc.credibarrio.shared.domain.Enums.EstadoActivo;

import java.io.IOException;

/** Autentica las peticiones con {@code Authorization: Bearer <jwt>}. */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final UsuarioRepository usuarios;

    public JwtAuthenticationFilter(JwtService jwt, UsuarioRepository usuarios) {
        this.jwt = jwt;
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                UserPrincipal principal = jwt.parse(header.substring(7));
                // Un usuario dado de baja pierde el acceso aunque su token siga vigente
                boolean activo = usuarios.findById(principal.id())
                        .map(Usuario::getEstado)
                        .filter(e -> e == EstadoActivo.ACTIVO)
                        .isPresent();
                if (activo) {
                    var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
