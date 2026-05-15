package com.maru.journalistbot.common.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * NewsCategory enum — shared across all services via common module.
 *
 * Được dùng trong:
 *   - fetcher-service: phân loại bài viết khi fetch
 *   - summarizer-service: biết đang tóm tắt category nào
 *   - notification-service: format message theo category
 *
 * Lưu ý khi truyền qua Kafka:
 *   Serialize thành String (dùng .name()) thay vì enum trực tiếp
 *   để tránh lỗi deserialization khi version enum thay đổi.
 *   → Xem ArticleFetchedEvent.category field (String, không phải enum)
 */
@Getter
@RequiredArgsConstructor
public enum NewsCategory {

    AI("🤖 AI & Machine Learning",
            "Tin tức mới nhất về AI, LLM, Claude, Gemini, Codex..."),

    PROGRAMMING("💻 Programming Languages",
            "Tin tức mới nhất về các ngôn ngữ lập trình, frameworks, tools..."),

    GAME_DEV("🎮 Game Development",
            "Tin tức mới nhất về phát triển game, Unity, Unreal Engine...");

    private final String displayName;
    private final String description;
}
