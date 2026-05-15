package com.maru.journalistbot.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Kafka event: fetcher-service → topic "news.fetched" → summarizer-service
 *
 * Một event = một batch bài viết của một category trong một lần chạy job.
 * Ví dụ: Quartz job chạy cho NewsCategory.AI → 5 bài → 1 event duy nhất.
 *
 * Tại sao dùng batch thay vì event-per-article?
 *   - AI summarizer tóm tắt theo batch (prompt gộp nhiều bài cùng lúc)
 *   - Giảm số lần gọi AI API (tiết kiệm cost)
 *   - Phù hợp với flow hiện tại của BroadcastService
 *
 * category là String (không phải enum) vì:
 *   - Tránh lỗi deserialization nếu sau này thêm/đổi tên category
 *   - Dùng NewsCategory.name() khi publish, NewsCategory.valueOf() khi consume
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArticleFetchedEvent {

    /** UUID — dùng để idempotency check ở consumer nếu cần */
    private String eventId;

    /** NewsCategory.name() — AI, PROGRAMMING, GAME_DEV */
    private String category;

    /** Danh sách bài viết đã fetch (chưa được tóm tắt) */
    private List<ArticleItemDto> articles;

    /** Thời điểm fetcher chạy xong */
    private LocalDateTime fetchedAt;

    /** Service instance nào publish (debug/tracing) */
    private String sourceInstance;
}
