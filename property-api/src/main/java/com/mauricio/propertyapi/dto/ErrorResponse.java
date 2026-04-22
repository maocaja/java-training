package com.mauricio.propertyapi.dto;

import java.time.LocalDateTime;
import java.util.List;

// Response estandar para TODOS los errores de la API.
// Tener un formato unico de error es lo que diferencia una API profesional de una amateur.
//
// Ejemplo de response:
// {
//   "status": 404,
//   "error": "Not Found",
//   "message": "Property not found: 3fa85f64-...",
//   "timestamp": "2026-03-30T17:30:00",
//   "fieldErrors": null
// }
public record ErrorResponse(
        int status,
        String error,
        String message,
        LocalDateTime timestamp,
        List<FieldError> fieldErrors
) {
    // Factory methods para crear respuestas de error facilmente
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, LocalDateTime.now(), null);
    }

    public static ErrorResponse withFieldErrors(int status, String error, String message, List<FieldError> fieldErrors) {
        return new ErrorResponse(status, error, message, LocalDateTime.now(), fieldErrors);
    }

    // Sub-record para errores de validacion por campo
    // Ejemplo: { "field": "name", "message": "Name is required" }
    public record FieldError(String field, String message) {}
}
