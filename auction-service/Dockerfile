# --- Build stage ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# --- Runtime stage ---
# JRE-only image (multi-arch: the alpine variant has no ARM build, so it could not build on Apple Silicon) - the build stage's Maven + full JDK never ship, keeping
# the final image small (typically ~180MB vs 600MB+ for a JDK-based image).
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
