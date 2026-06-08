package com.maru.journalistbot.fetcher.consumer;

import com.maru.journalistbot.common.event.ScheduleTriggerEvent;
import com.maru.journalistbot.common.kafka.KafkaTopics;
import com.maru.journalistbot.common.model.NewsCategory;
import com.maru.journalistbot.fetcher.domain.NewsArticle;
import com.maru.journalistbot.fetcher.domain.NewsService;
import com.maru.journalistbot.fetcher.lock.DistributedLockService;
import com.maru.journalistbot.fetcher.publisher.NewsEventPublisher;
import com.maru.journalistbot.fetcher.service.NewsServiceRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phase 6 — Kafka consumer thay thế Quartz job trong fetcher-service.
 *
 * Nhận ScheduleTriggerEvent từ scheduler-service → fetch news → publish news.fetched.
 *
 * Phân biệt với Phase 5 (NewsJob):
 *   Phase 5: Quartz bên trong fetcher-service tự trigger → fetch → publish
 *   Phase 6: Nhận trigger từ Kafka → fetch → publish (fetcher hoàn toàn reactive)
 *
 * Distributed Lock vẫn giữ — khi nhiều fetcher-service pod cùng consume
 * cùng 1 message (không xảy ra với Kafka consumer group, nhưng an toàn hơn).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduleTriggerConsumer {

    private final NewsServiceRegistry newsServiceRegistry;
    private final NewsEventPublisher newsEventPublisher;
    private final DistributedLockService lockService;
    private final MeterRegistry meterRegistry;

    private static final long LOCK_LEASE_SECONDS = 300L;

    @KafkaListener(
        topics = KafkaTopics.NEWS_SCHEDULE_TRIGGER,
        groupId = "fetcher-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTrigger(
            @Payload ScheduleTriggerEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {

        log.info("[FETCHER] Received trigger — category={}, triggeredBy={}, eventId={}, partition={}, offset={}",
                event.getCategory(), event.getTriggeredBy(), event.getEventId(), partition, offset);

        NewsCategory category;
        try {
            category = NewsCategory.valueOf(event.getCategory());
        } catch (IllegalArgumentException e) {
            log.error("[FETCHER] Unknown category '{}' in trigger event — skipping", event.getCategory());
            ack.acknowledge();
            return;
        }

        String lockName = "fetch-job:" + category.name();
        int fetchLimit = event.getFetchLimit() > 0 ? event.getFetchLimit() : 5;

        try {
            lockService.executeWithLock(lockName, LOCK_LEASE_SECONDS, () -> {
                Timer.Sample sample = Timer.start(meterRegistry);
                try {
                    runFetchAndPublish(category, fetchLimit);
                } finally {
                    sample.stop(Timer.builder("fetcher.job.duration")
                            .tag("category", category.name())
                            .tag("triggered_by", event.getTriggeredBy())
                            .description("Fetch job execution time (triggered by scheduler)")
                            .register(meterRegistry));
                }
            });
        } finally {
            ack.acknowledge();
        }
    }

    private void runFetchAndPublish(NewsCategory category, int fetchLimit) {
        log.info("[FETCHER] Fetching news — category={}, limit={}", category, fetchLimit);
        try {
            NewsService service = newsServiceRegistry.getByCategory(category);
            List<NewsArticle> articles = service.fetchLatestNews(fetchLimit);

            if (articles.isEmpty()) {
                log.info("[FETCHER] No articles for category={}", category);
                return;
            }

            newsEventPublisher.publish(articles, category.name());
            log.info("[FETCHER] Published {} articles for {} → news.fetched", articles.size(), category);

            meterRegistry.counter("fetcher.articles.published",
                    "category", category.name()).increment(articles.size());

        } catch (Exception e) {
            log.error("[FETCHER] Failed for category={}: {}", category, e.getMessage(), e);
            meterRegistry.counter("fetcher.articles.failed",
                    "category", category.name()).increment();
        }
    }
}
