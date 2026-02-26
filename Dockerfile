FROM eclipse-temurin:21-jdk

# Create a non-root user and group
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

WORKDIR /app

ARG JAR_FILE=build/libs/*.jar
COPY --chown=appuser:appgroup ${JAR_FILE} app.jar

USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]