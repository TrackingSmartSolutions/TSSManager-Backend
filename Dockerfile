FROM openjdk:21-jdk-slim

# Instala Maven
RUN apt-get update && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copia los archivos de dependencias primero para mejor caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Luego copia el código fuente
COPY src ./src

# Construye la aplicación
RUN mvn clean package -DskipTests -B

# Expone el puerto
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Comando optimizado para producción
ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-jar", "target/tssmanager-backend-0.0.1-SNAPSHOT.jar"]