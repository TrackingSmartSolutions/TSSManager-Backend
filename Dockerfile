# Usa una imagen base con JDK (OpenJDK 21 slim es una buena opción para un tamaño reducido)
FROM openjdk:21-jdk-slim

# Instala Maven
# openjdk:slim está basado en Debian/Ubuntu, así que usa apt-get
RUN apt-get update && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

# Establece el directorio de trabajo dentro del contenedor
WORKDIR /app

# Copia los archivos de Maven para que Docker pueda cachear las dependencias
# Esto es una optimización para builds más rápidos
COPY pom.xml .
COPY src ./src

# Construye la aplicación
# Usa 'mvn clean install -DskipTests' para saltar los tests durante el build del Dockerfile
RUN mvn clean install -DskipTests

# Expone el puerto en el que tu aplicación Spring Boot escucha (por defecto 8080)
EXPOSE 8080

# Comando para ejecutar la aplicación JAR
# Asume que tu JAR se genera en target/your-app-name.jar
# Ajusta 'target/*.jar' si el nombre es muy específico o usas Gradle (build/libs/*.jar)
ENTRYPOINT ["java", "-jar", "target/tssmanager-backend-0.0.1-SNAPSHOT.jar"]