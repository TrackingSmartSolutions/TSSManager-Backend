# --- FASE 1: Builder ---
FROM maven:3-eclipse-temurin-21-alpine AS builder

WORKDIR /app

COPY pom.xml .
COPY src ./src

# Usamos -B (Batch mode) para que los logs sean más limpios
RUN mvn clean package -DskipTests -B

# --- FASE 2: Runtime  ---
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Creamos usuario sin privilegios
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copiamos el JAR generado
COPY --from=builder /app/target/*.jar app.jar

# Exponemos el puerto
EXPOSE 8080

# Entrypoint con soporte para JAVA_OPTS
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]