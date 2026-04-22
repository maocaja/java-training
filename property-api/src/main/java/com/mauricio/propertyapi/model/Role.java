package com.mauricio.propertyapi.model;

// Enum simple para roles. En produccion podria ser una tabla aparte.
// USER: solo puede leer (GET).
// ADMIN: puede leer y escribir (POST, PUT, DELETE).
public enum Role {
    USER,
    ADMIN
}
