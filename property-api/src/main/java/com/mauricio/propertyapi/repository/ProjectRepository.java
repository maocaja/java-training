package com.mauricio.propertyapi.repository;

import com.mauricio.propertyapi.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    // --- JOIN FETCH: la solucion al problema N+1 ---
    // Sin esto: JPA hace 1 query para el Project + 1 query para sus Properties = 2 queries
    // Con 10 Projects y quieres sus Properties: 1 + 10 = 11 queries (N+1 problem)
    // JOIN FETCH hace todo en 1 sola query con un JOIN
    @Query("SELECT p FROM Project p LEFT JOIN FETCH p.properties WHERE p.id = :id")
    Optional<Project> findByIdWithProperties(@Param("id") UUID id);
}
