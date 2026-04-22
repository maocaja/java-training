package com.mauricio.propertyapi.service;

import com.mauricio.propertyapi.dto.AuthRequest;
import com.mauricio.propertyapi.dto.AuthResponse;
import com.mauricio.propertyapi.exception.BusinessRuleException;
import com.mauricio.propertyapi.model.Role;
import com.mauricio.propertyapi.model.User;
import com.mauricio.propertyapi.repository.UserRepository;
import com.mauricio.propertyapi.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(AuthRequest request) {
        return registerWithRole(request, Role.USER);
    }

    // Register como ADMIN — en produccion esto estaria protegido o seria un seed de BD
    public AuthResponse registerAdmin(AuthRequest request) {
        return registerWithRole(request, Role.ADMIN);
    }

    public AuthResponse login(AuthRequest request) {
        var user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessRuleException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessRuleException("Invalid email or password");
        }

        var token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(token, user.getEmail(), user.getRole().name());
    }

    // --- DRY: logica comun extraida ---
    // register() y registerAdmin() eran copy-paste con solo el Role diferente.
    // Si mañana agregas validacion de password strength, lo pones UNA vez aqui.
    private AuthResponse registerWithRole(AuthRequest request, Role role) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("Email already registered: " + request.email());
        }

        var user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(role);

        userRepository.save(user);

        var token = jwtService.generateToken(user.getEmail(), role.name());
        return new AuthResponse(token, user.getEmail(), role.name());
    }
}
