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

# Configuración JVM para contenedores
ENV JAVA_OPTS="-XX:+UseG1GC \
               -XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+OptimizeStringConcat \
               -XX:+UseStringDeduplication \
               -XX:+UnlockExperimentalVMOptions \
               -XX:+EnableJVMCI \
               -XX:+UseJVMCICompiler \
               -Xms512m \
               -Xmx2g \
               -XX:NewRatio=3 \
               -XX:SurvivorRatio=8 \
               -XX:MaxMetaspaceSize=256m \
               -XX:CompressedClassSpaceSize=128m \
               -XX:+DisableExplicitGC \
               -XX:+AlwaysPreTouch \
               -XX:+UseNUMA \
               -XX:+ParallelRefProcEnabled \
               -XX:+UnlockDiagnosticVMOptions \
               -XX:+LogVMOutput \
               -XX:+UseAES \
               -XX:+UseAESIntrinsics \
               -XX:+UseSHA \
               -XX:+UseSHA1Intrinsics \
               -XX:+UseSHA256Intrinsics \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.backgroundpreinitializer.ignore=true \
               -Dfile.encoding=UTF-8 \
               -Duser.timezone=America/Mexico_City"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Comando para ejecutar la aplicación
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]