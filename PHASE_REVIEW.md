# JournalistBot — Tổng Hợp Tiến Độ & Kế Hoạch Phase 6

---

## PHẦN 1 — TỔNG HỢP CÁC PHASE ĐÃ THỰC HIỆN

---

### ✅ Phase 1 — MVP (Hoàn thành)

**Mục tiêu:** RSS feeds cơ bản + Discord broadcast

**Đã làm:**
- RSS Parser bằng **Rome library** — parse Anthropic blog, HN Algolia
- **Quartz Scheduler** (JDBC mode, PostgreSQL) — scheduled jobs với cluster-safe
  - AI news: 30 phút / lần
  - Programming news: 1 giờ / lần
  - GameDev news: 2 giờ / lần
- **Discord JDA 5** — broadcast tự động lên channels
- Domain model: `NewsArticle`, `Platform` (enum), `NewsCategory` (enum)
- **Docker Compose** với infrastructure: Kafka, MongoDB, PostgreSQL, Redis
- **GitHub Actions** CI/CD (ci.yml, cd.yml)

---

### ✅ Phase 2 — Mở rộng nguồn + AI (Hoàn thành)

**Mục tiêu:** NewsAPI + Reddit + AI tóm tắt + `/news` command

**Đã làm:**
- **NewsApiFetcher** — NewsAPI.org (90 req/24h với rate limiter)
- **RedditFetcher** — r/MachineLearning, r/programming, r/gamedev (55 req/60s)
- **HnFetcher** — Hacker News Algolia API (100 req/60s)
- **Template Method Pattern** — `AbstractNewsService` → `AINewsService`, `ProgrammingNewsService`, `GameDevNewsService`
- **NewsServiceRegistry** — registry pattern để lookup service theo category
- **Claude API** (claude-haiku-4-5) + **Gemini** fallback
  - Fallback chain: Claude → Gemini → plain text (không bao giờ fail hoàn toàn)
- **BotCommand entity** — commands lưu trong PostgreSQL (dễ thay đổi không cần redeploy)
- **Resilience4j Circuit Breaker** — 3 instances cho news APIs (newsApi, reddit, hn)
- **Spring Retry** — tự retry khi API tạm lỗi
- `/news`, `/news_ai`, `/news_programming`, `/news_gamedev` commands — cả Discord lẫn Telegram

---

### ✅ Phase 3 — Multi-platform (Hoàn thành)

**Mục tiêu:** Telegram integration, multi-group support

**Đã làm:**
- **Telegram TelegramBots** — webhook-based, multi-group support
- **TelegramBot + TelegramAdapter** — xử lý commands, gửi tin nhắn
- **DiscordCommandListener** — slash commands `/` trên Discord
- **SubscriptionService** — subscribe/unsubscribe lưu PostgreSQL (`user_subscriptions`)
  - Hỗ trợ nhiều channel Discord + nhiều group Telegram đồng thời
- **DeduplicationService** — Redis (48h TTL) + MongoDB TTL index (7 ngày)
  - Không bao giờ gửi cùng 1 bài 2 lần cho cùng 1 channel
- **BroadcastService** — orchestrate gửi tới tất cả subscriber
- `NewsMessagePort` interface — port/adapter pattern để dễ thêm platform mới (Zalo)
- **K8s manifests** — namespace, deployment (2 replicas), service, configmap, secret
  - MongoDB StatefulSet, PostgreSQL StatefulSet, Redis Deployment

---

### ✅ Phase 4 — Polishing (Hoàn thành)

**Mục tiêu:** Redis dedup, rate limiting, monitoring, Quartz persistence

**Đã làm:**
- **Redis Distributed Lock** (Redisson RLock) — chống duplicate broadcast khi 2 instance chạy song song
- **Token Bucket Rate Limiter** (Redisson) — per API key: NewsAPI, Reddit, HN
- **Circuit Breaker** (Resilience4j) mở rộng thêm: claude, gemini, discord, telegram
- **Micrometer + Actuator** — metrics tại `/actuator/metrics`, `/actuator/prometheus`
  - `BotMetricsService` — track số bài fetch, summarize, broadcast thành công/thất bại
