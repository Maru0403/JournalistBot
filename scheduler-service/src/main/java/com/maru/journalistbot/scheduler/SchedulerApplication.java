package com.maru.journalistbot.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Phase 6 — Scheduler Service
 *
 * Vai trò: Quartz scheduler chạy định kỳ, publish ScheduleTriggerEvent → Kafka
 * → fetcher-service consume và thực hiện fetch news.
 *
 * Tách khỏi fetcher-service vì:
 *   - Separation of concerns: "khi nào fetch" tách khỏi "fetch như thế nào"
 *   - Quartz JDBC state lưu vào postgres-scheduler riêng (DB per service)
 *   - Khi scale fetcher-service nhiều pod, chỉ 1 trigger/cycle được gửi
 *   - Có thể thay đổi scheduling logic mà không deploy lại fetcher-service
 */
@SpringBootApplication
public class SchedulerApplication {
    public static void main(String[] args) {
        SpringApplication.run(SchedulerApplication.class, args);
    }
}
