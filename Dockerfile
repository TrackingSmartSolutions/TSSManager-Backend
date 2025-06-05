# Etapa 1: Construir la aplicación
FROM openjdk:21-jdk-slim AS builder
WORKDIR /app
COPY . .
RUN chmod +x ./mvnw  
RUN ./mvnw clean package -DskipTests

# Etapa 2: Crear la imagen final
FROM openjdk:21-jdk-slim
WORKDIR /app
COPY --from=builder /app/target/tssmanager-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
CMD ["java", "-Xms512m", "-Xmx1024m", "-jar", "app.jar"]