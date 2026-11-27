# CrediBarrio · Backend

## Producción

Hay dos despliegues de producción independientes, cada uno con su propia base de datos PostgreSQL.
**El servicio en uso es el de Railway**; el de Render se mantiene como respaldo (backup).

| | Railway (principal) | Render (backup) |
|---|---|---|
| Base de datos | PostgreSQL de Railway (mismo proyecto) | PostgreSQL de Aiven |
| Swagger UI | https://credibarrio-backend-production.up.railway.app/swagger-ui/index.html | https://credibarrio-backend.onrender.com/swagger-ui/index.html |
| API | `https://credibarrio-backend-production.up.railway.app/api/v1` | `https://credibarrio-backend.onrender.com/api/v1` |
| Salud | https://credibarrio-backend-production.up.railway.app/actuator/health | https://credibarrio-backend.onrender.com/actuator/health |

> Los datos no se comparten entre ambos despliegues. En el plan gratuito de Render, si el servicio estaba inactivo,
> la primera petición puede tardar mientras arranca.

## Ejecutar en local

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
requiere `Authorization: Bearer <token>`.

**Registro público de un negocio:** `POST /api/v1/auth/registro` con
`{"nombreComercial","ruc","direccion","giroNegocio","username","clave"}` crea la tienda (activa) y su usuario
Administrador del Negocio, y devuelve lo mismo que el login (`201`, sesión ya iniciada). Los giros válidos se obtienen
sin token en `GET /api/v1/tiendas/giros`. Errores: `{status, error, message, timestamp}` (422 = regla de negocio).

## Endpoints principales

| Método | Ruta | Rol |
|---|---|---|
| POST | `/auth/registro` · GET `/tiendas/giros` | público |
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

## Despliegue

Ambos despliegues construyen la imagen con el `Dockerfile` y usan el perfil `postgres`. `DB_URL` acepta una URI
(`postgres://…` o `postgresql://…`, usuario y clave incluidos) o una URL JDBC (`jdbc:postgresql://<host>:<puerto>/<base>`)
junto con `DB_USERNAME` y `DB_PASSWORD`.

| Variable | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `postgres` |
| `DB_URL` | URI de la base de datos (ver cada despliegue) |
| `DB_POOL_SIZE` | `5` |
| `JWT_SECRET` | cadena aleatoria de ≥ 32 caracteres |
| `JWT_EXPIRATION_HOURS` | `8` |
| `CORS_ALLOWED_ORIGINS` | URL exacta del frontend en Vercel (varias, separadas por coma) |
| `SEED_DEMO_DATA` | `true` |

### Railway + PostgreSQL de Railway (principal)

1. En Railway: **New Project → Deploy from GitHub repo**, elija este repositorio (rama `main`). Railway detecta el
   `Dockerfile` y asigna la variable `PORT` automáticamente.
2. En el mismo proyecto: **New → Database → PostgreSQL**.
3. En **Variables** del servicio del backend defina las variables de la tabla, con
   `DB_URL=${{Postgres.DATABASE_URL}}` (referencia a la base del proyecto) y `JWT_SECRET` ingresado a mano.
4. En **Settings → Networking** genere el dominio público
   (`https://credibarrio-backend-production.up.railway.app`) y, en **Settings → Deploy**, use `/actuator/health`
   como healthcheck.
5. Espere el primer despliegue. Hibernate crea las tablas y se cargan los datos de demostración.

### Render (Blueprint) + PostgreSQL de Aiven (backup)

`render.yaml` crea el servicio web `credibarrio-backend` con las variables de la tabla (`JWT_SECRET` la genera Render).

1. En [Aiven](https://console.aiven.io): cree un servicio **PostgreSQL** (plan gratuito) en una región cercana a
   `oregon` (oeste de EE. UU.). En **Overview** copie la **Service URI**:
   `postgres://avnadmin:<clave>@<host>.aivencloud.com:<puerto>/defaultdb?sslmode=require`.
2. En Render: **New → Blueprint**, elija este repositorio (rama `main`). Cuando pida `DB_URL`, pegue la Service URI.
3. Espere el primer despliegue (puede tardar varios minutos). Hibernate crea las tablas y se cargan los datos de
   demostración.

Aiven exige SSL: `sslmode=require` cifra la conexión (no verifica el certificado del servidor).

> Si alguna URL asignada es distinta, actualícela en `src/environments/environment.ts` del frontend. Revise las
> condiciones vigentes de cada plan: crédito mensual de Railway, suspensión por inactividad de Render y límite de
> conexiones y apagado por inactividad de Aiven.
