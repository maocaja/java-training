package com.mauricio.propertyapi.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

// Java 21 Record — reemplaza POJOs/DTOs con boilerplate
// Equivalente a Kotlin data class
public record CreatePropertyRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "City is required")
        String city,

        @NotNull(message = "Price is required")
        @Positive(message = "Price must be positive")
        BigDecimal price,

        @NotNull(message = "Bedrooms is required")
        @Min(value = 1, message = "At least 1 bedroom")
        Integer bedrooms
) {}
