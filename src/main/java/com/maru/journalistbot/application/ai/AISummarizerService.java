package com.maru.journalistbot.application.ai;

import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.infrastructure.ai.ClaudeApiClient;
import com.maru.journalistbot.infrastructure.ai.GeminiApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI-powered summarizer for news articles.
 *
 * Provider strategy (configurable via bot.ai.primary-provider):
 *   1. PRIMARY   — whichever provider is set as primary (claude / gemini)
 *   2. FALLBACK  — the other provider, if primary fails or not configured
 *   3. PLAIN TEXT — structured format with no AI (always available)
 *
 * Phase 1: Stub — plain text only.
 * Phase 2: Claude integration with plain-text fallback.
 * Phase 2 fix: Added Gemini as second AI provider.
 *   Claude → Gemini → plain text (never crashes regardless of key config).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AISummarizerService {

    private final ClaudeApiClient claudeApiClient;
    private final GeminiApiClient geminiApiClient;

    @Value("${bot.ai.primary-provider:claude}")
    private String primaryProvider;

    /**
     * Summarize articles for a given topic.
     * Returns a Discord/Telegram-formatted string (bold, links, emoji).
     *
     * @param articles list of articles to summarize
     * @param topic    display name for the topic (used in heading and prompt)
     * @return formatted summary string
     */
    public String summarize(List<NewsArticle> articles, String topic) {
        if (articles.isEmpty()) {
            return "📭 Không có tin tức mới về **" + topic + "** vào lúc này.";
        }

        // ── Try AI providers in configured order ───────────────────────────
        String aiResult = tryAiProviders(articles, topic);
        if (aiResult != null && !aiResult.isBlank()) {
            return aiResult;
        }

        // ── Fallback: plain-text format (no AI) ────────────────────────────
        log.info("[AI] All AI providers unavailable — using plain-text format for '{}'", topic);
        return formatWithoutAI(articles, topic);
    }

    // ── Provider strategy ────────────────────────────────────────────────────

    /**
     * Try AI providers in order: primary → fallback.
     * Returns the first successful response, or null if all fail.
     */
    private String tryAiProviders(List<NewsArticle> articles, String topic) {
        if ("gemini".equalsIgnoreCase(primaryProvider)) {
            // Primary: Gemini → Fallback: Claude
            String result = tryGemini(articles, topic);
            if (result != null) return result;

            log.debug("[AI] Gemini failed/unconfigured — trying Claude as fallback");
            return tryClaude(articles, topic);
        } else {
            // Primary: Claude (default) → Fallback: Gemini
            String result = tryClaude(articles, topic);
            if (result != null) return result;

            log.debug("[AI] Claude failed/unconfigured — trying Gemini as fallback");
            return tryGemini(articles, topic);
        }
    }

    private String tryClaude(List<NewsArticle> articles, String topic) {
        if (!claudeApiClient.isConfigured()) {
            log.debug("[AI] Claude not configured — skipping");
            return null;
        }
        String prompt = buildPrompt(articles, topic);
        String result = claudeApiClient.chat(prompt, 600);
        if (result != null) {
            log.info("[AI] ✅ Claude summarized {} articles for '{}'", articles.size(), topic);
        }
        return result;
    }

    private String tryGemini(List<NewsArticle> articles, String topic) {
        if (!geminiApiClient.isConfigured()) {
            log.debug("[AI] Gemini not configured — skipping");
            return null;
        }
        String prompt = buildPrompt(articles, topic);
        String result = geminiApiClient.chat(prompt, 800);
        if (result != null) {
            log.info("[AI] ✅ Gemini summarized {} articles for '{}'", articles.size(), topic);
        }
        return result;
    }

    // ── Prompt builder (shared by both providers) ────────────────────────────

    /**
     * Build the summarization prompt — identical for Claude and Gemini.
     * Limits to 5 articles to control token cost across both providers.
     */
    private String buildPrompt(List<NewsArticle> articles, String topic) {
        StringBuilder articleList = new StringBuilder();
        int count = Math.min(articles.size(), 5);

        for (int i = 0; i < count; i++) {
            NewsArticle a = articles.get(i);
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

    // ── Plain-text fallback ──────────────────────────────────────────────────

    /**
     * Fallback formatter — no AI, structured text only.
     * Used when both Claude and Gemini are unavailable or fail.
     */
    private String formatWithoutAI(List<NewsArticle> articles, String topic) {
        StringBuilder sb = new StringBuilder();
        sb.append("📰 **Tin tức mới về ").append(topic).append("**\n\n");

        int count = Math.min(articles.size(), 5);
        for (int i = 0; i < count; i++) {
            NewsArticle a = articles.get(i);
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
