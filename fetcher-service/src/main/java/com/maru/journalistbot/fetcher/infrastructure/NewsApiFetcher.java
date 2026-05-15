package com.maru.journalistbot.fetcher.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.maru.journalistbot.common.model.NewsCategory;
import com.maru.journalistbot.fetcher.domain.NewsArticle;
import com.maru.journalistbot.fetcher.ratelimit.RateLimiterService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * NewsAPI.org fetcher — free tier: 100 req/day.
 * Rate limiting handled by RateLimiterService (Redisson token bucket, 90/day buffer).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NewsApiFetcher {

    private final WebClient webClient;
    private final RateLimiterService rateLimiterService;

    @Value("${bot.news-api.key:}")
    private String apiKey;

    @Value("${bot.news-api.base-url:https://newsapi.org/v2}")
    private String baseUrl;

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("your-");
    }

    @CircuitBreaker(name = "newsApi", fallbackMethod = "fallbackSearch")
    @Retryable(
        retryFor = Exception.class,
        maxAttempts = 2,
        backoff = @Backoff(delay = 3000)
    )
    public List<NewsArticle> searchByKeyword(String keyword, int limit, NewsCategory category) {
        if (!isConfigured()) {
            log.debug("[NEWSAPI] Key not configured — skipping '{}'", keyword);
            return Collections.emptyList();
        }
        if (!rateLimiterService.tryAcquire("newsApi")) {
            log.warn("[NEWSAPI] Daily quota reached — skipping keyword='{}'", keyword);
            return Collections.emptyList();
        }
        log.debug("[NEWSAPI] Searching: keyword='{}', limit={}", keyword, limit);
        try {
            NewsApiResponse response = webClient.get()
                    .uri(baseUrl + "/everything", uriBuilder -> uriBuilder
                            .queryParam("q", keyword)
                            .queryParam("pageSize", limit)
                            .queryParam("language", "en")
                            .queryParam("sortBy", "publishedAt")
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(NewsApiResponse.class)
                    .block();

            if (response == null || response.articles() == null) {
                return Collections.emptyList();
            }

            return response.articles().stream()
                    .filter(a -> a.url() != null && a.title() != null && !a.title().isBlank())
                    .map(a -> mapToArticle(a, category))
                    .toList();
        } catch (Exception e) {
            log.warn("[NEWSAPI] Failed for keyword='{}': {}", keyword, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<NewsArticle> fallbackSearch(String keyword, int limit, NewsCategory category, Throwable ex) {
        log.warn("[NEWSAPI] Circuit OPEN for keyword='{}': {}", keyword, ex.getMessage());
        return Collections.emptyList();
    }

    private NewsArticle mapToArticle(NewsApiArticle article, NewsCategory category) {
        return NewsArticle.builder()
                .title(article.title())
                .url(article.url())
                .description(article.description() != null ? article.description() : "")
                .sourceName(article.source() != null ? article.source().name() : "NewsAPI")
                .category(category)
                .publishedAt(parseDate(article.publishedAt()))
                .build();
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(dateStr, ISO_FORMATTER);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NewsApiResponse(String status, Integer totalResults, List<NewsApiArticle> articles) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NewsApiArticle(
            @JsonProperty("source") NewsApiSource source,
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("url") String url,
            @JsonProperty("publishedAt") String publishedAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NewsApiSource(@JsonProperty("name") String name) {}
}
