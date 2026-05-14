package com.maru.journalistbot.application.broadcast;

import com.maru.journalistbot.application.ai.AISummarizerService;
import com.maru.journalistbot.application.dedup.DeduplicationService;
import com.maru.journalistbot.application.metrics.BotMetricsService;
import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.domain.model.NewsCategory;
import com.maru.journalistbot.domain.port.NewsMessagePort;
import com.maru.journalistbot.domain.model.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the full broadcast flow:
 *   1. Filter already-sent articles per platform (dedup)
 *   2. Summarize with AI
 *   3. Send via platform port
 *   4. Mark as sent (only on success)
 *   5. Record Micrometer metrics
 *
 * SRP: only responsible for broadcast orchestration.
 * DIP: depends on NewsMessagePort interface, not concrete adapters.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcastService {

    private final List<NewsMessagePort> adapters;
    private final DeduplicationService deduplicationService;
    private final AISummarizerService summarizerService;
    private final BotMetricsService metricsService;

    public void broadcastToAll(List<NewsArticle> articles, NewsCategory category) {
        if (articles.isEmpty()) {
            log.info("[BROADCAST] No articles to broadcast for {}", category);
            return;
        }
        for (NewsMessagePort adapter : adapters) {
            broadcastToPlatform(adapter, articles, category);
        }
    }

    public void broadcastToPlatform(Platform platform, List<NewsArticle> articles, NewsCategory category) {
        adapters.stream()
                .filter(a -> a.getPlatform() == platform)
                .findFirst()
                .ifPresent(adapter -> broadcastToPlatform(adapter, articles, category));
    }

    private void broadcastToPlatform(NewsMessagePort adapter, List<NewsArticle> articles, NewsCategory category) {
        Platform platform = adapter.getPlatform();

        if (!adapter.isConnected()) {
            log.debug("[BROADCAST] {} not connected, skipping", platform);
            return;
        }

        List<NewsArticle> unsent = deduplicationService.filterUnsent(articles, platform);
        int skipped = articles.size() - unsent.size();

        if (skipped > 0) {
            metricsService.recordArticlesSkipped(platform, skipped);
        }

        if (unsent.isEmpty()) {
            log.info("[BROADCAST] [{}] All {} articles already sent", platform, articles.size());
            return;
        }

        log.info("[BROADCAST] [{}] {} new articles (filtered {} duplicates)",
                platform, unsent.size(), skipped);

        try {
            String message = summarizerService.summarize(unsent, category.getDisplayName());
            adapter.sendNews(unsent, category, message);
            deduplicationService.markAllAsSent(unsent, platform);
            metricsService.recordArticlesSent(platform, category, unsent.size());
            log.info("[BROADCAST] [{}] Successfully sent {} articles for {}", platform, unsent.size(), category);
        } catch (Exception e) {
            // Do NOT mark as sent — will retry naturally next scheduler cycle
            metricsService.recordBroadcastError(platform, "send_error");
            log.error("[BROADCAST] [{}] Failed to send for {} — will retry next cycle: {}",
                    platform, category, e.getMessage());
        }
    }
}
