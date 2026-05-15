package com.maru.journalistbot.summarizer.publisher;

import com.maru.journalistbot.common.event.ArticleSummarizedEvent;
import com.maru.journalistbot.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publish ArticleSummarizedEvent → topic "news.summarized" → notification-service.
 * Key = category → cùng category vào cùng partition → đúng thứ tự.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SummarizedEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(ArticleSummarizedEvent event) {
        kafkaTemplate.send(KafkaTopics.NEWS_SUMMARIZED, event.getCategory(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[SUMMARIZED-PUB] Failed to publish eventId={}: {}", event.getEventId(), ex.getMessage());
                        throw new RuntimeException("Kafka publish failed", ex);
                    } else {
                        log.debug("[SUMMARIZED-PUB] Published eventId={} → partition={}, offset={}",
                                event.getEventId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
