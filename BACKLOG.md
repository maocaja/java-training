# Backlog de Ajustes Pre-Cloud — con decisiones de arquitectura

Este documento lista los cambios que necesita el codigo antes de desplegar a AWS. Cada item esta estructurado como un **Architecture Decision Record (ADR)** — contexto, opciones evaluadas, decision, y por que.

El objetivo no es solo saber **que** hacer, sino entender **por que** y pensar como un arquitecto de software.

---

## Principios que guian las decisiones

Antes de los items, estos son los principios que aplicamos en cada decision:

1. **Entornos reproducibles:** lo que corre en mi laptop debe correr igual en AWS, sin sorpresas.
2. **Fail fast, fail safe:** si algo esta mal configurado, que explote al arrancar (con mensaje claro), no en produccion a las 3 AM.
3. **12-Factor App:** [12factor.net](https://12factor.net/) — config en environment, logs a stdout, procesos stateless, graceful shutdown. Es el estandar de la industria.
4. **Security by default:** no correr como root, no commitear secretos, principio de menor privilegio.
5. **Observabilidad desde el dia 1:** no se puede operar lo que no se puede ver.
6. **Evitar over-engineering:** no agregar Kafka si SQS resuelve, no agregar Kubernetes si ECS resuelve. Complejidad justificada.

---

## Backlog overview

| # | Item | Categoria | Bloquea deploy? | Horas |
|---|------|-----------|-----------------|-------|
| B1 | Schema management con Flyway | Datos | Si | 1 |
| B2 | Healthchecks con Actuator | Infraestructura | Si | 0.5 |
| B3 | Secretos fuera del codigo | Seguridad | Si | 0.5 |
| B4 | Perfiles de configuracion | Configuracion | Si | 1 |
| B5 | Graceful shutdown | Reliability | Si | 0.3 |
| B6 | Dockerfiles production-grade | Infraestructura | Si | 1 |
| B7 | Resilience4j en PricingClient | Reliability | No (pero critico) | 1 |
| B8 | Structured logging (JSON) | Observabilidad | No | 1 |
| B9 | Correlation IDs (MDC filter) | Observabilidad | No | 0.5 |
| B10 | Micrometer metrics | Observabilidad | No | 0.5 |

**Total:** ~7 horas de trabajo. Se puede distribuir en varias sesiones.

---

# ADR-001: Gestion de schema de base de datos

## Contexto

La configuracion actual usa `spring.jpa.hibernate.ddl-auto: create-drop` en [property-api/src/main/resources/application.yml:24](property-api/src/main/resources/application.yml#L24). Esto le dice a Hibernate:

> "Al arrancar la aplicacion, borra todas las tablas y recrealas. Al parar, borra todo."

## Problema

Esto esta bien en desarrollo local porque queremos BD limpia en cada run. Pero en produccion:

- **Escenario:** ECS reinicia la task (deploy, auto-scaling, health failure)
- **Resultado:** property-api arranca → Hibernate borra todas las tablas → **datos perdidos**

Ademas, no hay manera de evolucionar el schema. Si mañana agregamos una columna `garage BOOLEAN` a `properties`, ¿como llevamos ese cambio a prod sin perder datos?

## Opciones evaluadas

### Opcion A: Scripts SQL manuales
Escribir archivos `.sql` y ejecutarlos a mano al hacer deploy.

**Pros:** Simple, cero dependencias.
**Contras:** Propenso a error (olvidar una migracion, correrlas en otro orden). Sin registro de "que se aplico ya". Imposible de reproducir en CI.

### Opcion B: Flyway (versionado de schema con SQL)
Archivos `V1__init.sql`, `V2__add_column.sql`, etc. La app los aplica en orden al arrancar.

**Pros:** Industry standard. SQL plano (todos lo saben). Mantiene tabla `flyway_schema_history` con que migraciones se aplicaron. Rollback con nueva migracion (forward-only).
**Contras:** Las migraciones son forward-only (no hay "rollback automatico", se hace con migracion inversa).

### Opcion C: Liquibase (versionado de schema con DSL)
Similar a Flyway pero usa XML/YAML/JSON con abstracciones DB-agnostic.

**Pros:** DB-agnostic (mismo changelog funciona en PostgreSQL y Oracle).
**Contras:** Mas complejo. Curva de aprendizaje del DSL. Overkill si usas una sola BD.

### Opcion D: `ddl-auto: update` (Hibernate auto-modifica schema)
Dejar que Hibernate compare entidades con schema y aplique ALTERs automaticos.

**Pros:** Cero trabajo manual.
**Contras:** **Muy peligroso en prod.** Hibernate puede tomar decisiones incorrectas (ej: borrar una columna con datos si el codigo no la tiene). No tiene control sobre el SQL generado. No es auditable.

## Decision

**Flyway (Opcion B).**

## Por que

- Los 3 JDs que compartiste lo mencionan como expectativa (el de Ion lo pide explicitamente).
- SQL plano = todos lo entienden, no hay DSL propietario que aprender.
- El historial de migraciones vive en git con el codigo = revisable en PR.
- Al arrancar, Flyway valida que el schema este al dia. Si no, aplica las pendientes. Si hay drift, alerta.
- Cambiamos `ddl-auto` a `validate`: Hibernate verifica que las entidades matcheen el schema, no lo modifica.

## Como aplicamos el principio

- **Fail fast:** si el schema no matchea las entidades, la app no arranca. No "funciona medio" en prod.
- **Reproducible:** mismas migraciones corren en dev (H2 o Postgres local) y en prod (RDS).

## Acceptance criteria

- Carpeta `src/main/resources/db/migration/` con al menos 2 archivos: `V1__initial_schema.sql` y `V2__seed_admin_user.sql`.
- `application.yml` con `ddl-auto: validate` y `flyway.enabled: true`.
- Al arrancar, se ejecutan las migraciones y se crea `flyway_schema_history`.
- Los tests `@DataJpaTest` siguen pasando (pueden seguir usando H2).

---

# ADR-002: Health checks en la aplicacion

## Contexto

ECS Fargate y ALB necesitan saber si una task/container esta saludable. Lo hacen llamando a un endpoint HTTP cada N segundos.

Actualmente no existe ese endpoint en nuestras apps.

## Problema

Sin endpoint de health:

- **ECS:** marca la task como "unhealthy" pero nunca sabe si la app arranco bien. Puede matar tasks que si estaban OK.
- **ALB:** marca el target como "unhealthy" y **no le manda trafico**. Deploy falla.

Ademas hay una sutileza importante: existen **dos tipos** de health check.

| Tipo | Pregunta que responde | Que hace si falla |
|------|----------------------|-------------------|
| **Liveness** | "¿Estoy vivo?" (el proceso esta corriendo) | Matar y reiniciar la task |
| **Readiness** | "¿Estoy listo para recibir trafico?" | Sacar la task del LB temporalmente, pero no matarla |

Ejemplo real: tu app arranca → esta VIVA pero aun no conecto a la BD → no esta READY. El LB no deberia mandar trafico todavia.

## Opciones evaluadas

### Opcion A: Spring Boot Actuator
Starter oficial de Spring. Expone `/actuator/health` (y decenas de otros endpoints utiles).

**Pros:** Estandar de facto. Incluye health de DB, Redis, etc. Separa liveness/readiness (`/actuator/health/liveness`, `/actuator/health/readiness`). Gratis + 0 codigo custom.
**Contras:** Agrega ~2MB al JAR (despreciable).

### Opcion B: Controller custom
Escribir un `@RestController` con metodo `/health` que retorne 200.

**Pros:** Control total.
**Contras:** Reinventar la rueda. No chequea dependencias (BD, disco). No separa liveness/readiness. Cero integracion con metrics.

### Opcion C: TCP health check
Configurar ECS/ALB para chequear solo que el puerto responda.

**Pros:** No requiere nada en el codigo.
**Contras:** El puerto esta abierto incluso si la app esta deadlockeada. Falsos positivos.

## Decision

**Spring Boot Actuator (Opcion A).**

## Por que

- No tiene sentido escribir un controller custom para algo que Spring ya tiene.
- Incluye chequeos de dependencias out-of-the-box (`db`, `diskSpace`, `ping`). Si la DB se cae, el health falla automaticamente.
- Separa liveness (para ECS) y readiness (para ALB) — mejor practica en k8s y tambien sirve en ECS.
- Los mismos endpoints se usan despues para metrics (Micrometer) y observabilidad. No es trabajo desperdiciado.

## Como aplicamos el principio

- **Observabilidad desde el dia 1:** el mismo starter que da health checks prepara el terreno para metrics y tracing.

## Acceptance criteria

- Dependencia `spring-boot-starter-actuator` en pom de los 3 servicios.
- `/actuator/health` retorna 200 cuando todo OK, 503 cuando no.
- Configuracion de exposicion controlada (no exponer todos los endpoints publicamente).
- `curl http://localhost:8081/actuator/health` responde correctamente.

---

# ADR-003: Gestion de secretos

## Contexto

Actualmente tenemos en el codigo:

- `application.yml:39` → `jwt.secret: dGhpc0lzQVZlcnlTZWN1cmVTZWNyZXRLZXlGb3JKV1RUb2tlbnM=`
- `docker-compose.yml:52` → mismo secret en env var, tambien hardcodeado
- `application.yml` → `datasource.password: ` (vacio en dev, inyectado por docker-compose en local)

## Problema

1. **El secret esta en git.** Cualquiera que clone el repo conoce la clave JWT de todos los ambientes.
2. **Mismo secret en todos los ambientes.** Si se filtra de staging, tambien compromete prod.
3. **Imposible rotar** sin redeployar.
4. **Audit:** no sabemos quien uso el secret ni cuando.

## Opciones evaluadas

### Opcion A: Variables de entorno
La app lee secrets de env vars (`JWT_SECRET`, `DB_PASSWORD`). En dev usamos `.env` file (gitignored). En AWS las inyecta ECS desde Secrets Manager.

**Pros:** Simple. 12-factor. Funciona igual en dev y prod. No hay codigo cloud-specific.
**Contras:** Env vars son visibles en `ps` output. Pueden filtrarse en crash dumps. Aceptable para la mayoria de casos.

### Opcion B: AWS Secrets Manager (directo desde la app)
La app usa AWS SDK para leer secrets en runtime.

**Pros:** Rotacion automatica, audit log, versioning.
**Contras:** Acopla el codigo a AWS. Dificulta correr en local (necesitas credenciales AWS). Mas latencia en arranque.

### Opcion C: Parameter Store (AWS)
Similar a Secrets Manager pero mas barato y simple.

**Pros:** Gratis para SecureString, mas barato que Secrets Manager.
**Contras:** Menos features (no rotacion automatica).

### Opcion D: HashiCorp Vault
Servidor dedicado de secretos.

**Pros:** Muy completo, multi-cloud.
**Contras:** Overhead operativo grande. Overkill para nuestro tamano.

## Decision

**Variables de entorno (Opcion A), combinadas con Secrets Manager en AWS (inyeccion por ECS).**

## Por que

La clave es: **el codigo solo lee env vars**. De donde vienen esas env vars es problema de la plataforma:

- **En dev:** `.env` file o `docker-compose.yml` (con `.env`)
- **En AWS:** ECS Task Definition lee de Secrets Manager y las inyecta como env vars al container

Ventajas:
- El codigo no sabe nada de AWS.
- Tests unitarios no necesitan credenciales cloud.
- Si mañana migramos a GCP, solo cambia la config de la plataforma, no el codigo.
- Cumple 12-factor principle #3 (config en environment).

## Como aplicamos el principio

- **12-Factor:** config en env, no en codigo.
- **Entornos reproducibles:** la app lee `JWT_SECRET` igual en dev y prod.

## Acceptance criteria

- Ningun secret en `application.yml` ni `docker-compose.yml`.
- Crear `.env.example` (commiteado, con valores de ejemplo) y `.env` (gitignored).
- `JWT_SECRET`, `DB_PASSWORD`, etc. vienen de env vars.
- Si falta un env var critico, la app falla al arrancar con mensaje claro.

---

# ADR-004: Perfiles de configuracion

## Contexto

Hoy solo existe `application.yml`. docker-compose.yml "overridea" con env vars para simular Postgres.

## Problema

No hay separacion clara entre:
- **dev local:** H2 en memoria, logging verbose, H2 console habilitado
- **dev con docker-compose:** PostgreSQL local, logging medio
- **test:** H2, config especial para tests
- **aws:** RDS PostgreSQL, logging estructurado, H2 console DESHABILITADO

Configurar todo por env vars es posible pero llena el task definition de variables.

## Opciones evaluadas

### Opcion A: Perfiles de Spring Boot
Varios `application-{profile}.yml`. Activar con `SPRING_PROFILES_ACTIVE`.

**Pros:** Estandar de Spring. Cada perfil hereda del `application.yml` comun y sobreescribe lo especifico. Minimiza duplicacion.
**Contras:** Hay que tener cuidado con que no se mezclen accidentalmente.

### Opcion B: Todo por env vars, un solo archivo
Todo en `application.yml` con defaults via `${VAR:default}`.

**Pros:** Un solo archivo.
**Contras:** Termina con 40 env vars. Dificil de leer. Dificil de mantener (buscar entre env vars vs entre perfiles).

### Opcion C: Spring Cloud Config Server
Config externalizada en un servicio aparte.

**Pros:** Cambios de config sin redeploy. Centralizado para muchos servicios.
**Contras:** Overhead operativo (otro servicio que mantener). Overkill para 3 apps.

## Decision

**Perfiles de Spring Boot (Opcion A).**

Estructura propuesta:

```
application.yml              → config comun (puerto, thread pool, etc)
application-dev.yml          → H2, logging SQL, H2 console, todo hablador
application-test.yml         → H2 dedicado para tests, profiles utiles
application-aws.yml          → PostgreSQL (RDS), logging estructurado, hardened
```

Activacion:
- Tests: `@ActiveProfiles("test")` en clases de test
- Dev local: default (no perfil activo) o `SPRING_PROFILES_ACTIVE=dev`
- Docker local: `SPRING_PROFILES_ACTIVE=dev` con docker-compose
- AWS: `SPRING_PROFILES_ACTIVE=aws` en ECS task definition

## Por que

- Cada perfil es explicito en que cambia. Leer `application-aws.yml` es suficiente para saber que se diferencia en prod.
- Lo comun queda en `application.yml` (no duplicacion).
- La app no tiene que conocer AWS — el perfil activo es solo un string.

## Como aplicamos el principio

- **Entornos reproducibles:** cada perfil es determinista.
- **12-Factor:** config en environment (perfil = variable de entorno).

## Acceptance criteria

- Existen `application.yml`, `application-dev.yml`, `application-test.yml`, `application-aws.yml`.
- Los tests pasan con `@ActiveProfiles("test")`.
- docker-compose arranca con `SPRING_PROFILES_ACTIVE=dev`.
- `application-aws.yml` no tiene H2, no tiene logging verbose, no tiene `show-sql: true`.

---

# ADR-005: Graceful shutdown

## Contexto

ECS Fargate maneja el ciclo de vida de las tasks. Cuando necesita parar una (deploy nuevo, auto-scaling, healthcheck fallido), hace:

1. Manda **SIGTERM** al container (aviso: "por favor termina")
2. Espera N segundos (default 30s, configurable con `stopTimeout`)
3. Si sigue vivo, manda **SIGKILL** (terminacion forzada)

## Problema

Spring Boot por default no respeta SIGTERM graceful. Cuando lo recibe:

- Cierra el servidor HTTP inmediatamente
- Los requests **en vuelo** fallan (responden 500 o cortan la conexion)
- Las conexiones a la DB no se cierran limpiamente
- Los listeners `@Async` que estan procesando algo se interrumpen

Resultado: cada deploy genera errores 500 visibles para los usuarios.

## Opciones evaluadas

### Opcion A: Graceful shutdown nativo de Spring Boot
Desde Spring Boot 2.3+, hay soporte built-in con `server.shutdown: graceful`.

**Pros:** Built-in, bien probado, cubre el 90% de casos.
**Contras:** No cubre algunos casos edge (conexiones que no son HTTP).

### Opcion B: Shutdown hook custom (`@PreDestroy`)
Escribir logica custom de cleanup.

**Pros:** Control total.
**Contras:** Reinventar la rueda. Tengo que saber exactamente que recursos cerrar.

### Opcion C: No hacer graceful shutdown
Vivir con los errores.

**Pros:** Cero trabajo.
**Contras:** UX degradada en cada deploy.

## Decision

**Graceful shutdown nativo de Spring Boot (Opcion A).**

Configuracion:
```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 25s
```

25s para que Spring termine, ECS default `stopTimeout` es 30s → da 5s de margen.

## Por que

- Spring Boot ya implementa lo correcto: deja de aceptar nuevos requests, espera a que los en-vuelo terminen, cierra las conexiones. Exactamente lo que queremos.
- Alinear el timeout de Spring con el `stopTimeout` de ECS es crucial. Si Spring tarda 35s y ECS mata a los 30s, perdimos.
- Combinado con ALB de-registration delay (proximo ADR), garantiza zero-downtime deploys.

## Como aplicamos el principio

- **Fail safe:** durante el deploy, los usuarios ven requests completados exitosamente, no errores 500.

## Acceptance criteria

- `server.shutdown: graceful` en `application.yml`.
- `spring.lifecycle.timeout-per-shutdown-phase: 25s`.
- Manualmente testeable: hacer un request lento + `docker kill --signal=TERM <container>` → request debe terminar OK.

---

# ADR-006: Dockerfiles production-grade

## Contexto

Los Dockerfiles actuales son multi-stage (bien) pero tienen problemas:

- Corren como root (PID 1 del container)
- No tienen HEALTHCHECK
- No usan `.dockerignore` adecuado
- No aprovechan layer caching al 100%

## Problema

### 1. Root user
Si un atacante explota una vulnerabilidad y ejecuta codigo dentro del container, lo hace como root. Puede:
- Modificar cualquier archivo del container
- Escalar si hay problemas de aislamiento del container engine

Defensa en profundidad = asumir que la primera barrera puede fallar.

### 2. Sin HEALTHCHECK
Docker y ECS tienen mecanismos de healthcheck nativos. Sin HEALTHCHECK en el Dockerfile:
- ECS solo tiene el healthcheck del ALB (que puede tardar en reaccionar).
- Docker no sabe si el container interno esta saludable.

### 3. Layer caching sub-optimo
Si copiamos `src/` antes del `pom.xml`, cualquier cambio de codigo invalida el cache de dependencias → `mvn install` vuelve a descargar todo → builds lentos.

## Opciones evaluadas

### Opcion A: Named non-root user (`addgroup` + `adduser`)
```dockerfile
RUN addgroup -S app && adduser -S app -G app
USER app
```

**Pros:** Claro, nombre legible en `ps`.
**Contras:** Ninguno significativo.

### Opcion B: UID numerico
```dockerfile
USER 1000:1000
```

**Pros:** Mas portable (K8s PodSecurityPolicy pide UID).
**Contras:** En `ps` se ve como `1000` sin nombre. Menos legible.

### Opcion C: Distroless images (Google)
Base images sin shell ni package manager, solo el JRE.

**Pros:** Minimalista, muchisima menos superficie de ataque, imagenes mas chicas.
**Contras:** Dificil de debuggear (no hay bash). Overkill para este momento.

## Decision

**Named non-root user (Opcion A)** con HEALTHCHECK + layer caching optimizado.

Dockerfile template:

```dockerfile
# === Stage 1: build ===
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Primero COPY de poms para cachear dependencias
COPY pom.xml .
COPY pricing-api-contract/pom.xml pricing-api-contract/
COPY property-api/pom.xml property-api/
RUN mvn -B -f property-api/pom.xml dependency:go-offline

# Luego COPY del codigo
COPY pricing-api-contract pricing-api-contract
COPY property-api property-api
RUN mvn -B -f property-api/pom.xml clean package -DskipTests

# === Stage 2: runtime ===
FROM eclipse-temurin:21-jre-alpine

# Non-root user
RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app
COPY --from=build --chown=app:app /build/property-api/target/*.jar app.jar

EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8081/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
```

## Por que

- **Non-root user:** security best practice, no cuesta nada.
- **HEALTHCHECK:** Docker y ECS lo usan para decisiones de restart y load balancing.
- **Layer caching:** copiar poms primero aprovecha que las dependencias cambian menos que el codigo. `mvn install` es lo mas lento del build, queremos cachearlo.
- **`-XX:MaxRAMPercentage=75`:** en containers, la JVM por default no conoce el limite de memoria del container. Esta flag le dice "usa 75% de la RAM disponible" — evita OOM kills sorpresivos.

## Como aplicamos el principio

- **Security by default:** non-root, superficie reducida.
- **Fail fast:** HEALTHCHECK detecta problemas temprano.

## Acceptance criteria

- Los 3 Dockerfiles (property-api, pricing-api, api-gateway) usan non-root user.
- Los 3 tienen HEALTHCHECK apuntando a `/actuator/health/liveness`.
- `docker image inspect` muestra `User: app` y `Healthcheck: ...`.
- Build es rapido en re-builds cuando solo cambia codigo.

---

# ADR-007: Resiliencia en llamadas inter-servicio

## Contexto

El `property-api` llama al `pricing-api` via Feign (OpenFeign declarativo). El cliente esta en [property-api/.../client/PricingClient.java](property-api/src/main/java/com/mauricio/propertyapi/client/PricingClient.java).

Actualmente no tiene:
- Timeout de conexion
- Timeout de lectura
- Retry
- Circuit breaker
- Fallback

## Problema

Si pricing-api esta lento o caido, property-api:

1. **Sin timeout:** los threads de property-api quedan bloqueados esperando. Bajo carga, se agotan los threads y property-api tampoco responde. **Cascading failure.**

2. **Sin retry:** un fallo transitorio (paquete perdido en red) termina en error para el usuario, cuando un retry hubiera resuelto.

3. **Sin circuit breaker:** si pricing-api esta 100% caido, property-api sigue intentando cada request. Sobrecargamos un servicio que ya esta muerto y gastamos recursos propios.

4. **Sin fallback:** no podemos degradar elegantemente. "No puedo darte el precio pero aqui tenes los datos de la property" > "error 500".

## Opciones evaluadas

### Opcion A: Resilience4j
Libreria moderna, reemplazo oficial de Hystrix (Netflix).

**Pros:** Provee circuit breaker, retry, timeout, bulkhead, rate limiter en una sola libreria. Integracion nativa con Spring Cloud. Metrics via Micrometer.
**Contras:** Requiere configuracion inicial.

### Opcion B: Hystrix
El clasico (Netflix OSS).

**Pros:** Muy probado.
**Contras:** **Deprecado oficialmente**. No se actualiza. No usar en proyectos nuevos.

### Opcion C: Spring Retry
Solo retry, nada mas.

**Pros:** Simple.
**Contras:** Falta circuit breaker. Sin circuit breaker, retry puede empeorar las cosas (retry storms contra un servicio caido).

### Opcion D: Implementacion custom
Escribir circuit breaker/retry a mano.

**Pros:** Control total.
**Contras:** Reinventar la rueda. Bugs sutiles (race conditions en el circuit breaker).

## Decision

**Resilience4j (Opcion A).**

Configuracion inicial:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      pricingService:
        failure-rate-threshold: 50
        minimum-number-of-calls: 10
        sliding-window-size: 20
        wait-duration-in-open-state: 30s
  retry:
    instances:
      pricingService:
        max-attempts: 3
        wait-duration: 500ms
        exponential-backoff-multiplier: 2
  timelimiter:
    instances:
      pricingService:
        timeout-duration: 3s

feign:
  client:
    config:
      pricing-service:
        connectTimeout: 2000
        readTimeout: 5000
```

Y anotamos el cliente:

```java
@CircuitBreaker(name = "pricingService", fallbackMethod = "getValuationFallback")
@Retry(name = "pricingService")
public ValuationData getValuation(String city, Integer bedrooms) { ... }

private ValuationData getValuationFallback(String city, Integer bedrooms, Throwable t) {
    log.warn("Pricing service unavailable, returning degraded response", t);
    return ValuationData.unavailable();
}
```

## Por que

- **Resilience4j** es el estandar actual post-Hystrix. Se pregunta en entrevistas ("como manejas fallas en microservicios?").
- **Circuit breaker** evita retry storms: si el servicio esta claramente caido, dejamos de intentar por un rato.
- **Timeout** protege nuestros threads: 3 segundos es suficiente para un calculo de valuacion; si tarda mas, algo anda mal.
- **Retry** resuelve fallos transitorios sin molestar al usuario.
- **Fallback** permite degradar elegantemente.

## Patterns que ilustramos

Este ADR demuestra varios patterns clave del design distribuido:

| Pattern | Que hace |
|---------|----------|
| Circuit Breaker | Deja de llamar cuando el servicio esta claramente caido |
| Timeout | Evita que un servicio lento nos tumbe |
| Retry con backoff | Reintenta fallos transitorios, pero con delay creciente para no sobrecargar |
| Fallback | Respuesta degradada cuando no se puede obtener la ideal |
| Bulkhead | Aisla pools de threads por dependencia (evita starvation) |

## Como aplicamos el principio

- **Fail safe:** un pricing-api caido no tumba property-api.
- **Observabilidad:** Resilience4j publica metricas a Micrometer → CloudWatch.

## Acceptance criteria

- Dependencia `spring-cloud-starter-circuitbreaker-resilience4j` en property-api.
- `PricingClient` (o el wrapper `PricingService`) tiene `@CircuitBreaker`, `@Retry`, y metodo de fallback.
- Test manual: parar pricing-api, hacer 10 requests a `/api/properties/{id}/valuation` → ver que retornan respuesta degradada, no error 500.
- Metrics `resilience4j.circuitbreaker.state` expuestas en `/actuator/metrics`.

---

# ADR-008: Structured logging (JSON)

## Contexto

Los logs actuales usan el formato default de Spring Boot:

```
2026-04-22 14:30:12.345  INFO 1 --- [nio-8081-exec-1] c.m.p.service.PropertyService : Property 'Casa Centro' creada
```

## Problema

CloudWatch Logs Insights es una herramienta potentisima que te permite hacer queries SQL-like sobre millones de logs:

```
fields @timestamp, level, message
| filter level = "ERROR" and service = "property-api"
| stats count() by error_type
```

**Pero solo funciona si los logs son JSON estructurado.** Con texto plano, CloudWatch intenta parsear con regex y es lento y poco confiable.

Ademas, con logs no-estructurados, es imposible agregar contexto de forma confiable (tenant_id, request_id, user_id, trace_id).

## Opciones evaluadas

### Opcion A: Logback + Logstash encoder (JSON)
Logback es el logger default de Spring Boot. Agregar `logstash-logback-encoder` nos da JSON con un solo cambio de config.

**Pros:** Minimo cambio. Compatible con todo el ecosistema Spring. Funciona con MDC.
**Contras:** Ninguno.

### Opcion B: Log4j2 con JsonLayout
Reemplazar Logback con Log4j2.

**Pros:** Log4j2 es un poco mas rapido en algunos benchmarks.
**Contras:** Hay que excluir logback de Spring Boot. La mayoria de bugs de Log4j (Log4Shell, etc) no aplican a JsonLayout pero el estigma queda.

### Opcion C: Dejar texto plano, configurar CloudWatch para parsearlo
Usar CloudWatch log patterns para extraer campos.

**Pros:** Sin cambios en codigo.
**Contras:** Fragil (si cambia el formato, los queries se rompen). CloudWatch tiene que procesar cada log.

## Decision

**Logback + logstash-logback-encoder (Opcion A).**

`logback-spring.xml`:

```xml
<configuration>
  <springProperty name="appName" source="spring.application.name"/>

  <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <customFields>{"service":"${appName}"}</customFields>
      <includeMdcKeyName>trace_id</includeMdcKeyName>
      <includeMdcKeyName>request_id</includeMdcKeyName>
      <includeMdcKeyName>tenant_id</includeMdcKeyName>
    </encoder>
  </appender>

  <springProfile name="aws">
    <root level="INFO"><appender-ref ref="JSON"/></root>
  </springProfile>

  <springProfile name="dev,default">
    <!-- En dev mantenemos formato legible -->
    <root level="INFO"><appender-ref ref="CONSOLE"/></root>
  </springProfile>
</configuration>
```

## Por que

- JSON en prod, texto legible en dev. Best of both worlds.
- Incluye MDC keys (trace_id, tenant_id) automaticamente. MDC se setea en el filter del ADR-009.
- Logstash encoder es estandar, lo usan miles de proyectos.

## Como aplicamos el principio

- **Observabilidad desde el dia 1:** CloudWatch Logs Insights queryable desde el primer deploy.

## Acceptance criteria

- Con perfil `aws`: logs salen como JSON por stdout.
- Con perfil `dev`: logs salen en formato legible humano.
- Campos: `timestamp`, `level`, `logger`, `message`, `service`, y MDC keys si presentes.
- Un log contiene: `{"timestamp":"...","level":"INFO","logger":"...","message":"...","service":"property-api","trace_id":"..."}`

---

# ADR-009: Correlation IDs y tracing basico

## Contexto

Un request entra por `api-gateway`, pasa a `property-api`, que a su vez llama a `pricing-api`. Son 3 logs diferentes. Actualmente no hay forma de saber "estos 3 logs son del mismo request".

## Problema

Debuggear un error en produccion requiere unir logs a traves de servicios. Sin correlation ID:

- Buscas por timestamp → encuentras 50 requests de ese segundo.
- Buscas por user → no tienes ese campo en logs de pricing-api.
- **Tardas 30 minutos en lugar de 30 segundos** en encontrar el root cause.

## Opciones evaluadas

### Opcion A: Servlet filter custom con MDC
Un `OncePerRequestFilter` que:
- Lee header `X-Request-ID` del request (o lo genera si no viene)
- Lo setea en MDC para que aparezca en los logs
- Lo propaga al response
- El Feign client lo propaga al siguiente servicio

**Pros:** Simple, bajo overhead, no requiere infra extra. ~30 lineas de codigo.
**Contras:** Es solo correlation, no tracing completo (no vemos duracion por span).

### Opcion B: Spring Cloud Sleuth
Libreria clasica pero **deprecada en Spring Boot 3** (se fusiono con Micrometer).

**Pros:** Era el estandar.
**Contras:** Ya no se usa en proyectos nuevos.

### Opcion C: Micrometer Tracing + OpenTelemetry
El nuevo estandar post-Sleuth.

**Pros:** Distributed tracing completo, compatible con Jaeger/Zipkin/AWS X-Ray.
**Contras:** Mas complejo. Requiere backend (Jaeger/X-Ray).

### Opcion D: AWS X-Ray SDK directo
Tracing nativo de AWS.

**Pros:** Integrado con CloudWatch y otros servicios AWS.
**Contras:** Vendor lock-in (codigo con `com.amazonaws.xray`). Si mañana cambias de cloud, reescribes.

## Decision

**Opcion A (Servlet filter custom) ahora. Migrar a Opcion C (Micrometer Tracing) mas adelante.**

## Por que

- Necesitamos correlation YA. Un filter es 30 lineas de codigo.
- Nos da el 80% del valor (encontrar logs de un request) con el 10% del trabajo.
- Micrometer Tracing requiere un backend (Zipkin, Jaeger, X-Ray) — no lo tenemos aun.
- Cuando implementemos observabilidad completa (probablemente con X-Ray en AWS), reemplazamos el filter sin mover la interfaz.

## Como aplicamos el principio

- **Evitar over-engineering:** empezamos simple, evolucionamos cuando hay necesidad real.
- **Observabilidad desde el dia 1:** aunque sea basica.

## Implementacion sketch

```java
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-Request-ID";
    private static final String MDC_KEY = "request_id";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
        String id = Optional.ofNullable(req.getHeader(HEADER))
                .orElseGet(() -> UUID.randomUUID().toString());
        MDC.put(MDC_KEY, id);
        res.setHeader(HEADER, id);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
```

Y configuracion de Feign para que propague el header:

```java
@Bean
public RequestInterceptor requestIdInterceptor() {
    return template -> {
        String id = MDC.get("request_id");
        if (id != null) template.header("X-Request-ID", id);
    };
}
```

## Acceptance criteria

- Todo request genera un `request_id` (o usa el que vino en header).
- El `request_id` aparece en todos los logs del request.
- Cuando property-api llama a pricing-api, el mismo `request_id` aparece en logs de pricing-api.
- El response tiene el header `X-Request-ID`.

---

# ADR-010: Metrics con Micrometer

## Contexto

Spring Boot Actuator (ADR-002) ya incluye Micrometer. Actualmente expone metricas basicas en `/actuator/metrics` (JVM, HTTP requests, DB pool, etc). No estan publicadas a CloudWatch.

## Problema

CloudWatch CPU/memoria son metricas utiles, pero incompletas:

- **CPU alta:** puede ser por traffic alto (OK) o por un loop infinito (bug).
- **No hay contexto de negocio:** ¿estamos creando muchas properties? ¿los usuarios estan logueandose mas? ¿cuantas valuaciones pedimos?

Metricas de aplicacion (custom) responden preguntas de negocio y alertan antes:

- `property_created_total` → alerta si baja drasticamente
- `valuation_request_duration_seconds` → SLO de latencia
- `circuit_breaker_state{name="pricingService"}` → alerta si se abre

## Opciones evaluadas

### Opcion A: Micrometer → CloudWatch registry
Publica las metricas directo a CloudWatch. AWS native.

**Pros:** Integrado con AWS. No requiere infra extra.
**Contras:** Costoso a escala (se cobra por metric y por request de PutMetricData).

### Opcion B: Micrometer → Prometheus endpoint + CloudWatch scraper
Expone `/actuator/prometheus`, un agente scrapea y publica.

**Pros:** Compatible con ecosystem Prometheus/Grafana. Portable.
**Contras:** Requiere el agente (ej: CloudWatch Agent). Mas piezas.

### Opcion C: Solo metricas default de Actuator
No exponer a CloudWatch, solo verlas localmente.

**Pros:** Cero trabajo.
**Contras:** En prod no vemos nada.

## Decision

**Micrometer → CloudWatch registry (Opcion A)** para empezar. Considerar Prometheus si escalamos.

## Por que

- AWS native = menos cosas que mantener.
- Suficiente para este scale (3 servicios, bajo volumen).
- Si algun dia escalamos a 50 servicios, migramos a Prometheus (los @Timed no cambian, solo el registry).

## Metricas custom iniciales

```java
@Timed(value = "property.creation", description = "Property creation latency")
public PropertyResponse create(CreatePropertyRequest request) { ... }

meterRegistry.counter("property.created", "city", city).increment();
meterRegistry.gauge("valuation.cache.size", cache.size());
```

## Como aplicamos el principio

- **Observabilidad desde el dia 1.**
- **Evitar over-engineering:** CloudWatch directo, sin agentes ni servidores de metricas. Podemos evolucionar.

## Acceptance criteria

- Dependencia `micrometer-registry-cloudwatch2`.
- Config en `application-aws.yml`: `management.metrics.export.cloudwatch.namespace: JavaTraining`.
- Al menos 3 metricas custom: `property.created`, `valuation.request.duration`, `auth.login.attempts`.
- En AWS, las metricas aparecen en CloudWatch Metrics bajo namespace `JavaTraining`.

---

# Orden de ejecucion y razonamiento

No es arbitrario. Hay un orden optimo:

## Fase 1 — Datos y configuracion (sin esto nada funciona)
**Orden:** B1 → B4 → B2 → B3 → B5

1. **B1 (Flyway)** primero porque cambiamos `ddl-auto` y queremos que los tests no se rompan.
2. **B4 (profiles)** despues porque los ADRs siguientes asumen que hay un `application-aws.yml` donde poner cosas.
3. **B2 (Actuator)** va aqui porque lo activamos en `application.yml` (comun a todos los perfiles).
4. **B3 (secretos)** despues de los profiles, para saber donde poner cada env var.
5. **B5 (graceful shutdown)** es 3 lineas de config, lo sumamos.

**Checkpoint:** al final de Fase 1, la app arranca, tiene `/actuator/health`, no tiene secrets hardcodeados, y Flyway gestiona el schema.

## Fase 2 — Infraestructura de container (Dockerfiles)
**Orden:** B6

Una vez que tenemos `/actuator/health` (Fase 1), tiene sentido agregar HEALTHCHECK al Dockerfile. Si lo hubieramos hecho antes, el healthcheck del Dockerfile apuntaria a algo que no existe.

**Checkpoint:** Docker images son production-grade.

## Fase 3 — Reliability y observabilidad
**Orden:** B8 → B9 → B7 → B10

1. **B8 (logging JSON)** primero porque los ADRs siguientes agregan cosas al log.
2. **B9 (correlation IDs)** despues porque lo publica en MDC → aparece en los logs JSON.
3. **B7 (Resilience4j)** despues porque queremos logs estructurados cuando el circuit breaker se dispare.
4. **B10 (metrics)** ultimo porque es independiente y no bloquea nada.

**Checkpoint:** logs estructurados, resiliencia en llamadas inter-servicio, metricas custom.

---

# Tests

Despues de **cada** ADR implementado, corremos:

```bash
./mvnw clean test
```

Los 59 tests actuales deben seguir pasando. Si se rompe algo:

- ADR-001 (Flyway): tests usan H2 que no conoce `flyway_schema_history`. Deshabilitamos Flyway en perfil `test`.
- ADR-003 (secretos): tests pueden pedir JWT secret — lo ponemos en `application-test.yml`.
- ADR-004 (profiles): aseguramos que `@ActiveProfiles("test")` este en las clases relevantes.

---

# Proximos pasos

Cuando confirmes el plan, arrancamos con **B1 (Flyway)**. Lo implementamos en una iteracion, corremos tests, y vamos al siguiente.

Podemos hacer los 10 items en una sesion (~7 horas) o distribuirlos en varias. Tu decides.

**Preguntas abiertas para vos:**

1. ¿Estas de acuerdo con el orden propuesto (Fase 1 → Fase 2 → Fase 3)?
2. ¿Queres que arranquemos ya con B1, o hay algun ADR donde prefieras cambiar la decision?
3. ¿Hay items que quieras skipear (ej: si no vas a usar distributed tracing, el ADR-009 puede ser mas simple)?
