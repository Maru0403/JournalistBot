package com.maru.journalistbot.scheduler.publisher;

import com.maru.journalistbot.common.event.ScheduleTriggerEvent;
import com.maru.journalistbot.common.kafka.KafkaTopics;
import com.maru.journalistbot.common.model.NewsCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer — publish ScheduleTriggerEvent → "news.schedule.trigger"
 * fetcher-service sẽ consume event này và thực hiện fetch news.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduleTriggerPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.application.name}")
    private String serviceName;

    /**
     * Publish trigger cho một category.
     * Partition key = category.name() → cùng category vào cùng partition → ordered.
     */
    public void publishTrigger(NewsCategory category, String triggeredBy, int fetchLimit) {
        String eventId = UUID.randomUUID().toString();

        ScheduleTriggerEvent event = ScheduleTriggerEvent.builder()
                .eventId(eventId)
                .category(category.name())
                .triggeredBy(triggeredBy)
                .fetchLimit(fetchLimit)
                .triggeredAt(LocalDateTime.now())
                .sourceInstance(serviceName)
                .build();

        String partitionKey = category.name();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(KafkaTopics.NEWS_SCHEDULE_TRIGGER, partitionKey, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[SCHEDULER] Failed to publish trigger for category={}, eventId={}: {}",
                        category, eventId, ex.getMessage());
            } else {
                log.info("[SCHEDULER] Trigger published — category={}, triggeredBy={}, eventId={}, partition={}",
                        category, triggeredBy, eventId,
                        result.getRecordMetadata().partition());
            }
        });
    }
}
