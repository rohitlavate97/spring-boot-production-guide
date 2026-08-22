# ==============================================================================
# Production-Hardened Multi-Stage Dockerfile for Spring Boot Microservices
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build Stage (Dependency Caching & Compilation)
# ------------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build executable JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# Extract layered Spring Boot JAR for optimized container layering
WORKDIR /build/target
RUN java -Djarmode=tools -jar module-16-docker-containerization-cgroup-traps-1.0.0-SNAPSHOT.jar extract --layers --launcher

# ------------------------------------------------------------------------------
# Stage 2: Hardened, Minimal, Non-Root Runtime Stage
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# Install curl for container health checks
RUN apk add --no-cache curl tzdata && \
    addgroup -S appgroup && \
    adduser -S appuser -G appgroup -u 10001

# Create application & heap dump directories with unprivileged permissions
WORKDIR /app
RUN mkdir -p /tmp/dumps && chown -R appuser:appgroup /app /tmp/dumps

# Copy layered Spring Boot artifacts from builder
COPY --from=builder --chown=appuser:appgroup /build/target/module-16-docker-containerization-cgroup-traps-1.0.0-SNAPSHOT/dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/target/module-16-docker-containerization-cgroup-traps-1.0.0-SNAPSHOT/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appgroup /build/target/module-16-docker-containerization-cgroup-traps-1.0.0-SNAPSHOT/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /build/target/module-16-docker-containerization-cgroup-traps-1.0.0-SNAPSHOT/application/ ./

# Switch to non-root user (security hardening & compliance)
USER appuser:appgroup

# Expose standard HTTP port
EXPOSE 8080

# Production JVM Container Options:
# 1. UseContainerSupport: Enables cgroup v1/v2 CPU & Memory limit detection.
# 2. MaxRAMPercentage=75.0: Reserves 25% container RAM for Metaspace, CodeCache, thread stacks (-Xss), and native off-heap memory.
# 3. ExitOnOutOfMemoryError: Immediately terminates JVM upon OOM to trigger fast container restart/failover.
# 4. HeapDumpOnOutOfMemoryError: Captures hprof diagnostic dump before exiting.
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport \
                       -XX:MaxRAMPercentage=75.0 \
                       -XX:InitialRAMPercentage=75.0 \
                       -XX:+ExitOnOutOfMemoryError \
                       -XX:+HeapDumpOnOutOfMemoryError \
                       -XX:HeapDumpPath=/tmp/dumps/oom.hprof"

# Health check probing Actuator
HEALTHCHECK --interval=15s --timeout=3s --retries=3 --start-period=20s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Exec form ENTRYPOINT ensures Java runs as PID 1 directly (handling SIGTERM/SIGINT for Spring Boot graceful shutdown)
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
