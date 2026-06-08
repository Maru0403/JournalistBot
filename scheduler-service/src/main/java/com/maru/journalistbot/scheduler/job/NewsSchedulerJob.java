package com.maru.journalistbot.scheduler.job;

import com.maru.journalistbot.common.model.NewsCategory;
import com.maru.journalistbot.scheduler.publisher.ScheduleTriggerPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * Phase 6 — Quartz job chỉ làm 1 việc: publish ScheduleTriggerEvent → Kafka.
 * fetcher-service tự biết cách fetch khi nhận trigger.
 *
 * So sánh với Phase 5 (NewsJob trong fetcher-service):
 *   Phase 5: Quartz trigger → fetch news → publish news.fetched    (trong cùng service)
 *   Phase 6: Quartz trigger → publish schedule.trigger → fetch news (2 service riêng)
 *
 * Ưu điểm:
 *   - scheduler-service không cần biết gì về RSS/HN/Reddit/NewsAPI
 *   - fetcher-service không biết về Quartz, chỉ respond to events
 *   - Scale độc lập: tăng fetcher-service pod không tăng số trigger
 */
@Slf4j
public class NewsSchedulerJob extends QuartzJobBean {

    @Autowired
    private ScheduleTriggerPublisher triggerPublisher;

    @Autowired
    private MeterRegistry meterRegistry;

    @Value("${bot.schedule.news-fetch-limit:5}")
    private int fetchLimit;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getMergedJobDataMap();
        String categoryName = dataMap.getString("category");

        NewsCategory category;
        try {
            category = NewsCategory.valueOf(categoryName);
        } catch (IllegalArgumentException e) {
            log.error("[SCHEDULER-JOB] Unknown category '{}' in JobDataMap — check QuartzConfig", categoryName);
            return;
        }

        log.info("[SCHEDULER-JOB] Firing — category={}, fetchLimit={}", category, fetchLimit);

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // Chỉ publish trigger — không fetch, không gọi AI, không broadcast
            triggerPublisher.publishTrigger(category, "SCHEDULER", fetchLimit);

            meterRegistry.counter("scheduler.trigger.published",
                    "category", category.name(),
                    "result", "success"
            ).increment();

        } catch (Exception e) {
            log.error("[SCHEDULER-JOB] Failed to publish trigger for category={}: {}", category, e.getMessage(), e);
            meterRegistry.counter("scheduler.trigger.published",
                    "category", category.name(),
                    "result", "error"
            ).increment();
        } finally {
            sample.stop(Timer.builder("scheduler.job.duration")
                    .tag("category", category.name())
                    .description("Quartz scheduler job execution time")
                    .register(meterRegistry));
        }
    }
}
