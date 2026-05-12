package com.maru.journalistbot.infrastructure.platform.zalo;

import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.domain.model.NewsCategory;
import com.maru.journalistbot.domain.port.NewsMessagePort;
import com.maru.journalistbot.domain.model.Platform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Zalo Official Account (OA) adapter — implements NewsMessagePort (DIP).
 *
 * Phase 1 — Stub: logs messages to console.
 * Phase 3 — Full: integrates Zalo OA REST API via WebClient.
 */
@Component
@Slf4j
public class ZaloAdapter implements NewsMessagePort {

    @Value("${bot.zalo.oa-token:}")
    private String oaToken;

    @Override
    public Platform getPlatform() {
        return Platform.ZALO;
    }

    @Override
    public void sendNews(List<NewsArticle> articles, NewsCategory category, String formattedMessage) {
        if (!isConnected()) {
            log.warn("[ZALO] Not connected — skipping send. Configure Zalo OA credentials in application.yml");
            return;
        }
        // TODO Phase 3: POST https://openapi.zalo.me/v2.0/oa/message
        log.info("[ZALO] [STUB] Would broadcast {} articles: {}", articles.size(), category.getDisplayName());
    }

    @Override
    public void replyToUser(String chatId, String message) {
        // TODO Phase 3: send individual message
        log.info("[ZALO] [STUB] Would reply to userId={}", chatId);
    }

    @Override
    public boolean isConnected() {
        return oaToken != null && !oaToken.isBlank() && !oaToken.startsWith("your-");
    }
}
