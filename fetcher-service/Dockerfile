# ─── fetcher-service/Dockerfile ───────────────────────────────────────────────
# Build context: project root (docker-compose: context: .)
# Strategy: copy all pom.xml files first → layer cache → copy src → build only this module
# ──────────────────────────────────────────────────────────────────────────────

# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# ── Step 1: Copy all pom.xml files (layer cache) ──────────────────────────────
# Maven reactor needs all pom.xml to resolve inter-module dependencies
# If only pom.xml changes → re-download deps; if src changes → skip this layer
COPY pom.xml .
COPY common/pom.xml             common/pom.xml
COPY fetcher-service/pom.xml    fetcher-service/pom.xml
COPY summarizer-service/pom.xml summarizer-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY gateway-service/pom.xml    gateway-service/pom.xml

# ── Step 2: Pre-download dependencies ─────────────────────────────────────────
# -pl fetcher-service  → only this module
# -am                  → Also Make: build common (dependency) first
# -B                   → batch mode (no interactive prompts)
RUN mvn dependency:go-offline -pl fetcher-service -am -B -q

# ── Step 3: Copy source & build ───────────────────────────────────────────────
COPY common/src          common/src
COPY fetcher-service/src fetcher-service/src

RUN mvn package -pl fetcher-service -am -DskipTests -B -q

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Non-root user for security (least privilege)
RUN addgroup -S botgroup && adduser -S botuser -G botgroup
USER botuser

# Copy the built JAR from build stage
COPY --from=builder /app/fetcher-service/target/*.jar app.jar

# Actuator health endpoint check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:${SERVER_PORT:-8081}/actuator/health || exit 1

EXPOSE 8081

ENTRYPOINT ["java", \
  "-XX:+UseVirtualThreads", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
