# Phase 6 — Kiến Trúc Microservice & Use Case Chi Tiết

---

## 1. Tổng Quan Kiến Trúc Phase 6

```
                              ┌─────────────────────────────────────────────────────────┐
                              │                   EXTERNAL CLIENTS                      │
                              │    Discord Users · Telegram Users · Admin REST API       │
                              └────────────────────────┬────────────────────────────────┘
                                                       │ HTTPS
                                                       ▼
                              ┌────────────────────────────────────────────────────────┐
                              │              gateway-service (port 8080)               │
                              │   Spring Cloud Gateway · IP Rate Limit · TG Webhook   │
                              │   Zipkin Tracing · Prometheus Metrics                  │
                              └──────┬──────────┬──────────┬──────────┬───────────────┘
                                     │          │          │          │
                        /scheduler/**│ /fetcher/**  /summarizer/** /notification/**
                                     ▼          ▼          ▼          ▼
┌─────────────────┐        ┌──────────────────┐  ┌────────────────┐  ┌──────────────────────┐
│ scheduler-svc   │◄──────►│  fetcher-svc     │  │ summarizer-svc │  │  notification-svc    │
│ port 8084       │Kafka   │  port 8081       │  │  port 8082     │  │  port 8083           │
│                 │trigger │                  │  │                │  │                      │
│ Quartz Scheduler│        │ ScheduleTrigger  │  │ News.fetched   │  │ News.summarized      │
│ → publish       │        │ Consumer         │  │ Consumer       │  │ Consumer             │
│   ScheduleTrigger        │                  │  │                │  │                      │
│   Event to Kafka│        │ RSS / HN /       │  │ Claude API     │  │ Discord (JDA 5)      │
│                 │        │ Reddit / NewsAPI  │  │ Gemini API     │  │ Telegram (BotFather) │
│ postgres-       │        │                  │  │                │  │                      │
│ scheduler       │        │ Redis (rate      │  │ news.summarized│  │ postgres-notification│
│ (QRTZ_* tables) │        │ limiter + lock)  │  │ Publisher      │  │ MongoDB (dedup)      │
│                 │        │                  │  │                │  │ Redis (lock + cache) │
└─────────────────┘        └──────────────────┘  └────────────────┘  └──────────────────────┘
         │                         │                      │                     │
         └─────────────────────────┴──────────────────────┴─────────────────────┘
                                                    │
                                             KAFKA (KRaft)
                                   ┌────────────────┴────────────────┐
                                   │  Topics:                        │
                                   │  news.schedule.trigger (NEW)    │
                                   │  news.fetched                   │
                                   │  news.summarized                │
                                   │  news.broadcast                 │
                                   │  news.failed (DLQ)              │
                                   └─────────────────────────────────┘

─────────────────────────────────────────
OBSERVABILITY STACK (Phase 6 — mới)
─────────────────────────────────────────
Zipkin  (port 9411) — Distributed Tracing
Prometheus (port 9090) — Metrics Scraping
Loki (port 3100) — Log Aggregation
Promtail — Docker log collector → Loki
Grafana (port 3000) — Dashboards
```

---

## 2. Thay Đổi So Với Phase 5

| Thành phần | Phase 5 | Phase 6 |
|---|---|---|
| **Quartz Scheduler** | Trong fetcher-service | Tách ra scheduler-service (port 8084) |
| **PostgreSQL** | 1 instance dùng chung | 2 instances: postgres-scheduler + postgres-notification |
| **fetcher-service** | Có Quartz + PostgreSQL | Chỉ có Redis + Kafka consumer/producer |
| **Kafka topics** | 4 topics | 5 topics (+`news.schedule.trigger`) |
| **Tracing** | Chưa có | Zipkin — traceId propagate qua tất cả service |
| **Logging** | Logs phân tán | Loki + Promtail → tập trung |
| **Metrics** | Prometheus endpoints | Prometheus + Grafana dashboards |
| **Reddit API** | Public JSON (sắp bị block) | OAuth2 Client Credentials |
| **K8s** | Thiếu Kafka, HPA | Đầy đủ: Kafka StatefulSet, HPA per service |
| **Gateway routes** | 4 routes | 5 routes (+`/scheduler/**`) |

---

