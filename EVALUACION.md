# Evaluacion Tecnica — Java 21 + Spring Boot 3.4 + Ingenieria de Software

Evaluacion basada en el codigo real de este proyecto. Cada pregunta referencia clases y patrones implementados en el monorepo.

**Instrucciones:** Selecciona UNA respuesta por pregunta. Las respuestas estan al final del documento.

---

## Bloque 1: Java Core (5 preguntas)

### P1. Sealed Classes

En `property-api/.../exception/ApiException.java`:
```java
public abstract sealed class ApiException extends RuntimeException
        permits ResourceNotFoundException, BusinessRuleException { }
```

**Si alguien en otro paquete hace `class PaymentException extends ApiException`, que pasa?**

- A) Compila normal, Java es flexible con herencia
- B) Error en runtime — `ClassCastException`
- C) Error de compilacion — solo las clases en `permits` pueden extender
- D) Compila solo si `PaymentException` esta en el mismo JAR

---

### P2. Records

En `property-api/.../dto/CreatePropertyRequest.java`:
```java
public record CreatePropertyRequest(String name, String city, BigDecimal price, Integer bedrooms) {}
```

**Cual de estas afirmaciones es FALSA?**

- A) Los records son inmutables — no tienen setters
- B) `equals()` y `hashCode()` se generan automaticamente basados en todos los campos
- C) Puedo agregar metodos custom a un record
- D) Puedo extender un record con `class ChildRequest extends CreatePropertyRequest`

---

### P3. Virtual Threads

En `property-api/src/main/resources/application.yml`:
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

**En cual escenario los virtual threads NO dan ventaja sobre platform threads?**

- A) 1000 requests concurrentes donde cada uno espera 200ms por una query a PostgreSQL
- B) Un endpoint que hace CPU-bound processing pesado (ej: calcular hash de un archivo de 2GB)
- C) 500 requests concurrentes donde cada uno llama a pricing-api via Feign y espera 300ms
- D) 100 requests concurrentes donde cada uno lee un archivo del filesystem

---

### P4. Pattern Matching

En `property-api/.../exception/GlobalExceptionHandler.java`:
```java
return switch (ex) {
    case ResourceNotFoundException e -> ...
    case BusinessRuleException e -> ...
};
```

**Que pasa si agregas `DuplicateResourceException` a los `permits` de `ApiException` pero NO agregas un case en el switch?**

- A) Compila, pero tira `MatchException` en runtime si llega ese tipo
- B) Error de compilacion — el switch ya no es exhaustivo
- C) Compila normal — el `default` implicito maneja el caso
- D) Compila con warning pero no error

---

### P5. `var` y Type Inference

```java
var properties = repository.findAll();
```

**Cual es el tipo inferido de `properties`?**

- A) `Object`
- B) `var` (es su propio tipo)
- C) `List<Property>` (el tipo de retorno de `findAll()`)
- D) `Iterable<Property>`

---

## Bloque 2: Spring Boot / Spring Framework (5 preguntas)

### P6. Dependency Injection

En `property-api/.../service/PropertyService.java`:
```java
public PropertyService(PropertyRepository repository,
                       ProjectRepository projectRepository,
                       ApplicationEventPublisher eventPublisher,
                       PricingService pricingService) { ... }
```

**Por que NO usas `@Autowired` en el constructor?**

- A) `@Autowired` fue deprecado en Spring Boot 3
- B) Spring Boot 3 inyecta automaticamente si hay UN solo constructor — `@Autowired` es redundante
- C) `@Autowired` solo funciona con field injection, no con constructors
- D) Constructor injection no soporta `@Autowired`

---

### P7. `@Transactional`

En `property-api/.../service/ProjectService.java`:
```java
@Transactional(readOnly = true)
public ProjectWithPropertiesResponse findByIdWithProperties(UUID id) { ... }
```

**Que pasa si QUITAS `@Transactional(readOnly = true)` y la entity `Project` tiene `@OneToMany(fetch = LAZY)`?**

- A) Nada, funciona igual
- B) `LazyInitializationException` al acceder a `project.getProperties()` fuera del service
- C) N+1 queries — Hibernate carga cada property en una query separada
- D) Error de compilacion — `@OneToMany(LAZY)` requiere `@Transactional`

---

### P8. SecurityFilterChain

En `property-api/.../config/SecurityConfig.java`:
```java
.requestMatchers(HttpMethod.POST, "/api/**").hasRole("ADMIN")
```

**En Spring Security, cuando verificas `hasRole("ADMIN")`, que authority busca internamente?**

- A) `ADMIN`
- B) `ROLE_ADMIN`
- C) `AUTHORITY_ADMIN`
- D) Depende de la configuracion

---

### P9. Spring Data JPA

