# ── Stage 1: Build ──────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependencies trước
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build
COPY src ./src
COPY movies_metadata_encoded.csv ./movies_metadata_encoded.csv
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Non-root user
RUN addgroup -S cinema && adduser -S cinema -G cinema
USER cinema

COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/movies_metadata_encoded.csv movies_metadata_encoded.csv

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
