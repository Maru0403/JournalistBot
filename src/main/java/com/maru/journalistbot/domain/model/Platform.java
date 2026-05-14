package com.maru.journalistbot.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Platform enum lives in the DOMAIN layer — not infrastructure.
 * This ensures domain ports (NewsMessagePort) can reference it
 * without violating the Dependency Inversion Principle (DIP).
 *
 * Infrastructure adapters (DiscordAdapter, TelegramAdapter)
 * also import from here — they depend inward toward domain, which is correct.
 *
 * Phase 4: Zalo removed — no OA credentials available, deferred to future phase.
 */
@Getter
@RequiredArgsConstructor
public enum Platform {

    DISCORD("Discord", "🎮"),
    TELEGRAM("Telegram", "✈️");

    private final String displayName;
    private final String emoji;
}
