# API Gateway — Punto Unico de Entrada

## Que es

Spring Cloud Gateway que se situa frente a todos los microservicios. Es el **unico punto de entrada** para los clientes — solo conocen `:8080`. El gateway rutea cada request al servicio correcto basado en el path de la URL.

## Arquitectura

```
                    ┌─────────────────────┐
  Cliente ──────→   │    API Gateway      │  :8080
                    │  Spring Cloud GW    │
                    └────┬───────────┬────┘
                         │           │
              ┌──────────┘           └──────────┐
              ↓                                 ↓
    ┌──────────────────┐          ┌──────────────────┐
    │  property-api    │  :8081   │  pricing-api     │  :8082
    │  REST (CRUD)     │          │  JSON-RPC        │
    └──────────────────┘          └──────────────────┘
```

## Por que un API Gateway?

| Sin Gateway | Con Gateway |
|-------------|-------------|
| Cliente conoce N URLs | Cliente conoce 1 URL |
| Auth en cada servicio | Auth centralizada |
| No hay rate limiting global | Rate limiting en un solo lugar |
| Agregar/quitar servicios rompe clientes | Agregar/quitar servicios es transparente |
| No hay logging unificado | Logging centralizado |

## Rutas configuradas

```yaml
spring:
  cloud:
    gateway:
      routes:
        # Todo /api/** va al property-api (REST)
        - id: property-service
          uri: ${PROPERTY_SERVICE_URL:http://localhost:8081}
          predicates:
            - Path=/api/**

        # Todo /rpc va al pricing-api (JSON-RPC)
        - id: pricing-service
          uri: ${PRICING_SERVICE_URL:http://localhost:8082}
          predicates:
            - Path=/rpc
```

- **Path predicate**: matchea requests por URL pattern
- **AddRequestHeader filter**: agrega `X-Gateway` para que el servicio sepa que vino del gateway
- Las URLs de backend se configuran con variables de entorno (Docker) o defaults (local)

## Diferencia con Nginx/Kong

| | Nginx | Kong | Spring Cloud Gateway |
|---|-------|------|---------------------|
| Lenguaje | Config/Lua | Lua plugins | **Java** (programable) |
| Filtros custom | Complejos | Plugins | **Codigo Java** |
| Modelo | Thread-based | Thread-based | **Reactivo (Netty)** |
| Ecosistema | Standalone | Standalone | **Spring Boot** (mismo stack) |

Spring Cloud Gateway es la opcion natural si tu backend es Spring Boot — mismo lenguaje, mismas herramientas, mismo ecosistema.

## Stack

- Java 21
- Spring Boot 3.4
- Spring Cloud Gateway 2024.0.1
- **Reactivo (Netty)** — NO usa Tomcat

**Importante:** Spring Cloud Gateway usa `spring-cloud-starter-gateway` (reactivo). **NO puedes agregar `spring-boot-starter-web`** al mismo proyecto — son incompatibles. El gateway usa Netty como servidor, no Tomcat.

## Como ejecutar

### Local (requiere que los otros servicios esten corriendo)

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./mvnw spring-boot:run
# Gateway en :8080
```

### Todo junto (3 terminales)

```bash
# Terminal 1: pricing-api
cd /Users/mauricio/dev/java-training/pricing-api && ./mvnw spring-boot:run

# Terminal 2: property-api
cd /Users/mauricio/dev/java-training/property-api && ./mvnw spring-boot:run

# Terminal 3: api-gateway
cd /Users/mauricio/dev/java-training/api-gateway && ./mvnw spring-boot:run
```

### Docker (un solo comando)

```bash
cd /Users/mauricio/dev/java-training
docker compose up --build
```

## Ejemplo de uso

```bash
# REST via gateway → property-api
curl http://localhost:8080/api/properties -H "Authorization: Bearer <token>"

# JSON-RPC via gateway → pricing-api
curl -X POST http://localhost:8080/rpc -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0",
  "method": "getValuation",
  "params": {"city": "Buenos Aires", "bedrooms": 3},
  "id": 1
}'
```

El cliente solo conoce `:8080`. No sabe que existen `:8081` y `:8082`.

## Conceptos de Spring Cloud

| Concepto | Donde se usa |
|----------|-------------|
| `spring-cloud-starter-gateway` | pom.xml — gateway reactivo |
| Spring Cloud BOM (`dependencyManagement`) | pom.xml — gestiona versiones de Spring Cloud |
| Route predicates (`Path=`) | application.yml — matchea URLs |
| Route filters (`AddRequestHeader`) | application.yml — modifica requests |
| Config declarativa en YAML | application.yml — rutas sin codigo Java |
| Variables de entorno (`${...}`) | application.yml — URLs de servicios configurable |

## Preguntas de entrevista

**"Que es un API Gateway?"**
→ Punto unico de entrada para microservicios. Rutea requests al servicio correcto. Centraliza auth, logging, rate limiting. El cliente solo conoce una URL.

**"Por que Spring Cloud Gateway y no Nginx?"**
→ Si todo tu backend es Spring Boot, usar Spring Cloud Gateway te da filtros custom en Java, integracion con Spring Security, y el mismo ecosistema. Nginx es mejor como load balancer puro o cuando tienes servicios en multiples lenguajes.

**"Diferencia entre reactivo (Netty) y servlet-based (Tomcat)?"**
→ Tomcat usa un thread por request (bloqueante). Netty usa event loop (no bloqueante) — puede manejar miles de conexiones con pocos threads. El gateway es I/O-bound puro (solo proxea requests), asi que el modelo reactivo es ideal.
