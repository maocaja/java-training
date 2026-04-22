package com.mauricio.propertyapi.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// --- LISTENER DE EVENTOS ---
// Esta clase REACCIONA cuando alguien publica un PropertyCreatedEvent.
// El service que publica el evento NO sabe que este listener existe.
// Eso es DESACOPLAMIENTO — puedes agregar 10 listeners mas sin tocar el service.
//
// Analogia con lo que conoces:
//   - Kotlin: Channel/Flow collectors
//   - Python/FastAPI: BackgroundTasks
//   - Go: goroutines con channels
//   - Kafka: consumer groups (pero aca es in-process, no entre servicios)
@Component
public class PropertyEventListener {

    // --- SLF4J Logger ---
    // El estandar de logging en Java/Spring. No uses System.out.println en produccion.
    // SLF4J es la fachada, Logback es la implementacion (viene con Spring Boot).
    private static final Logger log = LoggerFactory.getLogger(PropertyEventListener.class);

    // --- @Async + @EventListener ---
    // @EventListener: Spring llama este metodo automaticamente cuando alguien publica
    //   un PropertyCreatedEvent via ApplicationEventPublisher.
    // @Async: lo ejecuta en OTRO thread (no bloquea el thread del request HTTP).
    //   Sin @Async, el listener se ejecuta en el mismo thread que publico el evento,
    //   y el usuario espera a que termine.
    //
    // Con Virtual Threads activados, el @Async usa virtual threads automaticamente.
    // Antes necesitabas configurar un thread pool. Ahora Java 21 lo maneja por ti.
    @Async
    @EventListener
    public void handlePropertyCreated(PropertyCreatedEvent event) {
        log.info("[SEARCH INDEX] Indexando property '{}' en ciudad '{}' — thread: {}",
                event.name(), event.city(), Thread.currentThread());

        // Simulamos una operacion lenta (ej: llamar a un servicio de busqueda externo)
        simulateSlowOperation(500);

        log.info("[SEARCH INDEX] Property '{}' indexada exitosamente", event.name());
    }

    // Segundo listener para el MISMO evento — demuestra que multiples listeners
    // pueden reaccionar al mismo evento de forma independiente.
    // En MeLi: crear publicacion → indexar en busqueda + notificar vendedor + actualizar metricas
    @Async
    @EventListener
    public void handlePropertyNotification(PropertyCreatedEvent event) {
        log.info("[NOTIFICATION] Enviando notificacion para property '{}' — thread: {}",
                event.name(), Thread.currentThread());

        simulateSlowOperation(300);

        log.info("[NOTIFICATION] Notificacion enviada para property '{}'", event.name());
    }

    private void simulateSlowOperation(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
