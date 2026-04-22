# Property API — Servicio Principal (REST)

## Que es

- API REST de gestion de propiedades inmobiliarias
- Proyecto de entrenamiento: Java 21 + Spring Boot 3.4
- Demuestra conceptos modernos de Spring para entrevista tecnica
- Incluye autenticacion JWT, RBAC, comunicacion entre microservicios (OpenFeign), eventos de dominio, virtual threads y testing completo

## Arquitectura

```
api-gateway:8080 → property-api:8081 → pricing-api:8082 (Feign/JSON-RPC)
                                     → H2/PostgreSQL
```

- **api-gateway** — punto de entrada, rutea requests a los servicios
- **property-api** (este servicio) — CRUD de propiedades y proyectos, autenticacion, logica de negocio
- **pricing-api** — servicio de valuacion via JSON-RPC, llamado desde property-api con OpenFeign

## Stack completo

| Tecnologia | Uso |
|---|---|
| Java 21 | Records, sealed classes, pattern matching (switch), virtual threads, var, .toList(), text blocks |
| Spring Boot 3.4 | jakarta.*, SecurityFilterChain, auto-configuracion |
| Spring Data JPA | H2 (dev) / PostgreSQL (prod/Docker) |
| Spring Security + JWT | BCrypt, RBAC (USER/ADMIN), stateless sessions |
| Spring Cloud OpenFeign | Comunicacion declarativa entre microservicios |
| Maven | Build y dependencias |
| JUnit 5 + Mockito + MockMvc | Unit tests, integration tests, repository tests |
| Docker | Multi-stage build, ZGC garbage collector |

## Endpoints

### Auth (publicos)

| Metodo | Ruta | Descripcion |
|---|---|---|
| POST | `/api/auth/register` | Registrar usuario (role USER) |
| POST | `/api/auth/register-admin` | Registrar admin (solo dev/testing) |
| POST | `/api/auth/login` | Login, retorna JWT token |

### Properties (requiere token)

| Metodo | Ruta | Role | Descripcion |
|---|---|---|---|
| GET | `/api/properties` | USER, ADMIN | Listar todas (filtro opcional `?city=X`) |
| GET | `/api/properties/{id}` | USER, ADMIN | Buscar por ID |
| GET | `/api/properties/{id}/valuation` | USER, ADMIN | Valuacion via pricing-api (Feign/JSON-RPC) |
| POST | `/api/properties` | ADMIN | Crear propiedad |
| PUT | `/api/properties/{id}` | ADMIN | Actualizar propiedad |
| PUT | `/api/properties/{id}/project/{projectId}` | ADMIN | Asignar propiedad a un proyecto |
| DELETE | `/api/properties/{id}` | ADMIN | Eliminar propiedad |
| DELETE | `/api/properties/{id}/project` | ADMIN | Desasignar propiedad de su proyecto |

### Projects (requiere token)

| Metodo | Ruta | Role | Descripcion |
|---|---|---|---|
| GET | `/api/projects` | USER, ADMIN | Listar todos los proyectos |
| GET | `/api/projects/{id}` | USER, ADMIN | Detalle con properties (JOIN FETCH) |
| POST | `/api/projects` | ADMIN | Crear proyecto |
| PUT | `/api/projects/{id}` | ADMIN | Actualizar proyecto |
| DELETE | `/api/projects/{id}` | ADMIN | Eliminar proyecto (cascade a properties) |

## Conceptos de Spring demostrados

### `controller/` — REST Controllers
- `@RestController`, `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`
- `@Valid` + `@RequestBody` para validacion automatica de DTOs
- `@PathVariable`, `@RequestParam` para parametros de ruta y query
- `@ResponseStatus` para codigos HTTP personalizados (201 CREATED, 204 NO_CONTENT)

### `service/` — Logica de negocio
- `@Service`, `@Transactional`
- `ApplicationEventPublisher` para publicar eventos de dominio
- Inyeccion de dependencias por constructor (no `@Autowired`)
- **PricingService** — encapsula comunicacion JSON-RPC con pricing-api (SRP)
- **AuthService** — registro y login con logica DRY (`registerWithRole()`)

### `repository/` — Acceso a datos
- `JpaRepository<Entity, UUID>` — CRUD automatico
- Derived queries: `findByCityIgnoreCase()`, `findByCityAndPriceGreaterThanEqual()`
- `@Query` con JPQL personalizado
- `JOIN FETCH` para resolver el problema N+1

### `model/` — Entidades JPA
- `@Entity`, `@Table`, `@Id`, `@GeneratedValue(strategy = GenerationType.UUID)`
- `@OneToMany` / `@ManyToOne` con `FetchType.LAZY`
- `@JoinColumn`, `cascade = CascadeType.ALL`, `orphanRemoval = true`
- `@PrePersist` para timestamps automaticos
- `@Enumerated` para roles (USER, ADMIN)
- Metodos helper para relaciones bidireccionales (`addProperty()`, `removeProperty()`)

