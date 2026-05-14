package com.maru.journalistbot.application.scheduler.quartz;

import com.maru.journalistbot.application.broadcast.BroadcastService;
import com.maru.journalistbot.application.lock.DistributedLockService;
import com.maru.journalistbot.application.news.NewsServiceRegistry;
import com.maru.journalistbot.domain.model.NewsCategory;
import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.domain.port.NewsService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.util.List;

/**
 * Quartz Job — fetches and broadcasts news for a given NewsCategory.
 *
 * Extends QuartzJobBean (Spring's bridge) so @Autowired works here.
 * NewsCategory is read from JobDataMap (set in QuartzConfig).
 *
 * Key differences from @Scheduled NewsScheduler:
 *   1. DistributedLockService — only 1 instance executes per trigger
 *   2. Job state persisted in PostgreSQL (QRTZ_* tables)
 *   3. Misfire handling — skips missed cycles instead of firing all at once
 *   4. Micrometer timer — measures job execution time
 *
 * SRP: only responsible for orchestrating the broadcast flow.
 */
@Slf4j
public class NewsJob extends QuartzJobBean {

    // Spring injects these via SpringBeanJobFactory
    @Autowired
    private NewsServiceRegistry newsServiceRegistry;

    @Autowired
    private BroadcastService broadcastService;

    @Autowired
    private DistributedLockService lockService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Value("${bot.schedule.news-fetch-limit:5}")
    private int fetchLimit;

    // Lock lease = 5 minutes max per job (auto-release if pod crashes)
    private static final long LOCK_LEASE_SECONDS = 300L;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();
        String categoryName = dataMap.getString("category");

        NewsCategory category;
        try {
            category = NewsCategory.valueOf(categoryName);
        } catch (IllegalArgumentException e) {
            log.error("[QUARTZ] Unknown category '{}' in job data map", categoryName);
            return;
        }

        String lockName = "news-job:" + category.name();
        log.info("[QUARTZ] Job triggered — category: {}", category);

        // Execute under distributed lock — only 1 instance runs per cycle
        lockService.executeWithLock(lockName, LOCK_LEASE_SECONDS, () -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                runBroadcast(category);
            } finally {
                sample.stop(Timer.builder("quartz.job.duration")
                        .tag("category", category.name())
                        .description("Quartz news job execution time")
                        .register(meterRegistry));
            }
        });
    }

    private void runBroadcast(NewsCategory category) {
        log.info("[QUARTZ] Starting broadcast — category: {}", category);
        try {
            NewsService service = newsServiceRegistry.getByCategory(category);
            List<NewsArticle> articles = service.fetchLatestNews(fetchLimit);

            if (articles.isEmpty()) {
                log.info("[QUARTZ] No articles fetched for {}", category);
                return;
            }
            broadcastService.broadcastToAll(articles, category);
            log.info("[QUARTZ] Broadcast complete — category: {}, articles: {}",
                    category, articles.size());
        } catch (Exception e) {
            // Log but don't rethrow as JobExecutionException
            // — Quartz would retry immediately on exception, causing spam
            log.error("[QUARTZ] Broadcast failed for {} — will retry next cycle: {}",
                    category, e.getMessage(), e);
        }
    }
}
