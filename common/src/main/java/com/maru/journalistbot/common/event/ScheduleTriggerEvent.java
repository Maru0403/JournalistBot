package com.maru.journalistbot.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Phase 6 — Kafka event: scheduler-service → topic "news.schedule.trigger" → fetcher-service
 *
 * Tại sao tách scheduler ra service riêng?
 *   - fetcher-service trở thành pure "fetch engine" — không biết về scheduling
 *   - scheduler-service có thể thay đổi cron/interval mà không ảnh hưởng fetcher
 *   - Khi scale fetcher-service (nhiều pod), chỉ 1 trigger được gửi → không fetch trùng
 *   - Quartz JDBC state tách vào postgres-scheduler riêng (DB per service)
 *
 * triggeredBy:
 *   "SCHEDULER" — Quartz job tự động
 *   "ON_DEMAND"  — user gọi REST /api/trigger/{category}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleTriggerEvent {

    /** UUID — để idempotency và tracing */
    private String eventId;

    /** NewsCategory.name() — AI, PROGRAMMING, GAME_DEV */
    private String category;

    /** "SCHEDULER" hoặc "ON_DEMAND" */
    private String triggeredBy;

    /** Số bài tối đa cần fetch */
    private int fetchLimit;

    /** Thời điểm trigger được sinh ra */
    private LocalDateTime triggeredAt;

    /** Scheduler instance nào sinh ra trigger (debug/tracing) */
    private String sourceInstance;
}
