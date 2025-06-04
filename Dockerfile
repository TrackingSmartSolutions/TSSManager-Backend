# Usar una imagen base de Maven/Gradle para la compilación
FROM maven:3.9.5-openjdk-17 AS builder
# Si usas Gradle, cambia 'maven' por 'gradle' y el comando de construcción.

# Establecer el directorio de trabajo
WORKDIR /app

# Copiar el pom.xml y el src de tu proyecto
# Esto ayuda a aprovechar el cache de Docker si solo cambian los archivos de código
COPY pom.xml .
# Copia todo el código fuente
COPY src ./src


RUN mvn clean install -DskipTests

# Usar una imagen base ligera de OpenJDK para la ejecución
FROM openjdk:17-jdk-slim

# Establecer el directorio de trabajo
WORKDIR /app

# Copiar el JAR construido desde la etapa 'builder' a la imagen final
# Necesitas reemplazar 'your-app-name.jar' con el nombre exacto de tu JAR.
# Puedes encontrar este nombre ejecutando 'mvn clean install' localmente y mirando en la carpeta target/
COPY --from=builder /app/target/tssmanager-backend-0.0.1-SNAPSHOT.jar app.jar

# Exponer el puerto en el que tu aplicación Spring Boot escuchará
EXPOSE 8080

# Comando para ejecutar la aplicación
CMD ["java", "-jar", "app.jar"]