package com.mauricio.propertyapi.config;

import com.mauricio.propertyapi.filter.CorrelationIdFilter;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Config de Feign — propaga el correlation ID a los requests salientes.
 *
 * Cuando property-api llama a pricing-api, necesitamos que pricing-api
 * reciba el mismo X-Request-ID para poder correlacionar logs entre
 * servicios. Este interceptor lee el MDC actual (populado por
 * CorrelationIdFilter al entrar el request) y lo inyecta como header
 * en el request saliente.
 *
 * Sin este interceptor, pricing-api generaria un nuevo request_id al
 * recibir el request — perdiendo la trazabilidad cross-servicio.
 */
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor correlationIdPropagator() {
        return template -> {
            String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (requestId != null) {
                template.header(CorrelationIdFilter.HEADER_NAME, requestId);
            }
        };
    }
}
