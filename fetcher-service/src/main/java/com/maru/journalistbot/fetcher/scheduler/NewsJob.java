package com.maru.journalistbot.fetcher.scheduler;

/**
 * Phase 6 — NewsJob đã được chuyển sang scheduler-service.
 *
 * fetcher-service không còn chạy Quartz Job nữa.
 * Quartz scheduler chạy trong scheduler-service (port 8084),
 * publish ScheduleTriggerEvent → Kafka "news.schedule.trigger" → fetcher-service consume.
 *
 * @see com.maru.journalistbot.fetcher.consumer.ScheduleTriggerConsumer
 * @deprecated Removed in Phase 6 — NewsJob lives in scheduler-service only.
 */
public final class NewsJob {
    // Intentionally empty — Phase 6: job logic moved to scheduler-service
    private NewsJob() {}
}
