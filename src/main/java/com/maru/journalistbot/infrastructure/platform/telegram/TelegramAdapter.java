package com.maru.journalistbot.infrastructure.platform.telegram;

import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.domain.model.NewsCategory;
import com.maru.journalistbot.domain.port.NewsMessagePort;
import com.maru.journalistbot.domain.model.Platform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Telegram platform adapter — implements NewsMessagePort (DIP).
 *
 * Phase 1 — Stub: logs messages to console.
 * Phase 3 — Full: integrates TelegramBots library.
 */
@Component
@Slf4j
public class TelegramAdapter implements NewsMessagePort {

    @Value("${bot.telegram.token:}")
    private String token;

    @Override
    public Platform getPlatform() {
        return Platform.TELEGRAM;
    }

    @Override
    public void sendNews(List<NewsArticle> articles, NewsCategory category, String formattedMessage) {
        if (!isConnected()) {
            log.warn("[TELEGRAM] Not connected — skipping send. Set bot.telegram.token in application.yml");
            return;
        }
        // TODO Phase 3: send to subscribed chats/groups
        log.info("[TELEGRAM] [STUB] Would send {} articles: {}", articles.size(), category.getDisplayName());
    }

    @Override
    public void replyToUser(String chatId, String message) {
        // TODO Phase 3: sendMessage via TelegramBots API
        log.info("[TELEGRAM] [STUB] Would reply to chatId={}", chatId);
    }

    @Override
    public boolean isConnected() {
        return token != null && !token.isBlank() && !token.startsWith("your-");
    }
}