En `property-api/.../repository/PropertyRepository.java`:
```java
List<Property> findByCityIgnoreCase(String city);
```

**Cual de estas queries genera Spring automaticamente?**

- A) `SELECT * FROM property WHERE city = ?` (case sensitive)
- B) `SELECT * FROM property WHERE LOWER(city) = LOWER(?)`
- C) `SELECT * FROM property WHERE UPPER(city) = UPPER(?)`
- D) B o C — depende del dialecto de la BD

---

### P10. Bean Validation

`CreatePropertyRequest` tiene `@Valid` en el controller.

**Donde se ejecuta la validacion realmente?**

- A) En el constructor del record
- B) En el `DispatcherServlet`, antes de llamar al controller method
- C) En el service layer, cuando accedes a los campos
- D) En el repository, antes del `save()`

---

## Bloque 3: Microservicios y Arquitectura (5 preguntas)

### P11. OpenFeign

En `property-api/.../client/PricingClient.java`:
```java
@FeignClient(name = "pricing-service", url = "${services.pricing.url}")
public interface PricingClient {
    @PostMapping("/rpc")
    JsonRpcResponse call(@RequestBody JsonRpcRequest request);
}
```

**Que pasa si pricing-api esta caido y property-api llama a `pricingClient.call()`?**

- A) Retorna `null` silenciosamente
- B) Retorna un `JsonRpcResponse` con error
- C) Lanza una excepcion (ej: `FeignException`) que sube hasta el controller
- D) Spring reintenta automaticamente 3 veces antes de fallar

---

### P12. API Gateway

El gateway rutea `/api/**` a property-api y `/rpc` a pricing-api.

**Si property-api ya valida JWT, por que TAMBIEN seria util validar en el gateway?**

- A) No es util — seria redundante y duplica logica
- B) Para rechazar requests invalidos ANTES de que lleguen al servicio (ahorra recursos)
- C) Porque el gateway es el unico lugar donde se puede validar JWT
- D) Para evitar que pricing-api reciba requests sin autenticacion

---

### P13. Contract Module

Creaste `pricing-api-contract` para compartir DTOs.

**Cual es el mayor RIESGO de un modulo compartido?**

- A) Que compile mas lento
- B) Que se convierta en un "cajon de sastre" con utils, constants, y logica de negocio, acoplando todos los servicios
- C) Que los DTOs queden desactualizados
- D) Que necesite su propia base de datos

---

### P14. Event-Driven

En `property-api/.../service/PropertyService.java`:
```java
var saved = repository.save(property);
eventPublisher.publishEvent(new PropertyCreatedEvent(...));
```

**Si el listener falla (tira excepcion), que pasa con la property que ya se guardo en BD?**

- A) Se hace rollback — la property se borra
- B) Se queda guardada — el listener es `@Async` asi que corre en otro thread
- C) Depende — si el listener NO tiene `@Async`, hace rollback; si tiene `@Async`, no
- D) Spring reintenta el listener 3 veces antes de fallar

---

### P15. JSON-RPC vs REST

Pricing-api usa JSON-RPC.

**Si mañana necesitas agregar caching HTTP (304 Not Modified, ETags), que pasa?**

- A) Se agrega igual que en REST — JSON-RPC soporta caching HTTP
- B) No se puede — JSON-RPC siempre usa POST, y POST no es cacheable por el protocolo HTTP
- C) Se puede pero necesitas un proxy especial
- D) Solo funciona si cambias a GET

---

## Bloque 4: Ingenieria de Software — SOLID, Testing, Design (5 preguntas)

### P16. Single Responsibility Principle

Extrajiste `PricingService` de `PropertyService`.

**Cual es la forma mas practica de saber si una clase viola SRP?**

- A) Si tiene mas de 200 lineas
- B) Si tiene mas de 5 dependencias inyectadas
- C) Si hay mas de una razon por la que podria necesitar cambiar
- D) Si tiene metodos publicos y privados

---

### P17. Testing

`PropertyServiceTest` usa `@Mock` y `@InjectMocks`.

**Cual es la PRINCIPAL desventaja de mockear todo?**

- A) Es mas lento que integration tests
- B) Puede dar falsa confianza — tests pasan pero la integracion real falla (ej: queries SQL incorrectas)
- C) Mockito consume mucha memoria
- D) No puedes testear metodos privados

---

### P18. N+1 Problem

En `property-api/.../repository/ProjectRepository.java`:
```java
@Query("SELECT p FROM Project p LEFT JOIN FETCH p.properties WHERE p.id = :id")
```

**Si usas esto en un endpoint que retorna una LISTA de 100 projects (cada uno con properties), que problema nuevo introduces?**

- A) Ninguno — JOIN FETCH siempre es mejor que LAZY
- B) `MultipleBagFetchException` o producto cartesiano — el resultado crece exponencialmente
- C) Solo carga los primeros 10 projects
- D) Error de compilacion — JOIN FETCH no funciona con listas

