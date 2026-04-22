# Pricing API — Servicio de Valuacion (JSON-RPC)

## Que es

Microservicio **interno** que calcula valuaciones de propiedades inmobiliarias. Usa protocolo **JSON-RPC 2.0** (no REST). Es llamado por el property-api via OpenFeign.

No es un servicio que el cliente final consume directamente — siempre pasa por el API Gateway o es invocado por otro microservicio.

## JSON-RPC vs REST

| | REST | JSON-RPC |
|---|------|----------|
| **Orientacion** | Recursos (sustantivos) | Acciones (verbos) |
| **URL** | `GET /api/properties/123` | `POST /rpc` con `{"method":"getValuation"}` |
| **Verbos HTTP** | GET, POST, PUT, DELETE (semanticos) | Siempre **POST** |
| **Errores** | HTTP status codes (404, 422, 500) | Siempre **HTTP 200**, error en el body |
| **Cuando usarlo** | APIs publicas, CRUD, recursos claros | Servicios internos, calculos, acciones |
| **Ejemplo real** | API publica de MeLi (publicaciones) | Servicio interno de pricing, validaciones |

## Formato JSON-RPC 2.0

### Request

```json
{
  "jsonrpc": "2.0",
  "method": "getValuation",
  "params": {
    "city": "Buenos Aires",
    "bedrooms": 3
  },
  "id": 1
}
```

- `jsonrpc`: siempre "2.0" (version del protocolo)
- `method`: la accion a ejecutar (equivale al verbo HTTP + URL en REST)
- `params`: parametros de la accion (equivale al body/query params en REST)
- `id`: correlacion request/response (el response devuelve el mismo id)

### Response (exito)

```json
{
  "jsonrpc": "2.0",
  "result": {
    "city": "buenos aires",
    "bedrooms": 3,
    "estimatedValue": 225000,
    "pricePerSqm": 2500,
    "marketTrend": "high"
  },
  "error": null,
  "id": 1
}
```

### Response (error)

```json
{
  "jsonrpc": "2.0",
  "result": null,
  "error": {
    "code": -32601,
    "message": "Method not found: unknownMethod"
  },
  "id": 1
}
```

**HTTP status es SIEMPRE 200** — incluso para errores. Los errores se comunican dentro del body. Esta es una diferencia fundamental con REST.

Codigos de error estandar JSON-RPC:
- `-32601`: Method not found
- `-32602`: Invalid params

## Metodos disponibles

### 1. `getValuation`

Calcula la valuacion estimada de una propiedad.

**Params:** `city` (string), `bedrooms` (int)

**Formula:** `estimatedValue = pricePerSqm * (bedrooms * 30)`

**Precios base por ciudad:**

| Ciudad | Precio/m2 (USD) |
|--------|----------------|
| Buenos Aires | 2,500 |
| Santiago | 2,800 |
| Bogota | 2,000 |
| Lima | 1,800 |
| Mexico | 1,500 |
| Otras | 1,500 (default) |

**Ejemplo:**
```bash
curl -X POST http://localhost:8082/rpc -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0",
  "method": "getValuation",
  "params": {"city": "Buenos Aires", "bedrooms": 3},
  "id": 1
}'
```

### 2. `getMarketTrend`

Devuelve la tendencia del mercado para una ciudad.

**Params:** `city` (string)

**Ejemplo:**
```bash
curl -X POST http://localhost:8082/rpc -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0",
  "method": "getMarketTrend",
  "params": {"city": "Lima"},
  "id": 2
}'
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "result": {
    "city": "lima",
    "pricePerSqm": 1800,
    "trend": "stable",
    "confidence": "0.85"
  },
  "id": 2
}
```

## Stack

- Java 21
- Spring Boot 3.4
- Un solo endpoint: `POST /rpc`
- Sin base de datos (datos en memoria)
- Sin seguridad (servicio interno, protegido por red/gateway)
- DTOs compartidos via `pricing-api-contract` (modulo Maven compartido con property-api)

## Como ejecutar

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./mvnw spring-boot:run
# Corre en :8082
```

## Arquitectura

```
api-gateway:8080
    │
    ├── /api/** → property-api:8081 (REST)
    │                │
    │                └── Feign → pricing-api:8082 (JSON-RPC)
    │
    └── /rpc → pricing-api:8082 (JSON-RPC directo)
```

El pricing-api puede ser llamado de dos formas:
1. **Via Feign** desde property-api (cuando el property-api necesita una valuacion)
2. **Via Gateway** directamente (para consultas de mercado independientes)

## Conceptos de Spring usados

| Concepto | Donde |
|----------|-------|
| `@SpringBootApplication` | PricingApiApplication.java — punto de entrada |
| `@RestController` | RpcController.java — un solo controller |
| `@PostMapping("/rpc")` | RpcController.java — un solo endpoint |
| `@RequestBody` | RpcController.java — deserializa JSON-RPC request |
| `ObjectMapper` (Jackson) | Implicitamente para serializar/deserializar JSON |
| `switch` expression (Java 21) | RpcController.java — dispatch por method name |
| `record` (Java 16+) | Todos los DTOs via `pricing-api-contract` (modulo compartido) |

## Modulo de contrato compartido

Los DTOs de JSON-RPC (`JsonRpcRequest`, `JsonRpcResponse`, `ValuationResult`) viven en el modulo `pricing-api-contract`, no en este servicio. Esto garantiza que pricing-api y property-api usen exactamente los mismos tipos:

```
pricing-api-contract/                  ← libreria JAR compartida
├── JsonRpcRequest.java                ← request JSON-RPC con factory method
├── JsonRpcResponse.java               ← response con success/error factories
└── ValuationResult.java               ← resultado de valuacion

pricing-api  → depende de pricing-api-contract (produce responses)
property-api → depende de pricing-api-contract (consume via Feign)
```

**Por que no duplicar?** Antes cada servicio tenia su propia copia con implementaciones diferentes (`JsonNode` vs `Map<String, Object>`). Un cambio en un lado sin actualizar el otro rompe la comunicacion silenciosamente.

## Preguntas de entrevista

**"Cuando usarias JSON-RPC en vez de REST?"**
→ Para servicios internos que ejecutan acciones, no CRUD de recursos. Un servicio de pricing "calcula una valuacion" — eso es un verbo, no un recurso. JSON-RPC simplifica la API a un solo endpoint con methods nombrados. REST es mejor para APIs publicas donde los recursos son claros (properties, users, projects).

**"Como compartes el contrato entre microservicios?"**
→ Con un modulo Maven separado (`pricing-api-contract`) que solo tiene DTOs. Ambos servicios dependen de el. Si el contrato cambia, el compilador te avisa en ambos lados. Es el mismo concepto que `.proto` files en gRPC o un spec OpenAPI.
