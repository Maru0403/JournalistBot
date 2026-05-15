package com.maru.journalistbot.notification.platform.telegram;

import com.maru.journalistbot.common.model.Platform;
import com.maru.journalistbot.notification.persistence.jpa.BotCommand;
import com.maru.journalistbot.notification.service.BotCommandService;
import com.maru.journalistbot.notification.service.NewsCommandService;
import com.maru.journalistbot.notification.service.SubscriptionService;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

/**
 * Core Telegram bot — Phase 5 notification-service version.
 *
 * Phase 5 changes vs. monolith:
 *   - Uses NewsCommandService (REST calls to fetcher + summarizer)
 *     instead of NewsCommandHandler (which needed local NewsServiceRegistry + AISummarizerService)
 *   - All other logic identical to monolith TelegramBot
 *
 * NOT a Spring @Component — created manually in TelegramAdapter via ApplicationReadyEvent.
 */
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final NewsCommandService newsCommandService;
    private final SubscriptionService subscriptionService;
    private final BotCommandService botCommandService;

    public TelegramBot(String botToken,
                       String botUsername,
                       NewsCommandService newsCommandService,
                       SubscriptionService subscriptionService,
                       BotCommandService botCommandService) {
        super(botToken);
        this.botUsername        = botUsername;
        this.newsCommandService = newsCommandService;
        this.subscriptionService = subscriptionService;
        this.botCommandService  = botCommandService;
    }

    @Override
    public String getBotUsername() { return botUsername; }

    // ── Incoming update handler ──────────────────────────────────────────────

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        Message message = update.getMessage();
        String text     = message.getText().trim();
        String chatId   = message.getChatId().toString();
        String chatName = resolveChatName(message);

        if (!text.startsWith("/")) return;

        String[] parts   = text.split("\\s+", 2);
        String rawCmd    = parts[0].split("@")[0].toLowerCase();
        String argument  = parts.length > 1 ? parts[1].trim() : "";

        log.debug("[TELEGRAM] {} from chatId={} ({})", rawCmd, chatId, chatName);

        switch (rawCmd) {
            case "/start"      -> handleStart(chatId, chatName);
            case "/stop"       -> handleStop(chatId);
            case "/news"       -> handleNews(chatId, argument);
            case "/categories" -> handleCategories(chatId);
            case "/status"     -> handleStatus(chatId);
            case "/help"       -> handleHelp(chatId);
            default            -> sendText(chatId, "❓ Lệnh không được nhận diện. Gõ /help để xem danh sách.");
        }
    }

    // ── Command handlers ─────────────────────────────────────────────────────

    private void handleStart(String chatId, String chatName) {
        boolean newSub = subscriptionService.subscribe(chatId, Platform.TELEGRAM, chatName);
        if (newSub) {
            sendText(chatId, """
                    ✅ *Đã đăng ký nhận tin tức tự động!*

                    Bot sẽ tự động gửi tin tức AI, lập trình và game dev theo lịch.

                    📌 Dùng /stop để hủy đăng ký.
                    📌 Dùng /news <từ khóa> để lấy tin ngay.
                    """);
        } else {
            sendText(chatId, "ℹ️ Chat này đã được đăng ký rồi! Dùng /status để kiểm tra.");
        }
    }

    private void handleStop(String chatId) {
        boolean removed = subscriptionService.unsubscribe(chatId, Platform.TELEGRAM);
        if (removed) {
            sendText(chatId, "✅ Đã hủy đăng ký. Dùng /start để đăng ký lại.");
        } else {
            sendText(chatId, "ℹ️ Chat này chưa được đăng ký. Dùng /start để bắt đầu.");
        }
    }

    private void handleNews(String chatId, String keyword) {
        sendText(chatId, "⏳ Đang lấy tin tức, vui lòng đợi...");
        try {
            String response = newsCommandService.handle(keyword, chatId);
            sendLongText(chatId, response);
        } catch (Exception e) {
            log.error("[TELEGRAM] Error handling /news '{}': {}", keyword, e.getMessage(), e);
            sendText(chatId, "❌ Đã xảy ra lỗi khi lấy tin tức. Thử lại sau nhé!");
        }
    }

    private void handleCategories(String chatId) {
        String response = newsCommandService.handle("", chatId);
        sendLongText(chatId, response);
    }

    private void handleStatus(String chatId) {
        boolean subscribed = subscriptionService.isSubscribed(chatId, Platform.TELEGRAM);
        if (subscribed) {
            sendText(chatId, "✅ *Bot đang hoạt động* — Chat này đang nhận tin tức tự động.\nDùng /stop để hủy.");
        } else {
            sendText(chatId, "❌ *Chưa đăng ký* — Dùng /start để bắt đầu nhận tin.");
        }
    }

    private void handleHelp(String chatId) {
        String helpText = botCommandService.buildHelpMessage(Platform.TELEGRAM, "telegram");
        sendLongText(chatId, helpText);
    }

    // ── Sending utilities ─────────────────────────────────────────────────────

    public void sendText(String chatId, String text) {
        SendMessage msg = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .build();
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            // Retry without Markdown in case of parse error
            log.warn("[TELEGRAM] Markdown send failed for chatId={}, retrying plain: {}", chatId, e.getMessage());
            try {
                execute(SendMessage.builder().chatId(chatId).text(text).build());
            } catch (TelegramApiException ex) {
                log.error("[TELEGRAM] Failed to send to chatId={}: {}", chatId, ex.getMessage());
            }
        }
    }

    /** Split at Telegram 4096-char limit */
    public void sendLongText(String chatId, String text) {
        final int maxLen = 4096;
        if (text.length() <= maxLen) {
            sendText(chatId, text);
            return;
        }
        int splitAt = text.lastIndexOf("\n\n", maxLen);
        if (splitAt <= 0) splitAt = maxLen;
        sendText(chatId, text.substring(0, splitAt).trim());
        sendLongText(chatId, text.substring(splitAt).trim());
    }

    /** Register bot commands in Telegram UI (autocomplete hints) */
    public void registerCommands() {
        try {
            List<org.telegram.telegrambots.meta.api.objects.commands.BotCommand> tgCommands;
            try {
                List<BotCommand> dbCommands = botCommandService.getCommandsForPlatform(Platform.TELEGRAM);
                if (!dbCommands.isEmpty()) {
                    tgCommands = dbCommands.stream()
                            .map(c -> new org.telegram.telegrambots.meta.api.objects.commands.BotCommand(
                                    c.getName(), c.getDescription()))
                            .toList();
                } else {
                    tgCommands = getHardcodedDefaultCommands();
                    log.warn("[TELEGRAM] No commands in DB — using defaults");
                }
            } catch (Exception e) {
                log.warn("[TELEGRAM] Could not load commands from DB: {}", e.getMessage());
                tgCommands = getHardcodedDefaultCommands();
            }
            execute(SetMyCommands.builder()
                    .commands(tgCommands)
                    .scope(new BotCommandScopeDefault())
                    .build());
            log.info("[TELEGRAM] {} commands registered successfully", tgCommands.size());
        } catch (TelegramApiException e) {
            log.warn("[TELEGRAM] Failed to register commands: {}", e.getMessage());
        }
    }

    // ── Private utilities ─────────────────────────────────────────────────────

    private List<org.telegram.telegrambots.meta.api.objects.commands.BotCommand> getHardcodedDefaultCommands() {
        return List.of(
                new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("start",      "Đăng ký nhận tin tức tự động"),
                new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("stop",       "Hủy đăng ký nhận tin"),
                new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("news",       "Lấy tin: /news <từ khóa>"),
                new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("categories", "Xem chủ đề hỗ trợ"),
                new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("status",     "Kiểm tra trạng thái đăng ký"),
                new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("help",       "Hướng dẫn sử dụng")
        );
    }

    private String resolveChatName(Message message) {
        if (message.getChat().isGroupChat() || message.getChat().isSuperGroupChat()) {
            return message.getChat().getTitle();
        }
        return message.getFrom().getFirstName();
    }
}
