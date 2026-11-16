package pe.edu.upc.credibarrio.shared.infrastructure.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Permite configurar la base de datos con la "Service URI" que entregan proveedores como Aiven
 * ({@code postgres://usuario:clave@host:puerto/base?sslmode=require}) en la variable {@code DB_URL}.
 * La traduce a {@code spring.datasource.url} (JDBC), {@code username} y {@code password}.
 * Si {@code DB_URL} ya es una URL JDBC no hace nada.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "credibarrioDatabaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        String raw = env.getProperty("DB_URL");
        if (raw == null || raw.isBlank()) return;
        Map<String, Object> props = fromServiceUri(raw.trim());
        if (!props.isEmpty()) env.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, props));
    }

    /** Convierte {@code postgres://u:p@h:port/db?params} en propiedades de Spring; vacío si no es una Service URI. */
    static Map<String, Object> fromServiceUri(String raw) {
        if (!raw.startsWith("postgres://") && !raw.startsWith("postgresql://")) return Map.of();
        URI uri = URI.create(raw);
        Map<String, Object> props = new HashMap<>();
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        props.put("spring.datasource.url", "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getRawPath() + query);
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null && !userInfo.isEmpty()) {
            int sep = userInfo.indexOf(':');
            String user = sep >= 0 ? userInfo.substring(0, sep) : userInfo;
            props.put("spring.datasource.username", URLDecoder.decode(user, StandardCharsets.UTF_8));
            if (sep >= 0) {
                props.put("spring.datasource.password", URLDecoder.decode(userInfo.substring(sep + 1), StandardCharsets.UTF_8));
            }
        }
        return props;
    }
}
