package com.maru.journalistbot.application.ai;

import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.infrastructure.ai.ClaudeApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI-powered summarizer for news articles.
 *
 * Phase 1 — Stub: formatted plain text (no API cost).
 * Phase 2 — Real: calls Claude Haiku to generate Vietnamese summaries,
 *            falls back gracefully to plain-text format if API key is absent.
 *
 * Strategy:
 *   1. If ClaudeApiClient is configured → summarize with AI
 *   2. If Claude call fails → return plain-text format (never crash)
 *   3. If not configured → return plain-text format
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AISummarizerService {

    private final ClaudeApiClient claudeApiClient;

    /**
     * Summarize articles for a given topic.
     * Returns a Discord-formatted string (bold, links, emoji).
     */
    public String summarize(List<NewsArticle> articles, String topic) {
        if (articles.isEmpty()) {
            return "📭 Không có tin tức mới về **" + topic + "** vào lúc này.";
        }

        // Try AI summarization first
        if (claudeApiClient.isConfigured()) {
            String aiSummary = summarizeWithClaude(articles, topic);
            if (aiSummary != null && !aiSummary.isBlank()) {
                log.info("[AI] Claude summarized {} articles for '{}'", articles.size(), topic);
                return aiSummary;
            }
            log.warn("[AI] Claude call failed — falling back to plain format");
        }

        return formatWithoutAI(articles, topic);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private String summarizeWithClaude(List<NewsArticle> articles, String topic) {
        StringBuilder articleList = new StringBuilder();
        int count = Math.min(articles.size(), 5); // limit to 5 to control token cost

        for (int i = 0; i < count; i++) {
            NewsArticle a = articles.get(i);
            articleList.append(i + 1).append(". **").append(a.getTitle()).append("**\n");
            if (a.getDescription() != null && !a.getDescription().isBlank()) {
                articleList.append("   Excerpt: ").append(truncate(a.getDescription(), 150)).append("\n");
            }
            articleList.append("   Source: ").append(a.getSourceName()).append("\n");
            articleList.append("   URL: ").append(a.getUrl()).append("\n\n");
        }

        String prompt = """
                Bạn là một bot tin tức kỹ thuật. Tóm tắt %d bài báo sau về "%s" bằng tiếng Việt.

                Yêu cầu format (Discord markdown):
                - Mỗi bài: in đậm tiêu đề (**tiêu đề**), 1-2 câu tóm tắt ngắn gọn, giữ link gốc
                - Thêm emoji phù hợp (🤖 cho AI, 💻 cho code, 🎮 cho game)
                - Phần mở đầu: "📰 **Tin tức mới về %s**"
                - Không thêm nội dung ngoài danh sách bài báo
                - Tối đa 800 ký tự tổng cộng

                Bài viết:
                %s
                """.formatted(count, topic, topic, articleList);

        return claudeApiClient.chat(prompt, 600);
    }

    /**
     * Fallback formatter — no AI, just structured text.
     * Used when API key is missing or Claude call fails.
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
