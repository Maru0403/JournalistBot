package com.maru.journalistbot.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Kafka event: summarizer-service → topic "news.summarized" → notification-service
 *
 * Chứa danh sách bài viết gốc + AI summary đã tạo.
 * notification-service dùng summary để format message gửi Discord/Telegram.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArticleSummarizedEvent {

    /** Giữ nguyên eventId từ ArticleFetchedEvent để trace end-to-end */
    private String eventId;

    /** NewsCategory.name() */
    private String category;

    /** Danh sách bài viết gốc (pass-through từ ArticleFetchedEvent) */
    private List<ArticleItemDto> articles;

    /**
     * AI-generated summary của toàn bộ batch.
     * null nếu AI không available → notification-service dùng fallback format.
     */
    private String summary;

    /** "claude" | "gemini" | "none" — để debug biết AI nào đã summarize */
    private String aiProvider;

    private LocalDateTime summarizedAt;
}
