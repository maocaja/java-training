package com.mauricio.propertyapi.exception;

import java.util.UUID;

// --- "final" es OBLIGATORIO ---
// Las subclases de una sealed class deben ser: final, sealed, o non-sealed.
// "final" significa que nadie mas puede extender ResourceNotFoundException.
//
// Esta excepcion modela: "el recurso X con ID Y no existe".
// Es una excepcion de DOMINIO — no sabe nada de HTTP ni de status codes.
// El @ControllerAdvice decide que status code devolver (404).
public final class ResourceNotFoundException extends ApiException {

    private final String resourceName;
    private final UUID resourceId;

    public ResourceNotFoundException(String resourceName, UUID resourceId) {
        super("%s not found: %s".formatted(resourceName, resourceId));
        this.resourceName = resourceName;
        this.resourceId = resourceId;
    }

    public String getResourceName() { return resourceName; }
    public UUID getResourceId() { return resourceId; }
}
