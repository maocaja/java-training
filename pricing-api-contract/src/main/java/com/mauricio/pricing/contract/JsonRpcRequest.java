package com.mauricio.pricing.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

// --- JSON-RPC REQUEST (especificacion 2.0) ---
// Formato estandar:
// {
//   "jsonrpc": "2.0",
//   "method": "getValuation",
//   "params": { "city": "Lima", ... },
//   "id": 1
// }
//
// Este record unifica las dos versiones que existian:
//   - pricing-api usaba JsonNode params
//   - property-api usaba Map<String, Object> params
// Ahora ambos usan este mismo DTO con JsonNode (mas flexible)
// y un factory method que acepta Map para comodidad.
public record JsonRpcRequest(
        String jsonrpc,
        String method,
        JsonNode params,
        Object id
) {
    private static final AtomicLong ID_COUNTER = new AtomicLong(1);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Factory method para crear requests desde un Map (comodo para el caller)
    public static JsonRpcRequest of(String method, Map<String, Object> params) {
        return new JsonRpcRequest("2.0", method, MAPPER.valueToTree(params), ID_COUNTER.getAndIncrement());
    }
}
