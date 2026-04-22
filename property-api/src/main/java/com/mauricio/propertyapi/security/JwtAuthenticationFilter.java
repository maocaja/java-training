package com.mauricio.propertyapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// --- JWT AUTHENTICATION FILTER ---
// Intercepta CADA request HTTP antes de que llegue al controller.
// Busca el header "Authorization: Bearer <token>", valida el JWT,
// y setea la autenticacion en el SecurityContext.
//
// Equivalente en lo que conoces:
//   - FastAPI: Depends(get_current_user) — pero aca es un filtro global
//   - Go: middleware de autenticacion
//   - Kotlin/Spring: era igual, pero el config cambio en Spring Boot 3
//
// OncePerRequestFilter: garantiza que el filtro corre UNA sola vez por request
// (importante cuando hay redirects o forwards internos).
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. Extraer el header Authorization
        var authHeader = request.getHeader("Authorization");

        // 2. Si no hay header o no empieza con "Bearer ", dejar pasar sin autenticar
        //    (Spring Security decidira si la ruta requiere autenticacion o no)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Extraer el token (quitar "Bearer " del inicio)
        var token = authHeader.substring(7);

        // 4. Validar el token
        if (jwtService.isTokenValid(token)) {
            var email = jwtService.extractEmail(token);
            var role = jwtService.extractRole(token);

            // 5. Crear el objeto de autenticacion de Spring Security
            //    SimpleGrantedAuthority con prefijo "ROLE_" es requerido por Spring Security
            //    para que hasRole("ADMIN") funcione
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
            var authentication = new UsernamePasswordAuthenticationToken(email, null, authorities);

            // 6. Setear en el SecurityContext — ahora Spring sabe quien es el usuario
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 7. Continuar con la cadena de filtros
        filterChain.doFilter(request, response);
    }
}
