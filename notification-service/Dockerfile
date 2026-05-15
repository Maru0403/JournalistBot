# ─── notification-service/Dockerfile ─────────────────────────────────────────
# Build context: project root (docker-compose: context: .)
# This service needs: common + spring-kafka + MongoDB + PostgreSQL + Redis + JDA + TelegramBots
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
RUN mvn dependency:go-offline -pl notification-service -am -B -q

# ── Step 3: Copy source & build ───────────────────────────────────────────────
COPY common/src               common/src
COPY notification-service/src notification-service/src

RUN mvn package -pl notification-service -am -DskipTests -B -q

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S botgroup && adduser -S botuser -G botgroup
USER botuser

COPY --from=builder /app/notification-service/target/*.jar app.jar

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:${SERVER_PORT:-8083}/actuator/health || exit 1

EXPOSE 8083

ENTRYPOINT ["java", \
  "-XX:+UseVirtualThreads", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
