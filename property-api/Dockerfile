# --- MULTI-STAGE BUILD ---
# Usa dos etapas: una para compilar y otra para ejecutar.
# La imagen final solo contiene el JAR, no el codigo fuente ni Maven.
# Resultado: imagen ~300MB en vez de ~800MB.
#
# Equivalente a lo que hacias con Go multi-stage builds en Rockwell.

# === ETAPA 1: BUILD ===
# Usa JDK completo (necesario para compilar)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copiar Maven wrapper y pom.xml PRIMERO
# Docker cachea cada capa. Si solo cambias codigo (no dependencias),
# esta capa se reutiliza y no descarga dependencias de nuevo.
COPY .mvn .mvn
COPY mvnw pom.xml ./

# Descargar dependencias (cacheado si pom.xml no cambio)
RUN ./mvnw dependency:go-offline -q

# Copiar codigo fuente
COPY src ./src

# Compilar y empaquetar (sin ejecutar tests — ya se corrieron en CI)
RUN ./mvnw package -DskipTests -q

# === ETAPA 2: RUNTIME ===
# Usa JRE (solo runtime, no compilador). Imagen mas liviana.
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copiar SOLO el JAR de la etapa de build
COPY --from=build /app/target/*.jar app.jar

# Puerto que expone la app (documentacion, no abre el puerto)
EXPOSE 8080

# Comando para ejecutar la app
# -XX:+UseZGC: garbage collector moderno, bajo latency (ideal para APIs)
ENTRYPOINT ["java", "-XX:+UseZGC", "-jar", "app.jar"]
