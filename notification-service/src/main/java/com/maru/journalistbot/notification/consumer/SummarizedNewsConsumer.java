package com.maru.journalistbot.notification.consumer;

import com.maru.journalistbot.common.event.ArticleSummarizedEvent;
import com.maru.journalistbot.common.kafka.KafkaTopics;
import com.maru.journalistbot.notification.broadcast.BroadcastService;
import com.maru.journalistbot.notification.lock.DistributedLockService;
import com.maru.journalistbot.notification.publisher.BroadcastDonePublisher;
import com.maru.journalistbot.notification.publisher.FailedEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for news.summarized topic.
 *
 * Flow:
 *   1. Receive ArticleSummarizedEvent (article list + AI summary)
 *   2. Acquire Distributed Lock — prevent duplicate broadcast when multi-instance
 *   3. BroadcastService.broadcastToAll() → Discord + Telegram
 *   4. Publish BroadcastDoneEvent to news.broadcast (audit log)
 *   5. ACK the Kafka offset
 *
 * On unrecoverable error:
 *   → Publish ArticleFailedEvent to news.failed (DLQ)
 *   → ACK anyway (avoid infinite re-consume loop for poison pill messages)
 *
 * ACK Strategy: MANUAL_IMMEDIATE
 *   - Commit offset ONLY after broadcast is complete
 *   - If crash before ACK → re-consume on restart (at-least-once delivery)
 *   - DeduplicationService prevents double-sending on re-consume
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SummarizedNewsConsumer {

    private final BroadcastService broadcastService;
    private final DistributedLockService lockService;
    private final BroadcastDonePublisher broadcastDonePublisher;
    private final FailedEventPublisher failedEventPublisher;

    @KafkaListener(
            topics = KafkaTopics.NEWS_SUMMARIZED,
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, ArticleSummarizedEvent> record, Acknowledgment ack) {
        ArticleSummarizedEvent event = record.value();

        if (event == null || event.getArticles() == null) {
            log.warn("[CONSUMER] Received null or empty event — skipping. key={}", record.key());
            ack.acknowledge();
            return;
        }

        String category = event.getCategory();
        String lockKey  = "broadcast:" + category + ":" + event.getEventId();

        log.info("[CONSUMER] Received {} articles for category={}, aiProvider={}, eventId={}",
                event.getArticles().size(), category, event.getAiProvider(), event.getEventId());

        try {
            // Distributed lock: only 1 instance processes this event
            lockService.executeWithLock(lockKey, () -> {
                broadcastService.broadcastToAll(event.getArticles(), category, event.getSummary());
                broadcastDonePublisher.publish(category, event.getArticles().size());
            });

        } catch (Exception e) {
            log.error("[CONSUMER] Unrecoverable error broadcasting for category={}: {}", category, e.getMessage(), e);
            // Publish to DLQ — then ACK to prevent infinite loop
            failedEventPublisher.publish(category, e.getMessage());
        } finally {
            // Always ACK — DeduplicationService handles re-send protection
            ack.acknowledge();
            log.debug("[CONSUMER] ACK committed for category={}", category);
        }
    }
}
