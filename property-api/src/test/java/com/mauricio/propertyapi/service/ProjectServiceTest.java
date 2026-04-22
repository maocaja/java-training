package com.mauricio.propertyapi.service;

import com.mauricio.propertyapi.dto.CreateProjectRequest;
import com.mauricio.propertyapi.dto.ProjectResponse;
import com.mauricio.propertyapi.dto.ProjectWithPropertiesResponse;
import com.mauricio.propertyapi.model.Project;
import com.mauricio.propertyapi.model.Property;
import com.mauricio.propertyapi.repository.ProjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.mauricio.propertyapi.exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    ProjectRepository repository;

    @InjectMocks
    ProjectService service;

    private Project buildProject(UUID id, String name, String description) {
        var project = new Project();
        project.setId(id);
        project.setName(name);
        project.setDescription(description);
        return project;
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should create a project and return response")
        void shouldCreateProject() {
            var request = new CreateProjectRequest("Torre Norte", "Proyecto residencial");
            var savedId = UUID.randomUUID();
            var saved = buildProject(savedId, "Torre Norte", "Proyecto residencial");

            when(repository.save(any(Project.class))).thenReturn(saved);

            ProjectResponse result = service.create(request);

            assertThat(result.name()).isEqualTo("Torre Norte");
            assertThat(result.description()).isEqualTo("Proyecto residencial");
            assertThat(result.id()).isEqualTo(savedId);
            verify(repository).save(any(Project.class));
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("should return all projects without properties")
        void shouldReturnAllProjects() {
            var projects = List.of(
                    buildProject(UUID.randomUUID(), "Torre Norte", "Desc 1"),
                    buildProject(UUID.randomUUID(), "Torre Sur", "Desc 2")
            );

            when(repository.findAll()).thenReturn(projects);

            List<ProjectResponse> result = service.findAll();

            // Verificamos que devuelve ProjectResponse (SIN properties)
            // No hay campo "properties" en ProjectResponse — eso es a proposito
            assertThat(result).hasSize(2);
            assertThat(result.get(0).name()).isEqualTo("Torre Norte");
        }
    }

    @Nested
    @DisplayName("findByIdWithProperties")
    class FindByIdWithProperties {

        @Test
        @DisplayName("should return project with its properties")
        void shouldReturnProjectWithProperties() {
            var projectId = UUID.randomUUID();
            var project = buildProject(projectId, "Torre Norte", "Desc");

            // Agregar properties al project usando el helper method
            var prop = new Property();
            prop.setId(UUID.randomUUID());
            prop.setName("Depto 1A");
            prop.setCity("Lima");
            prop.setPrice(BigDecimal.valueOf(100000));
            prop.setBedrooms(2);
            project.addProperty(prop);

            when(repository.findByIdWithProperties(projectId)).thenReturn(Optional.of(project));

            ProjectWithPropertiesResponse result = service.findByIdWithProperties(projectId);

            assertThat(result.id()).isEqualTo(projectId);
            assertThat(result.name()).isEqualTo("Torre Norte");
            // Verificamos que las properties vienen incluidas
            assertThat(result.properties()).hasSize(1);
            assertThat(result.properties().get(0).name()).isEqualTo("Depto 1A");
        }

        @Test
        @DisplayName("should throw 404 when project not found")
        void shouldThrow404WhenNotFound() {
            var id = UUID.randomUUID();

            when(repository.findByIdWithProperties(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findByIdWithProperties(id))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Project not found");
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("should delete project when exists")
        void shouldDeleteWhenExists() {
            var id = UUID.randomUUID();

            when(repository.existsById(id)).thenReturn(true);

            service.delete(id);

            // cascade = ALL → al borrar el project, se borran sus properties
            verify(repository).deleteById(id);
        }

        @Test
        @DisplayName("should throw 404 when deleting non-existent project")
        void shouldThrow404WhenDeletingNonExistent() {
            var id = UUID.randomUUID();

            when(repository.existsById(id)).thenReturn(false);

            assertThatThrownBy(() -> service.delete(id))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Project not found");
        }
    }
}
