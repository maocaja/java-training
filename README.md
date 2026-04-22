# Java Training — Monorepo de Microservicios

Proyecto de entrenamiento en **Java 21 + Spring Boot 3.4** organizado como **Maven multi-module** (monorepo).

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
    │  REST (CRUD)     │──Feign──→│  JSON-RPC        │
    │  JPA + Security  │          │  (servicio       │
    │  Events + Async  │          │   interno)       │
    └───────┬──────────┘          └──────────────────┘
            │
         PostgreSQL / H2
```

## Modulos

| Modulo | Puerto | Protocolo | Descripcion |
|--------|--------|-----------|-------------|
| [pricing-api-contract](pricing-api-contract/) | — | — | DTOs compartidos (JSON-RPC). Libreria JAR, no ejecutable |
| [property-api](property-api/) | 8081 | REST | Servicio principal. CRUD de properties/projects, JWT auth, eventos async, OpenFeign |
| [pricing-api](pricing-api/) | 8082 | JSON-RPC | Servicio interno de valuaciones. Protocolo JSON-RPC 2.0 |
| [api-gateway](api-gateway/) | 8080 | HTTP routing | Spring Cloud Gateway. Punto unico de entrada para clientes |

## Maven Multi-Module

Este proyecto usa un POM padre que agrupa los 4 modulos. Las ventajas:

- **Un comando compila todo:** `./mvnw clean compile`
- **Un comando testea todo:** `./mvnw clean test`
- **Versiones centralizadas:** Java 21, Spring Boot 3.4, Spring Cloud 2024.0.1 definidos una sola vez en el padre
- **Compilar un modulo especifico:** `./mvnw clean test -pl property-api`

```
java-training/
├── pom.xml                  ← POM padre (packaging: pom, declara modulos)
├── pricing-api-contract/
│   └── pom.xml              ← libreria JAR con DTOs compartidos
├── property-api/
│   └── pom.xml              ← hereda de java-training, depende de pricing-api-contract
├── pricing-api/
│   └── pom.xml              ← hereda de java-training, depende de pricing-api-contract
├── api-gateway/
│   └── pom.xml              ← hereda de java-training
└── docker-compose.yml       ← orquesta todo + PostgreSQL
```

**Pregunta de entrevista:** *"Como organizas un monorepo?"*
→ Maven multi-module. POM padre con `<modules>`. Centraliza versiones y properties. Un `./mvnw clean test` compila y testea todo. En CI, puedo compilar modulos individuales con `-pl`. Es el patron estandar en la industria para monorepos Java.

**Pregunta de entrevista:** *"Como compartes DTOs entre microservicios?"*
→ Con un modulo de contrato (`pricing-api-contract`). Solo contiene DTOs de comunicacion (JSON-RPC request/response). Ambos servicios dependen de el. Esto evita duplicacion y garantiza que ambos lados hablen el mismo idioma. Si el contrato cambia, ambos servicios se actualizan al compilar.

## Stack completo

| Tecnologia | Version | Donde |
|-----------|---------|-------|
| Java | 21 | Records, sealed classes, pattern matching, virtual threads |
| Spring Boot | 3.4.4 | jakarta.*, SecurityFilterChain, auto-configuration |
| Spring Data JPA | 3.4 | Entities, repositories, derived queries, JOIN FETCH |
| Spring Security | 6.4 | JWT, BCrypt, RBAC (USER/ADMIN), stateless |
| Spring Cloud Gateway | 2024.0.1 | API Gateway reactivo (Netty) |
| Spring Cloud OpenFeign | 2024.0.1 | Comunicacion entre microservicios |
| H2 | Runtime | BD en memoria para desarrollo |
| PostgreSQL | 16 | BD en produccion (Docker) |
| JJWT | 0.12.6 | Generacion y validacion de tokens JWT |
| JUnit 5 + Mockito | 5.11 | Unit tests, integration tests, repository tests |
| Docker + Compose | - | Containerizacion y orquestacion |

## Como ejecutar

### Local (3 terminales)

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# Terminal 1
cd pricing-api && ./mvnw spring-boot:run

# Terminal 2
cd property-api && ./mvnw spring-boot:run

# Terminal 3
cd api-gateway && ./mvnw spring-boot:run
```

### Docker (un comando)

```bash
docker compose up --build
```

### Tests

```bash
./mvnw clean test                    # todos los modulos (59 tests)
./mvnw clean test -pl property-api   # solo property-api
```