- **Quartz JDBC** — job persistence: sau khi restart service, jobs không mất
- **DLQ (Dead Letter Queue)** — `ArticleFailedEvent` gửi vào topic `news.failed` khi xử lý lỗi
- **Spring Retry** với backoff — tự retry với exponential backoff

---

### ✅ Phase 5 — Microservice Migration (Hoàn thành)

**Mục tiêu:** Tách monolith → 4 microservices độc lập qua Kafka

**Đã làm:**

**Step 1 — fetcher-service (port 8081)**
- Tách hoàn toàn logic fetch news
- Kafka Producer: publish `ArticleFetchedEvent` → topic `news.fetched`
- Quartz jobs chạy trong service này
- Redisson rate limiter + distributed lock riêng

**Step 2 — summarizer-service (port 8082)**
- Kafka Consumer: consume từ `news.fetched`
- Claude/Gemini API keys chỉ service này có (principle of least privilege)
- Kafka Producer: publish `ArticleSummarizedEvent` → topic `news.summarized`
- DLQ: publish `ArticleFailedEvent` → `news.failed` nếu cả 2 AI đều lỗi
- Circuit Breaker: claude, gemini

**Step 3 — Kafka Event Bus (3 topics)**
- `news.fetched` — fetcher → summarizer
- `news.summarized` — summarizer → notification
- `news.failed` — DLQ topic (failed events)
- `news.broadcast` — confirmation sau khi broadcast xong
- Kafka KRaft mode (không Zookeeper), 3 partitions, retention 24h

**Step 4 — notification-service (port 8083)**
- Kafka Consumer: consume từ `news.summarized`
- Discord JDA + Telegram adapters trong service này
- PostgreSQL: subscriptions + bot commands
- MongoDB: sent articles dedup (TTL 7 ngày)
- Redis: dedup cache (TTL 48h) + distributed lock

**Step 5 — gateway-service (port 8080)**
- Spring Cloud Gateway (WebFlux-based)
- Routing: `/fetcher/**` → 8081, `/summarizer/**` → 8082, `/notification/**` → 8083
- IP-based rate limiting (Redis RequestRateLimiter)
- `TelegramWebhookFilter` — validate `X-Telegram-Bot-Api-Secret-Token`
- CORS global cho dev mode

**Step 6 — CI/CD per service**
- `ci-cd-fetcher.yml`, `ci-cd-summarizer.yml`, `ci-cd-notification.yml`, `ci-cd-gateway.yml`
- Mỗi service build + push Docker image độc lập
- `common` module là shared library dùng chung

**Common module:**
- `KafkaTopics.java` — constants cho tất cả topic names
- `Platform.java`, `NewsCategory.java` — shared enums
- Event DTOs: `ArticleFetchedEvent`, `ArticleSummarizedEvent`, `ArticleFailedEvent`, `BroadcastDoneEvent`

---

## PHẦN 2 — ĐÁNH GIÁ KHẢ NĂNG CHẠY THỰC TẾ

---

### ✅ Những gì đã sẵn sàng

| Thành phần | Trạng thái | Ghi chú |
|---|---|---|
| Docker Compose (infra) | ✅ Ready | Kafka, MongoDB, PostgreSQL, Redis đầy đủ |
| fetcher-service | ✅ Chạy được | Cần `NEWS_API_KEY` (optional), không cần thiết để chạy |
| summarizer-service | ✅ Chạy được | Cần ít nhất 1 trong 2: `ANTHROPIC_API_KEY` hoặc `GEMINI_API_KEY` |
| notification-service | ✅ Chạy được | Cần `DISCORD_BOT_TOKEN` hoặc `TELEGRAM_BOT_TOKEN` |
| gateway-service | ✅ Chạy được | Cần Redis đang chạy |
| Multi-group support | ✅ Có | Subscribe/unsubscribe lưu DB |
| Deduplication | ✅ Có | Redis 48h + MongoDB 7 ngày |
| Circuit Breaker | ✅ Có | 7 instances (newsApi, reddit, hn, claude, gemini, discord, telegram) |
| CI/CD | ✅ Có | Per-service GitHub Actions |
| README | ✅ Có | Hướng dẫn cài đặt chi tiết |

