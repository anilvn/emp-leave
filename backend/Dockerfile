# Builder: compiles the jar with the project's own Maven wrapper.
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY . .
RUN chmod +x mvnw && ./mvnw -DskipTests package

# Runtime: just a JRE and the built jar.
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
