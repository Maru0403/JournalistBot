package com.maru.journalistbot.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Kafka event: bất kỳ service nào → topic "news.failed" (DLQ)
 *
 * Khi nào publish lên DLQ?
 *   - summarizer-service: Claude + Gemini đều fail, Circuit Breaker mở
 *   - notification-service: gửi Discord/Telegram thất bại sau tất cả retry
 *   - fetcher-service: fetch hoàn toàn không có bài nào (edge case)
 *
 * DLQ consumer (có thể add sau) sẽ:
 *   1. Log để alert (Slack, email)
 *   2. Lưu vào DB để retry manual
 *   3. Gửi metric lên Prometheus
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArticleFailedEvent {

    /** eventId của event gốc gây ra lỗi */
    private String originalEventId;

    /** Service nào publish event này: "fetcher" | "summarizer" | "notification" */
    private String failedService;

    /** Bước nào bị fail: "FETCH" | "SUMMARIZE" | "BROADCAST" */
    private String failedStep;

    /** NewsCategory.name() */
    private String category;

    /** Error message (không bao gồm stack trace đầy đủ để tránh quá lớn) */
    private String errorMessage;

    /** Platform nếu lỗi ở bước broadcast, null ở các bước khác */
    private String platform;

    private LocalDateTime failedAt;
}