### ⚠️ Những gì còn thiếu / cần kiểm tra trước khi dùng thực

| Vấn đề | Mức độ | Gì cần làm |
|---|---|---|
| **File `.env` chưa có** | 🔴 Critical | Tạo `.env` từ `.env.example`, điền tokens thật |
| **Telegram webhook chưa set** | 🔴 Critical | Sau khi deploy cần gọi Telegram `setWebhook` API với URL public |
| **Reddit API auth** | 🟡 Important | Reddit hiện yêu cầu OAuth2, cần `REDDIT_CLIENT_ID` + `REDDIT_SECRET` |
| **K8s manifests chưa hoàn chỉnh** | 🟡 Important | Kafka chưa có manifest K8s riêng (chỉ có docker-compose) |
| **HPA config chưa có** | 🟡 Important | Phase 6 sẽ thêm |
| **Distributed tracing chưa có** | 🟡 Important | Phase 6 sẽ thêm Zipkin/Jaeger |
| **Centralized logging chưa có** | 🟡 Important | Phase 6 sẽ thêm ELK/Loki |
| **Database per service chưa tách** | 🟡 Important | fetcher và notification đang share cùng PostgreSQL instance |
| **Zalo adapter chưa implement** | 🟢 Low | Stub `isConnected=false`, không ảnh hưởng |
| **Test coverage** | 🟡 Important | Chưa thấy unit test / integration test |

### Kết luận: Chạy local được ngay — deploy production cần thêm việc của Phase 6

**Để chạy ngay trên local:**
```bash
# 1. Tạo .env
cp .env.example .env
# Điền: DISCORD_BOT_TOKEN, TELEGRAM_BOT_TOKEN, ANTHROPIC_API_KEY

# 2. Chạy
docker compose up -d --build

# 3. Kiểm tra
curl http://localhost:8080/actuator/health
```

---

## PHẦN 3 — USE CASE CHI TIẾT: LUỒNG TIN TỨC TỰ ĐỘNG

---

### Use Case: "Scheduled News Broadcast — AI Category"

**Mô tả:** Mỗi 30 phút, hệ thống tự động lấy tin tức AI/ML, tóm tắt bằng Claude, rồi broadcast đến tất cả Discord channel và Telegram group đã subscribe.

---

#### Sơ đồ luồng đầy đủ

```
[Quartz Scheduler] ──trigger──► [NewsJob]
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
             [RssFetcher]   [NewsApiFetcher]  [RedditFetcher]
             (HN Algolia)   (newsapi.org)     (r/MachineLearning)
                    │               │               │
                    └───────────────┴───────────────┘
                                    │ List<NewsArticle>
                                    ▼
                      [NewsEventPublisher] ──Kafka──► topic: news.fetched
                                                            │
                                                            ▼
                                                  [NewsConsumer] (summarizer-service)
                                                            │
                                              ┌─────────────┴─────────────┐
                                              ▼                           ▼
                                      [ClaudeApiClient]          [GeminiApiClient]
                                      (Primary)                   (Fallback)
                                              │ ArticleSummarizedEvent
                                              ▼
                                 [SummarizedEventPublisher] ──Kafka──► topic: news.summarized
                                                                               │
                                                                               ▼
                                                                   [SummarizedNewsConsumer]
                                                                   (notification-service)
                                                                               │
                                                                  [DeduplicationService]
                                                                  (Redis check 48h TTL)
                                                                               │
                                                              ┌────────────────┴────────────────┐
                                                              ▼                                 ▼
                                                  [DiscordAdapter]                  [TelegramAdapter]
                                                  (JDA 5)                           (TelegramBots)
                                                       │                                    │
                                              Discord channels                     Telegram groups
                                              (từ DB subscription)                (từ DB subscription)
```

---

#### Chi tiết từng bước

**Bước 1 — Quartz trigger NewsJob (fetcher-service)**
- Quartz Scheduler kiểm tra job trong PostgreSQL table `QRTZ_TRIGGERS`
- Trigger `AINewsJob` mỗi 30 phút
- `DistributedLockService` acquire Redis lock `lock:news-job:AI` → đảm bảo chỉ 1 instance chạy khi scale
- Nếu lock bị hold bởi instance khác → skip (không fetch trùng)

