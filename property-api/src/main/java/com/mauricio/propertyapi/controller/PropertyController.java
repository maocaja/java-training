package com.mauricio.propertyapi.controller;

import com.mauricio.propertyapi.dto.CreatePropertyRequest;
import com.mauricio.propertyapi.dto.PropertyResponse;
import com.mauricio.propertyapi.dto.ValuationResponse;
import com.mauricio.propertyapi.service.PropertyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService service;

    public PropertyController(PropertyService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyResponse create(@Valid @RequestBody CreatePropertyRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public PropertyResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @GetMapping
    public List<PropertyResponse> findAll(@RequestParam(required = false) String city) {
        if (city != null) {
            return service.findByCity(city);
        }
        return service.findAll();
    }

    @PutMapping("/{id}")
    public PropertyResponse update(@PathVariable UUID id, @Valid @RequestBody CreatePropertyRequest request) {
        return service.update(id, request);
    }

    // PUT /api/properties/{id}/project/{projectId} — asigna la property a un project
    @PutMapping("/{id}/project/{projectId}")
    public PropertyResponse assignToProject(@PathVariable UUID id, @PathVariable UUID projectId) {
        return service.assignToProject(id, projectId);
    }

    // DELETE /api/properties/{id}/project — desasigna la property de su project
    @DeleteMapping("/{id}/project")
    public PropertyResponse removeFromProject(@PathVariable UUID id) {
        return service.removeFromProject(id);
    }

    // GET /api/properties/{id}/valuation — llama al pricing-api via Feign (JSON-RPC)
    @GetMapping("/{id}/valuation")
    public ValuationResponse getValuation(@PathVariable UUID id) {
        return service.getValuation(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
