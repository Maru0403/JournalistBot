package com.maru.journalistbot.summarizer.publisher;

import com.maru.journalistbot.common.event.ArticleFailedEvent;
import com.maru.journalistbot.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publish ArticleFailedEvent → topic "news.failed" (DLQ).
 * Dùng khi summarizer không thể xử lý event sau tất cả fallback.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FailedEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(ArticleFailedEvent event) {
        kafkaTemplate.send(KafkaTopics.NEWS_FAILED, event.getCategory(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[DLQ-PUB] Failed to publish DLQ event: {}", ex.getMessage());
                    } else {
                        log.warn("[DLQ-PUB] Published to DLQ: originalEventId={}, step={}, reason={}",
                                event.getOriginalEventId(), event.getFailedStep(), event.getErrorMessage());
                    }
                });
    }
}