**Bước 2 — Fetch từ nhiều nguồn song song (Virtual Threads)**
- `AINewsService.fetchNews()` gọi cùng lúc 3 fetcher (Virtual Threads, I/O-heavy):
  - `RssFetcher` → HN Algolia API (top 10 AI stories)
  - `NewsApiFetcher` → `newsapi.org?q=artificial+intelligence` (rate limit: 90/24h)
  - `RedditFetcher` → `r/MachineLearning` top posts (rate limit: 55/60s)
- Mỗi fetcher có **Resilience4j Circuit Breaker** riêng
  - Nếu NewsAPI fail 60% calls → circuit OPEN 120s → fallback: skip nguồn này
- **RateLimiterService** (Redisson token bucket) kiểm soát tần suất call per API
- Kết quả: danh sách `List<NewsArticle>` (tối đa `news-fetch-limit=5` per nguồn)

**Bước 3 — Publish Kafka event (fetcher-service)**
- `NewsEventPublisher.publish(articles)` chuyển mỗi article → `ArticleFetchedEvent`
- Produce vào topic `news.fetched` (3 partitions, acks=all, retries=3)
- Partition key = `category.name()` → đảm bảo cùng category vào cùng partition → ordered processing

**Bước 4 — Consume và Summarize (summarizer-service)**
- `NewsConsumer` consume từ `news.fetched` (group-id: `summarizer-group`, manual ACK)
- `AISummarizerService.summarize(article)`:
  1. Gọi `ClaudeApiClient` → claude-haiku-4-5 (prompt: tóm tắt 2-3 câu, song ngữ EN/VI)
  2. Nếu Claude fail → Circuit Breaker trigger → fallback: `GeminiApiClient` → gemini-1.5-flash
  3. Nếu Gemini cũng fail → dùng original title + description (không tóm tắt, vẫn broadcast)
- Kết quả: `ArticleSummarizedEvent` với `summaryEn` + `summaryVi`

**Bước 5 — Publish kết quả AI (summarizer-service)**
- `SummarizedEventPublisher.publish(event)` → topic `news.summarized`
- Nếu cả 2 AI đều fail hẳn (exception, không phải graceful fallback) → `FailedEventPublisher` → topic `news.failed` (DLQ)

**Bước 6 — Consume và Broadcast (notification-service)**
- `SummarizedNewsConsumer` consume từ `news.summarized` (group-id: `notification-group`)
- `DeduplicationService.isDuplicate(articleUrl, platform)`:
  - Check Redis: key = `dedup:{platform}:{hash(url)}`, TTL 48h
  - Nếu miss Redis → check MongoDB `sent_articles` collection (TTL 7 ngày)
  - Nếu đã gửi → skip bài này
- `BroadcastService.broadcast(event)`:
  - Acquire Redis lock `lock:broadcast:{articleHash}` → tránh 2 instance gửi cùng lúc
  - Load tất cả subscriptions từ PostgreSQL: `userSubscriptionRepository.findByPlatformAndActive()`
  - Gửi đến từng subscriber:

  **Discord (JDA 5):**
  ```
  DiscordAdapter.send(channelId, article)
  → jda.getTextChannelById(channelId).sendMessageEmbeds(embed)
  → Embed: title (link), summary (bilingual), source, category tag
  → Circuit Breaker "discord": nếu fail 50% → wait 30s → half-open → retry
  ```

  **Telegram (TelegramBots):**
  ```
  TelegramAdapter.send(chatId, article)
  → execute(SendMessage(chatId, formatMarkdown(article)))
  → Circuit Breaker "telegram": tương tự discord
  ```

- Sau khi broadcast xong → `BroadcastDonePublisher` → topic `news.broadcast` (audit log)
- Cập nhật `SentArticle` vào MongoDB + Redis

---

#### Cơ chế Error Handling trong luồng này

