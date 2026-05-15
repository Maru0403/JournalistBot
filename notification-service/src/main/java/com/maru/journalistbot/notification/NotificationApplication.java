package com.maru.journalistbot.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Notification Service — Phase 5 Step 4
 *
 * Responsibilities:
 *   - Kafka consumer: news.summarized → broadcast to Discord + Telegram
 *   - Kafka producer: news.broadcast (done) + news.failed (DLQ)
 *   - Discord JDA 5 WebSocket: slash commands (/start /stop /news /categories /status)
 *   - Telegram long-polling: same commands
 *   - SubscriptionService: subscribe/unsubscribe per platform (PostgreSQL)
 *   - DeduplicationService: two-layer (Redis 48h + MongoDB 7d)
 *   - BotCommandService: commands stored in DB (PostgreSQL), cached in Redis
 *   - Distributed Lock (Redisson): chống duplicate broadcast khi scale multi-instance
 *   - Circuit Breaker: Discord + Telegram adapters
 */
@SpringBootApplication
@EnableRetry
@EnableCaching
@EnableAsync
public class NotificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
