package com.maru.journalistbot.summarizer.service;

import com.maru.journalistbot.common.event.ArticleItemDto;
import com.maru.journalistbot.summarizer.infrastructure.ClaudeApiClient;
import com.maru.journalistbot.summarizer.infrastructure.GeminiApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI summarizer — nhận List<ArticleItemDto> từ Kafka event, trả về summary string.
 *
 * Phase 5 thay đổi so với Phase 4:
 *   Phase 4: nhận List<NewsArticle> (domain model của monolith)
 *   Phase 5: nhận List<ArticleItemDto> (common DTO qua Kafka) — không phụ thuộc fetcher-service
 *
 * Provider strategy (configurable):
 *   1. PRIMARY   → claude hoặc gemini (theo config)
 *   2. FALLBACK  → provider còn lại
 *   3. PLAIN TEXT → luôn available, không crash
 *
 * Trả về cả summary text và tên provider đã dùng (để log vào ArticleSummarizedEvent).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AISummarizerService {

    private final ClaudeApiClient claudeApiClient;
    private final GeminiApiClient geminiApiClient;

    @Value("${bot.ai.primary-provider:claude}")
    private String primaryProvider;

    public record SummaryResult(String summary, String providerUsed) {}

    /**
     * Summarize một batch bài viết theo topic.
     * Luôn trả về kết quả — không bao giờ throw exception.
     *
     * @param articles  danh sách bài viết (từ ArticleFetchedEvent)
     * @param topic     tên category hiển thị (vd: "🤖 AI & Machine Learning")
     * @return SummaryResult chứa text và tên provider đã dùng
     */
    public SummaryResult summarize(List<ArticleItemDto> articles, String topic) {
        if (articles == null || articles.isEmpty()) {
            String msg = "📭 Không có tin tức mới về **" + topic + "** vào lúc này.";
            return new SummaryResult(msg, "none");
        }

        // ── Thử AI providers theo thứ tự ──────────────────────────────────
        if ("gemini".equalsIgnoreCase(primaryProvider)) {
            String geminiResult = tryGemini(articles, topic);
            if (geminiResult != null) return new SummaryResult(geminiResult, "gemini");

            log.debug("[SUMMARIZER] Gemini failed — fallback to Claude");
            String claudeResult = tryClaude(articles, topic);
            if (claudeResult != null) return new SummaryResult(claudeResult, "claude");
        } else {
            // Default: Claude first
            String claudeResult = tryClaude(articles, topic);
            if (claudeResult != null) return new SummaryResult(claudeResult, "claude");

            log.debug("[SUMMARIZER] Claude failed — fallback to Gemini");
            String geminiResult = tryGemini(articles, topic);
            if (geminiResult != null) return new SummaryResult(geminiResult, "gemini");
        }

        // ── Fallback cuối: plain text không cần AI ────────────────────────
        log.info("[SUMMARIZER] All AI providers unavailable — using plain-text fallback for '{}'", topic);
        return new SummaryResult(formatPlainText(articles, topic), "none");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String tryClaude(List<ArticleItemDto> articles, String topic) {
        if (!claudeApiClient.isConfigured()) {
            log.debug("[SUMMARIZER] Claude not configured");
            return null;
        }
        String result = claudeApiClient.chat(buildPrompt(articles, topic), 600);
        if (result != null) log.info("[SUMMARIZER] ✅ Claude summarized {} articles for '{}'", articles.size(), topic);
        return result;
    }

    private String tryGemini(List<ArticleItemDto> articles, String topic) {
        if (!geminiApiClient.isConfigured()) {
            log.debug("[SUMMARIZER] Gemini not configured");
            return null;
        }
        String result = geminiApiClient.chat(buildPrompt(articles, topic), 800);
        if (result != null) log.info("[SUMMARIZER] ✅ Gemini summarized {} articles for '{}'", articles.size(), topic);
        return result;
    }

    /**
     * Prompt template — dùng chung cho Claude và Gemini.
     * Giới hạn 5 bài để kiểm soát token cost.
     */
    private String buildPrompt(List<ArticleItemDto> articles, String topic) {
        StringBuilder articleList = new StringBuilder();
        int count = Math.min(articles.size(), 5);

        for (int i = 0; i < count; i++) {
            ArticleItemDto a = articles.get(i);
            articleList.append(i + 1).append(". **").append(a.getTitle()).append("**\n");
            if (a.getDescription() != null && !a.getDescription().isBlank()) {
                articleList.append("   Excerpt: ").append(truncate(a.getDescription(), 150)).append("\n");
            }
            articleList.append("   Source: ").append(a.getSourceName()).append("\n");
            articleList.append("   URL: ").append(a.getUrl()).append("\n\n");
        }

        return """
                Bạn là một bot tin tức kỹ thuật. Tóm tắt %d bài báo sau về "%s" bằng tiếng Việt.

                Yêu cầu format (Discord/Telegram markdown):
                - Mỗi bài: in đậm tiêu đề (**tiêu đề**), 1-2 câu tóm tắt ngắn gọn, giữ link gốc
                - Thêm emoji phù hợp (🤖 cho AI, 💻 cho code, 🎮 cho game)
                - Phần mở đầu: "📰 **Tin tức mới về %s**"
                - Không thêm nội dung ngoài danh sách bài báo
                - Tối đa 800 ký tự tổng cộng

                Bài viết:
                %s
                """.formatted(count, topic, topic, articleList);
    }

    private String formatPlainText(List<ArticleItemDto> articles, String topic) {
        StringBuilder sb = new StringBuilder();
        sb.append("📰 **Tin tức mới về ").append(topic).append("**\n\n");
        int count = Math.min(articles.size(), 5);
        for (int i = 0; i < count; i++) {
            ArticleItemDto a = articles.get(i);
            sb.append(i + 1).append(". **").append(a.getTitle()).append("**\n");
            if (a.getDescription() != null && !a.getDescription().isBlank()) {
                sb.append("   ").append(truncate(a.getDescription(), 150)).append("\n");
            }
            sb.append("   🔗 ").append(a.getUrl()).append("\n");
            sb.append("   📌 _").append(a.getSourceName()).append("_\n\n");
        }
        return sb.toString().trim();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen - 3) + "..." : text;
    }
}