| Điểm lỗi | Cơ chế xử lý |
|---|---|
| RSS/NewsAPI/Reddit timeout | Resilience4j Circuit Breaker → skip nguồn, dùng các nguồn còn lại |
| Duplicate article | DeduplicationService (Redis + MongoDB) → skip, không gửi |
| Claude API lỗi | Fallback → Gemini → fallback → plain text |
| Gemini API lỗi | Fallback → plain text (title + description gốc) |
| Discord channel lỗi | Circuit Breaker → log error, tiếp tục gửi channel khác |
| Telegram chatId lỗi | Circuit Breaker → log error, tiếp tục gửi group khác |
| Kafka consumer crash giữa chừng | `enable-auto-commit: false` → manual ACK → message được re-consume |
| 2 fetcher-service chạy song song | Redisson distributed lock → chỉ 1 instance fetch, instance kia skip |
| 2 notification-service chạy song song | Redisson broadcast lock → chỉ 1 instance gửi, tránh double-send |

---

### Use Case: "/news" Command (On-Demand)

**Mô tả:** User gõ `/news` trong Discord hoặc Telegram → bot lấy tin ngay lập tức (không đợi scheduler).

```
User: /news
    │
    ▼
[DiscordCommandListener] hoặc [TelegramBot]
    │ REST call
    ▼
notification-service → GET http://fetcher-service:8081/api/news?category=ALL&limit=5
    │ (Internal Docker network, không qua Gateway)
    ▼
fetcher-service [OnDemandNewsController]
    │ fetch + publish to Kafka như luồng thông thường
    ▼
Kafka: news.fetched → news.summarized
    │ (async — không block user response ngay)
    ▼
notification-service broadcast về đúng channel user đã gõ lệnh
```

**Lưu ý:** `/news` command trả về response ngay "Đang lấy tin, vui lòng chờ..." rồi broadcast async.

---

## PHẦN 4 — CÁC COMMAND HIỆN TẠI

---

### Commands được lưu trong PostgreSQL (bảng `bot_commands`)

| Command | Discord | Telegram | Chức năng | Service xử lý |
|---|---|---|---|---|
| `/start` | ✅ | ✅ | Subscribe channel/group hiện tại nhận tin tự động | notification-service → SubscriptionService |
| `/stop` | ✅ | ✅ | Unsubscribe — dừng nhận tin | notification-service → SubscriptionService |
| `/news` | ✅ | ✅ | Lấy tin tức mới nhất ngay (tất cả category) | notification-service → REST → fetcher-service |
| `/news_ai` | ✅ | ✅ | Tin tức AI & Machine Learning | notification-service → REST → fetcher-service (category=AI) |
| `/news_programming` | ✅ | ✅ | Tin tức lập trình Java, JS, backend | notification-service → REST → fetcher-service (category=PROGRAMMING) |
| `/news_gamedev` | ✅ | ✅ | Tin tức game development | notification-service → REST → fetcher-service (category=GAMEDEV) |
| `/status` | ✅ | ✅ | Xem trạng thái bot, số bài đã gửi, circuit breakers | notification-service → BotMetricsService |
| `/help` | ✅ | ✅ | Danh sách commands từ DB | notification-service → BotCommandService |

---

## PHẦN 5 — PHASE 6: PRODUCTION HARDENING

---

### Mục tiêu Phase 6

Chuyển từ "chạy được" → "chạy ổn định ở production với nhiều user/group".

---

### Use Case Phase 6 — Hướng Xử Lý

---

#### 6.1 — K8s Full Deployment + HPA (Auto-scale)

**Vấn đề hiện tại:** K8s manifests có nhưng chưa có HPA, Kafka chưa có K8s manifest, readiness/liveness probes cần chuẩn hóa.

**Hướng làm:**
- Tạo `k8s/kafka.yaml` — Kafka StatefulSet (KRaft mode trên K8s)
- Thêm HPA per service:
  ```yaml
  # fetcher-service — scale dựa trên CPU (I/O heavy)
  minReplicas: 1, maxReplicas: 3, targetCPUUtilizationPercentage: 70

  # notification-service — scale dựa trên Kafka consumer lag
  minReplicas: 2, maxReplicas: 5 (KEDA consumer lag metric)
  ```
- Chuẩn hóa probes:
  - Liveness: `/actuator/health/liveness`
  - Readiness: `/actuator/health/readiness` (check Kafka, DB, Redis connections)

---

#### 6.2 — Database Per Service

