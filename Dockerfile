# ─── Stage 1: Build ───────────────────────────────────────────
# Use official Maven image — no need for maven-wrapper.jar, works on all OS
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy pom.xml first — layer cache: only re-download deps when pom changes
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# Copy source and build
COPY src src
RUN mvn package -DskipTests -B -q

# ─── Stage 2: Runtime ─────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Non-root user for security
RUN addgroup -S botgroup && adduser -S botuser -G botgroup
USER botuser

# Copy JAR from build stage
COPY --from=builder /app/target/*.jar app.jar

# Actuator health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseVirtualThreads", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
