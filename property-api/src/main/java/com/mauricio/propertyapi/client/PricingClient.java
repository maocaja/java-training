package com.mauricio.propertyapi.client;

import com.mauricio.pricing.contract.JsonRpcRequest;
import com.mauricio.pricing.contract.JsonRpcResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// --- OPENFEIGN CLIENT ---
// Define una INTERFACE — Spring genera la implementacion HTTP automaticamente.
// No escribes RestTemplate, WebClient, HttpURLConnection, ni nada.
// Solo declaras el contrato y Feign hace el request por ti.
//
// Equivalente en lo que conoces:
//   - Python: httpx.post() pero declarativo
//   - Go: http.NewRequest() + http.Client.Do() pero automatico
//   - Kotlin: Retrofit (misma idea, interfaces con anotaciones)
//
// name: nombre logico del servicio (para logs y metricas)
// url: URL del servicio. ${...} se lee de application.yml/env vars.
//      En Docker seria http://pricing-api:8082 (nombre del container)
//
// Pregunta de entrevista: "Como se comunican tus microservicios?"
// → Con OpenFeign. Defino una interface con el contrato del servicio.
//   Spring genera el client HTTP automaticamente. Si el servicio esta caido,
//   puedo agregar un @CircuitBreaker (Resilience4j) para fallback.
@FeignClient(name = "pricing-service", url = "${services.pricing.url}")
public interface PricingClient {

    @PostMapping("/rpc")
    JsonRpcResponse call(@RequestBody JsonRpcRequest request);
}