**Vấn đề hiện tại:** fetcher-service và notification-service đang share 1 PostgreSQL instance (khác schema, nhưng cùng instance). Đây là antipattern microservices.

**Hướng làm:**
- Tách thành 2 PostgreSQL instance:
  - `postgres-fetcher`: chỉ có Quartz tables (fetcher-service)
  - `postgres-notification`: chỉ có `user_subscriptions`, `bot_commands` (notification-service)
- Update `docker-compose.yml` thêm `postgres-notification` service
- Update K8s: 2 StatefulSet riêng
- Update `application.yml` mỗi service dùng đúng connection string

---

#### 6.3 — Distributed Tracing (Zipkin/Jaeger)

**Vấn đề hiện tại:** Khi có lỗi, không biết request đi qua service nào, step nào fail.

**Hướng làm:**
- Add **Micrometer Tracing + Brave (Zipkin)** vào tất cả services
- Mỗi `ArticleFetchedEvent` mang theo `traceId` → propagate qua Kafka headers
- Deploy **Zipkin** container trong docker-compose + K8s
- Khi debug: search traceId → thấy toàn bộ journey: fetcher → summarizer → notification

**Implementation:**
```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
  <groupId>io.zipkin.reporter2</groupId>
  <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

---

#### 6.4 — Centralized Logging (Loki + Grafana)

**Vấn đề hiện tại:** Logs nằm rải rác ở từng container, không có cách search/filter tập trung.

**Hướng làm:**
- Deploy **Grafana Loki** + **Promtail** (log collector)
- Promtail collect logs từ tất cả containers → đẩy vào Loki
- Grafana dashboard: filter theo service, level, traceId
- Tích hợp với Prometheus metrics đã có
- Stack: `Prometheus + Loki + Grafana` trong 1 docker-compose profile

---

#### 6.5 — Tách Scheduler Service + Bot Gateway Service

**Vấn đề hiện tại:** Quartz chạy trong fetcher-service, Bot logic (JDA/Telegram) trong notification-service — mixing responsibilities khi scale.

**Hướng làm:**
- Tách `scheduler-service` (port 8084) — chỉ chứa Quartz jobs + trigger logic
  - fetcher-service trở thành pure "on-demand fetch" service
  - Scheduler trigger fetcher qua Kafka event thay vì Quartz trong cùng process
- Tách `bot-gateway-service` (port 8085) — chỉ nhận commands Discord/Telegram
  - Route command → notification-service qua REST/Kafka
  - notification-service thuần là "broadcast engine"

---

#### 6.6 — Deploy lên Railway / GCP GKE

**Hướng làm:**
- **Railway:** Dễ nhất cho demo/staging
  - Deploy từng service như separate Railway service
  - Link với Railway PostgreSQL + Redis managed services
  - Environment variables qua Railway dashboard
- **GCP GKE:** Production-grade
  - `gke-autopilot` — auto node provisioning
  - Cloud SQL (PostgreSQL), Memorystore (Redis), Pub/Sub có thể thay Kafka
  - GCR (Google Container Registry) cho Docker images
  - CICD push → GCR → trigger K8s rolling update

---

### Checklist Phase 6 — Thứ tự ưu tiên

```
Priority 1 (Cần để deploy stable):
  [ ] Thêm .env file với real tokens → test toàn bộ flow local
  [ ] Fix Reddit OAuth2 (client credentials)
  [ ] Tạo k8s/kafka.yaml
  [ ] Thêm HPA cho notification-service + fetcher-service
  [ ] Tách PostgreSQL per service

Priority 2 (Observability):
  [ ] Thêm Zipkin distributed tracing
  [ ] Deploy Loki + Grafana stack
  [ ] Chuẩn hóa readiness/liveness probes

Priority 3 (Scale & Reliability):
  [ ] Deploy lên Railway (staging)
  [ ] Service Discovery (K8s DNS native — không cần Eureka)
  [ ] Tách scheduler-service

Priority 4 (Nice to have):
  [ ] Deploy lên GKE (production)
  [ ] Zalo adapter thật (nếu có OA account)
  [ ] Unit tests + Integration tests
```

---

*Tài liệu cập nhật: Phase 1-5 hoàn thành. Phase 6 đang lên kế hoạch.*
