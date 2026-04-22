package com.mauricio.pricing.contract;

import com.fasterxml.jackson.databind.JsonNode;

// --- JSON-RPC RESPONSE (especificacion 2.0) ---
// Exito:  { "jsonrpc": "2.0", "result": { ... }, "id": 1 }
// Error:  { "jsonrpc": "2.0", "error": { "code": -32601, "message": "..." }, "id": 1 }
//
// El HTTP status es SIEMPRE 200 en JSON-RPC (incluso para errores).
// Los errores se comunican dentro del body, no con HTTP status codes.
public record JsonRpcResponse(
        String jsonrpc,
        Object result,
        JsonRpcError error,
        Object id
) {
    public static JsonRpcResponse success(Object result, Object id) {
        return new JsonRpcResponse("2.0", result, null, id);
    }

    public static JsonRpcResponse error(int code, String message, Object id) {
        return new JsonRpcResponse("2.0", null, new JsonRpcError(code, message), id);
    }

    public boolean hasError() {
        return error != null;
    }

    public record JsonRpcError(int code, String message) {}
}
