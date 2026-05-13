package com.maru.journalistbot.infrastructure.platform.telegram;

import com.maru.journalistbot.application.command.BotCommandService;
import com.maru.journalistbot.application.command.NewsCommandHandler;
import com.maru.journalistbot.application.subscription.SubscriptionService;
import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.domain.model.NewsCategory;
import com.maru.journalistbot.domain.model.Platform;
import com.maru.journalistbot.domain.port.NewsMessagePort;
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
 * Telegram platform adapter — implements NewsMessagePort (DIP).
 *
 * Phase 3: Full TelegramBots integration.
 *   - Bot initialized lazily via @EventListener(ApplicationReadyEvent).
 *   - If token is absent/placeholder → stub mode (logs only, no crash).
 *   - sendNews() broadcasts to ALL active Telegram subscriptions in DB.
 *   - replyToUser() sends to a specific chatId.
 *
 * Multi-group support:
 *   Each group/private chat subscribed via /start is stored in user_subscriptions.
 *   sendNews() looks up all active TELEGRAM subscriptions and sends to each.
 */
@Component
@Slf4j
public class TelegramAdapter implements NewsMessagePort {

    @Value("${bot.telegram.token:}")
    private String token;

    @Value("${bot.telegram.username:JournalistBot}")
    private String username;

    private final NewsCommandHandler newsCommandHandler;
    private final SubscriptionService subscriptionService;
    private final BotCommandService botCommandService;

    private TelegramBot telegramBot;

    public TelegramAdapter(NewsCommandHandler newsCommandHandler,
                           SubscriptionService subscriptionService,
                           BotCommandService botCommandService) {
        this.newsCommandHandler  = newsCommandHandler;
        this.subscriptionService = subscriptionService;
        this.botCommandService   = botCommandService;
    }

    /**
     * Initialize and register TelegramBot after Spring context is fully ready.
     * Mirrors the JDA pattern in DiscordAdapter.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initTelegram() {
        if (!isTokenValid()) {
            log.warn("[TELEGRAM] Token not configured — running in stub mode. Set bot.telegram.token to enable.");
            return;
        }

        try {
            telegramBot = new TelegramBot(token, username, newsCommandHandler, subscriptionService, botCommandService);

            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(telegramBot);

            // Register slash-command hints in Telegram UI
            telegramBot.registerCommands();

            log.info("[TELEGRAM] Bot '{}' registered and polling for updates", username);
        } catch (TelegramApiException e) {
            log.error("[TELEGRAM] Failed to initialize bot: {}", e.getMessage(), e);
        }
    }

    // ── NewsMessagePort implementation ───────────────────────────────────────

    @Override
    public Platform getPlatform() {
        return Platform.TELEGRAM;
    }

    /**
     * Broadcast to all active Telegram subscriptions in DB.
     * Multi-group: each subscribed chatId receives the message.
     */
    @Override
    public void sendNews(List<NewsArticle> articles, NewsCategory category, String formattedMessage) {
        if (!isConnected()) {
            log.warn("[TELEGRAM] Bot not connected — skipping broadcast for {}", category.getDisplayName());
            return;
        }

        List<String> subscribers = subscriptionService.getActiveSubscribers(Platform.TELEGRAM);
        if (subscribers.isEmpty()) {
            log.info("[TELEGRAM] No active subscribers — skipping broadcast for {}", category.getDisplayName());
            return;
        }

        log.info("[TELEGRAM] Broadcasting {} articles to {} chats for {}",
                articles.size(), subscribers.size(), category.getDisplayName());

        int successCount = 0;
        for (String chatId : subscribers) {
            try {
                telegramBot.sendLongText(chatId, formattedMessage);
                successCount++;
            } catch (Exception e) {
                log.error("[TELEGRAM] Failed to send to chatId={}: {}", chatId, e.getMessage());
            }
        }
        log.info("[TELEGRAM] Broadcast complete: {}/{} chats received news for {}",
                successCount, subscribers.size(), category.getDisplayName());
    }

    /**
     * Reply to a specific chat/user (used by command responses on other platforms
     * that might trigger cross-platform messages, or for direct replies).
     */
    @Override
    public void replyToUser(String chatId, String message) {
        if (!isConnected()) {
            log.warn("[TELEGRAM] Bot not connected — cannot reply to chatId={}", chatId);
            return;
        }
        telegramBot.sendLongText(chatId, message);
    }

    @Override
    public boolean isConnected() {
        return telegramBot != null;
    }

    // ── Private utilities ────────────────────────────────────────────────────

    private boolean isTokenValid() {
        return token != null && !token.isBlank() && !token.startsWith("your-");
    }
}
