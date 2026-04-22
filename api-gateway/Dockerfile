# ====================================================================
# api-gateway Dockerfile - production-grade
#
# api-gateway NO depende de pricing-api-contract (solo rutea).
#
# Build desde la raiz:
#   docker build -f api-gateway/Dockerfile -t api-gateway:latest .
# ====================================================================

# ============ Stage 1: build ============
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Layer caching: copiamos TODOS los pom.xml (el parent referencia los 4 modulos).
COPY pom.xml .
COPY pricing-api-contract/pom.xml pricing-api-contract/
COPY property-api/pom.xml property-api/
COPY pricing-api/pom.xml pricing-api/
COPY api-gateway/pom.xml api-gateway/

RUN mvn -B -pl api-gateway -am dependency:go-offline

COPY api-gateway/src api-gateway/src

RUN mvn -B -pl api-gateway -am package -DskipTests

# ============ Stage 2: runtime ============
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app
COPY --from=build --chown=app:app /build/api-gateway/target/*.jar app.jar

EXPOSE 8080

# api-gateway es reactivo (Netty), arranque rapido: 20s.
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
