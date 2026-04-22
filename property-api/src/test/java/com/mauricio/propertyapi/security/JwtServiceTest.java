package com.mauricio.propertyapi.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

// --- Tests para JwtService ---
// Cubre: generacion, extraccion de claims, validacion, y tokens invalidos/expirados.
// No necesita Spring context — JwtService es un POJO con dependencias inyectadas por constructor.
class JwtServiceTest {

    // Secret de 256 bits en Base64 (misma que application.yml de test)
    private static final String TEST_SECRET =
            Base64.getEncoder().encodeToString("this-is-a-test-secret-key-32-bytes!".getBytes());

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        // 1 hora de expiracion para tests normales
        jwtService = new JwtService(TEST_SECRET, 3600000);
    }

    @Nested
    @DisplayName("generateToken")
    class GenerateToken {

        @Test
        @DisplayName("should generate a non-null token")
        void shouldGenerateToken() {
            String token = jwtService.generateToken("admin@test.com", "ADMIN");

            assertThat(token).isNotNull().isNotBlank();
            // JWT tiene 3 partes separadas por '.'
            assertThat(token.split("\\.")).hasSize(3);
        }
    }

    @Nested
    @DisplayName("extractEmail")
    class ExtractEmail {

        @Test
        @DisplayName("should extract email from valid token")
        void shouldExtractEmail() {
            String token = jwtService.generateToken("user@test.com", "USER");

            assertThat(jwtService.extractEmail(token)).isEqualTo("user@test.com");
        }
    }

    @Nested
    @DisplayName("extractRole")
    class ExtractRole {

        @Test
        @DisplayName("should extract role from valid token")
        void shouldExtractRole() {
            String token = jwtService.generateToken("admin@test.com", "ADMIN");

            assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("should distinguish USER from ADMIN role")
        void shouldDistinguishRoles() {
            String userToken = jwtService.generateToken("user@test.com", "USER");
            String adminToken = jwtService.generateToken("admin@test.com", "ADMIN");

            assertThat(jwtService.extractRole(userToken)).isEqualTo("USER");
            assertThat(jwtService.extractRole(adminToken)).isEqualTo("ADMIN");
        }
    }

    @Nested
    @DisplayName("isTokenValid")
    class IsTokenValid {

        @Test
        @DisplayName("should return true for valid token")
        void shouldReturnTrueForValidToken() {
            String token = jwtService.generateToken("test@test.com", "USER");

            assertThat(jwtService.isTokenValid(token)).isTrue();
        }

        @Test
        @DisplayName("should return false for tampered token")
        void shouldReturnFalseForTamperedToken() {
            String token = jwtService.generateToken("test@test.com", "USER");
            // Alterar un caracter del token
            String tampered = token.substring(0, token.length() - 1) + "X";

            assertThat(jwtService.isTokenValid(tampered)).isFalse();
        }

        @Test
        @DisplayName("should return false for garbage string")
        void shouldReturnFalseForGarbage() {
            assertThat(jwtService.isTokenValid("not.a.jwt")).isFalse();
        }

        @Test
        @DisplayName("should return false for expired token")
        void shouldReturnFalseForExpiredToken() {
            // Crear un JwtService con expiracion de 0ms (token ya expira al crearse)
            var expiredService = new JwtService(TEST_SECRET, 0);
            String token = expiredService.generateToken("test@test.com", "USER");

            assertThat(jwtService.isTokenValid(token)).isFalse();
        }

        @Test
        @DisplayName("should return false for token signed with different key")
        void shouldReturnFalseForDifferentKey() {
            String otherSecret = Base64.getEncoder()
                    .encodeToString("a-completely-different-secret-key!!".getBytes());
            var otherService = new JwtService(otherSecret, 3600000);

            String token = otherService.generateToken("test@test.com", "USER");

            assertThat(jwtService.isTokenValid(token)).isFalse();
        }
    }
}
