package com.mauricio.propertyapi.exception;

import java.util.UUID;

// --- SEALED CLASS (Java 17+) ---
// Una clase sellada RESTRINGE quien puede extenderla.
// Solo las clases listadas en "permits" pueden heredar de ella.
//
// Equivalente Kotlin: sealed class ApiException
//
// Por que sealed y no una clase abstracta normal?
// 1. El compilador SABE todas las subclases posibles → te avisa si falta un case en switch
// 2. Nadie mas puede crear nuevos tipos de error sin modificar esta clase
// 3. Modelas un conjunto CERRADO de posibilidades (como un enum, pero con datos)
//
// Analogia: un enum puede ser ROJO, VERDE, AZUL. Un sealed class puede ser
// ResourceNotFound(id), BusinessRuleViolation(message) — con datos diferentes cada uno.
//
// Pregunta de entrevista: "Diferencia entre sealed class y abstract class?"
// → abstract class: cualquiera puede extenderla en cualquier paquete.
//   sealed class: solo las clases declaradas en "permits" pueden extenderla.
//   Esto da exhaustividad en switch/pattern matching — el compilador verifica todos los casos.
// "sealed" + "abstract" → no se puede instanciar directamente, solo via subclases.
// Esto le da al compilador la garantia de que un switch sobre ApiException
// solo necesita cubrir ResourceNotFoundException y BusinessRuleException.
public abstract sealed class ApiException extends RuntimeException
        permits ResourceNotFoundException, BusinessRuleException {

    protected ApiException(String message) {
        super(message);
    }
}
