package com.maru.journalistbot.common.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Platform enum — shared across all services via common module.
 *
 * Được dùng trong:
 *   - notification-service: biết gửi đến platform nào
 *   - common events: BroadcastDoneEvent, ArticleFailedEvent ghi nhận platform
 */
@Getter
@RequiredArgsConstructor
public enum Platform {

    DISCORD("Discord", "🎮"),
    TELEGRAM("Telegram", "✈️");

    private final String displayName;
    private final String emoji;
}
