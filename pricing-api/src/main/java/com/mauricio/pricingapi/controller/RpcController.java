package com.mauricio.pricingapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mauricio.pricing.contract.JsonRpcRequest;
import com.mauricio.pricing.contract.JsonRpcResponse;
import com.mauricio.pricing.contract.ValuationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

// --- JSON-RPC CONTROLLER ---
// UN solo endpoint: POST /rpc
// El "method" en el body determina que accion ejecutar.
// Es como un router interno basado en el campo "method".
//
// En REST tendrias:
//   GET /api/valuations?city=Lima&bedrooms=3
//   GET /api/market-trends/Lima
//
// En JSON-RPC:
//   POST /rpc  {"method": "getValuation", "params": {"city": "Lima", "bedrooms": 3}}
//   POST /rpc  {"method": "getMarketTrend", "params": {"city": "Lima"}}
//
// Todo pasa por el mismo endpoint. Mas simple para comunicacion entre servicios.
@RestController
public class RpcController {

    private static final Logger log = LoggerFactory.getLogger(RpcController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Precios base por ciudad (simulado — en produccion vendria de una BD)
    private static final Map<String, BigDecimal> PRICE_PER_SQM = Map.of(
            "buenos aires", BigDecimal.valueOf(2500),
            "lima", BigDecimal.valueOf(1800),
            "bogota", BigDecimal.valueOf(2000),
            "santiago", BigDecimal.valueOf(2800),
            "mexico", BigDecimal.valueOf(1500)
    );

    @PostMapping("/rpc")
    public JsonRpcResponse handleRpc(@RequestBody JsonRpcRequest request) {
        log.info("JSON-RPC request: method={}, id={}", request.method(), request.id());

        // --- Dispatch basado en el "method" ---
        // En un proyecto grande usarias un registry/map de handlers.
        // Aqui lo hacemos simple con switch.
        return switch (request.method()) {
            case "getValuation" -> handleGetValuation(request);
            case "getMarketTrend" -> handleGetMarketTrend(request);
            default -> JsonRpcResponse.error(
                    -32601, "Method not found: " + request.method(), request.id());
        };
    }

    private JsonRpcResponse handleGetValuation(JsonRpcRequest request) {
        try {
            var city = request.params().get("city").asText().toLowerCase();
            var bedrooms = request.params().get("bedrooms").asInt();

            var pricePerSqm = PRICE_PER_SQM.getOrDefault(city, BigDecimal.valueOf(1500));
            // Formula simple: precio = precio_m2 * (bedrooms * 30m2)
            var estimatedValue = pricePerSqm.multiply(BigDecimal.valueOf(bedrooms * 30L));
            var trend = estimatedValue.compareTo(BigDecimal.valueOf(200000)) > 0 ? "high" : "stable";

            var result = new ValuationResult(city, bedrooms, estimatedValue, pricePerSqm, trend);
            return JsonRpcResponse.success(result, request.id());
        } catch (Exception e) {
            return JsonRpcResponse.error(-32602, "Invalid params: " + e.getMessage(), request.id());
        }
    }

    private JsonRpcResponse handleGetMarketTrend(JsonRpcRequest request) {
        try {
            var city = request.params().get("city").asText().toLowerCase();
            var pricePerSqm = PRICE_PER_SQM.getOrDefault(city, BigDecimal.valueOf(1500));

            var trend = Map.of(
                    "city", city,
                    "pricePerSqm", pricePerSqm,
                    "trend", pricePerSqm.compareTo(BigDecimal.valueOf(2000)) > 0 ? "growing" : "stable",
                    "confidence", "0.85"
            );
            return JsonRpcResponse.success(trend, request.id());
        } catch (Exception e) {
            return JsonRpcResponse.error(-32602, "Invalid params: " + e.getMessage(), request.id());
        }
    }
}
