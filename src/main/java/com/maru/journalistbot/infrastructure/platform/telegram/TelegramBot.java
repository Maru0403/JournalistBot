package com.maru.journalistbot.infrastructure.platform.telegram;

import com.maru.journalistbot.application.command.BotCommandService;
import com.maru.journalistbot.application.command.NewsCommandHandler;
import com.maru.journalistbot.application.subscription.SubscriptionService;
import com.maru.journalistbot.domain.model.Platform;
import com.maru.journalistbot.infrastructure.persistence.jpa.BotCommand;
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
 * Core Telegram bot — extends TelegramLongPollingBot to receive and handle updates.
 *
 * NOT a Spring @Component itself — created and registered manually in TelegramAdapter
 * via ApplicationReadyEvent, same pattern as JDA in DiscordAdapter.
 *
 * Commands handled:
 *   /start    — subscribe current chat/group to receive auto-broadcast
 *   /stop     — unsubscribe
 *   /news [keyword] — fetch latest news on demand
 *   /categories     — list supported topics
 *   /status   — show subscription status for this chat
 *
 * Multi-group support: each group/private chat has its own chatId.
 * Subscriptions are persisted in PostgreSQL via SubscriptionService.
 */
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final NewsCommandHandler newsCommandHandler;
    private final SubscriptionService subscriptionService;
    private final BotCommandService botCommandService;

    public TelegramBot(String botToken,
                       String botUsername,
                       NewsCommandHandler newsCommandHandler,
                       SubscriptionService subscriptionService,
                       BotCommandService botCommandService) {
        super(botToken);
        this.botUsername         = botUsername;
        this.newsCommandHandler  = newsCommandHandler;
        this.subscriptionService = subscriptionService;
        this.botCommandService   = botCommandService;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    // ── Incoming update handler ──────────────────────────────────────────────

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        Message message = update.getMessage();
        String text     = message.getText().trim();
        String chatId   = message.getChatId().toString();
        String chatName = resolveChatName(message);

        log.debug("[TELEGRAM] Received '{}' from chatId={} ({})", text, chatId, chatName);

        // Commands start with '/' — strip bot mention if in group: /cmd@BotName → /cmd
        if (!text.startsWith("/")) return;
        String[] parts   = text.split("\\s+", 2);
        String rawCmd    = parts[0].split("@")[0].toLowerCase(); // strip @botname
        String argument  = parts.length > 1 ? parts[1].trim() : "";

        switch (rawCmd) {
            case "/start"      -> handleStart(chatId, chatName);
            case "/stop"       -> handleStop(chatId);
            case "/news"       -> handleNews(chatId, argument, chatId);
            case "/categories" -> handleCategories(chatId);
            case "/status"     -> handleStatus(chatId);
            case "/help"       -> handleHelp(chatId);
            default            -> sendText(chatId, "❓ Lệnh không được nhận diện. Gõ /help để xem danh sách lệnh.");
        }
    }

    // ── Command handlers ─────────────────────────────────────────────────────

    private void handleStart(String chatId, String chatName) {
        boolean newSub = subscriptionService.subscribe(chatId, Platform.TELEGRAM, chatName);
        if (newSub) {
            sendText(chatId, """
                    ✅ *Đã đăng ký nhận tin tức tự động!*

                    Bot sẽ tự động gửi tin tức AI, lập trình và game dev theo lịch.

                    📌 Dùng /stop để hủy đăng ký bất cứ lúc nào.
                    📌 Dùng /news <từ khóa> để lấy tin ngay lập tức.
                    """);
        } else {
            sendText(chatId, "ℹ️ Chat này đã được đăng ký rồi! Dùng /status để kiểm tra.");
        }
    }

    private void handleStop(String chatId) {
        boolean removed = subscriptionService.unsubscribe(chatId, Platform.TELEGRAM);
        if (removed) {
            sendText(chatId, "✅ Đã hủy đăng ký. Bạn sẽ không nhận tin tự động nữa.\nDùng /start để đăng ký lại.");
        } else {
            sendText(chatId, "ℹ️ Chat này chưa được đăng ký. Dùng /start để bắt đầu nhận tin.");
        }
    }

    private void handleNews(String chatId, String keyword, String requesterId) {
        sendText(chatId, "⏳ Đang lấy tin tức, vui lòng đợi...");
        try {
            String response = newsCommandHandler.handle(keyword, requesterId);
            sendLongText(chatId, response);
        } catch (Exception e) {
            log.error("[TELEGRAM] Error handling /news: {}", e.getMessage(), e);
            sendText(chatId, "❌ Đã xảy ra lỗi khi lấy tin tức. Thử lại sau nhé!");
        }
    }

    private void handleCategories(String chatId) {
        String response = newsCommandHandler.handle("", chatId);
        sendLongText(chatId, response);
    }

    private void handleStatus(String chatId) {
        boolean subscribed = subscriptionService.isSubscribed(chatId, Platform.TELEGRAM);
        if (subscribed) {
            sendText(chatId, "✅ *Bot đang hoạt động* — Chat này đang nhận tin tức tự động.\nDùng /stop để hủy đăng ký.");
        } else {
            sendText(chatId, "❌ *Chưa đăng ký* — Chat này chưa nhận tin tức tự động.\nDùng /start để đăng ký.");
        }
    }

    private void handleHelp(String chatId) {
        // Load help text dynamically from DB via BotCommandService
        String helpText = botCommandService.buildHelpMessage(Platform.TELEGRAM, "telegram");
        sendLongText(chatId, helpText);
    }

    // ── Sending utilities ────────────────────────────────────────────────────

    /**
     * Send a plain/markdown text message to a chat.
     */
    public void sendText(String chatId, String text) {
        SendMessage msg = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .build();
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            // Retry without markdown in case of parse error
            log.warn("[TELEGRAM] Markdown send failed for chatId={}, retrying as plain: {}", chatId, e.getMessage());
            try {
                execute(SendMessage.builder().chatId(chatId).text(text).build());
            } catch (TelegramApiException ex) {
                log.error("[TELEGRAM] Failed to send message to chatId={}: {}", chatId, ex.getMessage());
            }
        }
    }

    /**
     * Send a long message, splitting at 4096-char Telegram limit.
     * Telegram max message length is 4096 characters.
     */
    public void sendLongText(String chatId, String text) {
        final int maxLen = 4096;
        if (text.length() <= maxLen) {
            sendText(chatId, text);
            return;
        }
        // Split at last double-newline before limit
        int splitAt = text.lastIndexOf("\n\n", maxLen);
        if (splitAt <= 0) splitAt = maxLen;

        sendText(chatId, text.substring(0, splitAt).trim());
        sendLongText(chatId, text.substring(splitAt).trim()); // recursive for very long messages
    }

    /**
     * Register bot commands in Telegram UI (shows as autocomplete hints).
     * Loads commands from DB via BotCommandService — dynamic, no restart needed.
     * Falls back to hardcoded defaults if DB is unavailable.
     */
    public void registerCommands() {
        try {
            List<org.telegram.telegrambots.meta.api.objects.commands.BotCommand> tgCommands;

            try {
                // Load from DB
                List<BotCommand> dbCommands = botCommandService.getCommandsForPlatform(Platform.TELEGRAM);
                if (!dbCommands.isEmpty()) {
                    tgCommands = dbCommands.stream()
                            .map(cmd -> new org.telegram.telegrambots.meta.api.objects.commands.BotCommand(
                                    cmd.getName(), cmd.getDescription()))
                            .toList();
                    log.debug("[TELEGRAM] Loaded {} commands from DB", tgCommands.size());
                } else {
                    tgCommands = getHardcodedDefaultCommands();
                    log.warn("[TELEGRAM] No commands in DB — using hardcoded defaults");
                }
            } catch (Exception e) {
                log.warn("[TELEGRAM] Could not load commands from DB ({}), using defaults", e.getMessage());
                tgCommands = getHardcodedDefaultCommands();
            }

            execute(SetMyCommands.builder()
                    .commands(tgCommands)
                    .scope(new BotCommandScopeDefault())
                    .build());
            log.info("[TELEGRAM] {} bot commands registered successfully", tgCommands.size());
        } catch (TelegramApiException e) {
            log.warn("[TELEGRAM] Failed to register bot commands: {}", e.getMessage());
        }
    }

    private List<org.telegram.telegrambots.meta.api.objects.commands.BotCommand> getHardcodedDefaultCommands() {
        return List.of(
                new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("start",      "Đăng ký nhận tin tức tự động"),
                new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("stop",       "Hủy đăng ký nhận tin"),
                new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("news",       "Lấy tin ngay: /news <từ khóa>"),
                new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("categories", "Xem danh sách chủ đề hỗ trợ"),
                new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("status",     "Kiểm tra trạng thái đăng ký"),
                new org.telegram.telegrambots.meta.api.objects.commands.BotCommand("help",       "Hướng dẫn sử dụng bot")
        );
    }

    // ── Private utilities ────────────────────────────────────────────────────

    /**
     * Extract a human-readable name for the chat (group title or user first name).
     */
    private String resolveChatName(Message message) {
        if (message.getChat().isGroupChat() || message.getChat().isSuperGroupChat()) {
            return message.getChat().getTitle();
        }
        return message.getFrom().getFirstName();
    }
}
