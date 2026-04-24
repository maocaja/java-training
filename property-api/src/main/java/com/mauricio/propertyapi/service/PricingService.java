package com.mauricio.propertyapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mauricio.pricing.contract.JsonRpcRequest;
import com.mauricio.propertyapi.client.PricingClient;
import com.mauricio.propertyapi.exception.BusinessRuleException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Service que encapsula la comunicacion con pricing-api (JSON-RPC).
 *
 * Resiliencia aplicada:
 *   - @Retry: reintenta fallos transitorios (network blips).
 *   - @CircuitBreaker: si pricing-api esta claramente caido, deja de
 *     llamar por un rato (30s) para no tumbar property-api tampoco.
 *     Va directo al fallback mientras el circuit esta "open".
 *
 * El orden de las anotaciones importa:
 *   @CircuitBreaker primero → valida si el circuit esta cerrado.
 *   @Retry despues          → reintenta si falla.
 *   Los retry fallidos cuentan para abrir el circuit.
 *
 * Fallback: si todos los retries fallan O el circuit esta open,
 * devuelve ValuationData.unavailable() con valores neutros.
 * El caller ve una respuesta degradada, no un error 500.
 */
@Service
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CB_NAME = "pricingService";

    private final PricingClient pricingClient;

    public PricingService(PricingClient pricingClient) {
        this.pricingClient = pricingClient;
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "getValuationFallback")
    @Retry(name = CB_NAME)
    public ValuationData getValuation(String city, Integer bedrooms) {
        var rpcRequest = JsonRpcRequest.of("getValuation", Map.of(
                "city", city,
                "bedrooms", bedrooms
        ));

        log.info("Calling pricing-api: getValuation(city={}, bedrooms={})", city, bedrooms);

        var rpcResponse = pricingClient.call(rpcRequest);

        if (rpcResponse.hasError()) {
            throw new BusinessRuleException("Pricing service error: " + rpcResponse.error());
        }

        JsonNode result = MAPPER.valueToTree(rpcResponse.result());
        return new ValuationData(
                new BigDecimal(result.get("estimatedValue").asText()),
                new BigDecimal(result.get("pricePerSqm").asText()),
                result.get("marketTrend").asText()
        );
    }

    /**
     * Fallback ejecutado cuando: (a) todos los retries fallan,
     * o (b) el circuit breaker esta open.
     *
     * Firma requerida por Resilience4j: mismos parametros que el metodo
     * original + un Throwable al final. IMPORTANTE: el tipo del Throwable
     * debe matchear la excepcion real para que el fallback se dispare.
     * Usamos Throwable (el mas generico) para capturar cualquier fallo.
     *
     * IMPORTANTE: si el BusinessRuleException (error semantico del pricing-api)
     * llega aqui, lo re-lanzamos. Solo hacemos fallback para fallos de
     * infraestructura (Feign, timeout, circuit open).
     */
    @SuppressWarnings("unused")
    private ValuationData getValuationFallback(String city, Integer bedrooms, Throwable t) {
        if (t instanceof BusinessRuleException) {
            throw (BusinessRuleException) t;
        }
        log.warn("Pricing service unavailable, returning degraded response (city={}, bedrooms={}): {}",
                 city, bedrooms, t.getMessage());
        return ValuationData.unavailable();
    }

    /**
     * DTO interno con los datos de valuacion. Incluye factory para
     * respuestas degradadas cuando pricing-api no esta disponible.
     */
    public record ValuationData(
            BigDecimal estimatedValue,
            BigDecimal pricePerSqm,
            String marketTrend
    ) {
        public static ValuationData unavailable() {
            return new ValuationData(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    "unavailable"
            );
        }
    }
}
