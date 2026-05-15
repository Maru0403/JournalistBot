package com.maru.journalistbot.fetcher.ratelimit;

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
 * Token bucket rate limiter via Redisson — shared across ALL fetcher-service instances (Redis-backed).
 * Khi scale nhiều pod: mọi pod dùng chung quota, không bị vượt giới hạn API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final RedissonClient redissonClient;
    private static final String LIMITER_PREFIX = "fetcher:rate:";

    @Value("${bot.rate-limit.news-api.permits:90}")   private long newsApiPermits;
    @Value("${bot.rate-limit.news-api.period-hours:24}") private long newsApiPeriodHours;
    @Value("${bot.rate-limit.reddit.permits:55}")     private long redditPermits;
    @Value("${bot.rate-limit.reddit.period-seconds:60}") private long redditPeriodSeconds;
    @Value("${bot.rate-limit.hn.permits:100}")        private long hnPermits;
    @Value("${bot.rate-limit.hn.period-seconds:60}")  private long hnPeriodSeconds;

    @PostConstruct
    public void initRateLimiters() {
        initLimiter("newsApi", newsApiPermits, newsApiPeriodHours, RateIntervalUnit.HOURS);
        initLimiter("reddit",  redditPermits,  redditPeriodSeconds, RateIntervalUnit.SECONDS);
        initLimiter("hn",      hnPermits,      hnPeriodSeconds,     RateIntervalUnit.SECONDS);
        log.info("[RATE-LIMIT] Initialized: NewsAPI={}/{}h, Reddit={}/{}s, HN={}/{}s",
                newsApiPermits, newsApiPeriodHours, redditPermits, redditPeriodSeconds,
                hnPermits, hnPeriodSeconds);
    }

    /** Non-blocking — trả về false ngay nếu hết quota, không block thread */
    public boolean tryAcquire(String limiterName) {
        RRateLimiter limiter = redissonClient.getRateLimiter(LIMITER_PREFIX + limiterName);
        boolean acquired = limiter.tryAcquire(1);
        if (!acquired) log.warn("[RATE-LIMIT] Quota reached for '{}'", limiterName);
        return acquired;
    }

    public long availableTokens(String limiterName) {
        return redissonClient.getRateLimiter(LIMITER_PREFIX + limiterName).availablePermits();
    }

    private void initLimiter(String name, long rate, long interval, RateIntervalUnit unit) {
        RRateLimiter limiter = redissonClient.getRateLimiter(LIMITER_PREFIX + name);
        // trySetRate là idempotent — restart không reset quota đang dùng
        limiter.trySetRate(RateType.OVERALL, rate, interval, unit);
    }
}
