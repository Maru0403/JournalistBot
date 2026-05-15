package com.maru.journalistbot.notification.service;

import com.maru.journalistbot.common.model.Platform;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Custom Micrometer metrics for notification-service.
 *
 * Exposes via /actuator/metrics and /actuator/prometheus:
 *   bot.articles.sent        [counter] articles broadcast per platform+category
 *   bot.articles.skipped     [counter] articles skipped (dedup) per platform
 *   bot.broadcast.errors     [counter] broadcast failures per platform
 *
 * Circuit breaker state (OPEN/CLOSED/HALF_OPEN) is auto-exposed by resilience4j-micrometer.
 *
 * SRP: only responsible for metric recording in notification-service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BotMetricsService {

    private final MeterRegistry meterRegistry;

    public void recordArticlesSent(Platform platform, String category, int count) {
        Counter.builder("bot.articles.sent")
                .tag("platform", platform.name())
                .tag("category", category)
                .description("Articles successfully broadcast")
                .register(meterRegistry)
                .increment(count);
    }

    public void recordArticlesSkipped(Platform platform, int count) {
        Counter.builder("bot.articles.skipped")
                .tag("platform", platform.name())
                .description("Articles skipped (dedup)")
                .register(meterRegistry)
                .increment(count);
    }

    public void recordBroadcastError(Platform platform, String reason) {
        Counter.builder("bot.broadcast.errors")
                .tag("platform", platform.name())
                .tag("reason", reason)
                .description("Broadcast failures")
                .register(meterRegistry)
                .increment();
    }
}
