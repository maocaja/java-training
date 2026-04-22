package com.mauricio.propertyapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executors;

// --- @EnableAsync ---
// Activa el procesamiento asincrono en Spring. Sin esto, @Async se ignora.
// Es una anotacion de "feature toggle" — le dice a Spring que configure
// la infraestructura para ejecutar metodos en threads separados.
@Configuration
@EnableAsync
public class AsyncConfig {

    // --- TaskExecutor con Virtual Threads ---
    // Por defecto, @Async usa un SimpleAsyncTaskExecutor (crea platform threads).
    // Aqui lo reemplazamos con un executor que usa VIRTUAL THREADS de Java 21.
    //
    // Antes (Java 8-17): configurabas un ThreadPoolTaskExecutor con core/max size.
    //   Si tus tasks superaban el pool, se encolaban o se rechazaban.
    // Ahora (Java 21): virtual threads son tan baratos que no necesitas pool.
    //   Cada task crea su propio virtual thread (~pocos KB vs ~1MB de platform thread).
    //
    // Pregunta de entrevista: "Diferencia entre platform thread y virtual thread?"
    // → Platform thread = thread del OS (costoso, ~1MB stack, limite de ~miles).
    //   Virtual thread = thread de la JVM (barato, ~KB, puedes tener millones).
    //   Virtual threads son ideales para I/O-bound (esperar BD, HTTP, files).
    //   Para CPU-bound (procesamiento pesado), platform threads siguen siendo mejores.
    @Bean
    TaskExecutor taskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
