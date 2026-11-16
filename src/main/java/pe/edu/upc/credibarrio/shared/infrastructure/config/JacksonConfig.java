package pe.edu.upc.credibarrio.shared.infrastructure.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Formato de fechas compatible con el frontend: fechas {@code YYYY-MM-DD} y fecha-hora
 * {@code YYYY-MM-DDTHH:mm} (sin zona horaria). Al leer se aceptan también los segundos.
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Bean
    Jackson2ObjectMapperBuilderCustomizer dateTimeFormat() {
        return builder -> builder
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(OUT))
                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}
