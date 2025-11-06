# Usamos una imagen con JDK y Maven para COMPILAR
FROM eclipse-temurin:21-jdk AS builder

# Establece el directorio de trabajo
WORKDIR /app

# Copia el pom y el código fuente
COPY pom.xml .
COPY src ./src

# (Usamos mvnw si está disponible, si no, mvn)
RUN mvn clean install -DskipTests

# Usamos una imagen JRE (Java Runtime Environment)
FROM eclipse-temurin:21-jre

WORKDIR /app

# Expone el puerto de tu aplicación
EXPOSE 8080

# Copia SÓLO el .jar compilado desde la fase 'builder'
COPY --from=builder /app/target/tssmanager-backend-0.0.1-SNAPSHOT.jar app.jar

# Comando para arrancar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]