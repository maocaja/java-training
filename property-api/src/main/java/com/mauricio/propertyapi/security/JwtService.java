package com.mauricio.propertyapi.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

// --- Servicio de JWT ---
// Responsabilidad unica: generar y validar tokens JWT.
// No sabe nada de HTTP ni de Spring Security — solo maneja tokens.
//
// JWT tiene 3 partes: header.payload.signature
//   - header: algoritmo (HS256)
//   - payload: datos del usuario (email, role, expiracion)
//   - signature: firma con la secret key (garantiza que nadie altero el token)
//
// Conexion con lo que conoces:
//   - En FastAPI/MOC usabas python-jose para lo mismo
//   - En Go usabas golang-jwt
//   - El concepto es identico, cambia la libreria
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    // --- @Value ---
    // Inyecta valores del application.yml.
    // En produccion la secret key viene de una variable de entorno, NUNCA hardcodeada.
    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        this.expirationMs = expirationMs;
    }

    // Genera un token JWT con el email y el role del usuario
    public String generateToken(String email, String role) {
        var now = new Date();
        var expiration = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    // Extrae el email (subject) del token
    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    // Extrae el role del token
    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    // Verifica si el token es valido (firma correcta + no expirado)
    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
