package com.mauricio.propertyapi.controller;

import com.mauricio.propertyapi.dto.CreateProjectRequest;
import com.mauricio.propertyapi.dto.ProjectResponse;
import com.mauricio.propertyapi.dto.ProjectWithPropertiesResponse;
import com.mauricio.propertyapi.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<ProjectResponse> findAll() {
        return service.findAll();
    }

    // Este endpoint devuelve el Project CON sus Properties (JOIN FETCH)
    @GetMapping("/{id}")
    public ProjectWithPropertiesResponse findById(@PathVariable UUID id) {
        return service.findByIdWithProperties(id);
    }

    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable UUID id, @Valid @RequestBody CreateProjectRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
