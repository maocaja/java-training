package com.mauricio.propertyapi.exception;

import com.mauricio.propertyapi.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// --- @RestControllerAdvice ---
// Combina @ControllerAdvice + @ResponseBody.
// Intercepta TODAS las excepciones de TODOS los controllers.
// Centraliza el manejo de errores en UN solo lugar.
//
// Sin esto: cada controller maneja sus propios errores (duplicacion).
// Con esto: lanzas una excepcion en el service y el handler la convierte
//   en una response HTTP estructurada automaticamente.
//
// Equivalente FastAPI: los exception handlers que registrabas con @app.exception_handler()
// Equivalente Go: middleware de error handling
//
// Pregunta de entrevista: "Como manejas errores en Spring Boot?"
// → @RestControllerAdvice con @ExceptionHandler. Defino excepciones de dominio
//   (ResourceNotFound, BusinessRule) y el handler las mapea a responses HTTP.
//   Centralizado, no hay try-catch en los controllers.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // --- PATTERN MATCHING CON SEALED CLASSES (Java 21) ---
    // Este handler captura CUALQUIER ApiException (la sealed class padre).
    // Usamos switch con pattern matching para manejar cada subtipo.
    //
    // El compilador SABE que ApiException solo puede ser:
    //   - ResourceNotFoundException
    //   - BusinessRuleException
    // Si agregas un nuevo subtipo a "permits" y no agregas un case aqui,
    // el compilador te avisa (exhaustiveness check).
    //
    // Equivalente Kotlin: when (ex) { is ResourceNotFound -> ... }
    //
    // Pregunta de entrevista: "Que es pattern matching en Java 21?"
    // → Puedes hacer switch sobre el tipo de un objeto y extraer sus campos
    //   en una sola linea. Combinado con sealed classes, el compilador
    //   verifica que cubras todos los casos posibles.
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        return switch (ex) {
            // --- Pattern matching con variable binding ---
            // "case ResourceNotFoundException e" hace dos cosas:
            // 1. Verifica que ex es de tipo ResourceNotFoundException
            // 2. Lo castea a la variable "e" (ya puedes usar e.getResourceName())
            // En Java 8 necesitabas: if (ex instanceof ResourceNotFoundException) { var e = (ResourceNotFoundException) ex; }
            case ResourceNotFoundException e -> {
                log.warn("Resource not found: {}", e.getMessage());
                yield ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(ErrorResponse.of(404, "Not Found", e.getMessage()));
            }
            case BusinessRuleException e -> {
                log.warn("Business rule violation: {}", e.getMessage());
                yield ResponseEntity
                        .status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(ErrorResponse.of(422, "Unprocessable Entity", e.getMessage()));
            }
            // No necesitamos "default" porque sealed class cubre todos los casos.
            // Si agregas un nuevo tipo a "permits", el compilador te obliga a agregarlo aqui.
        };
    }

    // --- Handler para errores de validacion (@Valid) ---
    // Cuando @Valid falla (ej: name en blanco), Spring lanza MethodArgumentNotValidException.
    // Sin este handler, Spring devuelve un error generico feo.
    // Con este handler, devolvemos los errores campo por campo.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        var fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        log.warn("Validation failed: {} error(s)", fieldErrors.size());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.withFieldErrors(
                        400, "Bad Request", "Validation failed", fieldErrors));
    }

    // --- Catch-all para excepciones inesperadas ---
    // Si algo explota que no anticipamos, devolvemos 500 con un mensaje generico.
    // NUNCA exponemos el stack trace al cliente (seguridad).
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "Internal Server Error",
                        "An unexpected error occurred"));
    }
}
