package com.maru.journalistbot.application.metrics;

import com.maru.journalistbot.application.ratelimit.RateLimiterService;
import com.maru.journalistbot.domain.model.NewsCategory;
import com.maru.journalistbot.domain.model.Platform;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Custom Micrometer metrics for JournalistBot.
 *
 * Exposes the following metrics via /actuator/metrics and /actuator/prometheus:
 *
 *   bot.articles.sent        [counter]  articles successfully sent per platform+category
 *   bot.articles.skipped     [counter]  articles skipped (dedup) per platform
 *   bot.broadcast.errors     [counter]  broadcast failures per platform
 *   bot.rate_limit.remaining [gauge]    remaining tokens per API rate limiter
 *
 * Circuit breaker state (OPEN/CLOSED/HALF_OPEN) is auto-exposed by
 * resilience4j-micrometer via the resilience4j.circuitbreaker.* metrics.
 *
 * Quartz job timing is recorded in NewsJob via Timer.builder("quartz.job.duration").
 *
 * SRP: only responsible for metric recording.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BotMetricsService {

    private final MeterRegistry meterRegistry;
    private final RateLimiterService rateLimiterService;

    /**
     * Register gauge metrics on startup.
     * Gauges are lazy — they call the supplier each time Prometheus scrapes.
     */
    @PostConstruct
    public void registerGauges() {
        // Remaining rate limit tokens — useful for alerting when NewsAPI quota is low
        Gauge.builder("bot.rate_limit.remaining", rateLimiterService,
                        svc -> svc.availableTokens("newsApi"))
                .tag("limiter", "newsApi")
                .description("Remaining NewsAPI requests in current window")
                .register(meterRegistry);

        Gauge.builder("bot.rate_limit.remaining", rateLimiterService,
                        svc -> svc.availableTokens("reddit"))
                .tag("limiter", "reddit")
                .description("Remaining Reddit requests in current window")
                .register(meterRegistry);

        Gauge.builder("bot.rate_limit.remaining", rateLimiterService,
                        svc -> svc.availableTokens("hn"))
                .tag("limiter", "hn")
                .description("Remaining HN Algolia requests in current window")
                .register(meterRegistry);

        log.info("[METRICS] Custom gauges registered: rate_limit.remaining for newsApi/reddit/hn");
    }

    /**
     * Record a successful article broadcast.
     * Call this AFTER successful delivery and dedup marking.
     *
     * @param platform target platform
     * @param category news category
     * @param count    number of articles sent
     */
    public void recordArticlesSent(Platform platform, NewsCategory category, int count) {
        Counter.builder("bot.articles.sent")
                .tag("platform", platform.name())
                .tag("category", category.name())
                .description("Number of articles successfully broadcast")
                .register(meterRegistry)
                .increment(count);
    }

    /**
     * Record articles skipped due to deduplication.
     *
     * @param platform target platform
     * @param count    number of articles skipped
     */
    public void recordArticlesSkipped(Platform platform, int count) {
        Counter.builder("bot.articles.skipped")
                .tag("platform", platform.name())
                .description("Number of articles skipped (already sent)")
                .register(meterRegistry)
                .increment(count);
    }

    /**
     * Record a broadcast error (after all retries exhausted).
     *
     * @param platform target platform
     * @param reason   short error reason tag (e.g. "circuit_open", "api_error")
     */
    public void recordBroadcastError(Platform platform, String reason) {
        Counter.builder("bot.broadcast.errors")
                .tag("platform", platform.name())
                .tag("reason", reason)
                .description("Number of broadcast failures")
                .register(meterRegistry)
                .increment();
    }
}
