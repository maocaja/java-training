package com.mauricio.propertyapi.dto;

import com.mauricio.propertyapi.model.Property;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// Record como response DTO — inmutable, con factory method
public record PropertyResponse(
        UUID id,
        String name,
        String city,
        BigDecimal price,
        Integer bedrooms,
        UUID projectId,
        LocalDateTime createdAt
) {
    // Factory method — convierte Entity a DTO
    public static PropertyResponse from(Property property) {
        return new PropertyResponse(
                property.getId(),
                property.getName(),
                property.getCity(),
                property.getPrice(),
                property.getBedrooms(),
                // Si la Property tiene Project, devolvemos su ID. Si no, null.
                property.getProject() != null ? property.getProject().getId() : null,
                property.getCreatedAt()
        );
    }
}
