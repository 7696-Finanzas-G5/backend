package pe.edu.upc.credibarrio.iam.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import pe.edu.upc.credibarrio.shared.domain.Enums.Rol;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationHours;

    public JwtService(JwtProperties props) {
        if (props.secret() == null || props.secret().length() < 32) {
            throw new IllegalStateException("credibarrio.jwt.secret debe tener al menos 32 caracteres.");
        }
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
        this.expirationHours = props.expirationHours();
    }

    public String generate(UserPrincipal user) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(user.id()))
                .claim("username", user.username())
                .claim("rol", user.rol().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationHours, ChronoUnit.HOURS)));
        if (user.idTienda() != null) builder.claim("idTienda", user.idTienda());
        if (user.idCliente() != null) builder.claim("idCliente", user.idCliente());
        return builder.signWith(key).compact();
    }

    /** Valida firma y expiración; lanza {@link io.jsonwebtoken.JwtException} si el token no es válido. */
    public UserPrincipal parse(String token) {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new UserPrincipal(
                Long.valueOf(c.getSubject()),
                c.get("username", String.class),
                Rol.valueOf(c.get("rol", String.class)),
                toLong(c.get("idTienda")),
                toLong(c.get("idCliente")));
    }

    private static Long toLong(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }
}
