package com.mauricio.propertyapi.dto;

public record AuthResponse(
        String token,
        String email,
        String role
) {}
