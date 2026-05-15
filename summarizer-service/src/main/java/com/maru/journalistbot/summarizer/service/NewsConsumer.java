package com.maru.journalistbot.summarizer.service;

import com.maru.journalistbot.common.event.ArticleFetchedEvent;
import com.maru.journalistbot.common.event.ArticleFailedEvent;
import com.maru.journalistbot.common.event.ArticleSummarizedEvent;
import com.maru.journalistbot.common.kafka.KafkaTopics;
import com.maru.journalistbot.common.model.NewsCategory;
import com.maru.journalistbot.summarizer.publisher.FailedEventPublisher;
import com.maru.journalistbot.summarizer.publisher.SummarizedEventPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Kafka consumer — nhận ArticleFetchedEvent từ "news.fetched".
 *
 * Flow:
 *   1. Consume ArticleFetchedEvent
 *   2. Validate (không xử lý event rỗng)
 *   3. Gọi AISummarizerService (Claude → Gemini → plain text)
 *   4. Publish ArticleSummarizedEvent → "news.summarized"
 *   5. Manual ACK sau khi publish thành công
 *   6. Nếu exception không recover → publish ArticleFailedEvent → DLQ "news.failed"
 *
 * Manual ACK (MANUAL_IMMEDIATE):
 *   Chỉ commit offset sau khi đã publish "news.summarized" thành công.
 *   Nếu service crash giữa chừng → Kafka re-deliver event → không mất tin.
 *
 * groupId = "summarizer-group":
 *   Mỗi service có groupId riêng — scale nhiều pod trong cùng group,
 *   Kafka tự phân chia partition cho từng pod (chỉ 1 pod xử lý 1 partition).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsConsumer {

    private final AISummarizerService summarizerService;
    private final SummarizedEventPublisher summarizedPublisher;
    private final FailedEventPublisher failedPublisher;
    private final MeterRegistry meterRegistry;

    private Counter successCounter;
    private Counter failedCounter;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("summarizer.events.success")
                .description("Events summarized successfully")
                .register(meterRegistry);
        failedCounter = Counter.builder("summarizer.events.failed")
                .description("Events failed to summarize")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = KafkaTopics.NEWS_FETCHED,
            groupId = "summarizer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, ArticleFetchedEvent> record, Acknowledgment ack) {
        ArticleFetchedEvent event = record.value();

        if (event == null || event.getArticles() == null || event.getArticles().isEmpty()) {
            log.warn("[CONSUMER] Empty event received — partition={}, offset={}", record.partition(), record.offset());
            ack.acknowledge(); // ACK để không stuck ở event rỗng
            return;
        }

        log.info("[CONSUMER] Received: category={}, articles={}, eventId={}",
                event.getCategory(), event.getArticles().size(), event.getEventId());

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            processEvent(event);
            ack.acknowledge(); // Commit offset chỉ sau khi xử lý và publish thành công
            successCounter.increment();
            log.info("[CONSUMER] ACK: eventId={}", event.getEventId());
        } catch (Exception ex) {
            // Lỗi không recover được → publish DLQ → ACK (không re-consume vô hạn)
            log.error("[CONSUMER] Unrecoverable error for eventId={}: {}", event.getEventId(), ex.getMessage(), ex);
            publishToDlq(event, ex);
            ack.acknowledge();
            failedCounter.increment();
        } finally {
            sample.stop(Timer.builder("summarizer.processing.duration")
                    .tag("category", event.getCategory() != null ? event.getCategory() : "unknown")
                    .register(meterRegistry));
        }
    }

    private void processEvent(ArticleFetchedEvent event) {
        // Lấy displayName từ NewsCategory enum (vd: "🤖 AI & Machine Learning")
        String topicDisplayName = resolveDisplayName(event.getCategory());

        AISummarizerService.SummaryResult result =
                summarizerService.summarize(event.getArticles(), topicDisplayName);

        ArticleSummarizedEvent summarized = ArticleSummarizedEvent.builder()
                .eventId(event.getEventId())
                .category(event.getCategory())
                .articles(event.getArticles())
                .summary(result.summary())
                .aiProvider(result.providerUsed())
                .summarizedAt(LocalDateTime.now())
                .build();

        summarizedPublisher.publish(summarized);
        log.info("[CONSUMER] Published news.summarized: category={}, provider={}, eventId={}",
                event.getCategory(), result.providerUsed(), event.getEventId());
    }

    private void publishToDlq(ArticleFetchedEvent event, Exception ex) {
        try {
            ArticleFailedEvent failedEvent = ArticleFailedEvent.builder()
                    .originalEventId(event.getEventId())
                    .failedService("summarizer")
                    .failedStep("SUMMARIZE")
                    .category(event.getCategory())
                    .errorMessage(ex.getMessage())
                    .failedAt(LocalDateTime.now())
                    .build();
            failedPublisher.publish(failedEvent);
        } catch (Exception dlqEx) {
            log.error("[CONSUMER] Failed to publish DLQ event: {}", dlqEx.getMessage());
        }
    }

    private String resolveDisplayName(String categoryName) {
        if (categoryName == null) return "Tin tức";
        try {
            return NewsCategory.valueOf(categoryName).getDisplayName();
        } catch (IllegalArgumentException e) {
            return categoryName;
        }
    }
}