## 3. Luồng Hoàn Chỉnh — Use Case: "Scheduled News Broadcast AI"

### 3.1 Bản đồ giao tiếp

```
[postgres-scheduler]
      │ Quartz reads QRTZ_*
      ▼
[scheduler-service]
      │ publish ScheduleTriggerEvent
      │ → Kafka topic: news.schedule.trigger
      │   key: "AI" (partition = hash("AI") % 3)
      ▼
[fetcher-service] (consumer group: fetcher-group)
      │ fetch từ 3 nguồn song song (Virtual Threads):
      │   - RssFetcher → HN Algolia
      │   - NewsApiFetcher → newsapi.org
      │   - RedditFetcher → OAuth2 → r/MachineLearning
      │ Redis lock: "fetch-job:AI" (tránh 2 pod fetch cùng lúc)
      │ Redis rate limit: newsApi, reddit, hn
      │ publish ArticleFetchedEvent (5 articles)
      │ → Kafka topic: news.fetched
      ▼
[summarizer-service] (consumer group: summarizer-group)
      │ AISummarizerService:
      │   1. ClaudeApiClient (claude-haiku-4-5) → summaryEn + summaryVi
      │   2. Fallback: GeminiApiClient (gemini-1.5-flash)
      │   3. Fallback: original title + description
      │ publish ArticleSummarizedEvent
      │ → Kafka topic: news.summarized
      │ (hoặc news.failed nếu lỗi hoàn toàn)
      ▼
[notification-service] (consumer group: notification-group)
      │ DeduplicationService:
      │   - Redis check: "dedup:DISCORD:{hash(url)}" TTL 48h
      │   - MongoDB check: sent_articles collection TTL 7d
      │ Redis lock: "lock:broadcast:{articleHash}"
      │ BroadcastService → load subscriptions từ postgres-notification
      │
      ├──► DiscordAdapter (JDA 5)
      │    - Circuit Breaker "discord"
      │    - Gửi EmbedMessage tới tất cả channel đã subscribe
      │
      └──► TelegramAdapter (TelegramBots)
           - Circuit Breaker "telegram"
           - Gửi MarkdownV2 tới tất cả group đã subscribe
      │
      │ publish BroadcastDoneEvent → news.broadcast (audit)
      │ Update SentArticle → MongoDB + Redis
      ▼
[DONE] — Zipkin có full trace từ scheduler → fetcher → summarizer → notification
```

### 3.2 Zipkin Trace Propagation

Mỗi bước trong luồng trên đều được trace với cùng `traceId`:

```
Trace ID: abc123def456
  ├── Span 1: scheduler-service.publishTrigger (2ms)
  ├── Span 2: fetcher-service.onTrigger (850ms)
  │     ├── Span 2.1: rssFetcher.fetch (320ms)
  │     ├── Span 2.2: newsApiFetcher.fetch (280ms)
  │     └── Span 2.3: redditFetcher.fetchWithOAuth (250ms)
  ├── Span 3: summarizer-service.onNewsEvent (1200ms)
  │     └── Span 3.1: claudeApiClient.summarize (1100ms)
  └── Span 4: notification-service.onSummarized (450ms)
        ├── Span 4.1: discordAdapter.send x3 channels (200ms)
        └── Span 4.2: telegramAdapter.send x2 groups (250ms)
```

Tìm trace tại: `http://localhost:9411` → search by traceId

---

## 4. Use Case: "/news" Command (On-Demand)

```
User: /news (trong Discord hoặc Telegram)
      │
      ▼
[notification-service] — DiscordCommandListener / TelegramBot
      │ Reply ngay: "⏳ Đang lấy tin tức, chờ chút..."
      │
      │ REST call (nội bộ Docker network, không qua Gateway):
      │ POST http://scheduler-service:8084/api/trigger/ALL?limit=5
      ▼
[scheduler-service] — SchedulerController
      │ publishTrigger(AI, "ON_DEMAND", 5)
      │ publishTrigger(PROGRAMMING, "ON_DEMAND", 5)
      │ publishTrigger(GAME_DEV, "ON_DEMAND", 5)
      │ → Kafka: 3 ScheduleTriggerEvent (triggeredBy="ON_DEMAND")
      ▼
[fetcher-service] (3 messages, 3 partitions song song)
      │ → Kafka: 3 ArticleFetchedEvent
      ▼
[summarizer-service]
      │ → Kafka: 3 ArticleSummarizedEvent
      ▼
[notification-service]
      │ Broadcast về đúng channel/group của user đã gõ lệnh
      ▼
User nhận được tin tức (async, ~3-5 giây sau lệnh)
```

