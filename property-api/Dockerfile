# ====================================================================
# property-api Dockerfile - production-grade
#
# Build context: raiz del monorepo (necesario para resolver
# la dependencia al modulo pricing-api-contract).
#
# Build desde la raiz:
#   docker build -f property-api/Dockerfile -t property-api:latest .
# ====================================================================

# ============ Stage 1: build ============
# Imagen oficial de Maven con JDK 21 (Maven ya instalado, sin wrapper).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# --- Layer caching strategy ---
# Copiamos TODOS los pom.xml primero. El parent pom referencia los 4 modulos
# y Maven falla al parsear el reactor si faltan. Solo copiamos los src/ que
# necesitamos compilar.
# Si el codigo cambia pero las dependencias no, Docker reutiliza la capa de
# dependencias descargadas.
COPY pom.xml .
COPY pricing-api-contract/pom.xml pricing-api-contract/
COPY property-api/pom.xml property-api/
COPY pricing-api/pom.xml pricing-api/
COPY api-gateway/pom.xml api-gateway/

# Descargar dependencias al repo local de Maven.
# -B: batch mode (sin prompts interactivos)
# -pl property-api -am: build property-api AND its dependencies in the reactor
RUN mvn -B -pl property-api -am dependency:go-offline

# Copiar solo los fuentes que necesitamos (cache invalida desde aqui)
COPY pricing-api-contract/src pricing-api-contract/src
COPY property-api/src property-api/src

# Compilar property-api y pricing-api-contract (reactor build).
# -DskipTests: los tests se ejecutan en CI, no en cada docker build.
RUN mvn -B -pl property-api -am package -DskipTests

# ============ Stage 2: runtime ============
# JRE-only, alpine-based: imagen mucho mas chica que la de build.
FROM eclipse-temurin:21-jre-alpine

# --- Usuario no-root (security best practice) ---
# Si hay RCE, el atacante no es root. Menor blast radius.
RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app

# Copiar SOLO el jar, con ownership correcto para el usuario no-root.
COPY --from=build --chown=app:app /build/property-api/target/*.jar app.jar

EXPOSE 8081

# --- HEALTHCHECK ---
# ECS y Docker usan esto para saber si el container esta sano.
# Apunta a /liveness (no /readiness) porque si liveness falla =
# necesita restart. Readiness la maneja el ALB externo.
# start-period=40s: property-api tarda ~15s (Flyway + Hibernate + Spring).
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8081/actuator/health/liveness || exit 1

# --- JVM flags ---
# UseZGC: garbage collector de baja latencia (pausas <1ms).
# MaxRAMPercentage=75: la JVM respeta el limite de memoria del container.
#   Sin esto, asume RAM del host → OOM killer cuando el container supera su limite.
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
