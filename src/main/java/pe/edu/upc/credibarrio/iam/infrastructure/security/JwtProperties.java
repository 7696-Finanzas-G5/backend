package pe.edu.upc.credibarrio.iam.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret          clave HMAC (mínimo 32 caracteres)
 * @param expirationHours vigencia del token en horas
 */
@ConfigurationProperties(prefix = "credibarrio.jwt")
public record JwtProperties(String secret, long expirationHours) {
}
