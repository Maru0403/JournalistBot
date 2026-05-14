package com.maru.journalistbot.application.ratelimit;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Token bucket rate limiter using Redisson RRateLimiter.
 *
 * Why Redisson RRateLimiter?
 *   - Stored in Redis → shared across all bot instances (cluster-safe)
 *   - Token bucket algorithm → smooth rate distribution, no burst spikes
 *   - Auto-replenished based on configured rate/period
 *
 * Configured limits (from application.yml):
 *   - NewsAPI:  90 req / 24h  (free tier: 100/day, leave 10 buffer)
 *   - Reddit:   55 req / 60s  (rate limit: 60/min, leave 5 buffer)
 *   - HN:      100 req / 60s  (10,000/hour — effectively unlimited)
 *
 * Usage pattern:
 *   if (!rateLimiterService.tryAcquire("newsApi")) {
 *       log.warn("NewsAPI rate limit reached — skipping");
 *       return Collections.emptyList();
 *   }
 *   // proceed with API call
 *
 * SRP: only responsible for rate limit tracking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final RedissonClient redissonClient;

    private static final String LIMITER_PREFIX = "journalist-bot:rate:";

    // ── NewsAPI config ───────────────────────────────────────────────────────
    @Value("${bot.rate-limit.news-api.permits:90}")
    private long newsApiPermits;

    @Value("${bot.rate-limit.news-api.period-hours:24}")
    private long newsApiPeriodHours;

    // ── Reddit config ────────────────────────────────────────────────────────
    @Value("${bot.rate-limit.reddit.permits:55}")
    private long redditPermits;

    @Value("${bot.rate-limit.reddit.period-seconds:60}")
    private long redditPeriodSeconds;

    // ── HN config ────────────────────────────────────────────────────────────
    @Value("${bot.rate-limit.hn.permits:100}")
    private long hnPermits;

    @Value("${bot.rate-limit.hn.period-seconds:60}")
    private long hnPeriodSeconds;

    /**
     * Initialize rate limiters on startup.
     * RRateLimiter.trySetRate() is idempotent — safe to call on every restart.
     * If the limiter already exists in Redis with the same config, it's a no-op.
     */
    @PostConstruct
    public void initRateLimiters() {
        initLimiter("newsApi",
                newsApiPermits,
                newsApiPeriodHours,
                RateIntervalUnit.HOURS);

        initLimiter("reddit",
                redditPermits,
                redditPeriodSeconds,
                RateIntervalUnit.SECONDS);

        initLimiter("hn",
                hnPermits,
                hnPeriodSeconds,
                RateIntervalUnit.SECONDS);

        log.info("[RATE-LIMIT] Initialized: NewsAPI={}/{}h, Reddit={}/{}s, HN={}/{}s",
                newsApiPermits, newsApiPeriodHours,
                redditPermits, redditPeriodSeconds,
                hnPermits, hnPeriodSeconds);
    }

    /**
     * Try to acquire 1 token from the named rate limiter.
     * Non-blocking — returns false immediately if no tokens available.
     *
     * @param limiterName one of: "newsApi", "reddit", "hn"
     * @return true if token acquired (proceed), false if rate limit reached (skip)
     */
    public boolean tryAcquire(String limiterName) {
        RRateLimiter limiter = redissonClient.getRateLimiter(LIMITER_PREFIX + limiterName);
        boolean acquired = limiter.tryAcquire(1);
        if (!acquired) {
            log.warn("[RATE-LIMIT] Rate limit reached for '{}' — skipping this call", limiterName);
        }
        return acquired;
    }

    /**
     * Get remaining available tokens for a limiter.
     * Useful for monitoring / logging.
     */
    public long availableTokens(String limiterName) {
        RRateLimiter limiter = redissonClient.getRateLimiter(LIMITER_PREFIX + limiterName);
        return limiter.availablePermits();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void initLimiter(String name, long rate, long interval, RateIntervalUnit unit) {
        RRateLimiter limiter = redissonClient.getRateLimiter(LIMITER_PREFIX + name);
        // OVERALL: shared across all instances (not per-client)
        // trySetRate is idempotent — no error if limiter already configured
        boolean set = limiter.trySetRate(RateType.OVERALL, rate, interval, unit);
        if (set) {
            log.debug("[RATE-LIMIT] Created limiter '{}': {}/{}{}", name, rate, interval,
                    unit == RateIntervalUnit.HOURS ? "h" : "s");
        } else {
            log.debug("[RATE-LIMIT] Limiter '{}' already exists in Redis — reusing", name);
        }
    }
}
