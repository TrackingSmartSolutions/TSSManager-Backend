# Usa una imagen base más ligera y optimizada
FROM eclipse-temurin:21-jdk-alpine AS build

# Instala Maven
RUN apk add --no-cache maven

# Establece el directorio de trabajo
WORKDIR /app

# Copia solo el pom.xml
COPY pom.xml .

# Descarga las dependencias
RUN mvn dependency:go-offline -B

# Copia el código fuente
COPY src ./src

# Compila la aplicación (sin tests para mayor velocidad)
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine

# Instala herramientas de red útiles para debugging
RUN apk add --no-cache curl

# Crea un usuario no-root para mayor seguridad
RUN addgroup -g 1001 -S spring && \
    adduser -S spring -u 1001 -G spring

# Establece el directorio de trabajo
WORKDIR /app

# Copia el JAR desde la etapa de build
COPY --from=build --chown=spring:spring /app/target/tssmanager-backend-0.0.1-SNAPSHOT.jar app.jar

# Cambia al usuario no-root
USER spring:spring

# Expone el puerto
EXPOSE 8080

# Configuración JVM optimizada para Koyeb Free (512MB RAM total)
ENV JAVA_OPTS="-XX:+UnlockExperimentalVMOptions \
               -XX:+UseG1GC \
               -XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=65.0 \
               -XX:InitialRAMPercentage=40.0 \
               -Xms96m \
               -Xmx320m \
               -XX:MaxMetaspaceSize=96m \
               -XX:MetaspaceSize=48m \
               -XX:CompressedClassSpaceSize=48m \
               -XX:ReservedCodeCacheSize=24m \
               -XX:InitialCodeCacheSize=12m \
               -XX:MaxDirectMemorySize=48m \
               -XX:NewRatio=2 \
               -XX:SurvivorRatio=8 \
               -XX:G1HeapRegionSize=4m \
               -XX:G1NewSizePercent=25 \
               -XX:G1MaxNewSizePercent=35 \
               -XX:G1MixedGCCountTarget=4 \
               -XX:G1HeapWastePercent=5 \
               -XX:+DisableExplicitGC \
               -XX:+UseStringDeduplication \
               -XX:+OptimizeStringConcat \
               -XX:+UseCGroupMemoryLimitForHeap \
               -Djava.awt.headless=true \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.backgroundpreinitializer.ignore=true \
               -Dspring.jpa.hibernate.ddl-auto=validate \
               -Dspring.jpa.show-sql=false \
               -Dlogging.level.root=WARN \
               -Dspring.jpa.properties.hibernate.jdbc.batch_size=10 \
               -Dfile.encoding=UTF-8 \
               -Duser.timezone=America/Mexico_City"

# Health check más liviano
HEALTHCHECK --interval=45s --timeout=5s --start-period=60s --retries=2 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Comando para ejecutar la aplicación
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]