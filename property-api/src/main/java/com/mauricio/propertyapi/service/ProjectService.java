package com.mauricio.propertyapi.service;

import com.mauricio.propertyapi.dto.CreateProjectRequest;
import com.mauricio.propertyapi.dto.ProjectResponse;
import com.mauricio.propertyapi.dto.ProjectWithPropertiesResponse;
import com.mauricio.propertyapi.exception.ResourceNotFoundException;
import com.mauricio.propertyapi.model.Project;
import com.mauricio.propertyapi.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository repository;

    public ProjectService(ProjectRepository repository) {
        this.repository = repository;
    }

    public ProjectResponse create(CreateProjectRequest request) {
        var project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());

        var saved = repository.save(project);
        return ProjectResponse.from(saved);
    }

    public List<ProjectResponse> findAll() {
        // Devolvemos ProjectResponse (SIN properties) para el listado
        // Asi evitamos cargar las properties de TODOS los projects (N+1)
        return repository.findAll()
                .stream()
                .map(ProjectResponse::from)
                .toList();
    }

    // --- @Transactional ---
    // Necesario aqui porque findByIdWithProperties hace un JOIN FETCH
    // y luego accedemos a las properties en el DTO factory method.
    // Sin @Transactional, la sesion de Hibernate se cierra despues del repository call
    // y al acceder a properties (LAZY) lanza LazyInitializationException.
    //
    // Regla: si tu metodo accede a colecciones LAZY fuera del repository, necesitas @Transactional
    @Transactional(readOnly = true)
    public ProjectWithPropertiesResponse findByIdWithProperties(UUID id) {
        var project = repository.findByIdWithProperties(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));

        return ProjectWithPropertiesResponse.from(project);
    }

    public ProjectResponse findById(UUID id) {
        return repository.findById(id)
                .map(ProjectResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
    }

    public ProjectResponse update(UUID id, CreateProjectRequest request) {
        var project = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));

        project.setName(request.name());
        project.setDescription(request.description());

        var saved = repository.save(project);
        return ProjectResponse.from(saved);
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Project", id);
        }
        // cascade = CascadeType.ALL → al borrar el Project, se borran sus Properties
        repository.deleteById(id);
    }
}