---

## 5. Use Case: "/start" Subscribe Command

```
User: /start (trong Discord channel #tech-news)
      │
      ▼
[notification-service] — DiscordCommandListener
      │ SubscriptionService.subscribe(channelId, Platform.DISCORD)
      │ → INSERT INTO journalist_bot.user_subscriptions
      │   (channel_id="1234567890", platform="DISCORD", active=true)
      │   ON CONFLICT DO UPDATE SET active=true
      ▼
Discord: "✅ Channel này đã được subscribe nhận tin tức tự động!"

──── Lần fetch tiếp theo (scheduler trigger) ────
[notification-service] — BroadcastService
      │ userSubscriptionRepository.findByPlatformAndActive(DISCORD, true)
      │ → Includes channelId "1234567890"
      │ → Gửi tin tức tới channel này
```

---

## 6. Các Commands Hiện Tại (Đầy Đủ)

| Command | Nơi xử lý | Giao tiếp giữa các service |
|---|---|---|
| `/start` | notification-service → postgres-notification | Chỉ local DB write |
| `/stop` | notification-service → postgres-notification | Chỉ local DB write |
| `/news` | notification-service → scheduler-service (REST) → Kafka → fetcher → summarizer → notification | Full pipeline |
| `/news_ai` | notification-service → scheduler-service (REST, category=AI) → ... | Full pipeline, 1 category |
| `/news_programming` | notification-service → scheduler-service (REST, category=PROGRAMMING) → ... | Full pipeline, 1 category |
| `/news_gamedev` | notification-service → scheduler-service (REST, category=GAME_DEV) → ... | Full pipeline, 1 category |
| `/status` | notification-service → BotMetricsService (local) + Actuator | Local metrics only |
| `/help` | notification-service → BotCommandService → postgres-notification (cache Redis) | Local DB read |

---

## 7. Error Handling Matrix (Phase 6)

| Điểm lỗi | Cơ chế | Kết quả |
|---|---|---|
| Quartz trigger lỗi (DB) | Quartz misfire policy | Retry ở cycle tiếp theo |
| Kafka publish trigger lỗi | Kafka producer retry (3 lần) | Log error, bỏ qua cycle |
| RSS/HN fetch fail | Circuit Breaker → fallback empty | Dùng kết quả từ nguồn khác |
| NewsAPI fail | Circuit Breaker → fallback + Spring Retry | Tối đa 2 retry, rồi bỏ qua |
| Reddit OAuth token fail | Fallback → public JSON API | Vẫn fetch được nhưng có thể bị rate limit |
| Reddit rate limit | Redisson token bucket → skip | Log warn, không fetch lần này |
| 2 fetcher pod cùng consume | Redisson lock "fetch-job:{category}" | Chỉ 1 pod fetch, pod kia skip |
| Claude API lỗi | Circuit Breaker → fallback Gemini | Dùng Gemini |
| Gemini API lỗi | Circuit Breaker → fallback plain text | Gửi không có summary |
| Duplicate article | Redis 48h → MongoDB 7d dedup | Skip, không gửi trùng |
| 2 notification pod cùng broadcast | Redisson lock "lock:broadcast:{hash}" | Chỉ 1 pod gửi |
| Discord channel lỗi | Circuit Breaker → log + skip channel | Tiếp tục gửi channels khác |
| Telegram group lỗi | Circuit Breaker → log + skip group | Tiếp tục gửi groups khác |
| Kafka consumer crash | enable-auto-commit: false + manual ACK | Message re-consumed khi restart |
| Service crash (bất kỳ) | Kafka consumer group re-balance | Messages assigned lại cho pod khác |

---

## 8. Hướng Dẫn Chạy Phase 6

### Local (Docker Compose)

