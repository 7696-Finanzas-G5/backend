package pe.edu.upc.credibarrio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Flujo completo sobre los datos de demostración (juegos de datos 1 y 2 del Informe). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CredibarrioApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private String login(String rol, String username, String clave) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"%s\",\"username\":\"%s\",\"clave\":\"%s\"}".formatted(rol, username, clave)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("token").asText();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode json(MockHttpServletRequestBuilder b, String token, int expectedStatus) throws Exception {
        String body = mvc.perform(auth(b, token)).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : mapper.readTree(body);
    }

    @Test
    void flujoCompletoDelNegocioYDelCliente() throws Exception {
        // --- autenticación
        mvc.perform(get("/api/v1/clientes")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"CLIENTE\",\"username\":\"admin\",\"clave\":\"Admin2026\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Usuario, contraseña o rol incorrectos."));

        String negocio = login("ADMIN_NEGOCIO", "bodega.sanpedro", "Negocio2026");
        String vecino = login("CLIENTE", "juan.perez", "Cliente2026");
        String admin = login("ADMIN_SISTEMA", "admin", "Admin2026");

        // --- clientes de la tienda
        JsonNode clientes = json(get("/api/v1/clientes"), negocio, 200);
        assertThat(clientes).hasSize(4);
        long juan = 0;
        long maria = 0;
        for (JsonNode c : clientes) {
            if (c.get("dni").asText().equals("74859612")) juan = c.get("id").asLong();
            if (c.get("dni").asText().equals("46782915")) maria = c.get("id").asLong();
        }
        assertThat(juan).isPositive();
        for (JsonNode c : clientes) {
            if (c.get("id").asLong() == juan) assertThat(c.get("disponible").asDouble()).isEqualTo(716.04);
        }
        assertThat(json(get("/api/v1/compras?limite=5"), negocio, 200)).hasSize(3);

        // Deuda vigente: 100 + 80 (fin de mes) + 603.96 (amortización pendiente de las cuotas)
        JsonNode credito = json(get("/api/v1/clientes/" + juan + "/credito"), negocio, 200);
        assertThat(credito.get("deudaVigente").asDouble()).isEqualTo(783.96);
        assertThat(credito.get("disponible").asDouble()).isEqualTo(716.04);

        // --- juego de datos 2: listado de fin de mes con 5 días de mora
        JsonNode ciclos = json(get("/api/v1/clientes/" + juan + "/estado-cuenta/ciclos"), negocio, 200);
        assertThat(ciclos.get(0).asText()).isEqualTo("2026-09-26");
        JsonNode ciclo = json(get("/api/v1/clientes/" + juan + "/estado-cuenta/ciclos/2026-09-26?fechaEfectiva=2026-10-01"), negocio, 200);
        assertThat(ciclo.at("/liquidacion/totalExigible").asDouble()).isEqualTo(181.93);
        // El proceso automático de corte ya generó el listado del 20/09 (y venció el 26/09 sin pagarse)
        assertThat(ciclo.at("/listado/fechaCorte").asText()).isEqualTo("2026-09-20");
        assertThat(ciclo.at("/listado/estado").asText()).isEqualTo("VENCIDA");
        assertThat(ciclo.at("/listado/detalle").size()).isEqualTo(2);
        assertThat(ciclo.at("/mora/totalExigible").asDouble()).isEqualTo(182.22);

        // El vecino puede consultar, pero no registrar pagos
        json(post("/api/v1/clientes/" + juan + "/estado-cuenta/ciclos/2026-09-26/pagos")
                .content("{\"fechaEfectiva\":\"2026-10-01\",\"monto\":182.22}"), vecino, 403);
        json(post("/api/v1/clientes/" + juan + "/estado-cuenta/ciclos/2026-09-26/pagos")
                .content("{\"fechaEfectiva\":\"2026-10-01\",\"monto\":150}"), negocio, 422);
        JsonNode pago = json(post("/api/v1/clientes/" + juan + "/estado-cuenta/ciclos/2026-09-26/pagos")
                .content("{\"fechaEfectiva\":\"2026-10-01\",\"monto\":182.22}"), negocio, 201);
        assertThat(pago.at("/imputacion/mora").asDouble()).isEqualTo(0.29);
        assertThat(pago.at("/imputacion/compensatorio").asDouble()).isEqualTo(1.93);
        assertThat(pago.at("/imputacion/capital").asDouble()).isEqualTo(180.0);
        assertThat(json(get("/api/v1/clientes/" + juan + "/estado-cuenta/ciclos"), negocio, 200)).isEmpty();
        // El pago cancela el mismo listado generado al corte (no crea otro)
        JsonNode liqs = json(get("/api/v1/clientes/" + juan + "/liquidaciones"), negocio, 200);
        assertThat(liqs.size()).isEqualTo(1);
        assertThat(liqs.get(0).get("estado").asText()).isEqualTo("PAGADA");

        // --- juego de datos 1: cuotas en orden
        JsonNode cuotas = json(get("/api/v1/clientes/" + juan + "/estado-cuenta/cuotas-pendientes?fechaEfectiva=2026-10-26"), negocio, 200);
        assertThat(cuotas).hasSize(3);
        assertThat(cuotas.get(0).at("/cuota/montoCuota").asDouble()).isEqualTo(208.65);
        assertThat(cuotas.get(0).get("pagable").asBoolean()).isTrue();
        long cuota2 = cuotas.get(1).at("/cuota/id").asLong();
        json(post("/api/v1/cuotas/" + cuota2 + "/pagos").content("{\"fechaEfectiva\":\"2026-11-26\",\"monto\":208.65}"), negocio, 422);
        long cuota1 = cuotas.get(0).at("/cuota/id").asLong();
        json(post("/api/v1/cuotas/" + cuota1 + "/pagos").content("{\"fechaEfectiva\":\"2026-10-20\",\"monto\":208.65}"), negocio, 201);

        // --- simulación y registro de una compra nueva
        long canasta = 0;
        for (JsonNode p : json(get("/api/v1/productos?soloActivos=true"), negocio, 200)) {
            if (p.get("codigo").asText().equals("P-001")) canasta = p.get("id").asLong();
        }
        String compra = "{\"idCliente\":%d,\"idProducto\":%d,\"cantidad\":1,\"fechaHoraCompra\":\"2026-09-15T10:00\",\"modalidad\":\"CUOTAS\",\"n\":3}"
                .formatted(juan, canasta);
        JsonNode sim = json(post("/api/v1/compras/simulacion").content(compra), negocio, 200);
        assertThat(sim.get("errores")).isEmpty();
        assertThat(sim.at("/cronograma/cuota").asDouble()).isEqualTo(208.65);
        JsonNode registrada = json(post("/api/v1/compras").content(compra), negocio, 201);
        assertThat(registrada.get("fechaHoraCompra").asText()).isEqualTo("2026-09-15T10:00");
        assertThat(json(get("/api/v1/compras/" + registrada.get("id").asLong() + "/cuotas"), negocio, 200)).hasSize(3);

        // --- aislamiento: el vecino solo ve su propia cuenta
        json(get("/api/v1/clientes/" + juan + "/credito"), vecino, 200);
        json(get("/api/v1/clientes/" + maria + "/credito"), vecino, 404);
        json(get("/api/v1/clientes"), vecino, 403);

        // --- administrador del sistema
        JsonNode resumen = json(get("/api/v1/dashboard/sistema"), admin, 200);
        assertThat(resumen.get("tiendas").asLong()).isGreaterThanOrEqualTo(4);
        assertThat(json(get("/api/v1/bitacora"), admin, 200).size()).isGreaterThan(3);
        json(get("/api/v1/dashboard/negocio"), negocio, 200);
    }

    @Test
    void altaDeTiendaYClienteConVersionadoDeCondiciones() throws Exception {
        String admin = login("ADMIN_SISTEMA", "admin", "Admin2026");
        json(post("/api/v1/tiendas").content("""
                {"nombreComercial":"Pollería El Sol","ruc":"20999888777","direccion":"Av. Sol 123",
                 "giroNegocio":"Pollería","activa":true,"adminUsername":"polleria.sol","adminClave":"Polleria2026"}
                """), admin, 201);
        json(post("/api/v1/tiendas").content("""
                {"nombreComercial":"Otra","ruc":"20999888777","direccion":"X","giroNegocio":"Bodega",
                 "activa":true,"adminUsername":"otra.tienda","adminClave":"Otra2026xx"}
                """), admin, 422);

        String negocio = login("ADMIN_NEGOCIO", "polleria.sol", "Polleria2026");
        String cliente = """
                {"nombres":"Rosa","apellidos":"Quispe","dni":"41234567","activo":true,
                 "terminos":{"moneda":"PEN","limiteCredito":%s,"plazoMaxMeses":6,"diaCorte":20,"diaPago":26,
                   "tasas":[{"finalidad":"COMPENSATORIA","tipoTasa":"NOMINAL","valorTasa":0.18,"periodoTasaDias":360,"capitalizacionDias":30},
                            {"finalidad":"MORATORIA","tipoTasa":"EFECTIVA","valorTasa":0.12,"periodoTasaDias":360}]},
                 "acceso":{"username":"rosa.quispe","clave":"Rosa20261"}}
                """;
        JsonNode creado = json(post("/api/v1/clientes").content(cliente.formatted("1000")), negocio, 201);
        long id = creado.get("id").asLong();
        assertThat(creado.get("username").asText()).isEqualTo("rosa.quispe");

        // Mismos términos: no se crea nueva versión; nuevo límite: la vigente pasa a histórica
        json(put("/api/v1/clientes/" + id).content(cliente.formatted("1000")), negocio, 200);
        assertThat(json(get("/api/v1/clientes/" + id + "/condiciones"), negocio, 200)).hasSize(1);
        json(put("/api/v1/clientes/" + id).content(cliente.formatted("1500")), negocio, 200);
        JsonNode versiones = json(get("/api/v1/clientes/" + id + "/condiciones"), negocio, 200);
        assertThat(versiones).hasSize(2);
        assertThat(versiones.get(0).get("estado").asText()).isEqualTo("VIGENTE");
        assertThat(versiones.get(1).get("estado").asText()).isEqualTo("HISTORICA");

        // Otra tienda no ve a este cliente
        String sanPedro = login("ADMIN_NEGOCIO", "bodega.sanpedro", "Negocio2026");
        json(get("/api/v1/clientes/" + id), sanPedro, 404);
    }
}
