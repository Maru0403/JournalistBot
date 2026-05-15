package com.maru.journalistbot.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Kafka event: notification-service → topic "news.broadcast"
 *
 * Confirm log sau khi gửi thành công đến một platform.
 * Dùng cho: audit trail, monitoring, future analytics.
 *
 * Một batch summarized → nhiều BroadcastDoneEvent (1 per platform per group).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BroadcastDoneEvent {

    private String eventId;
    private String category;

    /** Platform.name() — DISCORD | TELEGRAM */
    private String platform;

    /** Channel/group ID đã nhận được tin */
    private String targetId;

    /** Số bài viết đã gửi thực tế (sau dedup) */
    private int articlesSent;

    /** Số bài bị skip (đã gửi trước đó — dedup) */
    private int articlesSkipped;

    private LocalDateTime broadcastAt;
}
