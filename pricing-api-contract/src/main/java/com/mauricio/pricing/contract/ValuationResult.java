package com.mauricio.pricing.contract;

import java.math.BigDecimal;

// Resultado de una valuacion — compartido entre pricing-api (produce) y property-api (consume)
public record ValuationResult(
        String city,
        Integer bedrooms,
        BigDecimal estimatedValue,
        BigDecimal pricePerSqm,
        String marketTrend
) {}