```bash
# 1. Tạo .env
cp .env.example .env
# Điền: DISCORD_BOT_TOKEN, TELEGRAM_BOT_TOKEN, ANTHROPIC_API_KEY
# Reddit OAuth2 (optional nhưng recommended):
# REDDIT_CLIENT_ID=your_client_id
# REDDIT_CLIENT_SECRET=your_client_secret

# 2. Build và chạy tất cả
docker compose up -d --build

# 3. Kiểm tra health
curl http://localhost:8080/actuator/health

# 4. Xem Grafana dashboards
open http://localhost:3000  # admin/admin

# 5. Xem Zipkin traces
open http://localhost:9411

# 6. Xem Prometheus metrics
open http://localhost:9090

# 7. Trigger on-demand (test)
curl -X POST http://localhost:8080/scheduler/api/trigger/AI?limit=3

# 8. Logs tập trung (qua Grafana → Loki)
open http://localhost:3000/explore  # Chọn Loki datasource
```

### Kubernetes

```bash
# 1. Apply namespace
kubectl apply -f k8s/namespace.yaml

# 2. Apply secrets (điền values thật trước)
kubectl apply -f k8s/secret.yaml -n journalist-bot

# 3. Apply configmaps
kubectl apply -f k8s/configmap.yaml -n journalist-bot

# 4. Apply infrastructure
kubectl apply -f k8s/kafka.yaml -n journalist-bot
kubectl apply -f k8s/postgres.yaml -n journalist-bot
kubectl apply -f k8s/mongodb.yaml -n journalist-bot
kubectl apply -f k8s/redis.yaml -n journalist-bot

# 5. Apply observability
kubectl apply -f k8s/monitoring.yaml -n journalist-bot

# 6. Apply microservices
kubectl apply -f k8s/deployment.yaml -n journalist-bot
kubectl apply -f k8s/service.yaml -n journalist-bot

# 7. Apply HPA
kubectl apply -f k8s/hpa.yaml -n journalist-bot

# 8. Kiểm tra
kubectl get pods -n journalist-bot
kubectl get hpa -n journalist-bot
```

---

## 9. Cấu Trúc File Phase 6 (Thêm mới)

```
JournalistBot/
├── scheduler-service/          ← NEW: Quartz scheduler tách ra
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/.../scheduler/
│       │   ├── SchedulerApplication.java
│       │   ├── job/NewsSchedulerJob.java
│       │   ├── publisher/ScheduleTriggerPublisher.java
│       │   ├── controller/SchedulerController.java
│       │   └── config/
│       │       ├── QuartzConfig.java
│       │       └── KafkaProducerConfig.java
│       └── resources/application.yml
│
├── monitoring/                  ← NEW: Observability config
│   ├── prometheus.yml
│   ├── loki-config.yml
│   ├── promtail-config.yml
│   └── grafana/
│       └── provisioning/
│           ├── datasources/datasources.yml
│           └── dashboards/dashboard.yml
│
├── common/src/.../common/
│   ├── kafka/KafkaTopics.java   ← Updated: +NEWS_SCHEDULE_TRIGGER
│   └── event/
│       ├── ScheduleTriggerEvent.java  ← NEW
│       └── ... (existing events)
│
├── fetcher-service/
│   └── src/main/java/.../fetcher/
│       ├── consumer/
│       │   └── ScheduleTriggerConsumer.java  ← NEW (replaces NewsJob)
│       ├── config/
│       │   └── KafkaConsumerConfig.java      ← NEW
│       └── infrastructure/
│           └── RedditFetcher.java            ← Updated: OAuth2
│
├── k8s/
│   ├── kafka.yaml              ← NEW: Kafka StatefulSet
│   ├── postgres.yaml           ← Updated: 2 instances
│   ├── hpa.yaml                ← NEW: HPA per service
│   ├── monitoring.yaml         ← NEW: Zipkin+Prometheus+Loki+Grafana
│   ├── deployment.yaml         ← Updated: per-service + scheduler
│   ├── configmap.yaml          ← Updated: per-service configmaps
│   └── secret.yaml             ← Updated: Reddit OAuth2 + Grafana
│
├── .github/workflows/
│   └── ci-cd-scheduler.yml     ← NEW: CI/CD cho scheduler-service
│
└── docker-compose.yml          ← Updated: observability + 2 PG instances
```
