package com.maru.journalistbot.fetcher.config;

/**
 * Phase 6 — QuartzConfig đã được chuyển sang scheduler-service.
 *
 * fetcher-service không còn chạy Quartz nữa.
 * Nhận trigger qua Kafka topic "news.schedule.trigger" từ scheduler-service.
 *
 * @see com.maru.journalistbot.fetcher.consumer.ScheduleTriggerConsumer
 * @deprecated Removed in Phase 6 — Quartz lives in scheduler-service only.
 */
public final class QuartzConfig {
    // Intentionally empty — Phase 6: Quartz moved to scheduler-service
    private QuartzConfig() {}
}
