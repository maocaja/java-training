package com.mauricio.propertyapi.event;

import java.math.BigDecimal;
import java.util.UUID;

// --- EVENTO DE DOMINIO ---
// Un record inmutable que representa "algo que paso" en el sistema.
// No contiene logica — solo datos. Quien lo publica no sabe quien lo escucha.
//
// Este patron se llama "Event-Driven Architecture" y es la base de
// como funcionan sistemas como MeLi internamente:
//   - Se crea una publicacion → evento → indexar en busqueda, notificar vendedor, actualizar metricas
//   - Se vende un producto → evento → actualizar stock, enviar email, generar factura
//
// Dentro de una app Spring usamos ApplicationEvent.
// Entre microservicios se usa Kafka/RabbitMQ (misma idea, diferente transporte).
public record PropertyCreatedEvent(
        UUID propertyId,
        String name,
        String city,
        BigDecimal price,
        Integer bedrooms
) {}
