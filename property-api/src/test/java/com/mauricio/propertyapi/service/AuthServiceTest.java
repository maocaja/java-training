package com.mauricio.propertyapi.service;

import com.mauricio.propertyapi.dto.AuthRequest;
import com.mauricio.propertyapi.dto.AuthResponse;
import com.mauricio.propertyapi.exception.BusinessRuleException;
import com.mauricio.propertyapi.model.Role;
import com.mauricio.propertyapi.model.User;
import com.mauricio.propertyapi.repository.UserRepository;
import com.mauricio.propertyapi.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtService jwtService;

    @InjectMocks
    AuthService authService;

    private User buildUser(String email, String hashedPassword, Role role) {
        var user = new User();
        user.setEmail(email);
        user.setPassword(hashedPassword);
        user.setRole(role);
        return user;
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("should register new user with USER role")
        void shouldRegisterUser() {
            var request = new AuthRequest("user@test.com", "secret123");

            when(userRepository.existsByEmail("user@test.com")).thenReturn(false);
            when(passwordEncoder.encode("secret123")).thenReturn("hashed");
            when(jwtService.generateToken("user@test.com", "USER")).thenReturn("jwt-token");

            AuthResponse response = authService.register(request);

            assertThat(response.email()).isEqualTo("user@test.com");
            assertThat(response.role()).isEqualTo("USER");
            assertThat(response.token()).isEqualTo("jwt-token");

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("should throw when email already exists")
        void shouldThrowWhenEmailExists() {
            var request = new AuthRequest("existing@test.com", "secret123");

            when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Email already registered");

            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("registerAdmin")
    class RegisterAdmin {

        @Test
        @DisplayName("should register new user with ADMIN role")
        void shouldRegisterAdmin() {
            var request = new AuthRequest("admin@test.com", "secret123");

            when(userRepository.existsByEmail("admin@test.com")).thenReturn(false);
            when(passwordEncoder.encode("secret123")).thenReturn("hashed");
            when(jwtService.generateToken("admin@test.com", "ADMIN")).thenReturn("admin-jwt");

            AuthResponse response = authService.registerAdmin(request);

            assertThat(response.email()).isEqualTo("admin@test.com");
            assertThat(response.role()).isEqualTo("ADMIN");
            assertThat(response.token()).isEqualTo("admin-jwt");
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("should login with correct credentials")
        void shouldLoginSuccessfully() {
            var request = new AuthRequest("user@test.com", "secret123");
            var user = buildUser("user@test.com", "hashed", Role.USER);

            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("secret123", "hashed")).thenReturn(true);
            when(jwtService.generateToken("user@test.com", "USER")).thenReturn("jwt-token");

            AuthResponse response = authService.login(request);

            assertThat(response.email()).isEqualTo("user@test.com");
            assertThat(response.token()).isEqualTo("jwt-token");
        }

        @Test
        @DisplayName("should throw when email not found")
        void shouldThrowWhenEmailNotFound() {
            var request = new AuthRequest("nobody@test.com", "secret123");

            when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Invalid email or password");
        }

        @Test
        @DisplayName("should throw when password is wrong")
        void shouldThrowWhenPasswordWrong() {
            var request = new AuthRequest("user@test.com", "wrongpass");
            var user = buildUser("user@test.com", "hashed", Role.USER);

            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongpass", "hashed")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Invalid email or password");
        }
    }
}
