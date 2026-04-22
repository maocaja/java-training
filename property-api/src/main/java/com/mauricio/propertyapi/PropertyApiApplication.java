package com.mauricio.propertyapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

// --- @EnableFeignClients ---
// Activa OpenFeign. Escanea interfaces con @FeignClient y crea implementaciones automaticas.
// Sin esto, las interfaces @FeignClient se ignoran.
@SpringBootApplication
@EnableFeignClients
public class PropertyApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PropertyApiApplication.class, args);
    }
}
