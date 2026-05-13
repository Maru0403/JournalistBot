package com.maru.journalistbot.infrastructure.platform.zalo;

import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.domain.model.NewsCategory;
import com.maru.journalistbot.domain.port.NewsMessagePort;
import com.maru.journalistbot.domain.model.Platform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Zalo Official Account (OA) adapter — implements NewsMessagePort (DIP).
 *
 * Phase 3: Zalo integration is temporarily DISABLED.
 *   - isConnected() always returns false → BroadcastService skips this adapter.
 *   - No crash, no log spam — silently excluded from broadcasts.
 *   - Full implementation planned for a future phase when OA credentials are available.
 *
 * Future implementation notes:
 *   - Broadcast to followers: POST https://openapi.zalo.me/v2.0/oa/broadcast/morethan200
 *   - Send to specific user: POST https://openapi.zalo.me/v2.0/oa/message
 *   - Token refresh: Zalo OA access tokens expire; need refresh via app-secret
 *   - Webhook: POST /webhook/zalo — verify via SHA256(appSecret + data)
 */
@Component
@Slf4j
public class ZaloAdapter implements NewsMessagePort {

    @Override
    public Platform getPlatform() {
        return Platform.ZALO;
    }

    @Override
    public void sendNews(List<NewsArticle> articles, NewsCategory category, String formattedMessage) {
        // Disabled in Phase 3 — isConnected() = false prevents this from being called
        log.debug("[ZALO] Disabled — skipping broadcast for {}", category.getDisplayName());
    }

    @Override
    public void replyToUser(String chatId, String message) {
        // Disabled in Phase 3
        log.debug("[ZALO] Disabled — skipping reply to userId={}", chatId);
    }

    /**
     * Zalo integration is disabled in this phase.
     * Change to return true and implement the above methods when OA credentials are ready.
     */
    @Override
    public boolean isConnected() {
        return false;
    }
}
