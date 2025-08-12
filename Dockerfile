# === STAGE 1: BUILD ===
FROM eclipse-temurin:21-jdk-alpine AS build

# Instalar solo Maven
RUN apk add --no-cache maven

WORKDIR /app

# Copiar solo pom.xml primero
COPY pom.xml .

# Descargar dependencias
RUN mvn dependency:go-offline -B

# Copiar código fuente
COPY src ./src

# Compilar
RUN mvn clean package -DskipTests -B -Dmaven.compiler.debug=false -Dmaven.compiler.debuglevel=none

# === STAGE 2: RUNTIME ===
FROM eclipse-temurin:21-jre-alpine

# Instalar solo lo esencial
RUN apk add --no-cache curl && \
    apk cache clean

# Usuario no-root
RUN addgroup -g 1001 -S spring && \
    adduser -S spring -u 1001 -G spring

WORKDIR /app

# Copiar JAR
COPY --from=build --chown=spring:spring /app/target/tssmanager-backend-0.0.1-SNAPSHOT.jar app.jar

USER spring:spring

EXPOSE 8080

#JVM OPTIMIZADA
ENV JAVA_OPTS="-XX:+UseG1GC \
               -XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=60.0 \
               -XX:InitialRAMPercentage=35.0 \
               -Xms80m \
               -Xmx270m \
               -XX:MaxMetaspaceSize=140m \
               -XX:MetaspaceSize=120m \
               -XX:CompressedClassSpaceSize=30m \
               -XX:ReservedCodeCacheSize=30m \
               -XX:InitialCodeCacheSize=12m \
               -XX:MaxDirectMemorySize=28m \
               -XX:NewRatio=2 \
               -XX:SurvivorRatio=8 \
               -XX:+DisableExplicitGC \
               -XX:+UseStringDeduplication \
               -XX:+OptimizeStringConcat \
               -Djava.awt.headless=true \
               -Djava.security.egd=file:/dev/./urandom \
               -Dfile.encoding=UTF-8 \
               -Duser.timezone=America/Mexico_City"

# Health check conservador
HEALTHCHECK --interval=45s --timeout=5s --start-period=60s --retries=2 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Comando de ejecución
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]