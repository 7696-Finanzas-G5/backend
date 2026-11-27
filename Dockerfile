# ---- Compilación ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Dependencias primero (se cachean mientras pom.xml no cambie)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q package -DskipTests && cp target/*.jar app.jar

# ---- Ejecución ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 spring
USER spring

COPY --from=build /app/app.jar app.jar

# Render asigna el puerto en la variable PORT (la app la lee en application.yml)
ENV PORT=8080
EXPOSE 8080
# Ajusta la memoria de la JVM al límite del contenedor (512 MB en el plan gratuito de Render)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
