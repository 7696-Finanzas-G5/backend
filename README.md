# CrediBarrio · Backend

## Ejecutar

```bash
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

- API: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Consola H2: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:file:./data/credibarrio`, usuario `sa`)

Por defecto usa H2 en archivo (`./data`) y carga los datos de demostración del frontend.
Para MySQL: `./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql` (variables `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`).

| Variable | Por defecto | Uso |
|---|---|---|
| `JWT_SECRET` | secreto de desarrollo | Clave HMAC del JWT (≥ 32 caracteres). **Cambiar en producción.** |
| `JWT_EXPIRATION_HOURS` | `8` | Vigencia del token |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Orígenes del frontend, separados por coma |
| `SEED_DEMO_DATA` | `true` | Carga datos demo si la base está vacía |
| `PORT` | `8080` | Puerto HTTP |

Usuarios demo: `admin / Admin2026` (Administrador del Sistema), `bodega.sanpedro / Negocio2026` (Administrador del
Negocio), `juan.perez / Cliente2026` (Cliente / Vecino).

## Arquitectura

Mismos bounded contexts que el frontend (`src/app/*`), cada uno con `domain` / `application` / `interfaces.rest`:

| Contexto | Responsabilidad |
|---|---|
| `iam` | Usuarios, roles, login JWT, cambio y restablecimiento de clave |
| `stores` | Alta/baja de tiendas multi-giro y su administrador |
| `catalog` | Productos y servicios (precio contado / crédito, modalidad permitida) |
| `customers` | Clientes y condición de crédito **versionada** (tasas compensatoria y moratoria) |
| `credits` | Simulación y registro de compras, cronograma francés, deuda vigente y crédito disponible |
| `billing` | Estado de cuenta al corte, liquidación fin de mes, mora y pagos |
| `audit` | Bitácora de auditoría |
| `dashboard` | Resúmenes de inicio por rol |

Las fórmulas están en `shared/domain/finance/FinancialMath.java`, réplica exacta de `financial-math.ts`
(los tests reproducen los juegos de datos 1, 2 y 3 del punto 6.4).

## Autenticación

`POST /api/v1/auth/login` con `{"rol","username","clave"}` devuelve `{token, tokenType, usuario}`. El resto de endpoints
requiere `Authorization: Bearer <token>`. Errores: `{status, error, message, timestamp}` (422 = regla de negocio).

## Endpoints principales

| Método | Ruta | Rol |
|---|---|---|
| POST | `/auth/login` · GET `/auth/me` · POST `/auth/cambiar-clave` | público / autenticado |
| GET, PATCH | `/usuarios`, `/usuarios/{id}/estado`, POST `/usuarios/{id}/restablecer-clave` | ADMIN_SISTEMA |
| GET, POST, PUT, PATCH | `/tiendas`, `/tiendas/{id}`, `/tiendas/{id}/estado` | ADMIN_SISTEMA |
| GET, POST, PUT, PATCH | `/productos`, `/productos/{id}`, `/productos/{id}/estado` | ADMIN_NEGOCIO |
| GET, POST, PUT, PATCH | `/clientes`, `/clientes/{id}`, `/clientes/{id}/estado`, `/clientes/{id}/condiciones` | ADMIN_NEGOCIO (lectura también CLIENTE propio) |
| GET | `/clientes/{id}/credito` (condiciones, deuda vigente, disponible), `/clientes/{id}/compras` | negocio / cliente propio |
| POST | `/compras/simulacion`, `/compras` · GET `/compras/{id}`, `/compras/{id}/cuotas` | ADMIN_NEGOCIO |
| GET | `/clientes/{id}/estado-cuenta/ciclos`, `/ciclos/{fechaPago}?fechaEfectiva=` | negocio / cliente propio |
| POST | `/clientes/{id}/estado-cuenta/ciclos/{fechaPago}/pagos` `{fechaEfectiva, monto}` | ADMIN_NEGOCIO |
| GET | `/clientes/{id}/estado-cuenta/cuotas-pendientes?fechaEfectiva=` | negocio / cliente propio |
| POST | `/cuotas/{id}/pagos` `{fechaEfectiva, monto}` | ADMIN_NEGOCIO |
| GET | `/clientes/{id}/pagos`, `/clientes/{id}/liquidaciones` | negocio / cliente propio |
| GET | `/dashboard/sistema`, `/dashboard/negocio`, `/bitacora` | según rol |

Fechas: `YYYY-MM-DD` y fecha-hora `YYYY-MM-DDTHH:mm`, igual que el frontend.

## Tests

```bash
./mvnw test
```

Incluye un test del perfil de producción (`postgres`) contra un PostgreSQL embebido real, sin Docker.

## Despliegue en Render (Blueprint)

`render.yaml` crea el servicio web `credibarrio-backend` (Docker, `Dockerfile`) y la base PostgreSQL `credibarrio-db`,
y conecta ambos con el perfil `postgres`.

1. En Render: **New → Blueprint**, elija este repositorio y la rama `main`, y confirme.
2. Espere el primer despliegue (compila la imagen; puede tardar varios minutos). La salud se verifica en
   `/actuator/health`; la documentación queda en `https://<servicio>.onrender.com/swagger-ui.html`.
3. Si la URL asignada no es `https://credibarrio-backend.onrender.com`, actualícela en
   `src/environments/environment.ts` del frontend.
4. En **Environment** del servicio, reemplace `CORS_ALLOWED_ORIGINS` (por defecto `https://*.vercel.app`) por la URL
   exacta del frontend en Vercel.

Variables que configura el Blueprint: `SPRING_PROFILES_ACTIVE=postgres`, `DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD`
(desde la base de datos), `JWT_SECRET` (generada por Render), `CORS_ALLOWED_ORIGINS` y `SEED_DEMO_DATA`.

> En el plan gratuito el servicio se suspende tras un periodo sin tráfico (la primera petición tarda mientras
> Spring Boot arranca) y la base PostgreSQL gratuita tiene fecha de vencimiento.
