# ====================================================================
# pricing-api Dockerfile - production-grade
#
# Build context: raiz del monorepo (para resolver pricing-api-contract).
#
# Build desde la raiz:
#   docker build -f pricing-api/Dockerfile -t pricing-api:latest .
# ====================================================================

# ============ Stage 1: build ============
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Layer caching: copiamos TODOS los pom.xml (el parent referencia los 4 modulos).
# Solo copiamos src/ de los que necesitamos compilar.
COPY pom.xml .
COPY pricing-api-contract/pom.xml pricing-api-contract/
COPY pricing-api/pom.xml pricing-api/
COPY property-api/pom.xml property-api/
COPY api-gateway/pom.xml api-gateway/

RUN mvn -B -pl pricing-api -am dependency:go-offline

COPY pricing-api-contract/src pricing-api-contract/src
COPY pricing-api/src pricing-api/src

RUN mvn -B -pl pricing-api -am package -DskipTests

# ============ Stage 2: runtime ============
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app
COPY --from=build --chown=app:app /build/pricing-api/target/*.jar app.jar

EXPOSE 8082

# pricing-api arranca mas rapido (sin BD, sin security, sin Flyway): 20s.
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8082/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
