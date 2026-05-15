package com.maru.journalistbot.notification.broadcast;

import com.maru.journalistbot.common.event.ArticleItemDto;
import com.maru.journalistbot.common.model.Platform;
import com.maru.journalistbot.notification.platform.NewsMessagePort;
import com.maru.journalistbot.notification.service.BotMetricsService;
import com.maru.journalistbot.notification.service.DeduplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the full broadcast flow for notification-service.
 *
 * Phase 5 change vs. monolith:
 *   - No longer calls AISummarizerService (done by summarizer-service upstream)
 *   - Receives pre-summarized message from ArticleSummarizedEvent via Kafka
 *   - Iterates over all connected platform adapters
 *
 * Flow per platform:
 *   1. Filter unsent articles (dedup — Redis + MongoDB)
 *   2. Send via platform adapter (Discord/Telegram)
 *   3. Mark as sent ONLY on success (guarantee: no dedup marking on failure)
 *   4. Record Micrometer metrics
 *
 * SRP: only responsible for broadcast orchestration.
 * DIP: depends on NewsMessagePort interface, not on concrete adapters.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcastService {

    private final List<NewsMessagePort> adapters;
    private final DeduplicationService deduplicationService;
    private final BotMetricsService metricsService;

    /**
     * Broadcast articles to ALL connected platforms.
     *
     * @param articles  articles from the Kafka event
     * @param category  e.g. "AI", "PROGRAMMING", "GAME_DEV"
     * @param summary   AI-generated summary (already done by summarizer-service)
     */
    public void broadcastToAll(List<ArticleItemDto> articles, String category, String summary) {
        if (articles == null || articles.isEmpty()) {
            log.info("[BROADCAST] No articles to broadcast for {}", category);
            return;
        }

        log.info("[BROADCAST] Broadcasting {} articles for category={}", articles.size(), category);
        for (NewsMessagePort adapter : adapters) {
            broadcastToPlatform(adapter, articles, category, summary);
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void broadcastToPlatform(NewsMessagePort adapter,
                                     List<ArticleItemDto> articles,
                                     String category,
                                     String summary) {
        Platform platform = adapter.getPlatform();

        if (!adapter.isConnected()) {
            log.debug("[BROADCAST] {} not connected, skipping", platform);
            return;
        }

        // Filter out already-sent articles for this platform
        List<ArticleItemDto> unsent = deduplicationService.filterUnsent(articles, platform);
        int skipped = articles.size() - unsent.size();

        if (skipped > 0) {
            metricsService.recordArticlesSkipped(platform, skipped);
        }

        if (unsent.isEmpty()) {
            log.info("[BROADCAST] [{}] All {} articles already sent for {}", platform, articles.size(), category);
            return;
        }

        log.info("[BROADCAST] [{}] {} new articles ({} skipped as duplicate) for {}",
                platform, unsent.size(), skipped, category);

        try {
            adapter.sendNews(unsent, category, summary);

            // Mark as sent ONLY after successful delivery
            deduplicationService.markAllAsSent(unsent, platform);
            metricsService.recordArticlesSent(platform, category, unsent.size());

            log.info("[BROADCAST] [{}] ✅ Successfully sent {} articles for {}",
                    platform, unsent.size(), category);
        } catch (Exception e) {
            // Do NOT mark as sent — will retry naturally via next Kafka event cycle
            metricsService.recordBroadcastError(platform, "send_error");
            log.error("[BROADCAST] [{}] ❌ Failed to send for {} — not marking as sent: {}",
                    platform, category, e.getMessage());
        }
    }
}
