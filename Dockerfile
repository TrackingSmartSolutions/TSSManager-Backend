# Usa una imagen base con JDK
FROM openjdk:21-jdk-slim

# Instala Maven
RUN apt-get update && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

# Establece el directorio de trabajo dentro del contenedor
WORKDIR /app

COPY pom.xml .
COPY src ./src

# Usa 'mvn clean install -DskipTests' para saltar los tests durante el build del Dockerfile
RUN mvn clean install -DskipTests

# Expone el puerto
EXPOSE 8080

# Comando para ejecutar la aplicación JAR
ENTRYPOINT ["java", "-Xms64m", "-Xmx256m", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=100", "-XX:+UseStringDeduplication", "-jar", "target/tssmanager-backend-0.0.1-SNAPSHOT.jar"]