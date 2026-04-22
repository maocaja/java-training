package com.mauricio.propertyapi.exception;

import com.mauricio.propertyapi.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// --- Tests para GlobalExceptionHandler ---
// Verifica que cada tipo de excepcion produce el status code y body correctos.
// No necesita Spring context — testeamos el handler directamente.
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Nested
    @DisplayName("handleApiException")
    class HandleApiException {

        @Test
        @DisplayName("should return 404 for ResourceNotFoundException")
        void shouldReturn404ForResourceNotFound() {
            var ex = new ResourceNotFoundException("Property", UUID.randomUUID());

            ResponseEntity<ErrorResponse> response = handler.handleApiException(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo(404);
            assertThat(response.getBody().error()).isEqualTo("Not Found");
            assertThat(response.getBody().message()).contains("Property not found");
        }

        @Test
        @DisplayName("should return 422 for BusinessRuleException")
        void shouldReturn422ForBusinessRule() {
            var ex = new BusinessRuleException("Email already registered");

            ResponseEntity<ErrorResponse> response = handler.handleApiException(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo(422);
            assertThat(response.getBody().error()).isEqualTo("Unprocessable Entity");
            assertThat(response.getBody().message()).isEqualTo("Email already registered");
        }
    }

    @Nested
    @DisplayName("handleUnexpected")
    class HandleUnexpected {

        @Test
        @DisplayName("should return 500 with generic message (no stack trace leak)")
        void shouldReturn500WithGenericMessage() {
            var ex = new RuntimeException("NullPointerException in some internal code");

            ResponseEntity<ErrorResponse> response = handler.handleUnexpected(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo(500);
            // Verifica que NO expone el mensaje interno
            assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
            assertThat(response.getBody().message()).doesNotContain("NullPointerException");
        }
    }
}
