package com.maru.journalistbot.infrastructure.fetcher;

import com.maru.journalistbot.application.ratelimit.RateLimiterService;
import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.domain.model.NewsCategory;
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
 * Fetches top posts from Reddit public JSON API (no auth required).
 *
 * Endpoint: GET https://www.reddit.com/r/{subreddit}/top.json?limit=N&t=day
 * Reddit requires a proper User-Agent header to avoid 429/403 errors.
 *
 * Phase 2: Added for r/MachineLearning, r/artificial, r/programming, r/java, r/gamedev, etc.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedditFetcher {

    private final WebClient webClient;
    private final RateLimiterService rateLimiterService;

    private static final String BASE_URL = "https://www.reddit.com";
    private static final String USER_AGENT = "journalist-bot/2.0 (by journalist_bot; news aggregator)";

    @CircuitBreaker(name = "reddit", fallbackMethod = "fallbackFetch")
    @Retryable(
        retryFor = Exception.class,
        maxAttempts = 2,
        backoff = @Backoff(delay = 4000)
    )
    public List<NewsArticle> fetchTopPosts(String subreddit, int limit, NewsCategory category) {
        // Rate limit check — 60 req/min (55 with buffer)
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
                log.debug("[REDDIT] Empty response from r/{}", subreddit);
                return Collections.emptyList();
            }

            List<NewsArticle> articles = response.data().children().stream()
                    .map(RedditChild::data)
                    .filter(post -> post != null && post.url() != null && post.title() != null)
                    .filter(post -> !post.stickied())  // skip pinned mod posts
                    .filter(post -> post.score() > 10) // skip low-quality posts
                    .map(post -> toNewsArticle(post, subreddit, category))
                    .toList();

            log.debug("[REDDIT] r/{}: {} articles fetched", subreddit, articles.size());
            return articles;

        } catch (Exception e) {
            log.warn("[REDDIT] Failed to fetch r/{}: {}", subreddit, e.getMessage());
            return Collections.emptyList();
        }
    }

    private NewsArticle toNewsArticle(RedditPost post, String subreddit, NewsCategory category) {
        // Use external article URL if available, otherwise link to the Reddit thread
        boolean isExternalLink = post.url() != null
                && !post.url().contains("reddit.com")
                && !post.url().startsWith("https://www.reddit.com");

        String articleUrl = isExternalLink
                ? post.url()
                : "https://www.reddit.com" + post.permalink();

        String description = buildDescription(post, subreddit);

        return NewsArticle.builder()
                .title(cleanHtml(post.title()))
                .url(articleUrl)
                .description(description)
                .sourceName("Reddit r/" + subreddit)
                .category(category)
                .publishedAt(fromEpoch(post.created_utc()))
                .build();
    }

    private String buildDescription(RedditPost post, String subreddit) {
        if (post.selftext() != null && post.selftext().length() > 20) {
            return truncate(cleanHtml(post.selftext()), 200);
        }
        return String.format("r/%s — %d upvotes | %d comments",
                subreddit, post.score(), post.num_comments());
    }

    private String cleanHtml(String text) {
        if (text == null) return "";
        return text.replaceAll("&amp;", "&")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("&quot;", "\"")
                   .replaceAll("&#x200B;", "")
                   .trim();
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.isEmpty()) return "";
        return text.length() > maxLen ? text.substring(0, maxLen - 3) + "..." : text;
    }

    private LocalDateTime fromEpoch(double epochSeconds) {
        return Instant.ofEpochSecond((long) epochSeconds)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    /** Circuit Breaker fallback — return empty list, broadcast continues with other sources */
    private List<NewsArticle> fallbackFetch(String subreddit, int limit, NewsCategory category, Throwable ex) {
        log.warn("[REDDIT] Circuit OPEN or error for r/{} — returning empty. Reason: {}",
                subreddit, ex.getMessage());
        return Collections.emptyList();
    }

    // ── Reddit JSON response shape ───────────────────────────────────────────
    record RedditResponse(RedditData data) {}

    record RedditData(List<RedditChild> children, String after) {}

    record RedditChild(RedditPost data) {}

    record RedditPost(
            String id,
            String title,
            String url,
            String selftext,
            String permalink,
            String subreddit,
            int score,
            int num_comments,
            double created_utc,
            boolean is_self,
            boolean stickied
    ) {}
}
