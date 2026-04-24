package com.mauricio.pricingapi.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Filtro que propaga el X-Request-ID desde property-api.
 *
 * Flujo cross-servicio:
 *   1. property-api asigna X-Request-ID al recibir el request publico.
 *   2. property-api llama a pricing-api via Feign; FeignConfig inyecta
 *      el header X-Request-ID en el request saliente.
 *   3. Este filtro lee ese header y lo pone en el MDC de pricing-api.
 *   4. Todos los logs de pricing-api comparten el mismo request_id
 *      que los de property-api → trazabilidad end-to-end.
 *
 * Si alguien llama a pricing-api directo (sin header), generamos uno
 * nuevo para al menos tener trazabilidad interna.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-ID";
    public static final String MDC_KEY = "request_id";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain chain)
            throws ServletException, IOException {
        String requestId = Optional.ofNullable(request.getHeader(HEADER_NAME))
                .filter(id -> !id.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER_NAME, requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
