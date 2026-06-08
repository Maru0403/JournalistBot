# ── Stage 1: Build ────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy parent pom + common module (dependency của scheduler-service)
COPY pom.xml .
COPY common/pom.xml common/
COPY common/src common/src
COPY scheduler-service/pom.xml scheduler-service/

# Download dependencies trước (cache layer)
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw dependency:go-offline -pl common,scheduler-service -am -q 2>/dev/null || true

COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw

# Build common + scheduler-service
COPY scheduler-service/src scheduler-service/src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw clean package -pl common,scheduler-service -am -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Security: non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/scheduler-service/target/scheduler-service-*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", \
    "-XX:+UseVirtualThreads", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
