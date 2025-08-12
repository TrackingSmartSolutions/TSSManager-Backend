#STAGE 1: BUILD
FROM eclipse-temurin:21-jdk-alpine AS build

# Instalar solo Maven
RUN apk add --no-cache maven

WORKDIR /app

# Copiar solo pom.xml
COPY pom.xml .

# Descargar dependencias
RUN mvn dependency:go-offline -B

# Copiar codigo fuente
COPY src ./src

# Compilar
RUN mvn clean package -DskipTests -B -Dmaven.compiler.debug=false -Dmaven.compiler.debuglevel=none

FROM eclipse-temurin:21-jre-alpine

# Instalar solo lo esencial
RUN apk add --no-cache curl && \
    apk cache clean

# Usuario no-root
RUN addgroup -g 1001 -S spring && \
    adduser -S spring -u 1001 -G spring

WORKDIR /app

# Copiar JAR
COPY --from=build --chown=spring:spring /app/target/tssmanager-backend-0.0.1-SNAPSHOT.jar app.jar

USER spring:spring

EXPOSE 8080

#JVM
ENV JAVA_OPTS="-XX:+UseG1GC \
               -XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=55.0 \
               -XX:InitialRAMPercentage=30.0 \
               -Xms64m \
               -Xmx256m \
               -XX:MaxMetaspaceSize=96m \
               -XX:MetaspaceSize=64m \
               -XX:CompressedClassSpaceSize=24m \
               -XX:ReservedCodeCacheSize=24m \
               -XX:InitialCodeCacheSize=8m \
               -XX:MaxDirectMemorySize=24m \
               -XX:NewRatio=3 \
               -XX:SurvivorRatio=6 \
               -XX:+DisableExplicitGC \
               -XX:+UseStringDeduplication \
               -XX:+OptimizeStringConcat \
               -XX:+UnlockExperimentalVMOptions \
               -XX:+UseJVMCICompiler \
               -XX:+EagerJVMCI \
               -XX:TieredStopAtLevel=1 \
               -XX:+UseCompressedOops \
               -XX:+UseCompressedClassPointers \
               -Djava.awt.headless=true \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.backgroundpreinitializer.ignore=true \
               -Dspring.main.lazy-initialization=true \
               -Dspring.jmx.enabled=false \
               -Dspring.main.banner-mode=off \
               -Dfile.encoding=UTF-8 \
               -Duser.timezone=America/Mexico_City \
               -Djava.net.preferIPv4Stack=true \
               -Dsun.net.useExclusiveBind=false"

# Health check optimizado
HEALTHCHECK --interval=60s --timeout=3s --start-period=90s --retries=2 \
    CMD curl -f -m 3 http://localhost:8080/actuator/health || exit 1

# Comando de ejecucion
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]