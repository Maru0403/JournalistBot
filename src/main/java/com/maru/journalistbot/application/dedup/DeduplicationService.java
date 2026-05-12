package com.maru.journalistbot.application.dedup;

import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.infrastructure.persistence.mongo.SentArticle;
import com.maru.journalistbot.infrastructure.persistence.mongo.SentArticleRepository;
import com.maru.journalistbot.domain.model.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Two-layer deduplication:
 *   Layer 1 — Redis (fast, in-memory, TTL 48h)
 *   Layer 2 — MongoDB (persistent, survives restart, TTL 7 days)
 *
 * SRP: only responsible for deduplication logic.
 * Mark as sent ONLY after successful delivery.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeduplicationService {

    private final RedisTemplate<String, String> redisTemplate;
    private final SentArticleRepository sentArticleRepository;

    private static final String REDIS_PREFIX = "bot:sent:";

    @Value("${bot.dedup.redis-ttl-hours:48}")
    private int redisTtlHours;

    @Value("${bot.dedup.mongo-ttl-days:7}")
    private int mongoTtlDays;

    public List<NewsArticle> filterUnsent(List<NewsArticle> articles, Platform platform) {
        return articles.stream()
                .filter(article -> !isAlreadySent(article, platform))
                .toList();
    }

    public boolean isAlreadySent(NewsArticle article, Platform platform) {
        String hash = buildHash(article.getUrl());
        String redisKey = buildRedisKey(hash, platform);

        if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            log.debug("[DEDUP] Redis hit — skip '{}' for {}", article.getTitle(), platform);
            return true;
        }

        boolean inMongo = sentArticleRepository.existsByArticleHashAndPlatform(hash, platform.name());
        if (inMongo) {
            redisTemplate.opsForValue().set(redisKey, "1", Duration.ofHours(redisTtlHours));
            log.debug("[DEDUP] MongoDB hit — skip '{}' for {}", article.getTitle(), platform);
            return true;
        }

        return false;
    }

    public void markAsSent(NewsArticle article, Platform platform) {
        String hash = buildHash(article.getUrl());
        String redisKey = buildRedisKey(hash, platform);

        redisTemplate.opsForValue().set(redisKey, "1", Duration.ofHours(redisTtlHours));

        SentArticle record = SentArticle.builder()
                .articleHash(hash)
                .url(article.getUrl())
                .title(article.getTitle())
                .platform(platform.name())
                .sentAt(LocalDateTime.now())
                .expireAt(LocalDateTime.now().plusDays(mongoTtlDays))
                .build();
        sentArticleRepository.save(record);

        log.debug("[DEDUP] Marked as sent: '{}' -> {}", article.getTitle(), platform);
    }

    public void markAllAsSent(List<NewsArticle> articles, Platform platform) {
        articles.forEach(article -> markAsSent(article, platform));
    }

    private String buildHash(String url) {
        String normalized = NewsArticle.normalizeUrl(url);
        return DigestUtils.md5DigestAsHex(normalized.getBytes(StandardCharsets.UTF_8));
    }

    private String buildRedisKey(String hash, Platform platform) {
        return REDIS_PREFIX + platform.name() + ":" + hash;
    }
}
