package com.mauricio.propertyapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mauricio.pricing.contract.JsonRpcRequest;
import com.mauricio.propertyapi.client.PricingClient;
import com.mauricio.propertyapi.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

// --- SRP: Single Responsibility Principle ---
// PropertyService se encargaba de TODO: CRUD de properties + comunicacion con pricing-api.
// Ahora PricingService encapsula la logica de comunicacion JSON-RPC con pricing-api.
//
// Si mañana pricing-api migra de JSON-RPC a REST, solo tocas esta clase.
// PropertyService no se entera del cambio.
//
// Pregunta de entrevista: "Por que un service separado?"
// → Porque PropertyService tenia dos razones para cambiar: logica de properties
//   y protocolo de comunicacion con pricing. Eso viola SRP.
@Service
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PricingClient pricingClient;

    public PricingService(PricingClient pricingClient) {
        this.pricingClient = pricingClient;
    }

    // Llama al pricing-api para obtener una valuacion
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

        // Convertir el result (Object) a JsonNode para extraer campos
        JsonNode result = MAPPER.valueToTree(rpcResponse.result());
        return new ValuationData(
                new BigDecimal(result.get("estimatedValue").asText()),
                new BigDecimal(result.get("pricePerSqm").asText()),
                result.get("marketTrend").asText()
        );
    }

    // DTO interno — datos que el PricingService devuelve al caller
    public record ValuationData(
            BigDecimal estimatedValue,
            BigDecimal pricePerSqm,
            String marketTrend
    ) {}
}
