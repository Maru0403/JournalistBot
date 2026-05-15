package com.maru.journalistbot.notification.publisher;

import com.maru.journalistbot.common.event.BroadcastDoneEvent;
import com.maru.journalistbot.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Publishes BroadcastDoneEvent to news.broadcast topic.
 * Used as an audit/confirmation log after successful broadcast.
 *
 * BroadcastDoneEvent fields (from common):
 *   platform, targetId, articlesSent, articlesSkipped, category, broadcastAt
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BroadcastDonePublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publish a summary-level confirmation (no specific platform/targetId).
     * Called from SummarizedNewsConsumer after broadcastToAll() completes.
     *
     * @param category      news category (e.g. "AI")
     * @param articlesSent  total articles delivered across all platforms
     */
    public void publish(String category, int articlesSent) {
        BroadcastDoneEvent event = BroadcastDoneEvent.builder()
                .category(category)
                .platform("ALL")          // summary event — covers all platforms
                .targetId("ALL")          // all subscribed channels
                .articlesSent(articlesSent)
                .articlesSkipped(0)       // unknown at this level (per-platform dedup handles it)
                .broadcastAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(KafkaTopics.NEWS_BROADCAST, category, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("[BROADCAST-DONE] Failed to publish confirmation for {}: {}", category, ex.getMessage());
                    } else {
                        log.debug("[BROADCAST-DONE] Published confirmation for {} ({} articles)", category, articlesSent);
                    }
                });
    }

    /**
     * Publish a per-platform confirmation with full dedup stats.
     * Call this from BroadcastService if per-platform granularity is needed.
     *
     * @param category        news category
     * @param platform        platform name (e.g. "DISCORD", "TELEGRAM")
     * @param targetId        channel/chat ID that received the message
     * @param articlesSent    articles actually delivered (after dedup)
     * @param articlesSkipped articles skipped (already sent — dedup)
     */
    public void publishPerPlatform(String category, String platform, String targetId,
                                   int articlesSent, int articlesSkipped) {
        BroadcastDoneEvent event = BroadcastDoneEvent.builder()
                .category(category)
                .platform(platform)
                .targetId(targetId)
                .articlesSent(articlesSent)
                .articlesSkipped(articlesSkipped)
                .broadcastAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(KafkaTopics.NEWS_BROADCAST, category, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("[BROADCAST-DONE] Failed to publish for {}/{}: {}", platform, targetId, ex.getMessage());
                    } else {
                        log.debug("[BROADCAST-DONE] {} → {} sent={} skipped={}", platform, targetId, articlesSent, articlesSkipped);
                    }
                });
    }
}
