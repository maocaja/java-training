package com.mauricio.propertyapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mauricio.propertyapi.dto.CreateProjectRequest;
import com.mauricio.propertyapi.dto.CreatePropertyRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WithMockUser(roles = "ADMIN")
class ProjectControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private String createProject(String name, String description) throws Exception {
        var request = new CreateProjectRequest(name, description);
        var result = mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createProperty(String name, String city, int price, int bedrooms) throws Exception {
        var request = new CreatePropertyRequest(name, city, BigDecimal.valueOf(price), bedrooms);
        var result = mockMvc.perform(post("/api/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Nested
    @DisplayName("POST /api/projects")
    class CreateProject {

        @Test
        @DisplayName("should create project and return 201")
        void shouldCreateProject() throws Exception {
            var request = new CreateProjectRequest("Torre Norte", "Proyecto residencial");

            mockMvc.perform(post("/api/projects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Torre Norte"))
                    .andExpect(jsonPath("$.description").value("Proyecto residencial"))
                    .andExpect(jsonPath("$.id").exists());
        }

        @Test
        @DisplayName("should return 400 when name is blank")
        void shouldReturn400WhenNameIsBlank() throws Exception {
            var request = new CreateProjectRequest("", "Descripcion");

            mockMvc.perform(post("/api/projects")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/projects/{id} — con properties (JOIN FETCH)")
    class GetProjectWithProperties {

        @Test
        @DisplayName("should return project with its assigned properties")
        void shouldReturnProjectWithProperties() throws Exception {
            // 1. Crear un project
            var projectId = createProject("Torre Norte", "Proyecto residencial");

            // 2. Crear una property
            var propertyId = createProperty("Depto 1A", "Lima", 150000, 2);

            // 3. Asignar la property al project
            mockMvc.perform(put("/api/properties/{id}/project/{projectId}", propertyId, projectId))
                    .andExpect(status().isOk());

            // 4. Consultar el project por ID — debe venir CON properties (JOIN FETCH)
            mockMvc.perform(get("/api/projects/{id}", projectId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Torre Norte"))
                    .andExpect(jsonPath("$.properties").isArray())
                    .andExpect(jsonPath("$.properties.length()").value(1))
                    .andExpect(jsonPath("$.properties[0].name").value("Depto 1A"));
        }

        @Test
        @DisplayName("should return project with empty properties list")
        void shouldReturnProjectWithEmptyProperties() throws Exception {
            var projectId = createProject("Torre Vacia", "Sin propiedades aun");

            mockMvc.perform(get("/api/projects/{id}", projectId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.properties").isArray())
                    .andExpect(jsonPath("$.properties.length()").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/projects — listado sin properties")
    class GetAllProjects {

        @Test
        @DisplayName("should list projects WITHOUT properties (avoids N+1)")
        void shouldListProjectsWithoutProperties() throws Exception {
            createProject("Torre Norte", "Desc 1");
            createProject("Torre Sur", "Desc 2");

            // El listado devuelve ProjectResponse (SIN campo properties)
            // Esto es a proposito para evitar el problema N+1
            mockMvc.perform(get("/api/projects"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("Torre Norte"))
                    // Verificamos que NO viene el campo properties en el listado
                    .andExpect(jsonPath("$[0].properties").doesNotExist());
        }
    }

    @Nested
    @DisplayName("Property ←→ Project assignment")
    class PropertyProjectAssignment {

        @Test
        @DisplayName("should assign property to project and show projectId")
        void shouldAssignPropertyToProject() throws Exception {
            var projectId = createProject("Torre Norte", "Desc");
            var propertyId = createProperty("Depto 1A", "Lima", 100000, 2);

            // Asignar
            mockMvc.perform(put("/api/properties/{id}/project/{projectId}", propertyId, projectId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.projectId").value(projectId));

            // Verificar que la property ahora tiene projectId
            mockMvc.perform(get("/api/properties/{id}", propertyId))
                    .andExpect(jsonPath("$.projectId").value(projectId));
        }

        @Test
        @DisplayName("should remove property from project")
        void shouldRemovePropertyFromProject() throws Exception {
            var projectId = createProject("Torre Norte", "Desc");
            var propertyId = createProperty("Depto 1A", "Lima", 100000, 2);

            // Asignar
            mockMvc.perform(put("/api/properties/{id}/project/{projectId}", propertyId, projectId));

            // Desasignar
            mockMvc.perform(delete("/api/properties/{id}/project", propertyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.projectId").isEmpty());
        }
    }

    @Nested
    @DisplayName("DELETE /api/projects/{id} — cascade")
    class DeleteProject {

        @Test
        @DisplayName("should delete project and return 204")
        void shouldDeleteProject() throws Exception {
            var projectId = createProject("Torre Delete", "Para borrar");

            mockMvc.perform(delete("/api/projects/{id}", projectId))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/projects/{id}", projectId))
                    .andExpect(status().isNotFound());
        }
    }
}