## Ejemplo de uso

```bash
# 1. Register admin (via gateway)
curl -X POST http://localhost:8080/api/auth/register-admin \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@meli.com","password":"secret123"}'
# → {"token":"eyJ...", "email":"admin@meli.com", "role":"ADMIN"}

# 2. Crear property (via gateway, con JWT)
curl -X POST http://localhost:8080/api/properties \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"name":"Penthouse","city":"Buenos Aires","price":450000,"bedrooms":3}'

# 3. Valuation (gateway → property-api → Feign → pricing-api JSON-RPC)
curl http://localhost:8080/api/properties/<id>/valuation \
  -H "Authorization: Bearer <token>"

# 4. JSON-RPC directo (via gateway → pricing-api)
curl -X POST http://localhost:8080/rpc \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"getMarketTrend","params":{"city":"Lima"},"id":1}'
```

## Patrones de Diseno

### 1. Layered Architecture (Arquitectura en Capas)

Cada microservicio sigue la separacion clasica en capas:

```
Controller (HTTP)  →  Service (logica)  →  Repository (datos)  →  Entity (BD)
```

- **Controller:** recibe el request HTTP, valida con `@Valid`, delega al service
- **Service:** contiene la logica de negocio, maneja transacciones con `@Transactional`
- **Repository:** abstrae el acceso a datos via Spring Data JPA
- **Entity/Model:** mapeo directo a tablas con JPA annotations

### 2. DTO Pattern (Data Transfer Object)

Nunca exponemos las entidades JPA directamente al cliente. Usamos records inmutables:

```java
// Request DTO — lo que el cliente envia
public record CreatePropertyRequest(
    @NotBlank String name,
    @NotBlank String city,
    @NotNull BigDecimal price,
    @NotNull Integer bedrooms) {}

// Response DTO — lo que el cliente recibe
public record PropertyResponse(UUID id, String name, String city, ...) {
    public static PropertyResponse from(Property entity) { ... }  // Factory method
}
```

**Por que:** evita exponer campos internos (password, relaciones lazy), circular references en JSON, y desacopla el contrato de la API del modelo de datos.

### 3. Repository Pattern

Interfaces que extienden `JpaRepository` — Spring genera la implementacion automaticamente:

```java
public interface PropertyRepository extends JpaRepository<Property, UUID> {
    List<Property> findByCityIgnoreCase(String city);           // Derived query
    List<Property> findByBedroomsGreaterThanEqual(Integer n);   // Derived query

    @Query("SELECT p FROM Project p LEFT JOIN FETCH p.properties WHERE p.id = :id")
    Optional<Project> findByIdWithProperties(@Param("id") UUID id);  // JPQL custom
}
```

### 4. Sealed Classes + Pattern Matching (Java 21)

Jerarquia de excepciones cerrada — el compilador verifica que manejemos TODOS los casos:

```java
// Jerarquia sellada
public abstract sealed class ApiException extends RuntimeException
        permits ResourceNotFoundException, BusinessRuleException { ... }

// En GlobalExceptionHandler — switch exhaustivo
return switch (ex) {
    case ResourceNotFoundException e -> ResponseEntity.status(404).body(...)
    case BusinessRuleException e     -> ResponseEntity.status(422).body(...)
};
// Si agregas un nuevo tipo y no lo manejas, no compila
```

### 5. Filter Chain (Middleware)

`JwtAuthenticationFilter` intercepta CADA request antes de llegar al controller:

```
Request → JwtAuthenticationFilter → SecurityFilterChain → Controller
              ↓
        Extrae Bearer token
        Valida firma + expiracion
        Setea SecurityContext (email + rol)
```

### 6. Observer / Event-Driven

El service publica eventos, los listeners reaccionan de forma desacoplada y asincrona:

```java
// Service publica (fire-and-forget)
eventPublisher.publishEvent(new PropertyCreatedEvent(id, name, city));

// Listener reacciona en un virtual thread separado
@Async @EventListener
public void handlePropertyCreated(PropertyCreatedEvent event) {
    // Indexar en search, enviar notificacion, etc.
}
```

**Por que:** el service no necesita saber que pasa despues del save. Puedo agregar mas listeners sin tocar el service.

### 7. Declarative HTTP Client (OpenFeign)

Comunicacion entre microservicios con solo una interfaz — Spring genera el HTTP client:

```java
@FeignClient(name = "pricing-service", url = "${services.pricing.url}")
public interface PricingClient {
    @PostMapping("/rpc")
    JsonRpcResponse call(@RequestBody JsonRpcRequest request);
}
```

**vs RestTemplate:** cero boilerplate. No hay `new RestTemplate()`, no hay `exchange()`, no hay manejo manual de headers.

### 8. API Gateway Pattern

Un unico punto de entrada para todos los clientes:

```
Cliente solo conoce :8080 (gateway)
  → /api/**  se rutea a property-api :8081
  → /rpc     se rutea a pricing-api :8082
```

**Por que:** los servicios internos son invisibles al cliente. Puedo mover, escalar, o reemplazar servicios sin que el cliente cambie nada.

### 9. Constructor Injection (Dependency Injection)

Spring inyecta dependencias via constructor — sin `@Autowired`, sin reflection:

```java
public PropertyService(PropertyRepository repository,
                       ProjectRepository projectRepository,
                       ApplicationEventPublisher eventPublisher,
                       PricingService pricingService) { ... }
```

**Por que:** las dependencias son explicitas, inmutables, y testeables (puedo pasar mocks en tests).

### 11. Contract-First con Modulo Compartido (DRY)

Los DTOs de JSON-RPC viven en `pricing-api-contract`, compartidos entre servicios:

```
pricing-api-contract/     ← libreria JAR, no ejecutable
├── JsonRpcRequest.java   ← un solo DTO para request
├── JsonRpcResponse.java  ← un solo DTO para response
└── ValuationResult.java  ← resultado compartido

pricing-api  → depende de pricing-api-contract
property-api → depende de pricing-api-contract
```

**Por que:** antes los DTOs estaban duplicados con implementaciones diferentes (uno usaba `JsonNode`, el otro `Map<String, Object>`). Eso es una bomba de tiempo — si cambias uno y no el otro, la comunicacion se rompe silenciosamente.

### 10. Stateless JWT Authentication

Sin sesiones en el servidor. Cada request lleva su propio token:

```
POST /auth/login → JWT token
GET  /api/properties + Authorization: Bearer <token> → datos
```

**Por que:** escala horizontalmente. No importa que instancia reciba el request, el token se valida sin estado compartido.

---

## Flujo Completo de un Request

### Escenario: `POST /api/properties` (crear propiedad como ADMIN)

```
┌──────────┐     ┌───────────────┐     ┌────────────────────────────────────────┐
│  Cliente  │────→│  API Gateway  │────→│            property-api                │
│  (curl)   │     │    :8080      │     │               :8081                    │
└──────────┘     └───────────────┘     └────────────────────────────────────────┘
```

#### Paso 1 — API Gateway recibe el request

```
POST http://localhost:8080/api/properties
Headers: Authorization: Bearer eyJhbG...
Body: {"name":"Penthouse","city":"Buenos Aires","price":450000,"bedrooms":3}
```

El gateway matchea `Path=/api/**`, agrega header `X-Gateway`, y rutea a `http://localhost:8081/api/properties`.

#### Paso 2 — JwtAuthenticationFilter intercepta

```java
// JwtAuthenticationFilter.java (OncePerRequestFilter)
String token = extractTokenFromHeader(request);    // "eyJhbG..."
if (jwtService.isTokenValid(token)) {
    String email = jwtService.extractEmail(token); // "admin@meli.com"
    String role  = jwtService.extractRole(token);  // "ADMIN"
    // Setea SecurityContext para que Spring Security sepa quien es
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
    );
}
```

#### Paso 3 — Spring Security autoriza

```java
// SecurityConfig.java
.requestMatchers(HttpMethod.POST, "/api/**").hasRole("ADMIN")
```

El usuario tiene `ROLE_ADMIN` → pasa. Si fuera `ROLE_USER` → 403 Forbidden.

#### Paso 4 — Controller recibe y valida

```java
// PropertyController.java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public PropertyResponse create(@Valid @RequestBody CreatePropertyRequest request) {
    return service.create(request);
}
```

`@Valid` ejecuta Bean Validation. Si `name` es blank → `MethodArgumentNotValidException` → `GlobalExceptionHandler` → 400 Bad Request con detalle de campos.

#### Paso 5 — Service ejecuta logica de negocio

