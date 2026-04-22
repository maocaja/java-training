package com.mauricio.propertyapi.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    // --- LA RELACION 1:N ---
    // mappedBy = "project" → el campo "project" en Property es el dueño de la relacion
    // cascade = CascadeType.ALL → si borras un Project, se borran sus Properties
    // orphanRemoval = true → si quitas una Property de la lista, se borra de la BD
    // fetch = FetchType.LAZY → NO carga las properties hasta que las pidas (CRITICO)
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Property> properties = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // --- Getters y Setters ---

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<Property> getProperties() { return properties; }
    public void setProperties(List<Property> properties) { this.properties = properties; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    // --- Metodo helper para mantener la relacion bidireccional consistente ---
    // Sin esto, tendrias que hacer project.getProperties().add(prop) Y prop.setProject(project)
    // Con esto, solo haces project.addProperty(prop) y listo
    public void addProperty(Property property) {
        properties.add(property);
        property.setProject(this);
    }

    public void removeProperty(Property property) {
        properties.remove(property);
        property.setProject(null);
    }
}
