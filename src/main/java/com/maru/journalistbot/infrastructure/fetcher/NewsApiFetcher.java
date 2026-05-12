package com.maru.journalistbot.infrastructure.fetcher;

import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.domain.model.NewsCategory;
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
