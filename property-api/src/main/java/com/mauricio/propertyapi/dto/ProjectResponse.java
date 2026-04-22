package com.mauricio.propertyapi.dto;

import com.mauricio.propertyapi.model.Project;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// Response SIN properties — para listados donde no necesitas los hijos
public record ProjectResponse(
        UUID id,
        String name,
        String description,
        LocalDateTime createdAt
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getCreatedAt()
        );
    }
}
