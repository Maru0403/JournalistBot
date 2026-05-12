package com.maru.journalistbot.application.scheduler;

import com.maru.journalistbot.application.broadcast.BroadcastService;
import com.maru.journalistbot.application.news.NewsServiceRegistry;
import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.domain.model.NewsCategory;
import com.maru.journalistbot.domain.port.NewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled jobs for each news category.
 * SRP: only responsible for triggering the broadcast at the right interval.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NewsScheduler {

    private final NewsServiceRegistry registry;
    private final BroadcastService broadcastService;

    @Value("${bot.schedule.news-fetch-limit:5}")
    private int fetchLimit;

    // AI & Machine Learning — every 30 minutes
    @Scheduled(fixedDelayString = "${bot.schedule.ai-news-interval-ms:1800000}",
               initialDelayString = "${bot.schedule.initial-delay-ms:30000}")
    public void broadcastAINews() {
        runScheduledBroadcast(NewsCategory.AI);
    }

    // Programming Languages — every 60 minutes
    @Scheduled(fixedDelayString = "${bot.schedule.prog-news-interval-ms:3600000}",
               initialDelayString = "${bot.schedule.initial-delay-ms:30000}")
    public void broadcastProgrammingNews() {
        runScheduledBroadcast(NewsCategory.PROGRAMMING);
    }

    // Game Development — every 2 hours (lower frequency, content updates slower)
    @Scheduled(fixedDelayString = "${bot.schedule.gamedev-news-interval-ms:7200000}",
               initialDelayString = "${bot.schedule.initial-delay-ms:30000}")
    public void broadcastGameDevNews() {
        runScheduledBroadcast(NewsCategory.GAME_DEV);
    }

    private void runScheduledBroadcast(NewsCategory category) {
        log.info("[SCHEDULER] Starting scheduled broadcast — category: {}", category);
        try {
            NewsService service = registry.getByCategory(category);
            List<NewsArticle> articles = service.fetchLatestNews(fetchLimit);

            if (articles.isEmpty()) {
                log.info("[SCHEDULER] No articles fetched for {}", category);
                return;
            }
            broadcastService.broadcastToAll(articles, category);
        } catch (Exception e) {
            // Never let scheduler die — log and continue
            log.error("[SCHEDULER] Broadcast failed for {} — will retry next cycle: {}",
                    category, e.getMessage(), e);
        }
    }
}