### `dto/` — Records + Bean Validation
- Java 16+ Records como DTOs inmutables (reemplazo de POJOs con boilerplate)
- `@NotBlank`, `@NotNull`, `@Positive`, `@Min`, `@Email`, `@Size`
- Records separados para request y response
- DTOs de JSON-RPC viven en `pricing-api-contract` (modulo compartido, DRY)

### `exception/` — Sealed Classes + Pattern Matching
- `sealed class ApiException permits ResourceNotFoundException, BusinessRuleException`
- `@RestControllerAdvice` + `@ExceptionHandler` para manejo centralizado de errores
- Pattern matching en `switch` con exhaustiveness check del compilador
- Handler para `MethodArgumentNotValidException` (errores de `@Valid`)

### `security/` — JWT + Spring Security
- `JwtService` — generacion y validacion de tokens con JJWT (HS256)
- `JwtAuthenticationFilter extends OncePerRequestFilter` — filtro custom en la cadena de seguridad
- `BCryptPasswordEncoder` para hashear passwords
- `SecurityFilterChain` (reemplazo de `WebSecurityConfigurerAdapter` en Spring Boot 3)
- Sesiones stateless (`SessionCreationPolicy.STATELESS`)
- RBAC: USER solo GET, ADMIN puede POST/PUT/DELETE

### `config/` — Configuracion
- `@Configuration`, `@EnableWebSecurity`, `@EnableAsync`
- Virtual threads para `@Async` via `TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor())`
- Virtual threads para requests HTTP via `spring.threads.virtual.enabled: true`

### `event/` — Eventos de dominio
- `PropertyCreatedEvent` — record inmutable como evento
- `ApplicationEventPublisher` para publicar eventos
- `@EventListener` + `@Async` para procesamiento asincrono desacoplado
- Multiples listeners para el mismo evento (indexacion + notificacion)

### `client/` — OpenFeign
- `@FeignClient(name, url)` — interface declarativa para HTTP client
- `@EnableFeignClients` en la clase main
- Comunicacion con pricing-api via JSON-RPC
- DTOs compartidos via `pricing-api-contract` (contrato unico entre servicios)

## Features de Java 21

| Feature | Donde se usa |
|---|---|
| **Records** (Java 16+) | Todos los DTOs: `CreatePropertyRequest`, `AuthRequest`, `PropertyResponse`, etc. |
| **Sealed classes** (Java 17+) | `ApiException permits ResourceNotFoundException, BusinessRuleException` |
| **Pattern matching switch** (Java 21) | `GlobalExceptionHandler.handleApiException()` — switch sobre sealed class |
| **Virtual threads** (Java 21) | `application.yml` para requests HTTP + `AsyncConfig` para `@Async` tasks |
| **var** (Java 10+) | Inferencia de tipos en variables locales |
| **.toList()** (Java 16+) | Reemplazo de `.collect(Collectors.toList())` en streams |

## Como ejecutar

### Local (H2 en memoria)

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./mvnw spring-boot:run    # Puerto 8081
```

La consola H2 esta disponible en http://localhost:8081/h2-console (JDBC URL: `jdbc:h2:mem:propertydb`).

### Con Docker (PostgreSQL)

```bash
cd /Users/mauricio/dev/java-training
docker compose up --build
```

Esto levanta los 3 servicios + PostgreSQL:
- api-gateway: http://localhost:8080
- property-api: http://localhost:8081
- pricing-api: http://localhost:8082

### Tests

```bash
./mvnw test               # 59 tests
```

## Ejemplo de uso completo

```bash
# 1. Registrar admin
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/register-admin \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@test.com","password":"admin123"}' | jq -r '.token')

