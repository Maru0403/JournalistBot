package com.maru.journalistbot.fetcher.scheduler;

import com.maru.journalistbot.common.model.NewsCategory;
import com.maru.journalistbot.fetcher.domain.NewsArticle;
import com.maru.journalistbot.fetcher.domain.NewsService;
import com.maru.journalistbot.fetcher.lock.DistributedLockService;
import com.maru.journalistbot.fetcher.publisher.NewsEventPublisher;
import com.maru.journalistbot.fetcher.service.NewsServiceRegistry;
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
 * Quartz Job — fetch news và publish lên Kafka thay vì gọi BroadcastService trực tiếp.
 *
 * Phase 5 thay đổi so với Phase 4:
 *   Phase 4: NewsJob → BroadcastService → AI → Discord/Telegram  (monolith)
 *   Phase 5: NewsJob → NewsEventPublisher → Kafka "news.fetched"  (microservice)
 *            Kafka → summarizer-service → notification-service    (async)
 *
 * Distributed Lock vẫn giữ nguyên — chỉ 1 pod publish event cho mỗi category/cycle.
 * Quartz vẫn persist job state trong PostgreSQL QRTZ_* tables.
 */
@Slf4j
public class NewsJob extends QuartzJobBean {

    @Autowired private NewsServiceRegistry newsServiceRegistry;
    @Autowired private NewsEventPublisher newsEventPublisher;
    @Autowired private DistributedLockService lockService;
    @Autowired private MeterRegistry meterRegistry;

    @Value("${bot.schedule.news-fetch-limit:5}")
    private int fetchLimit;

    private static final long LOCK_LEASE_SECONDS = 300L;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();
        String categoryName = dataMap.getString("category");

        NewsCategory category;
        try {
            category = NewsCategory.valueOf(categoryName);
        } catch (IllegalArgumentException e) {
            log.error("[JOB] Unknown category '{}' in JobDataMap", categoryName);
            return;
        }

        String lockName = "fetch-job:" + category.name();
        log.info("[JOB] Triggered — category={}", category);

        lockService.executeWithLock(lockName, LOCK_LEASE_SECONDS, () -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                runFetchAndPublish(category);
            } finally {
                sample.stop(Timer.builder("fetcher.job.duration")
                        .tag("category", category.name())
                        .description("Quartz fetch job execution time")
                        .register(meterRegistry));
            }
        });
    }

    private void runFetchAndPublish(NewsCategory category) {
        log.info("[JOB] Fetching news — category={}", category);
        try {
            NewsService service = newsServiceRegistry.getByCategory(category);
            List<NewsArticle> articles = service.fetchLatestNews(fetchLimit);

            if (articles.isEmpty()) {
                log.info("[JOB] No articles fetched for {}", category);
                return;
            }

            // Phase 5: publish Kafka event thay vì gọi BroadcastService trực tiếp
            newsEventPublisher.publish(articles, category.name());
            log.info("[JOB] Published {} articles for {} → Kafka", articles.size(), category);

        } catch (Exception e) {
            // Không rethrow JobExecutionException — tránh Quartz retry ngay lập tức (spam)
            log.error("[JOB] Failed for {} — next cycle will retry: {}", category, e.getMessage(), e);
        }
    }
}
