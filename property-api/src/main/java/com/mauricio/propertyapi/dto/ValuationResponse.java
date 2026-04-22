package com.mauricio.propertyapi.dto;

import java.math.BigDecimal;
import java.util.UUID;

// Response que combina datos de la property + valuacion del pricing-api
public record ValuationResponse(
        UUID propertyId,
        String name,
        String city,
        Integer bedrooms,
        BigDecimal currentPrice,
        BigDecimal estimatedValue,
        BigDecimal pricePerSqm,
        String marketTrend
) {}
