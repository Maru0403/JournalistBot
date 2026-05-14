package com.maru.journalistbot.infrastructure.fetcher;

import com.maru.journalistbot.application.ratelimit.RateLimiterService;
import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.domain.model.NewsCategory;
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
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NewsApiFetcher {

    private final WebClient webClient;
    private final RateLimiterService rateLimiterService;

    @Value("${bot.news-api.key}")
    private String apiKey;

    @Value("${bot.news-api.base-url}")
    private String baseUrl;

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    /** Returns true only when a real API key is configured (not the placeholder). */
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
            log.debug("[NEWSAPI] Key not configured — skipping search for '{}'", keyword);
            return Collections.emptyList();
        }
        // Rate limit check — free tier: 100 req/day (90 with buffer)
        if (!rateLimiterService.tryAcquire("newsApi")) {
            log.warn("[NEWSAPI] Daily quota reached — skipping keyword='{}'", keyword);
            return Collections.emptyList();
        }
        log.debug("NewsAPI search: keyword='{}', limit={}", keyword, limit);
        try {
            NewsApiResponse response = webClient.get()
                    .uri(baseUrl + "/everything", uri -> uri
                            .queryParam("q", keyword)
                            .queryParam("sortBy", "publishedAt")
                            .queryParam("pageSize", limit)
                            .queryParam("language", "en")
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(NewsApiResponse.class)
                    .block();

            if (response == null || response.articles() == null) {
                return Collections.emptyList();
            }

            return response.articles().stream()
                    .filter(a -> a.url() != null && a.title() != null)
                    .filter(a -> !"[Removed]".equals(a.title()))
                    .map(a -> toNewsArticle(a, category))
                    .toList();

        } catch (Exception e) {
            log.warn("NewsAPI search failed for keyword '{}': {}", keyword, e.getMessage());
            return Collections.emptyList();
        }
    }

    private NewsArticle toNewsArticle(NewsApiArticle a, NewsCategory category) {
        return NewsArticle.builder()
                .title(a.title())
                .url(a.url())
                .description(a.description() != null ? truncate(a.description(), 300) : "")
                .sourceName(a.source() != null ? a.source().getOrDefault("name", "NewsAPI") : "NewsAPI")
                .category(category)
                .publishedAt(parseDate(a.publishedAt()))
                .build();
    }

    private String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen - 3) + "..." : text;
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(dateStr, ISO_FORMATTER);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    /** Circuit Breaker fallback — return empty list, broadcast continues without NewsAPI */
    private List<NewsArticle> fallbackSearch(String keyword, int limit, NewsCategory category, Throwable ex) {
        log.warn("[NEWSAPI] Circuit OPEN or error for keyword '{}' — returning empty. Reason: {}",
                keyword, ex.getMessage());
        return Collections.emptyList();
    }

    record NewsApiResponse(String status, int totalResults, List<NewsApiArticle> articles) {}

    record NewsApiArticle(
            Map<String, String> source,
            String author,
            String title,
            String description,
            String url,
            String publishedAt
    ) {}
}
