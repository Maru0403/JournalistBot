package com.maru.journalistbot.infrastructure.fetcher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.domain.model.NewsCategory;
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
 *
 * API: https://hn.algolia.com/api/v1/search
 * Free, no auth required, rate limit: 10,000 req/hour.
 *
 * Searches for top HN stories matching a given query.
 * Returns stories sorted by relevance (default) or date.
 *
 * Phase 1 requirement: "HN Algolia API — free, lấy top stories từ Hacker News"
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HnFetcher {

    private static final String HN_API_BASE      = "https://hn.algolia.com/api/v1";
    private static final String SOURCE_NAME      = "Hacker News";

    private final WebClient webClient;

    /**
     * Search HN top stories for a given query.
     *
     * @param query    search term, e.g. "AI machine learning" or "Java Spring"
     * @param limit    max articles to return
     * @param category news category for tagging
     * @return list of NewsArticle, empty on error
     */
    @Retryable(
        retryFor = Exception.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2.0)
    )
    public List<NewsArticle> searchTopStories(String query, int limit, NewsCategory category) {
        log.debug("[HN] Searching top stories: query='{}', limit={}", query, limit);
        try {
            HnSearchResponse response = webClient.get()
                    .uri(HN_API_BASE + "/search", uriBuilder -> uriBuilder
                            .queryParam("query", query)
                            .queryParam("tags", "story")
                            .queryParam("hitsPerPage", limit)
                            .queryParam("minPoints", 10) // filter low-quality posts
                            .build())
                    .retrieve()
                    .bodyToMono(HnSearchResponse.class)
                    .block();

            if (response == null || response.hits() == null || response.hits().isEmpty()) {
                log.debug("[HN] No results for query='{}'", query);
                return Collections.emptyList();
            }

            List<NewsArticle> articles = response.hits().stream()
                    .filter(hit -> hit.url() != null && !hit.url().isBlank())
                    .map(hit -> mapToArticle(hit, category))
                    .toList();

            log.debug("[HN] Fetched {} stories for query='{}'", articles.size(), query);
            return articles;

        } catch (Exception e) {
            log.warn("[HN] Failed to fetch stories for query='{}': {}", query, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetch top "Ask HN" or "Show HN" posts for a topic.
     * Useful for community discussions around a subject.
     *
     * @param tag   HN tag: "ask_hn", "show_hn", "story"
     * @param query keyword to narrow results
     * @param limit max results
     */
    @Retryable(
        retryFor = Exception.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2.0)
    )
    public List<NewsArticle> fetchByTag(String tag, String query, int limit, NewsCategory category) {
        log.debug("[HN] Fetching tag='{}', query='{}'", tag, query);
        try {
            HnSearchResponse response = webClient.get()
                    .uri(HN_API_BASE + "/search_by_date", uriBuilder -> uriBuilder
                            .queryParam("query", query)
                            .queryParam("tags", tag)
                            .queryParam("hitsPerPage", limit)
                            .build())
                    .retrieve()
                    .bodyToMono(HnSearchResponse.class)
                    .block();

            if (response == null || response.hits() == null) {
                return Collections.emptyList();
            }

            return response.hits().stream()
                    .filter(hit -> hit.url() != null && !hit.url().isBlank())
                    .map(hit -> mapToArticle(hit, category))
                    .toList();

        } catch (Exception e) {
            log.warn("[HN] Failed to fetch tag='{}', query='{}': {}", tag, query, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── Mapping ─────────────────────────────────────────────────────────────────

    private NewsArticle mapToArticle(HnHit hit, NewsCategory category) {
        String title       = Objects.requireNonNullElse(hit.title(), "Untitled");
        String url         = Objects.requireNonNullElse(hit.url(), "https://news.ycombinator.com");
        String description = buildDescription(hit);

        return NewsArticle.builder()
                .title(title)
                .url(url)
                .description(description)
                .sourceName(SOURCE_NAME)
                .category(category)
                .publishedAt(parseDate(hit.createdAt()))
                .build();
    }

    private String buildDescription(HnHit hit) {
        StringBuilder sb = new StringBuilder();
        if (hit.points() != null) {
            sb.append("⬆️ ").append(hit.points()).append(" points");
        }
        if (hit.numComments() != null) {
            sb.append(" · 💬 ").append(hit.numComments()).append(" comments");
        }
        if (hit.author() != null) {
            sb.append(" · by ").append(hit.author());
        }
        return sb.toString();
    }

    private LocalDateTime parseDate(String createdAt) {
        if (createdAt == null) return LocalDateTime.now();
        try {
            // HN format: "2024-01-15T10:30:00.000Z"
            return LocalDateTime.parse(
                    createdAt.replace("Z", ""),
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME
            );
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    // ── API Response Records ─────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HnSearchResponse(
            List<HnHit> hits,
            @JsonProperty("nbHits") Integer nbHits,
            @JsonProperty("page") Integer page
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HnHit(
            @JsonProperty("objectID")  String objectId,
            @JsonProperty("title")     String title,
            @JsonProperty("url")       String url,
            @JsonProperty("author")    String author,
            @JsonProperty("points")    Integer points,
            @JsonProperty("num_comments") Integer numComments,
            @JsonProperty("created_at") String createdAt
    ) {}
}
