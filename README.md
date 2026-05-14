# JournalistBot

Bot tổng hợp tin tức tự động cho Discord và Telegram. Lấy tin từ **NewsAPI**, **Reddit**, và **Hacker News**, tóm tắt bằng AI (Claude / Gemini), rồi broadcast theo lịch.

---

## Mục lục

- [Yêu cầu](#yêu-cầu)
- [Bước 1 — Tạo Discord Bot](#bước-1--tạo-discord-bot)
- [Bước 2 — Tạo Telegram Bot](#bước-2--tạo-telegram-bot)
- [Bước 3 — Lấy API Keys](#bước-3--lấy-api-keys)
- [Bước 4 — Cấu hình .env](#bước-4--cấu-hình-env)
- [Bước 5 — Chạy với Docker Compose](#bước-5--chạy-với-docker-compose)
- [Bước 6 — Chạy thủ công (không dùng Docker)](#bước-6--chạy-thủ-công-không-dùng-docker)
- [Lệnh Bot](#lệnh-bot)
- [Kiến trúc](#kiến-trúc)

---

## Yêu cầu

| Công cụ | Phiên bản tối thiểu |
|---|---|
| Java | 21 |
| Maven | 3.9+ (hoặc dùng `./mvnw` đính kèm) |
| Docker + Docker Compose | Docker 24+ |
| PostgreSQL | 16 (nếu chạy thủ công) |
| MongoDB | 7.0 (nếu chạy thủ công) |
| Redis | 7.2 (nếu chạy thủ công) |

---

## Bước 1 — Tạo Discord Bot

### 1.1 Tạo Application

1. Truy cập [Discord Developer Portal](https://discord.com/developers/applications)
2. Nhấn **New Application** → đặt tên (ví dụ: `JournalistBot`) → **Create**
3. Vào tab **Bot** → nhấn **Add Bot** → xác nhận

### 1.2 Lấy Bot Token

Trong tab **Bot** → mục **Token** → nhấn **Reset Token** → copy token.

> ⚠️ Token chỉ hiện một lần. Lưu lại ngay.

### 1.3 Bật Intents

Trong tab **Bot** → mục **Privileged Gateway Intents**, bật:

- ✅ **Message Content Intent** — để bot đọc được nội dung tin nhắn trong server

### 1.4 Thêm Bot vào Server

1. Vào tab **OAuth2** → **URL Generator**
2. Chọn scope: **`bot`** và **`applications.commands`**
3. Chọn permissions:
   - ✅ Send Messages
   - ✅ Read Message History
   - ✅ Use Slash Commands
   - ✅ Embed Links
4. Copy URL được tạo ra → mở trên trình duyệt → chọn server → **Authorize**

### 1.5 Lấy Channel ID

Channel ID là nơi bot **tự động broadcast** tin tức (ngoài các channel đã subscribe bằng `/start`).

1. Trong Discord: **Settings → Advanced → bật Developer Mode**
2. Chuột phải vào channel muốn dùng → **Copy Channel ID**

---

## Bước 2 — Tạo Telegram Bot

### 2.1 Tạo bot qua BotFather

1. Mở Telegram, tìm **@BotFather**
2. Gửi lệnh `/newbot`
3. Đặt tên hiển thị (ví dụ: `Journalist Bot`)
4. Đặt username (phải kết thúc bằng `bot`, ví dụ: `journalist_news_bot`)
5. BotFather trả về **Bot Token** — copy lại

> ⚠️ Token có dạng: `123456789:AAFxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`

### 2.2 (Tùy chọn) Thêm bot vào Group

- Mở group chat → **Add Member** → tìm theo username của bot → **Add**
- Cấp quyền Admin để bot có thể gửi tin nhắn vào group

### 2.3 Đặt Commands cho BotFather

Gửi `/setcommands` cho BotFather, chọn bot của bạn, rồi paste:

```
start - Subscribe nhận tin tức
stop - Unsubscribe
news - Lấy tin ngay lập tức
news_ai - Tin tức AI & Machine Learning
news_programming - Tin tức lập trình
news_gamedev - Tin tức game dev
status - Trạng thái bot
help - Danh sách lệnh
```

---

## Bước 3 — Lấy API Keys

### NewsAPI (tin tức tổng hợp)

1. Đăng ký tại [newsapi.org](https://newsapi.org/register)
2. Sau khi verify email → vào Dashboard → copy **API Key**
3. Free tier: **100 requests/ngày**

### Anthropic Claude (AI tóm tắt — khuyến nghị)

1. Đăng ký tại [console.anthropic.com](https://console.anthropic.com)
2. Vào **Settings → API Keys** → **Create Key** → copy key
3. Model dùng: `claude-haiku` (rẻ nhất, đủ nhanh cho tóm tắt)

### Google Gemini (AI tóm tắt — thay thế miễn phí)

1. Vào [aistudio.google.com](https://aistudio.google.com/app/apikey)
2. Nhấn **Create API Key** → copy key
3. Free tier: **1,500 requests/ngày** — dùng được mà không tốn tiền

> Bot tự động fallback: nếu Claude lỗi → thử Gemini → nếu cả hai lỗi → gửi tin không tóm tắt. Không cần cả hai, một trong hai là đủ.

---

## Bước 4 — Cấu hình .env

Tạo file `.env` ở thư mục gốc project (cùng chỗ với `docker-compose.yml`):

```env
# ── Discord ────────────────────────────────────────────────────
DISCORD_BOT_TOKEN=your-discord-bot-token-here
DISCORD_NEWS_CHANNEL_ID=your-channel-id-here

# ── Telegram ───────────────────────────────────────────────────
TELEGRAM_BOT_TOKEN=123456789:AAFxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TELEGRAM_BOT_USERNAME=your_bot_username

# ── AI (chỉ cần một trong hai) ─────────────────────────────────
AI_PRIMARY_PROVIDER=claude
ANTHROPIC_API_KEY=sk-ant-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
GEMINI_API_KEY=AIzaSyxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# ── News Sources ───────────────────────────────────────────────
NEWS_API_KEY=your-newsapi-key-here

# ── Lịch phát tin (ms) — mặc định đủ dùng ─────────────────────
# AI_NEWS_INTERVAL_MS=1800000       # 30 phút
# PROG_NEWS_INTERVAL_MS=3600000     # 1 giờ
# GAMEDEV_NEWS_INTERVAL_MS=7200000  # 2 giờ
```

> Không có key nào là bắt buộc hoàn toàn. Thiếu key nào thì tính năng đó tắt, bot vẫn chạy bình thường.

---

## Bước 5 — Chạy với Docker Compose

```bash
# 1. Clone project (nếu chưa có)
git clone <repo-url>
cd JournalistBot

# 2. Tạo file .env (xem Bước 4)

# 3. Build và khởi động toàn bộ
docker compose up -d --build

# 4. Xem log
docker compose logs -f journalist-bot

# 5. Dừng
docker compose down
```

Docker Compose sẽ tự khởi động: **PostgreSQL**, **MongoDB**, **Redis**, và **JournalistBot**.

Kiểm tra bot đang chạy: truy cập `http://localhost:8080/actuator/health`

---

## Bước 6 — Chạy thủ công (không dùng Docker)

### 6.1 Cài PostgreSQL, MongoDB, Redis

Đảm bảo cả 3 service đang chạy trên localhost với port mặc định.

Tạo schema trong PostgreSQL:

```sql
CREATE SCHEMA IF NOT EXISTS journalist_bot;
```

### 6.2 Cấu hình application.yml

Mở `src/main/resources/application.yml`, điền trực tiếp vào các trường `${...}`:

```yaml
bot:
  discord:
    token: "your-discord-bot-token"
    news-channel-id: "your-channel-id"
  telegram:
    token: "your-telegram-token"
    username: "your_bot_username"
  news-api:
    key: "your-newsapi-key"
  ai:
    primary-provider: claude
    anthropic:
      api-key: "your-anthropic-key"
    gemini:
      api-key: "your-gemini-key"
```

### 6.3 Build và chạy

```bash
# Build (skip test để chạy nhanh)
./mvnw clean package -DskipTests

# Chạy
java -jar target/journalist-bot-0.0.1-SNAPSHOT.jar
```

---

## Lệnh Bot

Các lệnh hoạt động trên cả **Discord** (slash commands `/`) và **Telegram**:

| Lệnh | Mô tả |
|---|---|
| `/start` | Subscribe channel/chat hiện tại vào danh sách nhận tin |
| `/stop` | Unsubscribe — dừng nhận tin tự động |
| `/news` | Lấy tin tức mới nhất ngay lập tức (tất cả category) |
| `/news_ai` | Tin tức AI & Machine Learning |
| `/news_programming` | Tin tức lập trình, Java, JavaScript |
| `/news_gamedev` | Tin tức game development |
| `/status` | Xem trạng thái bot và các nguồn tin |
| `/help` | Danh sách đầy đủ các lệnh |

---

## Kiến trúc

```
NewsAPI / Reddit / Hacker News
         │
         ▼
   [News Fetchers]  ←── Circuit Breaker + Rate Limiter
         │
         ▼
   [AI Summarizer]  ←── Claude → Gemini → Plain text (fallback chain)
         │
         ▼
  [BroadcastService]
         │
    ┌────┴────┐
    ▼         ▼
Discord   Telegram    ←── Circuit Breaker per platform
```

**Infrastructure:**

- **Quartz Scheduler** — job persistence trong PostgreSQL, cluster-safe
- **Redis (Redisson)** — distributed lock + rate limiter
- **MongoDB** — lịch sử tin đã gửi (deduplication) + subscriptions
- **Micrometer** — metrics tại `/actuator/metrics` và `/actuator/prometheus`

**News categories:**

| Category | Nguồn |
|---|---|
| AI & ML | NewsAPI + Reddit r/MachineLearning, r/artificial + HN |
| Programming | NewsAPI + Reddit r/programming, r/java, r/javascript + HN |
| Game Dev | NewsAPI + Reddit r/gamedev, r/indiegaming + HN |
