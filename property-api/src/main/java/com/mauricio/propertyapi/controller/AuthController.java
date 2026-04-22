package com.mauricio.propertyapi.controller;

import com.mauricio.propertyapi.dto.AuthRequest;
import com.mauricio.propertyapi.dto.AuthResponse;
import com.mauricio.propertyapi.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody AuthRequest request) {
        return authService.register(request);
    }

    // Solo para desarrollo/testing — en produccion NO existiria
    @PostMapping("/register-admin")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerAdmin(@Valid @RequestBody AuthRequest request) {
        return authService.registerAdmin(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest request) {
        return authService.login(request);
    }
}
