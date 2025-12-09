# --- FASE 1: Construcción (Builder) ---
# Usamos una imagen que INCLUYE Maven y el JDK
FROM maven:3-eclipse-temurin-21 AS builder

# Establece el directorio de trabajo
WORKDIR /app

# Copia el pom y el código fuente
COPY pom.xml .
COPY src ./src

# Compila la aplicación
RUN mvn clean install -DskipTests

# --- FASE 2: Ejecución (Final) ---
# Usamos una imagen JRE (Java Runtime Environment)
FROM eclipse-temurin:21-jre

WORKDIR /app

# Expone el puerto de tu aplicación
EXPOSE 8080

COPY --from=builder /app/target/tssmanager-backend-0.0.1-SNAPSHOT.jar app.jar

# Comando para arrancar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]