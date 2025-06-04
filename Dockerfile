# Usar una imagen base de OpenJDK (Java 17, slim para ser ligera)
FROM openjdk:17-jdk-slim

# Establecer el directorio de trabajo dentro del contenedor
WORKDIR /app

# Copiar el archivo JAR construido por Maven/Gradle al contenedor
# Necesitas reemplazar 'your-app-name.jar' con el nombre exacto de tu JAR.
# Por ejemplo, si se llama 'tssmanager-backend-0.0.1-SNAPSHOT.jar', sería:
# COPY target/tssmanager-backend-0.0.1-SNAPSHOT.jar app.jar
COPY target/tssmanager-backend-0.0.1-SNAPSHOT.jar app.jar

# Exponer el puerto en el que tu aplicación Spring Boot escuchará
# Por defecto, Spring Boot usa 8080. Render mapeará este puerto internamente.
EXPOSE 8080

# Comando para ejecutar la aplicación cuando el contenedor se inicie
# Spring Boot detecta automáticamente la variable de entorno PORT que Render proporciona
CMD ["java", "-jar", "app.jar"]