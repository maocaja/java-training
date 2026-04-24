package com.mauricio.propertyapi.filter;

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
 * Filtro que asigna un correlation ID a cada request para poder rastrear
 * un mismo request a traves de logs y microservicios.
 *
 * Flujo:
 *   1. Lee el header X-Request-ID. Si no viene, genera uno (UUID).
 *   2. Lo guarda en MDC (Mapped Diagnostic Context) de SLF4J.
 *   3. El logback-spring.xml (B8) incluye request_id en cada log JSON.
 *   4. Lo devuelve en el response para que el cliente pueda correlacionar.
 *   5. Al terminar, limpia el MDC (critico: sin esto, el proximo request
 *      en el mismo thread reusaria el ID viejo).
 *
 * Propagacion a downstream:
 *   - property-api llama a pricing-api via Feign.
 *   - Un RequestInterceptor de Feign (en SecurityConfig u otro bean)
 *     lee MDC.get("request_id") y lo pone en el header X-Request-ID
 *     del request saliente.
 *   - pricing-api tiene su propio filtro equivalente que lee el header.
 *
 * @Order con HIGHEST_PRECEDENCE para que corra PRIMERO — asi todos los
 * logs de los filtros posteriores ya tienen el request_id en MDC.
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
            // CRITICO: limpiar MDC al terminar. Sin esto, el proximo request
            // en el mismo thread (en servidores con thread pools) veria el
            // request_id viejo. En virtual threads el riesgo es menor pero
            // la buena practica es siempre limpiar.
            MDC.remove(MDC_KEY);
        }
    }
}