# 2. Crear propiedad
PROP_ID=$(curl -s -X POST http://localhost:8081/api/properties \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"Depto Palermo","city":"Buenos Aires","price":150000,"bedrooms":3}' | jq -r '.id')

# 3. Obtener valuacion (llama a pricing-api via Feign)
curl -s http://localhost:8081/api/properties/$PROP_ID/valuation \
  -H "Authorization: Bearer $TOKEN" | jq

# 4. Listar propiedades
curl -s http://localhost:8081/api/properties \
  -H "Authorization: Bearer $TOKEN" | jq

# 5. Filtrar por ciudad
curl -s "http://localhost:8081/api/properties?city=Buenos%20Aires" \
  -H "Authorization: Bearer $TOKEN" | jq
```

## Testing (59 tests)

### Unit tests — `@Mock` + `@InjectMocks` (Mockito)
- `PropertyServiceTest` — testea logica de negocio aislada del framework
- `ProjectServiceTest` — testea relaciones 1:N, asignacion de properties
- `AuthServiceTest` — register (USER/ADMIN), login, email duplicado, password incorrecto
- `PricingServiceTest` — llamada exitosa a pricing-api, error JSON-RPC

### Unit tests — sin Spring context (POJO directo)
- `JwtServiceTest` — generacion de token, extraccion de claims, token expirado, firma invalida, token alterado
- `GlobalExceptionHandlerTest` — 404 para ResourceNotFound, 422 para BusinessRule, 500 sin leak de stack trace

### Integration tests — `@SpringBootTest` + `MockMvc` + `@WithMockUser`
- `PropertyControllerTest` — testea endpoints HTTP completos con seguridad
- `ProjectControllerTest` — testea endpoints de projects con roles

### Repository tests — `@DataJpaTest`
- `PropertyRepositoryTest` — testea derived queries y JPQL custom con H2

## Estructura de carpetas

```
src/main/java/com/mauricio/propertyapi/
├── PropertyApiApplication.java          # Main class (@SpringBootApplication, @EnableFeignClients)
├── client/
│   └── PricingClient.java               # @FeignClient — interface declarativa para pricing-api
├── config/
│   ├── AsyncConfig.java                 # @EnableAsync + virtual threads executor
│   └── SecurityConfig.java              # SecurityFilterChain, BCrypt, RBAC rules
├── controller/
│   ├── AuthController.java              # /api/auth — register, login
│   ├── ProjectController.java           # /api/projects — CRUD de proyectos
│   └── PropertyController.java          # /api/properties — CRUD + valuation + asignacion
├── dto/
│   ├── AuthRequest.java                 # Record: email + password con validacion
│   ├── AuthResponse.java                # Record: token + email
│   ├── CreateProjectRequest.java        # Record: name + description
│   ├── CreatePropertyRequest.java       # Record: name, city, price, bedrooms
│   ├── ErrorResponse.java               # Record: respuesta de error estructurada
│   ├── ProjectResponse.java             # Record: proyecto sin properties
│   ├── ProjectWithPropertiesResponse.java # Record: proyecto con lista de properties
│   ├── PropertyResponse.java            # Record: property con project name
│   └── ValuationResponse.java           # Record: resultado de valuacion
│   # NOTA: JsonRpcRequest/Response movidos a pricing-api-contract (modulo compartido)
├── event/
│   ├── PropertyCreatedEvent.java        # Record: evento de dominio inmutable
│   └── PropertyEventListener.java       # @EventListener + @Async — indexacion y notificacion
├── exception/
│   ├── ApiException.java                # sealed class — base de excepciones de dominio
│   ├── BusinessRuleException.java       # Regla de negocio violada (422)
│   ├── GlobalExceptionHandler.java      # @RestControllerAdvice — manejo centralizado
│   └── ResourceNotFoundException.java   # Recurso no encontrado (404)
├── model/
│   ├── Project.java                     # @Entity — proyecto con @OneToMany a properties
│   ├── Property.java                    # @Entity — propiedad con @ManyToOne a project
│   ├── Role.java                        # Enum: USER, ADMIN
│   └── User.java                        # @Entity — usuario con email, password hash, role
├── repository/
│   ├── ProjectRepository.java           # JpaRepository + JOIN FETCH custom query
│   ├── PropertyRepository.java          # JpaRepository + derived queries + @Query JPQL
│   └── UserRepository.java              # JpaRepository + findByEmail
├── security/
│   ├── JwtAuthenticationFilter.java     # OncePerRequestFilter — valida JWT en cada request
│   └── JwtService.java                  # Genera y valida tokens JWT (JJWT + HS256)
└── service/
    ├── AuthService.java                 # Registro (BCrypt) + login + DRY con registerWithRole()
    ├── PricingService.java              # Comunicacion JSON-RPC con pricing-api (SRP)
    ├── ProjectService.java              # CRUD proyectos + relacion bidireccional
    └── PropertyService.java             # CRUD properties + eventos + delega valuacion a PricingService

src/test/java/com/mauricio/propertyapi/
├── controller/
│   ├── ProjectControllerTest.java       # @SpringBootTest + MockMvc + @WithMockUser
│   └── PropertyControllerTest.java      # @SpringBootTest + MockMvc + @WithMockUser
├── exception/
│   └── GlobalExceptionHandlerTest.java  # Test directo del handler (404, 422, 500)
├── repository/
│   └── PropertyRepositoryTest.java      # @DataJpaTest — queries con H2
├── security/
│   └── JwtServiceTest.java             # Token generation, validation, expiration, tampered tokens
└── service/
    ├── AuthServiceTest.java             # Register, registerAdmin, login, email duplicado, password incorrecto
    ├── PricingServiceTest.java          # RPC exitoso, error de pricing-api
    ├── ProjectServiceTest.java          # @Mock + @InjectMocks (Mockito)
    └── PropertyServiceTest.java         # @Mock + @InjectMocks (Mockito)
```
