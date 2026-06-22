# ── Stage 1: Build ──────────────────────────────────────────────
# Use Maven + Java 21 to compile the project and create the jar
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Set working directory inside the build container
WORKDIR /app

# Copy pom.xml first — Docker caches this layer
# If pom.xml hasn't changed, Maven dependencies won't re-download
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build the jar
# -DskipTests because tests need DB/Redis which aren't available at build time
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Run ─────────────────────────────────────────────────
# Use a lightweight Java 21 runtime — NOT the full Maven image
# This keeps the final image small (~200MB vs ~600MB)
FROM eclipse-temurin:21-jre-alpine

# Create a non-root user for security
# Never run production containers as root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy only the compiled jar from Stage 1
# The Maven build artifacts and source code are left behind
COPY --from=builder /app/target/*.jar app.jar

# Change ownership to the non-root user
RUN chown appuser:appgroup app.jar

# Switch to non-root user
USER appuser

# Expose the port Spring Boot runs on
EXPOSE 8080

# Start the application
# JAVA_OPTS allows Railway to inject JVM flags via environment variable
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]