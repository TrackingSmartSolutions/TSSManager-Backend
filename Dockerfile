# Usa Ubuntu como base e instala Java manualmente
FROM ubuntu:22.04

# Evita interacciones durante la instalación
ENV DEBIAN_FRONTEND=noninteractive

# Instala Java 21 y Maven
RUN apt-get update && \
    apt-get install -y openjdk-21-jdk maven && \
    rm -rf /var/lib/apt/lists/*

# Establece JAVA_HOME
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# Establece el directorio de trabajo
WORKDIR /app

# Copia los archivos del proyecto
COPY pom.xml .
COPY src ./src

# Construye la aplicación
RUN mvn clean install -DskipTests

# Expone el puerto
EXPOSE 8080

# Comando para ejecutar la aplicación
ENTRYPOINT ["java", "-jar", "target/tssmanager-backend-0.0.1-SNAPSHOT.jar"]