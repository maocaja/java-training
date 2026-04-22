package com.mauricio.propertyapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// --- @SpringBootTest ---
// Levanta TODA la aplicacion Spring (controller, service, repository, H2).
// Es un test de INTEGRACION — prueba el flujo completo de HTTP hasta la BD.
// Mas lento que unit test, pero prueba que todo funciona junto.
//
// Diferencia con unit test: aqui NO hay mocks. Todo es real.
@SpringBootTest
@ActiveProfiles("test")
// --- @AutoConfigureMockMvc ---
// Configura MockMvc automaticamente. MockMvc simula requests HTTP
// sin levantar un servidor real (no abre un puerto).
// Es mas rapido que hacer requests con RestTemplate o WebTestClient.
@AutoConfigureMockMvc
// --- @DirtiesContext ---
// Resetea el contexto de Spring (incluyendo la BD H2) entre cada clase de test.
// Sin esto, los datos de un test pueden afectar a otro.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
// --- @WithMockUser ---
// Simula un usuario autenticado con role ADMIN para TODOS los tests de esta clase.
// Sin esto, Spring Security bloquea los requests con 401/403.
// No necesita un JWT real — Spring Security confía en el mock user.
@WithMockUser(roles = "ADMIN")
class PropertyControllerTest {

    // --- @Autowired en tests ---
    // En tests de integracion SI usamos @Autowired porque Spring gestiona todo.
    // En unit tests usamos @Mock/@InjectMocks porque no hay Spring context.
    @Autowired
    MockMvc mockMvc;

    // --- ObjectMapper ---
    // Serializa Java objects a JSON (para enviar en el body de los requests).
    // Spring Boot lo configura automaticamente con Jackson.
    @Autowired
    ObjectMapper objectMapper;

    @Nested
    @DisplayName("POST /api/properties")
    class CreateProperty {

        @Test
        @DisplayName("should create property and return 201")
        void shouldCreateProperty() throws Exception {
            var request = new CreatePropertyRequest(
                    "Casa Centro", "Lima", BigDecimal.valueOf(200000), 3);

            // --- mockMvc.perform() ---
            // Simula un HTTP request. Encadenas:
            // 1. post("/url") — metodo HTTP + URL
            // 2. contentType() — Content-Type header
            // 3. content() — body del request (serializado a JSON)
            // 4. andExpect() — validaciones sobre la response
            mockMvc.perform(post("/api/properties")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    // --- status().isCreated() ---
                    // Verifica que el response code sea 201
                    .andExpect(status().isCreated())
                    // --- jsonPath ---
                    // Navega el JSON de respuesta con expresiones JSONPath.
                    // $.name → campo "name" en el root del JSON
                    .andExpect(jsonPath("$.name").value("Casa Centro"))
                    .andExpect(jsonPath("$.city").value("Lima"))
                    .andExpect(jsonPath("$.price").value(200000))
                    .andExpect(jsonPath("$.bedrooms").value(3))
                    // Verificamos que se genero un ID
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        @DisplayName("should return 400 when name is blank")
        void shouldReturn400WhenNameIsBlank() throws Exception {
            // Request con name vacio — debe fallar validacion @NotBlank
            var request = new CreatePropertyRequest(
                    "", "Lima", BigDecimal.valueOf(200000), 3);

            mockMvc.perform(post("/api/properties")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when price is negative")
        void shouldReturn400WhenPriceIsNegative() throws Exception {
            var request = new CreatePropertyRequest(
                    "Casa", "Lima", BigDecimal.valueOf(-100), 3);

            mockMvc.perform(post("/api/properties")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 when bedrooms is zero")
        void shouldReturn400WhenBedroomsIsZero() throws Exception {
            var request = new CreatePropertyRequest(
                    "Casa", "Lima", BigDecimal.valueOf(100000), 0);

            mockMvc.perform(post("/api/properties")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/properties")
    class GetProperties {

        @Test
        @DisplayName("should return empty list initially")
        void shouldReturnEmptyList() throws Exception {
            mockMvc.perform(get("/api/properties"))
                    .andExpect(status().isOk())
                    // --- $ ---
                    // El root del JSON. hasSize(0) verifica que el array esta vacio.
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("should return properties after creation")
        void shouldReturnPropertiesAfterCreation() throws Exception {
            // Primero creamos una property
            var request = new CreatePropertyRequest(
                    "Casa Test", "Bogota", BigDecimal.valueOf(300000), 4);

            mockMvc.perform(post("/api/properties")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));

            // Luego listamos y verificamos
            mockMvc.perform(get("/api/properties"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("Casa Test"));
        }

        @Test
        @DisplayName("should filter by city")
        void shouldFilterByCity() throws Exception {
            // Crear 2 properties en ciudades diferentes
            mockMvc.perform(post("/api/properties")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            new CreatePropertyRequest("Casa Lima", "Lima", BigDecimal.valueOf(100000), 2))));

            mockMvc.perform(post("/api/properties")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(
                            new CreatePropertyRequest("Casa Bogota", "Bogota", BigDecimal.valueOf(200000), 3))));

            // Filtrar por Lima — solo debe devolver 1
            mockMvc.perform(get("/api/properties").param("city", "Lima"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].city").value("Lima"));
        }
    }

    @Nested
    @DisplayName("GET /api/properties/{id}")
    class GetPropertyById {

        @Test
        @DisplayName("should return property by id")
        void shouldReturnPropertyById() throws Exception {
            // Crear y capturar el ID de la respuesta
            var request = new CreatePropertyRequest(
                    "Casa ID", "Lima", BigDecimal.valueOf(150000), 2);

            var createResult = mockMvc.perform(post("/api/properties")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            // Extraer el ID del JSON de respuesta
            var responseJson = createResult.getResponse().getContentAsString();
            var id = objectMapper.readTree(responseJson).get("id").asText();

            // Buscar por ID
            mockMvc.perform(get("/api/properties/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Casa ID"))
                    .andExpect(jsonPath("$.id").value(id));
        }

        @Test
        @DisplayName("should return 404 for non-existent id")
        void shouldReturn404ForNonExistentId() throws Exception {
            mockMvc.perform(get("/api/properties/{id}", java.util.UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/properties/{id}")
    class DeleteProperty {

        @Test
        @DisplayName("should delete property and return 204")
        void shouldDeleteProperty() throws Exception {
            // Crear
            var request = new CreatePropertyRequest(
                    "Casa Delete", "Lima", BigDecimal.valueOf(100000), 2);

            var createResult = mockMvc.perform(post("/api/properties")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn();

            var id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                    .get("id").asText();

            // Borrar — debe devolver 204
            mockMvc.perform(delete("/api/properties/{id}", id))
                    .andExpect(status().isNoContent());

            // Verificar que ya no existe — debe devolver 404
            mockMvc.perform(get("/api/properties/{id}", id))
                    .andExpect(status().isNotFound());
        }
    }
}
