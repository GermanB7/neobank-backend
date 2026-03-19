# ============================================================================
# Neobank Backend - Dockerfile
# Multi-stage build: optimized for production containerized runtime
# ============================================================================

# Stage 1: Builder
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# Copy Maven wrapper and dependencies first (better caching)
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Download dependencies (this layer can be cached)
RUN ./mvnw -B -ntp -DskipTests dependency:go-offline

# Copy source code and build application
COPY src src
RUN ./mvnw -B -ntp -DskipTests clean package

# ============================================================================
# Stage 2: Runtime
FROM eclipse-temurin:21-jre

LABEL maintainer="Neobank Team"
LABEL description="Neobank Backend - Production Runtime"

WORKDIR /app

# Create non-root user for security
RUN useradd --system --create-home spring

# Copy built JAR from builder stage
COPY --from=build /workspace/target/*.jar /app/app.jar

# Port 8080: Application API
EXPOSE 8080

# Health check: uses Spring Boot actuator endpoint
# Ensure actuator health endpoint is accessible
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health/readiness || exit 1

# Non-root user for security
USER spring

# ============================================================================
# Runtime Configuration
# ============================================================================
#
# Environment Variables (all optional, with sensible defaults):
#
#   Spring Configuration:
#     - SPRING_PROFILES_ACTIVE: Active profile (dev, test, prod)
#
#   Database:
#     - DB_HOST: PostgreSQL host (default: postgres)
#     - DB_PORT: PostgreSQL port (default: 5432)
#     - DB_NAME: Database name (default: neobank)
#     - DB_USER: Database user (default: neobank)
#     - DB_PASSWORD: Database password (required in prod)
#
#   Cache:
#     - REDIS_HOST: Redis host (default: redis)
#     - REDIS_PORT: Redis port (default: 6379)
#
#   Messaging:
#     - KAFKA_BOOTSTRAP_SERVERS: Kafka brokers (default: kafka:9092)
#
#   Security (CRITICAL - must be set):
#     - JWT_SECRET: JWT signing key (min 64 chars, no default)
#     - JWT_REFRESH_TOKEN_PEPPER: Refresh token pepper (min 64 chars, no default)
#
#   See .env.example for complete list of environment variables
#
# ============================================================================

# Default Spring profile (can be overridden at runtime)
ENV SPRING_PROFILES_ACTIVE=prod

# Default server port
ENV SERVER_PORT=8080

# Java options for containerized runtime
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app"

# Application entry point with support for JVM options and Spring profiles
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

