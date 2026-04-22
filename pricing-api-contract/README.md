# Pricing API Contract — DTOs Compartidos

## Que es

Modulo Maven de tipo **libreria JAR** (no ejecutable). Contiene exclusivamente los DTOs de comunicacion JSON-RPC compartidos entre `pricing-api` y `property-api`.

**No es un microservicio.** No tiene `@SpringBootApplication`, no tiene `main`, no tiene dependencias de Spring.

## Por que existe

Antes de este modulo, los DTOs de JSON-RPC estaban **duplicados** en pricing-api y property-api con implementaciones **diferentes**:

| | pricing-api (antes) | property-api (antes) |
|---|---|---|
| `JsonRpcRequest.params` | `JsonNode` | `Map<String, Object>` |
| `JsonRpcResponse.result` | `Object` | `JsonNode` |
| `JsonRpcResponse.error` | `JsonRpcError` (record) | `JsonNode` |
| Factory methods | `success()`, `error()` | `hasError()` |

Esto viola DRY y es una **bomba de tiempo**: un cambio en un lado sin actualizar el otro rompe la comunicacion entre servicios silenciosamente.

## Contenido

```
src/main/java/com/mauricio/pricing/contract/
├── JsonRpcRequest.java    # Request JSON-RPC 2.0 con factory method of()
├── JsonRpcResponse.java   # Response con success(), error(), hasError()
└── ValuationResult.java   # Resultado de valuacion (ciudad, precio, tendencia)
```

### JsonRpcRequest

```java
// Factory method — crea un request listo para enviar
var request = JsonRpcRequest.of("getValuation", Map.of(
    "city", "Buenos Aires",
    "bedrooms", 3
));
// → {"jsonrpc":"2.0","method":"getValuation","params":{...},"id":1}
```

### JsonRpcResponse

```java
// En pricing-api (produce)
return JsonRpcResponse.success(result, request.id());
return JsonRpcResponse.error(-32601, "Method not found", request.id());

// En property-api (consume)
if (rpcResponse.hasError()) {
    throw new BusinessRuleException("Pricing error: " + rpcResponse.error());
}
```

## Quien depende de este modulo

```xml
<!-- pricing-api/pom.xml -->
<dependency>
    <groupId>com.mauricio</groupId>
    <artifactId>pricing-api-contract</artifactId>
    <version>${project.version}</version>
</dependency>

<!-- property-api/pom.xml (mismo) -->
```

## Regla de oro

**Solo DTOs de comunicacion van aqui.** Si no es un DTO que viaja entre servicios, no pertenece a este modulo. Nada de utils, helpers, constantes, ni logica de negocio. Si este modulo crece mas alla de DTOs de contrato, se convierte en un problema de acoplamiento.

## Analogias

| Tecnologia | Equivalente |
|---|---|
| gRPC | Archivos `.proto` que generan DTOs para ambos lados |
| OpenAPI | Spec YAML que genera DTOs para cliente y servidor |
| Kotlin Multiplatform | Modulo `shared` con expect/actual |
| Este modulo | DTOs Java compartidos via Maven dependency |

## Pregunta de entrevista

**"Como evitas duplicacion de DTOs entre microservicios?"**
→ Con un modulo Maven de contrato (`pricing-api-contract`). Solo tiene DTOs de comunicacion. Ambos servicios dependen de el. Si el contrato cambia, ambos lados se actualizan al compilar. Es el mismo concepto que `.proto` files en gRPC — un contrato unico como fuente de verdad.
