package com.mauricio.propertyapi.config;

import com.mauricio.propertyapi.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// --- SPRING SECURITY CONFIG (Spring Boot 3) ---
// ESTE ES EL CAMBIO MAS GRANDE respecto a Spring Boot 2:
//
// ANTES (Spring Boot 2):
//   public class SecurityConfig extends WebSecurityConfigurerAdapter {
//       @Override
//       protected void configure(HttpSecurity http) { ... }
//   }
//
// AHORA (Spring Boot 3):
//   WebSecurityConfigurerAdapter fue ELIMINADO.
//   Usas un @Bean que retorna SecurityFilterChain.
//   Es mas declarativo y mas facil de testear.
//
// Pregunta de entrevista: "Que cambio en la seguridad de Spring Boot 3?"
// → WebSecurityConfigurerAdapter fue eliminado. Ahora defines un @Bean SecurityFilterChain.
//   Toda la config usa lambdas (csrf(c -> c.disable()) en vez de .csrf().disable()).
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // --- CSRF deshabilitado ---
                // CSRF protege contra ataques de formularios HTML.
                // En una API REST con JWT no necesitas CSRF (el token ya protege).
                .csrf(csrf -> csrf.disable())

                // --- Stateless sessions ---
                // No guardamos sesion en el servidor. Cada request trae su JWT.
                // Esto es lo que diferencia autenticacion con JWT de session-based auth.
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // --- Reglas de autorizacion ---
                .authorizeHttpRequests(auth -> auth
                        // /api/auth/** es publico (login, register)
                        .requestMatchers("/api/auth/**").permitAll()
                        // H2 console publico (solo desarrollo)
                        .requestMatchers("/h2-console/**").permitAll()
                        // --- Actuator: health/info PUBLICOS ---
                        // Razon: el ALB/ECS hace healthcheck sin JWT.
                        // /actuator/info expone metadata no-sensible (version, app name).
                        // Los OTROS endpoints de actuator (env, metrics, etc) requieren
                        // auth — aun si los exponemos por management.endpoints, Spring
                        // Security los protege.
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").authenticated()
                        // GET es para todos los autenticados (USER y ADMIN)
                        .requestMatchers(HttpMethod.GET, "/api/**").authenticated()
                        // POST, PUT, DELETE solo para ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                        // Cualquier otra ruta requiere autenticacion
                        .anyRequest().authenticated()
                )

                // H2 console usa frames — necesitamos desactivar frameOptions
                .headers(headers -> headers.frameOptions(fo -> fo.disable()))

                // --- Agregar nuestro filtro JWT ANTES del filtro de autenticacion de Spring ---
                // Spring tiene su propia cadena de filtros. Insertamos el nuestro antes
                // de UsernamePasswordAuthenticationFilter para que valide el JWT primero.
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }

    // --- BCrypt PasswordEncoder ---
    // Hashea passwords con BCrypt (estandar de la industria).
    // BCrypt incluye salt automatico — dos passwords iguales generan hashes diferentes.
    //
    // Pregunta de entrevista: "Por que BCrypt y no SHA-256?"
    // → BCrypt es LENTO a proposito (configurable con rounds). SHA-256 es rapido,
    //   lo que facilita ataques de fuerza bruta. BCrypt esta disenado para hashear passwords.
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
