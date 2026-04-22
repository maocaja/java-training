package com.mauricio.propertyapi.dto;

import com.mauricio.propertyapi.model.Project;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// Response CON properties — solo cuando pides un Project especifico por ID
// Tener dos DTOs (con y sin hijos) evita cargar datos innecesarios en listados
public record ProjectWithPropertiesResponse(
        UUID id,
        String name,
        String description,
        List<PropertyResponse> properties,
        LocalDateTime createdAt
) {
    public static ProjectWithPropertiesResponse from(Project project) {
        return new ProjectWithPropertiesResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                // Aqui accedemos a project.getProperties() → dispara la carga LAZY
                project.getProperties().stream()
                        .map(PropertyResponse::from)
                        .toList(),
                project.getCreatedAt()
        );
    }
}
