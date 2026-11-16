package pe.edu.upc.credibarrio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perfil de producción ({@code postgres}, el que usa Render) contra un PostgreSQL real embebido:
 * esquema generado por Hibernate, datos demo, compra, pago e imagen grande del catálogo.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
@ActiveProfiles("postgres")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgresProfileIntegrationTest {

    private static final EmbeddedPostgres PG;

    static {
        try {
            PG = EmbeddedPostgres.start();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo iniciar PostgreSQL embebido", e);
        }
        // Igual que en Render + Aiven: una "Service URI" en DB_URL (usuario y clave incluidos)
        System.setProperty("DB_URL", "postgres://postgres:postgres@localhost:" + PG.getPort() + "/postgres?sslmode=prefer");
    }

    @AfterAll
    static void stop() throws IOException {
        System.clearProperty("DB_URL");
        PG.close();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private String login(String rol, String user, String clave) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"%s\",\"username\":\"%s\",\"clave\":\"%s\"}".formatted(rol, user, clave)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(body).get("token").asText();
    }

    private JsonNode call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, String token,
                          int expected) throws Exception {
        String body = mvc.perform(b.header("Authorization", token).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : mapper.readTree(body);
    }

    @Test
    void flujoPrincipalSobrePostgres() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());

        String negocio = login("ADMIN_NEGOCIO", "bodega.sanpedro", "Negocio2026");
        long juan = 0;
        for (JsonNode c : call(get("/api/v1/clientes"), negocio, 200)) {
            if (c.get("dni").asText().equals("74859612")) {
                juan = c.get("id").asLong();
                assertThat(c.get("disponible").asDouble()).isEqualTo(716.04);
                assertThat(c.at("/condicionVigente/tasas/0/valorTasa").asDouble()).isEqualTo(0.24);
            }
        }

        // Imagen de ~540 000 caracteres (límite de 400 KB del frontend en base64)
        String imagen = "data:image/png;base64," + "A".repeat(540_000);
        JsonNode producto = call(post("/api/v1/productos").content("""
                {"tipoItem":"PRODUCTO","descripcion":"Arroz Costeño x 5 kg","unidadMedida":"Bolsa","imagenUrl":"%s",
                 "precioContado":20,"precioCredito":22.5,"modalidadPermitida":"AMBAS","estado":"ACTIVO"}
                """.formatted(imagen)), negocio, 201);
        assertThat(producto.get("imagenUrl").asText()).hasSize(imagen.length());

        JsonNode compra = call(post("/api/v1/compras").content("""
                {"idCliente":%d,"idProducto":%d,"cantidad":2,"fechaHoraCompra":"2026-09-15T10:00","modalidad":"CUOTAS","n":2}
                """.formatted(juan, producto.get("id").asLong())), negocio, 201);
        assertThat(call(get("/api/v1/compras/" + compra.get("id").asLong() + "/cuotas"), negocio, 200)).hasSize(2);

        JsonNode pago = call(post("/api/v1/clientes/" + juan + "/estado-cuenta/ciclos/2026-09-26/pagos")
                .content("{\"fechaEfectiva\":\"2026-10-01\",\"monto\":182.22}"), negocio, 201);
        assertThat(pago.at("/imputacion/capital").asDouble()).isEqualTo(180.0);

        String admin = login("ADMIN_SISTEMA", "admin", "Admin2026");
        assertThat(call(get("/api/v1/dashboard/sistema"), admin, 200).get("tiendas").asInt()).isEqualTo(4);
    }
}
