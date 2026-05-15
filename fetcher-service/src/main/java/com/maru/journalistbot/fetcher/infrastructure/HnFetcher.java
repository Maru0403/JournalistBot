package com.maru.journalistbot.fetcher.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.maru.journalistbot.common.model.NewsCategory;
import com.maru.journalistbot.fetcher.domain.NewsArticle;
import com.maru.journalistbot.fetcher.ratelimit.RateLimiterService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Hacker News Algolia API fetcher.
 * API: https://hn.algolia.com/api/v1/search — free, no auth, 10k req/hour.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HnFetcher {

    private static final String HN_API_BASE = "https://hn.algolia.com/api/v1";
    private static final String SOURCE_NAME = "Hacker News";

    private final WebClient webClient;
    private final RateLimiterService rateLimiterService;

    @CircuitBreaker(name = "hn", fallbackMethod = "fallbackSearch")
    @Retryable(
        retryFor = Exception.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2.0)
    )
    public List<NewsArticle> searchTopStories(String query, int limit, NewsCategory category) {
        if (!rateLimiterService.tryAcquire("hn")) {
            log.warn("[HN] Rate limit reached — skipping query='{}'", query);
            return Collections.emptyList();
        }
        try {
            HnSearchResponse response = webClient.get()
                    .uri(HN_API_BASE + "/search", uriBuilder -> uriBuilder
                            .queryParam("query", query)
                            .queryParam("tags", "story")
                            .queryParam("hitsPerPage", limit)
                            .queryParam("minPoints", 10)
                            .build())
                    .retrieve()
                    .bodyToMono(HnSearchResponse.class)
                    .block();

            if (response == null || response.hits() == null || response.hits().isEmpty()) {
                return Collections.emptyList();
            }

            return response.hits().stream()
                    .filter(hit -> hit.url() != null && !hit.url().isBlank())
                    .map(hit -> mapToArticle(hit, category))
                    .toList();
        } catch (Exception e) {
            log.warn("[HN] Failed for query='{}': {}", query, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<NewsArticle> fallbackSearch(String query, int limit, NewsCategory category, Throwable ex) {
        log.warn("[HN] Circuit OPEN for query='{}': {}", query, ex.getMessage());
        return Collections.emptyList();
    }

    private NewsArticle mapToArticle(HnHit hit, NewsCategory category) {
        String desc = "";
        if (hit.points() != null) desc += "⬆️ " + hit.points() + " points";
        if (hit.numComments() != null) desc += " · 💬 " + hit.numComments() + " comments";
        if (hit.author() != null) desc += " · by " + hit.author();

        return NewsArticle.builder()
                .title(Objects.requireNonNullElse(hit.title(), "Untitled"))
                .url(Objects.requireNonNullElse(hit.url(), "https://news.ycombinator.com"))
                .description(desc)
                .sourceName(SOURCE_NAME)
                .category(category)
                .publishedAt(parseDate(hit.createdAt()))
                .build();
    }

    private LocalDateTime parseDate(String createdAt) {
        if (createdAt == null) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(createdAt.replace("Z", ""), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HnSearchResponse(List<HnHit> hits, @JsonProperty("nbHits") Integer nbHits) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HnHit(
            @JsonProperty("title") String title,
            @JsonProperty("url") String url,
            @JsonProperty("author") String author,
            @JsonProperty("points") Integer points,
            @JsonProperty("num_comments") Integer numComments,
            @JsonProperty("created_at") String createdAt
    ) {}
}
