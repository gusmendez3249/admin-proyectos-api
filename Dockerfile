# Imagen de la API REST (Ejercicio5MyAPIGestor)
# Etapa 1: compilacion con Maven. Las pruebas ya se ejecutan en el pipeline de Jenkins,
# por eso aqui se omiten para que la imagen se construya rapido.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests -Djacoco.skip=true

# Etapa 2: solo el JRE y el jar, con usuario sin privilegios
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8081
# La conexion a la BD llega por variables de entorno: DB_URL, DB_USERNAME, DB_PASSWORD
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
