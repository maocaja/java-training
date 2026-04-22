package com.mauricio.propertyapi.exception;

// Modela violaciones de reglas de negocio.
// Ejemplo: "no puedes asignar una property a un project que esta cerrado"
// El @ControllerAdvice decide que status code devolver (422 Unprocessable Entity).
public final class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
