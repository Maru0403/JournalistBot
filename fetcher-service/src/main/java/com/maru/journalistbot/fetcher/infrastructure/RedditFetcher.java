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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

/**
 * Reddit public JSON API — không cần auth, rate limit: 60 req/min.
 * User-Agent bắt buộc, Reddit trả về 429/403 nếu không có header này.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedditFetcher {

    private final WebClient webClient;
    private final RateLimiterService rateLimiterService;

    private static final String BASE_URL   = "https://www.reddit.com";
    private static final String USER_AGENT = "journalist-bot/2.0 (news aggregator)";

    @CircuitBreaker(name = "reddit", fallbackMethod = "fallbackFetch")
    @Retryable(
        retryFor = Exception.class,
        maxAttempts = 2,
        backoff = @Backoff(delay = 4000)
    )
    public List<NewsArticle> fetchTopPosts(String subreddit, int limit, NewsCategory category) {
        if (!rateLimiterService.tryAcquire("reddit")) {
            log.warn("[REDDIT] Rate limit reached — skipping r/{}", subreddit);
            return Collections.emptyList();
        }
        log.debug("[REDDIT] Fetching r/{} top posts (limit={}, t=day)", subreddit, limit);
        try {
            RedditResponse response = webClient.get()
                    .uri(BASE_URL + "/r/{sub}/top.json?limit={limit}&t=day", subreddit, limit)
                    .header("User-Agent", USER_AGENT)
                    .retrieve()
                    .bodyToMono(RedditResponse.class)
                    .block();

            if (response == null || response.data() == null || response.data().children() == null) {
                return Collections.emptyList();
            }

            return response.data().children().stream()
                    .filter(child -> child.data() != null)
                    .filter(child -> !child.data().isSelf())    // loại bỏ text-only posts
                    .filter(child -> child.data().url() != null)
                    .map(child -> mapToArticle(child.data(), category, subreddit))
                    .toList();
        } catch (Exception e) {
            log.warn("[REDDIT] Failed r/{}: {}", subreddit, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<NewsArticle> fallbackFetch(String subreddit, int limit, NewsCategory category, Throwable ex) {
        log.warn("[REDDIT] Circuit OPEN for r/{}: {}", subreddit, ex.getMessage());
        return Collections.emptyList();
    }

    private NewsArticle mapToArticle(RedditPost post, NewsCategory category, String subreddit) {
        String desc = "⬆️ " + post.score() + " · 💬 " + post.numComments() + " · r/" + subreddit;
        return NewsArticle.builder()
                .title(post.title())
                .url(post.url())
                .description(desc)
                .sourceName("Reddit r/" + subreddit)
                .category(category)
                .publishedAt(epochToDateTime(post.createdUtc()))
                .build();
    }

    private LocalDateTime epochToDateTime(Double epoch) {
        if (epoch == null) return LocalDateTime.now();
        return Instant.ofEpochSecond(epoch.longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    // ── Response records ──────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RedditResponse(RedditListing data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RedditListing(List<RedditChild> children) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RedditChild(RedditPost data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RedditPost(
            @JsonProperty("title")       String title,
            @JsonProperty("url")         String url,
            @JsonProperty("score")       Integer score,
            @JsonProperty("num_comments") Integer numComments,
            @JsonProperty("is_self")     boolean isSelf,
            @JsonProperty("created_utc") Double createdUtc
    ) {}
}
