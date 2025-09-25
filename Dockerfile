# Usar imagen de Ubuntu (disponible en todos los registries)
FROM ubuntu:24.04

# Evitar interacciones durante instalación
ENV DEBIAN_FRONTEND=noninteractive

# Instalar Java 21 y herramientas necesarias
RUN apt-get update && \
    apt-get install -y \
        openjdk-21-jdk \
        maven \
        curl \
        wget && \
    rm -rf /var/lib/apt/lists/*

# Configurar JAVA_HOME
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH="$JAVA_HOME/bin:$PATH"

# Verificar instalación de Java
RUN java -version && mvn -version

# Establecer directorio de trabajo
WORKDIR /app

# Copiar archivos del proyecto
COPY pom.xml .
COPY src ./src

# Compilar aplicación
RUN mvn clean package -DskipTests -q

# Verificar que el JAR se creó
RUN ls -la target/

# Exponer puerto
EXPOSE 8080

# Usar variables de entorno para el puerto
ENV SERVER_PORT=8080

# Ejecutar aplicación
CMD ["java", "-Dserver.port=${PORT:-8080}", "-jar", "target/tssmanager-backend-0.0.1-SNAPSHOT.jar"]