---

### P19. BCrypt

`SecurityConfig` usa `BCryptPasswordEncoder`.

**Por que BCrypt y no SHA-256 para passwords?**

- A) BCrypt produce hashes mas cortos
- B) BCrypt es mas rapido que SHA-256
- C) BCrypt es intencionalmente LENTO (configurable con work factor) y agrega salt automatico — resistente a brute force y rainbow tables
- D) SHA-256 no puede hashear strings, solo archivos

---

### P20. HTTP Status Codes

`GlobalExceptionHandler` retorna 422 para `BusinessRuleException`.

**Cual es la diferencia entre 400 y 422?**

- A) Son lo mismo — ambos significan "bad request"
- B) 400 = request malformado (JSON invalido, campos faltantes). 422 = request bien formado pero semanticamente incorrecto (email duplicado, regla de negocio violada)
- C) 400 es para GET, 422 es para POST
- D) 422 es un codigo custom — no es parte del estandar HTTP

---

## Scoring

| Rango | Nivel |
|-------|-------|
| 18-20 | Senior — dominas Java moderno, Spring, y arquitectura |
| 14-17 | Semi-Senior — buen conocimiento, algunos gaps en internals |
| 10-13 | Junior avanzado — entiendes lo basico pero faltan internals |
| < 10 | Necesitas repasar fundamentos |

---

## Respuestas

<details>
<summary>Click para ver respuestas (no hagas trampa)</summary>

### P1: C
**Error de compilacion.** `sealed` restringe la herencia a las clases listadas en `permits`. Nadie mas puede extender `ApiException` sin modificar la declaracion original. Es como un enum pero con datos.

### P2: D
**FALSA: D.** Los records son implicitamente `final` — no se pueden extender. Ademas, los records extienden `java.lang.Record` implicitamente, y Java no soporta herencia multiple. A, B, y C son verdaderas.

### P3: B
**CPU-bound.** Virtual threads brillan en I/O-bound (esperar BD, APIs, filesystem) porque permiten miles de threads "estacionados" esperando I/O sin consumir recursos. Para CPU-bound, necesitas platform threads reales — un virtual thread que hace calculo pesado ocupa un carrier thread igual que un platform thread. No hay ganancia.

### P4: B
**Error de compilacion.** Esta es la magia de sealed classes + pattern matching. El compilador sabe TODOS los subtipos posibles. Si el switch no los cubre todos, no compila. Este es exactamente el motivo por el que usamos sealed classes en la jerarquia de excepciones.

### P5: C
`var` infiere `List<Property>` del tipo de retorno de `JpaRepository.findAll()`. `var` no es un tipo — es azucar sintactica que el compilador resuelve en tiempo de compilacion. El bytecode es identico a escribir `List<Property>`.

### P6: B
**Spring inyecta automaticamente si hay un solo constructor.** Desde Spring 4.3 (2016), si una clase tiene un unico constructor, Spring lo usa para inyeccion sin necesidad de `@Autowired`. No fue deprecado (A es falsa), y SI funciona con constructors (C y D son falsas). Simplemente es redundante.

### P7: B
**`LazyInitializationException`.** Sin `@Transactional`, la sesion de Hibernate se cierra al salir del repository. Cuando el service intenta acceder a `project.getProperties()`, la coleccion LAZY no tiene sesion abierta para cargar los datos → excepcion. `@Transactional` mantiene la sesion abierta durante todo el metodo. Nota: `findByIdWithProperties` usa JOIN FETCH que carga las properties en la misma query, pero sin `@Transactional` el contexto de persistencia puede cerrarse antes de acceder a la coleccion.

### P8: B
**`ROLE_ADMIN`.** Spring Security automaticamente agrega el prefijo `ROLE_` cuando usas `hasRole()`. Por eso en `JwtAuthenticationFilter` creamos la authority como `new SimpleGrantedAuthority("ROLE_" + role)`. Si usaras `hasAuthority("ADMIN")` en vez de `hasRole("ADMIN")`, buscaria `ADMIN` sin prefijo.

### P9: D
**Depende del dialecto.** `IgnoreCase` le dice a Spring Data que genere una comparacion case-insensitive. La implementacion exacta (`LOWER()`, `UPPER()`, o `ILIKE` en PostgreSQL) depende del dialecto de Hibernate para tu BD. H2, PostgreSQL, y MySQL pueden generar SQL diferente.

### P10: B
**En el `DispatcherServlet`.** Mas especificamente, el `HandlerMethodArgumentResolver` ejecuta la validacion ANTES de invocar el metodo del controller. Si la validacion falla, el metodo del controller NUNCA se ejecuta — Spring lanza `MethodArgumentNotValidException` directamente, que es capturada por `GlobalExceptionHandler`.

