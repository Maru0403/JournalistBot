package com.maru.journalistbot.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Platform enum lives in the DOMAIN layer — not infrastructure.
 * This ensures domain ports (NewsMessagePort) can reference it
 * without violating the Dependency Inversion Principle (DIP).
 *
 * Infrastructure adapters (DiscordAdapter, TelegramAdapter, ZaloAdapter)
 * also import from here — they depend inward toward domain, which is correct.
 */
@Getter
@RequiredArgsConstructor
public enum Platform {

    DISCORD("Discord", "🎮"),
    TELEGRAM("Telegram", "✈️"),
    ZALO("Zalo", "💬");

    private final String displayName;
    private final String emoji;
}