```java
// PropertyService.java
public PropertyResponse create(CreatePropertyRequest request) {
    var property = new Property();
    property.setName(request.name());
    property.setCity(request.city());
    property.setPrice(request.price());
    property.setBedrooms(request.bedrooms());

    var saved = repository.save(property);  // INSERT INTO properties ...

    // Publica evento (asincrono, no bloquea)
    eventPublisher.publishEvent(new PropertyCreatedEvent(saved.getId(), saved.getName(), saved.getCity()));

    return PropertyResponse.from(saved);
}
```

#### Paso 6 — Repository persiste en BD

```java
repository.save(property)
  → Hibernate genera: INSERT INTO properties (id, name, city, price, bedrooms, created_at) VALUES (...)
  → @PrePersist: property.onCreate() setea createdAt = LocalDateTime.now()
  → UUID generado automaticamente
```

#### Paso 7 — Event Listeners reaccionan (async, no bloquean el response)

```java
// PropertyEventListener.java — ejecuta en virtual thread separado
@Async @EventListener
public void handlePropertyCreated(PropertyCreatedEvent event) {
    log.info("[SEARCH INDEX] Indexing property: {} in {}", event.name(), event.city());
}

@Async @EventListener
public void handlePropertyNotification(PropertyCreatedEvent event) {
    log.info("[NOTIFICATION] New property available: {}", event.name());
}
```

#### Paso 8 — Response al cliente

```
HTTP/1.1 201 Created
Content-Type: application/json

{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Penthouse",
  "city": "Buenos Aires",
  "price": 450000,
  "bedrooms": 3,
  "projectId": null,
  "createdAt": "2026-03-31T14:30:00"
}
```

### Escenario: Flujo inter-servicio (Valuation)

```
Cliente → Gateway → PropertyController.getValuation(id)
                         ↓
                    PropertyService (busca property en BD)
                         ↓
                    PricingService (encapsula comunicacion JSON-RPC)
                         ↓
                    PricingClient.call()  ← OpenFeign
                         ↓
                    pricing-api :8082
                    RpcController.handleRpc()
                         ↓ (calcula valuacion)
                    JsonRpcResponse
                         ↓ (Feign deserializa)
                    PricingService parsea → ValuationData
                         ↓
                    PropertyService arma ValuationResponse
                         ↓
                    HTTP 200 OK al cliente
```

```java
// PropertyService.java — solo busca la property y delega
var valuation = pricingService.getValuation(property.getCity(), property.getBedrooms());
return new ValuationResponse(property.getId(), ..., valuation.estimatedValue(), ...);

// PricingService.java — encapsula toda la comunicacion JSON-RPC
var rpcRequest = JsonRpcRequest.of("getValuation", Map.of("city", city, "bedrooms", bedrooms));
var rpcResponse = pricingClient.call(rpcRequest);
if (rpcResponse.hasError()) throw new BusinessRuleException("Pricing service error");
```

### Escenario: Error handling

```
Request con body invalido
    → @Valid falla
    → MethodArgumentNotValidException
    → GlobalExceptionHandler.handleValidation()
    → 400 {"error":"Validation failed", "details":{"name":"must not be blank"}}

Request a property que no existe
    → PropertyService lanza ResourceNotFoundException
    → GlobalExceptionHandler.handleApiException()
    → switch pattern matching (sealed class)
    → 404 {"error":"Property not found", "code":"RESOURCE_NOT_FOUND"}

Error inesperado
    → Exception generica
    → GlobalExceptionHandler.handleGeneric()
    → 500 {"error":"Internal server error"}
```

---

## Conceptos demostrados

### Java 21
Records, sealed classes, pattern matching (switch), virtual threads, var, .toList()

### Spring Boot 3
jakarta.* (no javax), SecurityFilterChain (no WebSecurityConfigurerAdapter), constructor injection, Bean Validation

### Spring Data JPA
@Entity, @OneToMany/@ManyToOne, FetchType.LAZY, JOIN FETCH, N+1 problem, @Transactional, derived queries, @Query JPQL

### Spring Security
JWT stateless, BCrypt, RBAC (USER/ADMIN), OncePerRequestFilter, @WithMockUser

### Microservicios
API Gateway (Spring Cloud), OpenFeign (comunicacion declarativa), JSON-RPC (protocolo alternativo), docker-compose (orquestacion)

### Async / Events
@Async + virtual threads, ApplicationEventPublisher, @EventListener, procesamiento desacoplado

### Error Handling
sealed classes + pattern matching en @RestControllerAdvice, ErrorResponse estructurado

### Testing
JUnit 5 + Mockito (unit), MockMvc (integration), @DataJpaTest (repository) — 59 tests
