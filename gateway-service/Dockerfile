# ─── gateway-service/Dockerfile ──────────────────────────────────────────────
# Build context: project root (docker-compose: context: .)
# This service needs: Spring Cloud Gateway (WebFlux-based, reactive) only
# No DB, no Kafka consumer, no bot libraries — purely routing + filter
# ──────────────────────────────────────────────────────────────────────────────

# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# ── Step 1: Copy all pom.xml files (layer cache) ──────────────────────────────
COPY pom.xml .
COPY common/pom.xml               common/pom.xml
COPY fetcher-service/pom.xml      fetcher-service/pom.xml
COPY summarizer-service/pom.xml   summarizer-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY gateway-service/pom.xml      gateway-service/pom.xml

# ── Step 2: Pre-download dependencies ─────────────────────────────────────────
# gateway-service does NOT depend on common at compile time (no shared events needed)
# -am still required so Maven reactor can resolve the full dependency tree
RUN mvn dependency:go-offline -pl gateway-service -am -B -q

# ── Step 3: Copy source & build ───────────────────────────────────────────────
COPY common/src          common/src
COPY gateway-service/src gateway-service/src

RUN mvn package -pl gateway-service -am -DskipTests -B -q

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S botgroup && adduser -S botuser -G botgroup
USER botuser

COPY --from=builder /app/gateway-service/target/*.jar app.jar

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseVirtualThreads", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
