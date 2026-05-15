package com.maru.journalistbot.common.kafka;

/**
 * Kafka topic name constants — shared across all services.
 *
 * Tại sao dùng constants thay vì hardcode string?
 *   - Thay tên topic ở 1 chỗ → cập nhật toàn bộ services
 *   - Không bị lỗi typo khi gõ tay
 *   - IDE có thể tìm kiếm usage dễ dàng
 *
 * Flow:
 *   fetcher-service  → news.fetched     → summarizer-service
 *   summarizer-service → news.summarized → notification-service
 *   notification-service → news.broadcast (confirm log)
 *   bất kỳ service nào → news.failed (DLQ khi xảy ra lỗi không recover được)
 */
public final class KafkaTopics {

    private KafkaTopics() {
        // Utility class — không cho phép instantiate
    }

    /** fetcher-service publish sau khi fetch xong một batch bài viết */
    public static final String NEWS_FETCHED = "news.fetched";

    /** summarizer-service publish sau khi AI tóm tắt xong */
    public static final String NEWS_SUMMARIZED = "news.summarized";

    /** notification-service publish sau khi gửi thành công (confirm log) */
    public static final String NEWS_BROADCAST = "news.broadcast";

    /**
     * Dead Letter Queue — nhận event khi service xử lý thất bại
     * hoặc Circuit Breaker mở sau khi hết retry.
     * Consumer riêng sẽ log, alert, hoặc retry manual.
     */
    public static final String NEWS_FAILED = "news.failed";
}
