package pe.edu.upc.credibarrio.shared.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseUrlEnvironmentPostProcessorTest {

    @Test
    void convierteLaServiceUriDeAiven() {
        Map<String, Object> p = DatabaseUrlEnvironmentPostProcessor.fromServiceUri(
                "postgres://avnadmin:AVNS_x%40y%3Az@pg-credibarrio-demo.aivencloud.com:12345/defaultdb?sslmode=require");
        assertThat(p).containsEntry("spring.datasource.url",
                        "jdbc:postgresql://pg-credibarrio-demo.aivencloud.com:12345/defaultdb?sslmode=require")
                .containsEntry("spring.datasource.username", "avnadmin")
                .containsEntry("spring.datasource.password", "AVNS_x@y:z");
    }

    @Test
    void aceptaElEsquemaPostgresqlYPuertoPorDefecto() {
        Map<String, Object> p = DatabaseUrlEnvironmentPostProcessor.fromServiceUri("postgresql://u:p@host/db");
        assertThat(p).containsEntry("spring.datasource.url", "jdbc:postgresql://host:5432/db");
    }

    @Test
    void ignoraUnaUrlJdbc() {
        assertThat(DatabaseUrlEnvironmentPostProcessor.fromServiceUri("jdbc:postgresql://h:1/db?sslmode=require")).isEmpty();
    }
}