### P11: C
**Lanza `FeignException`.** Feign no retorna null ni inventa responses. Si el servicio no responde, lanza `FeignException` (subclase de `RuntimeException`). Sin un `@CircuitBreaker` o `fallback`, la excepcion sube hasta el controller y termina en `GlobalExceptionHandler.handleUnexpected()` → HTTP 500. En produccion agregarias Resilience4j para circuit breaking y fallbacks.

### P12: B
**Rechazar requests invalidos ANTES.** Si 1000 requests con tokens invalidos llegan al gateway, los rechazas ahi sin consumir recursos de property-api (conexiones, threads, memoria). Es una optimizacion real. Ademas protege pricing-api que NO tiene autenticacion propia. No es redundante — es defense in depth.

### P13: B
**El cajon de sastre.** El mayor riesgo real. Empieza con 3 DTOs, luego alguien agrega `StringUtils`, otro `BaseEntity`, otro `Constants`... y de repente TODOS los servicios dependen de un modulo gigante. Un cambio en `common` fuerza rebuild y redeploy de todos los servicios. La regla: solo DTOs de contrato, nada mas.

### P14: C
**Depende de `@Async`.** Esta es una pregunta que separa junior de senior:
- **Sin `@Async`:** el listener se ejecuta en el MISMO thread y transaccion. Si el listener tira excepcion, el `@Transactional` del service (si existe) hace rollback del save.
- **Con `@Async`:** el listener corre en OTRO thread con su propia transaccion (o sin transaccion). Si falla, el save original ya se commiteo — no se revierte.
En tu codigo los listeners tienen `@Async`, asi que la property se queda guardada.

### P15: B
**POST no es cacheable.** HTTP caching (ETags, 304, Cache-Control) funciona con GET y HEAD. JSON-RPC usa exclusivamente POST → los proxies HTTP y browsers no cachean la respuesta. Es un trade-off real de JSON-RPC vs REST. Para caching en JSON-RPC necesitarias caching a nivel de aplicacion (Redis, Caffeine), no HTTP.

### P16: C
**Mas de una razon para cambiar.** Esta es la definicion de Robert C. Martin (Uncle Bob). No se trata de lineas de codigo ni cantidad de metodos. `PropertyService` tenia dos razones para cambiar: (1) logica de properties, (2) protocolo de comunicacion con pricing. Eso es dos responsabilidades → se viola SRP. Las lineas de codigo o cantidad de dependencias son heuristicas, no la definicion.

### P17: B
**Falsa confianza.** Unit tests con mocks verifican logica de negocio aislada, pero NO verifican que las piezas funcionen juntas. Ejemplo real: tu `PropertyRepositoryTest` (con `@DataJpaTest`) encontro que `findByCityIgnoreCase` funciona — un mock de repository NUNCA detectaria un error en la query JPQL. Por eso necesitas AMBOS: unit tests (rapidos, logica) + integration tests (lentos, integracion real).

### P18: B
**Producto cartesiano.** Si haces `JOIN FETCH` en una query que retorna N projects, cada project se multiplica por el numero de properties que tiene. 100 projects con 10 properties cada uno = 1000 filas en el ResultSet. Hibernate deduplica, pero el volumen de datos transferido desde la BD crece exponencialmente. Ademas, con multiples `@OneToMany` JOIN FETCH, Hibernate lanza `MultipleBagFetchException`. Para listas grandes, es mejor usar `@BatchSize` o `@EntityGraph`.

### P19: C
**BCrypt es intencionalmente lento.** SHA-256 es un hash de proposito general — rapido por diseno (~millones de hashes/segundo). Eso es MALO para passwords porque un atacante puede hacer brute force rapido. BCrypt:
1. Es lento por diseno (configurable con work factor: 10 rounds ≈ 100ms por hash)
2. Agrega salt automatico (cada password tiene salt unico → rainbow tables inutiles)
3. El work factor se puede incrementar conforme el hardware mejora

### P20: B
**400 vs 422.** Ambos son errores del cliente, pero con semantica diferente:
- **400 Bad Request:** el request esta malformado. JSON invalido, campo obligatorio faltante, tipo de dato incorrecto. El servidor NO PUDO entender el request.
- **422 Unprocessable Entity:** el request esta bien formado (JSON valido, todos los campos presentes) pero es semanticamente incorrecto. Email duplicado, precio negativo, regla de negocio violada. El servidor ENTENDIO el request pero no puede procesarlo.

En tu proyecto: `@Valid` falla → 400 (request malformado). `BusinessRuleException` → 422 (request valido pero viola regla de negocio).

</details>

---

*Evaluacion generada a partir del codigo real del monorepo `java-training`.*
*Cada pregunta referencia clases y patrones implementados en el proyecto.*
