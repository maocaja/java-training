package com.mauricio.gateway.filter;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Correlation ID filter para el api-gateway (reactivo / Spring WebFlux).
 *
 * Este servicio es REACTIVO (Netty, no Tomcat) → no puede usar
 * OncePerRequestFilter (servlet-based). En su lugar implementamos
 * WebFilter, el equivalente en el stack reactivo.
 *
 * Flujo:
 *   1. Si el request entra sin X-Request-ID, generamos uno.
 *   2. Mutamos el request para propagar el header a los servicios
 *      downstream (property-api, pricing-api) — asi ellos reciben
 *      el mismo ID.
 *   3. Respondemos con el header para que el cliente pueda guardarlo
 *      y correlacionar con soporte.
 *
 * NOTA sobre MDC en codigo reactivo:
 *   MDC usa ThreadLocal, pero en Reactor el codigo salta entre threads.
 *   Para logs del gateway mismo, habria que usar MDC en cada operador
 *   (mas complejo). Para el gateway simple de routing, nos basta con
 *   propagar el header — los servicios downstream (servlet-based) lo
 *   ponen en sus MDC.
 */
@Component
public class CorrelationIdWebFilter implements WebFilter, Ordered {

    public static final String HEADER_NAME = "X-Request-ID";

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange,
                              @NonNull WebFilterChain chain) {
        String existing = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        String requestId = (existing != null && !existing.isBlank())
                ? existing
                : UUID.randomUUID().toString();

        // Mutar el request para propagar el header a downstream
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HEADER_NAME, requestId)
                .build();

        // Setear el header en la response para el cliente
        exchange.getResponse().getHeaders().set(HEADER_NAME, requestId);

        // MDC para logs del propio gateway (limitado en reactivo, pero sirve para routing logs)
        MDC.put("request_id", requestId);
        try {
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } finally {
            MDC.remove("request_id");
        }
    }

    /**
     * HIGHEST_PRECEDENCE para correr antes de cualquier filter del gateway.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
