package com.maru.journalistbot.notification.publisher;

import com.maru.journalistbot.common.event.ArticleFailedEvent;
import com.maru.journalistbot.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Publishes ArticleFailedEvent to news.failed (DLQ) topic.
 * Used when broadcast cannot be processed after all retries.
 *
 * ArticleFailedEvent fields (from common):
 *   originalEventId, failedService, failedStep, category, errorMessage, platform, failedAt
 *
 * Note: field is "originalEventId" (ID of the upstream event that caused the failure),
 *       NOT a new UUID for this failure event itself.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FailedEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publish a failed event to the DLQ.
     *
     * @param category         news category (e.g. "AI")
     * @param errorMessage     short error description
     */
    public void publish(String category, String errorMessage) {
        publish(category, null, null, errorMessage);
    }

    /**
     * Publish a failed event with full context.
     *
     * @param category         news category
     * @param originalEventId  ID of the ArticleSummarizedEvent that triggered this
     * @param platform         platform that failed ("DISCORD" / "TELEGRAM"), null if pre-broadcast
     * @param errorMessage     short error description
     */
    public void publish(String category, String originalEventId, String platform, String errorMessage) {
        ArticleFailedEvent event = ArticleFailedEvent.builder()
                .originalEventId(originalEventId)   // ID of the upstream event (may be null)
                .category(category)
                .failedService("notification-service")
                .failedStep("BROADCAST")
                .errorMessage(truncate(errorMessage, 500))
                .platform(platform)                 // null if failure is not platform-specific
                .failedAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(KafkaTopics.NEWS_FAILED, category, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[DLQ] Failed to publish to DLQ for {}: {}", category, ex.getMessage());
                    } else {
                        log.warn("[DLQ] Published failed event for {} → news.failed", category);
                    }
                });
    }

    private String truncate(String msg, int max) {
        if (msg == null) return "unknown error";
        return msg.length() > max ? msg.substring(0, max - 3) + "..." : msg;
    }
}
