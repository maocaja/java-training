-- ===============================================================
-- V1: Initial schema
-- Creates the 3 base tables: projects, properties, users.
-- SQL kept ANSI-compatible to work with both H2 (tests) and PostgreSQL.
-- ===============================================================

-- ----------------------------------------------------------------
-- projects: real estate projects that group properties
-- ----------------------------------------------------------------
CREATE TABLE projects (
    id          UUID            PRIMARY KEY,
    name        VARCHAR(255)    NOT NULL,
    description VARCHAR(2000)   NOT NULL,
    created_at  TIMESTAMP(6)
);

-- ----------------------------------------------------------------
-- properties: individual real estate units
-- Can optionally belong to a project (project_id nullable).
-- ----------------------------------------------------------------
CREATE TABLE properties (
    id          UUID            PRIMARY KEY,
    name        VARCHAR(255)    NOT NULL,
    city        VARCHAR(255)    NOT NULL,
    price       NUMERIC(12, 2)  NOT NULL,
    bedrooms    INTEGER         NOT NULL,
    project_id  UUID,
    created_at  TIMESTAMP(6),
    CONSTRAINT fk_properties_project
        FOREIGN KEY (project_id)
        REFERENCES projects(id)
        ON DELETE CASCADE
);

-- ----------------------------------------------------------------
-- users: authentication accounts (USER or ADMIN role)
-- Password is BCrypt-hashed, never plain text.
-- ----------------------------------------------------------------
CREATE TABLE users (
    id       UUID         PRIMARY KEY,
    email    VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role     VARCHAR(20)  NOT NULL
);

-- ----------------------------------------------------------------
-- Indexes for frequent query patterns.
-- - properties.city: findByCityIgnoreCase()
-- - properties.project_id: JOIN with projects
-- - users.email: already indexed via UNIQUE constraint
-- ----------------------------------------------------------------
CREATE INDEX idx_properties_city       ON properties(city);
CREATE INDEX idx_properties_project_id ON properties(project_id);
