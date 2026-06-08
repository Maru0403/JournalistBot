package com.maru.journalistbot.scheduler.controller;

import com.maru.journalistbot.common.model.NewsCategory;
import com.maru.journalistbot.scheduler.publisher.ScheduleTriggerPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoint để trigger fetch on-demand (không chờ Quartz schedule).
 * Dùng cho: admin tool, testing, /news bot command.
 *
 * Gateway route: /scheduler/api/trigger/** → scheduler-service:8084
 */
@RestController
@RequestMapping("/api/trigger")
@RequiredArgsConstructor
@Slf4j
public class SchedulerController {

    private final ScheduleTriggerPublisher triggerPublisher;

    @Value("${bot.schedule.news-fetch-limit:5}")
    private int defaultFetchLimit;

    /**
     * Trigger fetch cho 1 category cụ thể.
     * POST /api/trigger/{category}?limit=5
     */
    @PostMapping("/{category}")
    public ResponseEntity<Map<String, String>> triggerCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "5") int limit) {

        NewsCategory newsCategory;
        try {
            newsCategory = NewsCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "Unknown category: " + category + ". Valid: AI, PROGRAMMING, GAME_DEV")
            );
        }

        int fetchLimit = Math.min(limit, 20); // cap ở 20
        triggerPublisher.publishTrigger(newsCategory, "ON_DEMAND", fetchLimit);
        log.info("[SCHEDULER-REST] On-demand trigger — category={}, limit={}", newsCategory, fetchLimit);

        return ResponseEntity.ok(Map.of(
            "status", "triggered",
            "category", newsCategory.name(),
            "fetchLimit", String.valueOf(fetchLimit),
            "message", "Trigger published to Kafka. fetcher-service sẽ xử lý."
        ));
    }

    /**
     * Trigger tất cả categories cùng lúc.
     * POST /api/trigger/all
     */
    @PostMapping("/all")
    public ResponseEntity<Map<String, String>> triggerAll(
            @RequestParam(defaultValue = "5") int limit) {

        int fetchLimit = Math.min(limit, 20);
        for (NewsCategory category : NewsCategory.values()) {
            triggerPublisher.publishTrigger(category, "ON_DEMAND", fetchLimit);
        }
        log.info("[SCHEDULER-REST] On-demand trigger all categories, limit={}", fetchLimit);

        return ResponseEntity.ok(Map.of(
            "status", "triggered",
            "categories", "AI, PROGRAMMING, GAME_DEV",
            "fetchLimit", String.valueOf(fetchLimit)
        ));
    }
}
