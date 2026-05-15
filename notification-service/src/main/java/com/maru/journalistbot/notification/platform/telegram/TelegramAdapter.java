package com.maru.journalistbot.notification.platform.telegram;

import com.maru.journalistbot.common.event.ArticleItemDto;
import com.maru.journalistbot.common.model.Platform;
import com.maru.journalistbot.notification.platform.NewsMessagePort;
import com.maru.journalistbot.notification.service.BotCommandService;
import com.maru.journalistbot.notification.service.NewsCommandService;
import com.maru.journalistbot.notification.service.SubscriptionService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.List;

/**
 * Telegram platform adapter — Phase 5 notification-service version.
 *
 * Phase 5 changes vs. monolith:
 *   - sendNews() receives List<ArticleItemDto> (from common) + pre-summarized message
 *   - Uses NewsCommandService instead of NewsCommandHandler for /news command
 *
 * Multi-group support: each subscribed chatId receives the message independently.
 * @CircuitBreaker("telegram"): if Telegram API fails repeatedly → fallback
 */
@Component
@Slf4j
public class TelegramAdapter implements NewsMessagePort {

    @Value("${bot.telegram.token:}")
    private String token;

    @Value("${bot.telegram.username:JournalistBot}")
    private String username;

    private final NewsCommandService newsCommandService;
    private final SubscriptionService subscriptionService;
    private final BotCommandService botCommandService;

    private TelegramBot telegramBot;

    public TelegramAdapter(NewsCommandService newsCommandService,
                           SubscriptionService subscriptionService,
                           BotCommandService botCommandService) {
        this.newsCommandService = newsCommandService;
        this.subscriptionService = subscriptionService;
        this.botCommandService  = botCommandService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initTelegram() {
        if (!isTokenValid()) {
            log.warn("[TELEGRAM] Token not configured — running in stub mode. Set TELEGRAM_BOT_TOKEN to enable.");
            return;
        }
        try {
            telegramBot = new TelegramBot(token, username, newsCommandService, subscriptionService, botCommandService);
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(telegramBot);
            telegramBot.registerCommands();
            log.info("[TELEGRAM] Bot '{}' registered and polling", username);
        } catch (TelegramApiException e) {
            log.error("[TELEGRAM] Failed to initialize: {}", e.getMessage(), e);
        }
    }

    // ── NewsMessagePort ───────────────────────────────────────────────────────

    @Override
    public Platform getPlatform() { return Platform.TELEGRAM; }

    @Override
    @CircuitBreaker(name = "telegram", fallbackMethod = "fallbackSendNews")
    public void sendNews(List<ArticleItemDto> articles, String category, String formattedMessage) {
        if (!isConnected()) {
            log.warn("[TELEGRAM] Not connected — skipping broadcast for {}", category);
            return;
        }

        List<String> subscribers = subscriptionService.getActiveSubscribers(Platform.TELEGRAM);
        if (subscribers.isEmpty()) {
            log.info("[TELEGRAM] No active subscribers — skipping broadcast for {}", category);
            return;
        }

        log.info("[TELEGRAM] Broadcasting {} articles to {} chats for {}",
                articles.size(), subscribers.size(), category);

        int successCount = 0;
        for (String chatId : subscribers) {
            try {
                telegramBot.sendLongText(chatId, formattedMessage);
                successCount++;
            } catch (Exception e) {
                log.error("[TELEGRAM] Failed to send to chatId={}: {}", chatId, e.getMessage());
            }
        }
        log.info("[TELEGRAM] Broadcast complete: {}/{} chats for {}", successCount, subscribers.size(), category);
    }

    @Override
    public void replyToUser(String chatId, String message) {
        if (!isConnected()) {
            log.warn("[TELEGRAM] Not connected — cannot reply to chatId={}", chatId);
            return;
        }
        telegramBot.sendLongText(chatId, message);
    }

    @Override
    public boolean isConnected() { return telegramBot != null; }

    // ── Private ───────────────────────────────────────────────────────────────

    private void fallbackSendNews(List<ArticleItemDto> articles, String category,
                                  String formattedMessage, Throwable ex) {
        log.warn("[TELEGRAM] Circuit OPEN for {} — skipping. Reason: {}", category, ex.getMessage());
    }

    private boolean isTokenValid() {
        return token != null && !token.isBlank() && !token.startsWith("your-");
    }
}
