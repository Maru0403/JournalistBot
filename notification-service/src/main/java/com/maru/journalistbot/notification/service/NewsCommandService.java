package com.maru.journalistbot.notification.service;

import com.maru.journalistbot.common.event.ArticleItemDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * Handles /news <keyword> commands from Discord and Telegram.
 *
 * Flow (synchronous REST):
 *   1. Call fetcher-service GET /api/news/on-demand?keyword=X&limit=5
 *      → List<ArticleItemDto>
 *   2. Call summarizer-service POST /api/summarize
 *      → String (formatted summary)
 *   3. Return the summary to the calling platform adapter
 *
 * This service makes internal service-to-service REST calls (bot-network Docker).
 * Not using Kafka here because /news is a synchronous request-response command.
 */
@Service
@Slf4j
public class NewsCommandService {

    private final WebClient fetcherWebClient;
    private final WebClient summarizerWebClient;

    @Value("${bot.schedule.news-fetch-limit:5}")
    private int fetchLimit;

    public NewsCommandService(
            @Qualifier("fetcherWebClient") WebClient fetcherWebClient,
            @Qualifier("summarizerWebClient") WebClient summarizerWebClient) {
        this.fetcherWebClient    = fetcherWebClient;
        this.summarizerWebClient = summarizerWebClient;
    }

    /**
     * Handle /news command for a given keyword.
     *
     * @param keyword   e.g. "ai", "java", "gamedev", "python"
     * @param requesterId platform userId (for logging only)
     * @return formatted news summary string
     */
    public String handle(String keyword, String requesterId) {
        log.info("[CMD-NEWS] /news '{}' from {}", keyword, requesterId);

        if (keyword == null || keyword.isBlank()) {
            return buildHelpMessage();
        }

        try {
            // Step 1: Fetch articles from fetcher-service
            List<ArticleItemDto> articles = fetchArticles(keyword.trim());
            if (articles == null || articles.isEmpty()) {
                return "🔍 Không tìm thấy tin tức mới về *" + keyword + "* lúc này. Thử lại sau nhé!";
            }

            // Step 2: Summarize via summarizer-service
            String summary = summarizeArticles(articles, keyword.trim());
            return summary != null ? summary : buildFallbackSummary(articles, keyword);

        } catch (Exception e) {
            log.error("[CMD-NEWS] Failed to handle /news '{}': {}", keyword, e.getMessage());
            return "❌ Đã xảy ra lỗi khi lấy tin tức. Thử lại sau nhé!";
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private List<ArticleItemDto> fetchArticles(String keyword) {
        return fetcherWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/news/on-demand")
                        .queryParam("keyword", keyword)
                        .queryParam("limit", fetchLimit)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<ArticleItemDto>>() {})
                .block();
    }

    private String summarizeArticles(List<ArticleItemDto> articles, String keyword) {
        return summarizerWebClient.post()
                .uri("/api/summarize")
                .bodyValue(new SummarizeRequest(articles, keyword))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private String buildFallbackSummary(List<ArticleItemDto> articles, String keyword) {
        StringBuilder sb = new StringBuilder();
        sb.append("📰 *Tin tức về ").append(keyword).append(":*\n\n");
        for (ArticleItemDto article : articles) {
            sb.append("• *").append(article.getTitle()).append("*\n");
            if (article.getUrl() != null) {
                sb.append("  🔗 ").append(article.getUrl()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildHelpMessage() {
        return """
                📖 *Cách dùng lệnh /news:*
                `/news <từ khóa>`

                *Ví dụ:*
                `/news claude` — tin tức về Claude AI
                `/news java` — tin tức về Java
                `/news gamedev` — tin tức về phát triển game

                *Từ khóa hỗ trợ:* ai, claude, gemini, openai, java, python, javascript,\
                 programming, gamedev, unity, unreal
                """;
    }

    // ── Inner request class ───────────────────────────────────────────────────

    /** Request body gửi lên summarizer-service POST /api/summarize */
    public record SummarizeRequest(List<ArticleItemDto> articles, String category) {}
}
