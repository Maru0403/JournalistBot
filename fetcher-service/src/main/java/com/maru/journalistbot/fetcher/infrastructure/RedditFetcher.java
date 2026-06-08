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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phase 6 — Reddit Fetcher với OAuth2 Client Credentials.
 *
 * Reddit đã deprecated unauthenticated API (JSON endpoint) và yêu cầu OAuth2.
 * Flow:
 *   1. POST https://www.reddit.com/api/v1/access_token (Basic Auth: clientId:clientSecret)
 *   2. Lấy access_token (expires_in: 86400 giây = 24h)
 *   3. Dùng Bearer token cho các API calls
 *   4. Token được cache trong memory và tự renew khi hết hạn
 *
 * Setup Reddit App:
 *   https://www.reddit.com/prefs/apps → "script" type app
 *   Lấy: client_id + client_secret
 *
 * Fallback khi không có credentials: vẫn thử public JSON endpoint (may work tùy region)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedditFetcher {

    private final WebClient webClient;
    private final RateLimiterService rateLimiterService;

    @Value("${bot.reddit.client-id:}")
    private String clientId;

    @Value("${bot.reddit.client-secret:}")
    private String clientSecret;

    @Value("${bot.reddit.user-agent:journalist-bot/2.0 by /u/journalist_bot_dev}")
    private String userAgent;

    private static final String OAUTH_TOKEN_URL = "https://www.reddit.com/api/v1/access_token";
    private static final String OAUTH_API_BASE  = "https://oauth.reddit.com";
    private static final String PUBLIC_API_BASE  = "https://www.reddit.com";

    // Token cache — tự renew khi hết hạn
    private final AtomicReference<RedditToken> cachedToken = new AtomicReference<>();

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
            boolean hasOAuth = !clientId.isBlank() && !clientSecret.isBlank();
            return hasOAuth
                    ? fetchWithOAuth(subreddit, limit, category)
                    : fetchPublic(subreddit, limit, category);
        } catch (Exception e) {
            log.warn("[REDDIT] Failed r/{}: {}", subreddit, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── OAuth2 flow ────────────────────────────────────────────────────────────

    private List<NewsArticle> fetchWithOAuth(String subreddit, int limit, NewsCategory category) {
        String token = getValidToken();
        if (token == null) {
            log.warn("[REDDIT] Could not get OAuth token — falling back to public API");
            return fetchPublic(subreddit, limit, category);
        }

        RedditResponse response = webClient.get()
                .uri(OAUTH_API_BASE + "/r/{sub}/top.json?limit={limit}&t=day", subreddit, limit)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("User-Agent", userAgent)
                .retrieve()
                .bodyToMono(RedditResponse.class)
                .block();

        return parseResponse(response, subreddit, category);
    }

    private List<NewsArticle> fetchPublic(String subreddit, int limit, NewsCategory category) {
        // Fallback: public JSON endpoint (no auth required, but may rate-limit more aggressively)
        RedditResponse response = webClient.get()
                .uri(PUBLIC_API_BASE + "/r/{sub}/top.json?limit={limit}&t=day", subreddit, limit)
                .header("User-Agent", userAgent)
                .retrieve()
                .bodyToMono(RedditResponse.class)
                .block();

        return parseResponse(response, subreddit, category);
    }

    /**
     * Lấy valid token từ cache hoặc request mới.
     * Token Reddit có thời hạn 24h (86400s).
     */
    private String getValidToken() {
        RedditToken token = cachedToken.get();
        if (token != null && !token.isExpired()) {
            return token.accessToken();
        }

        log.info("[REDDIT] Requesting new OAuth2 token...");
        try {
            String credentials = Base64.getEncoder()
                    .encodeToString((clientId + ":" + clientSecret).getBytes());

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "client_credentials");

            RedditTokenResponse tokenResponse = webClient.post()
                    .uri(OAUTH_TOKEN_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                    .header("User-Agent", userAgent)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(RedditTokenResponse.class)
                    .block();

            if (tokenResponse == null || tokenResponse.accessToken() == null) {
                log.error("[REDDIT] Failed to get OAuth token — response is null");
                return null;
            }

            // Cache token với buffer 5 phút trước khi hết hạn
            long expiresAt = System.currentTimeMillis()
                    + (tokenResponse.expiresIn() - 300) * 1000L;
            RedditToken newToken = new RedditToken(tokenResponse.accessToken(), expiresAt);
            cachedToken.set(newToken);

            log.info("[REDDIT] OAuth2 token acquired, expires in {}s", tokenResponse.expiresIn());
            return newToken.accessToken();

        } catch (Exception e) {
            log.error("[REDDIT] OAuth token request failed: {}", e.getMessage());
            return null;
        }
    }

    // ── Response parsing ───────────────────────────────────────────────────────

    private List<NewsArticle> parseResponse(RedditResponse response, String subreddit, NewsCategory category) {
        if (response == null || response.data() == null || response.data().children() == null) {
            return Collections.emptyList();
        }

        return response.data().children().stream()
                .filter(child -> child.data() != null)
                .filter(child -> !child.data().isSelf())    // loại text-only posts
                .filter(child -> child.data().url() != null)
                .map(child -> mapToArticle(child.data(), category, subreddit))
                .toList();
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
        return Instant.ofEpochSecond(epoch.longValue())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    // ── Inner types ────────────────────────────────────────────────────────────

    /** Token cache record */
    private record RedditToken(String accessToken, long expiresAtMs) {
        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtMs;
        }
    }

    /** OAuth2 token response */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RedditTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in")  int expiresIn,
            @JsonProperty("token_type")  String tokenType
    ) {}

    /** Reddit API response */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RedditResponse(RedditListing data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RedditListing(List<RedditChild> children) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RedditChild(RedditPost data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RedditPost(
            @JsonProperty("title")        String title,
            @JsonProperty("url")          String url,
            @JsonProperty("score")        Integer score,
            @JsonProperty("num_comments") Integer numComments,
            @JsonProperty("is_self")      boolean isSelf,
            @JsonProperty("created_utc")  Double createdUtc
    ) {}
}
