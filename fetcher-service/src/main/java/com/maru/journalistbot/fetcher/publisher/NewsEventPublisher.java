package com.maru.journalistbot.fetcher.publisher;

import com.maru.journalistbot.common.event.ArticleFetchedEvent;
import com.maru.journalistbot.common.event.ArticleItemDto;
import com.maru.journalistbot.common.kafka.KafkaTopics;
import com.maru.journalistbot.fetcher.domain.NewsArticle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka producer — publish ArticleFetchedEvent lên topic "news.fetched".
 *
 * Key = category name → Kafka routing bài viết cùng category vào cùng partition.
 * Đảm bảo summarizer-service consume đúng thứ tự trong một category.
 *
 * Error handling:
 *   - Send là async (non-blocking) — không làm chậm Quartz job
 *   - Lỗi send được log nhưng không crash job — job vẫn chạy cycle tiếp theo
 *   - Spring Kafka auto-retry theo spring.kafka.producer.retries config
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NewsEventPublisher {

    private final KafkaTemplate<String, ArticleFetchedEvent> kafkaTemplate;

    /**
     * Convert List<NewsArticle> → ArticleFetchedEvent → publish lên Kafka.
     *
     * @param articles  danh sách bài viết đã fetch
     * @param category  NewsCategory.name() — dùng làm Kafka message key
     */
    public void publish(List<NewsArticle> articles, String category) {
        if (articles == null || articles.isEmpty()) {
            log.debug("[PUBLISHER] No articles to publish for category={}", category);
            return;
        }

        ArticleFetchedEvent event = buildEvent(articles, category);

        CompletableFuture<SendResult<String, ArticleFetchedEvent>> future =
                kafkaTemplate.send(KafkaTopics.NEWS_FETCHED, category, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                // Log lỗi nhưng không throw — job tiếp tục chạy cycle sau
                log.error("[PUBLISHER] Failed to publish event for category={}: {}",
                        category, ex.getMessage());
            } else {
                log.info("[PUBLISHER] Published {} articles for category={} → partition={}, offset={}",
                        articles.size(),
                        category,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    private ArticleFetchedEvent buildEvent(List<NewsArticle> articles, String category) {
        List<ArticleItemDto> dtos = articles.stream()
                .map(a -> ArticleItemDto.builder()
                        .title(a.getTitle())
                        .url(a.getUrl())
                        .description(a.getDescription())
                        .sourceName(a.getSourceName())
                        .publishedAt(a.getPublishedAt())
                        .build())
                .toList();

        return ArticleFetchedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .category(category)
                .articles(dtos)
                .fetchedAt(LocalDateTime.now())
                .sourceInstance(System.getenv().getOrDefault("HOSTNAME", "fetcher-local"))
                .build();
    }
}
