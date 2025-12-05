# --- FASE 1: Builder (Con Caché de Dependencias) ---
FROM maven:3-eclipse-temurin-21-alpine AS builder

WORKDIR /app

COPY pom.xml .

RUN mvn dependency:go-offline

COPY src ./src

RUN mvn clean package -DskipTests

# Usamos Alpine: Linux recortado que pesa nada
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Creamos un usuario sin privilegios (Seguridad básica)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /app/target/*.jar app.jar

# Exponemos el puerto
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]