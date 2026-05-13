package com.maru.journalistbot.infrastructure.platform.discord;

import com.maru.journalistbot.application.command.BotCommandService;
import com.maru.journalistbot.application.command.NewsCommandHandler;
import com.maru.journalistbot.application.subscription.SubscriptionService;
import com.maru.journalistbot.domain.model.Platform;
import com.maru.journalistbot.infrastructure.persistence.jpa.BotCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JDA event listener for Discord slash commands.
 *
 * Phase 3 additions (aligned with Telegram commands):
 *   /start  — subscribe current channel to receive auto-broadcast
 *   /stop   — unsubscribe current channel
 *   /status — show subscription status for current channel
 *
 * Existing commands:
 *   /news [keyword]  — fetch latest news for a topic
 *   /categories      — show available categories
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscordCommandListener extends ListenerAdapter {

    private final NewsCommandHandler newsCommandHandler;
    private final SubscriptionService subscriptionService;
    private final BotCommandService botCommandService;

    /**
     * Called when JDA is fully connected.
     * Loads slash commands from DB and registers them dynamically.
     * /news always gets a keyword option appended regardless of DB config.
     */
    @Override
    public void onReady(ReadyEvent event) {
        log.info("[DISCORD] Bot ready: {} ({})", event.getJDA().getSelfUser().getName(),
                event.getJDA().getSelfUser().getId());

        List<SlashCommandData> slashCommands = buildSlashCommandsFromDb();

        event.getJDA().updateCommands().addCommands(slashCommands).queue(
                cmds -> log.info("[DISCORD] {} slash commands registered from DB", cmds.size()),
                err  -> log.error("[DISCORD] Failed to register commands: {}", err.getMessage())
        );
    }

    /**
     * Build Discord slash command list from DB.
     * Special case: "news" command always gets a keyword option.
     */
    private List<SlashCommandData> buildSlashCommandsFromDb() {
        List<BotCommand> dbCommands = botCommandService.getCommandsForPlatform(Platform.DISCORD);
        List<SlashCommandData> result = new ArrayList<>();

        for (BotCommand cmd : dbCommands) {
            if ("news".equals(cmd.getName())) {
                result.add(Commands.slash(cmd.getName(), cmd.getDescription())
                        .addOption(OptionType.STRING, "keyword",
                                "Chủ đề: ai, java, python, gamedev... (bỏ trống = xem hướng dẫn)", false));
            } else {
                result.add(Commands.slash(cmd.getName(), cmd.getDescription()));
            }
        }

        // Fallback: if DB is empty or not ready, register hardcoded defaults
        if (result.isEmpty()) {
            log.warn("[DISCORD] No commands from DB — using hardcoded defaults");
            result.add(Commands.slash("start",      "Đăng ký kênh này nhận tin tức tự động"));
            result.add(Commands.slash("stop",       "Hủy đăng ký kênh này khỏi tin tức tự động"));
            result.add(Commands.slash("status",     "Kiểm tra trạng thái đăng ký của kênh này"));
            result.add(Commands.slash("news",       "Lấy tin tức mới nhất")
                    .addOption(OptionType.STRING, "keyword",
                            "Chủ đề: ai, java, python, gamedev... (bỏ trống = xem hướng dẫn)", false));
            result.add(Commands.slash("categories", "Xem danh sách chủ đề tin tức hỗ trợ"));
        }

        return result;
    }

    /**
     * Dispatch incoming slash commands.
     */
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command   = event.getName();
        String userId    = event.getUser().getId();
        String guildName = event.isFromGuild() ? event.getGuild().getName() : "DM";

        log.info("[DISCORD] /{} from user={} guild={}", command, userId, guildName);

        switch (command) {
            case "start"      -> handleStartCommand(event);
            case "stop"       -> handleStopCommand(event);
            case "status"     -> handleStatusCommand(event);
            case "news"       -> handleNewsCommand(event);
            case "categories" -> handleCategoriesCommand(event);
            default           -> event.reply("❓ Lệnh không được nhận diện.").setEphemeral(true).queue();
        }
    }

    // ── Subscription command handlers ────────────────────────────────────────

    private void handleStartCommand(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("⚠️ Lệnh /start chỉ dùng được trong server channel, không phải DM.")
                    .setEphemeral(true).queue();
            return;
        }

        String channelId   = event.getChannel().getId();
        String channelName = resolveChannelName(event);

        boolean newSub = subscriptionService.subscribe(channelId, Platform.DISCORD, channelName);
        if (newSub) {
            event.reply("""
                    ✅ **Đã đăng ký kênh này nhận tin tức tự động!**

                    Bot sẽ tự động gửi tin tức AI, lập trình và game dev theo lịch.

                    📌 Dùng `/stop` để hủy đăng ký.
                    📌 Dùng `/news <từ khóa>` để lấy tin ngay lập tức.
                    """).queue();
        } else {
            event.reply("ℹ️ Kênh này đã được đăng ký rồi! Dùng `/status` để kiểm tra.")
                    .setEphemeral(true).queue();
        }
    }

    private void handleStopCommand(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("⚠️ Lệnh /stop chỉ dùng được trong server channel.")
                    .setEphemeral(true).queue();
            return;
        }

        String channelId = event.getChannel().getId();
        boolean removed  = subscriptionService.unsubscribe(channelId, Platform.DISCORD);

        if (removed) {
            event.reply("✅ Đã hủy đăng ký. Kênh này sẽ không nhận tin tự động nữa.\nDùng `/start` để đăng ký lại.").queue();
        } else {
            event.reply("ℹ️ Kênh này chưa được đăng ký. Dùng `/start` để bắt đầu nhận tin.")
                    .setEphemeral(true).queue();
        }
    }

    private void handleStatusCommand(SlashCommandInteractionEvent event) {
        String channelId   = event.getChannel().getId();
        boolean subscribed = subscriptionService.isSubscribed(channelId, Platform.DISCORD);

        if (subscribed) {
            event.reply("✅ **Bot đang hoạt động** — Kênh này đang nhận tin tức tự động.\nDùng `/stop` để hủy đăng ký.")
                    .setEphemeral(true).queue();
        } else {
            event.reply("❌ **Chưa đăng ký** — Kênh này chưa nhận tin tức tự động.\nDùng `/start` để đăng ký.")
                    .setEphemeral(true).queue();
        }
    }

    // ── News command handlers ────────────────────────────────────────────────

    private void handleNewsCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        String keyword = event.getOption("keyword") != null
                ? event.getOption("keyword").getAsString()
                : "";

        try {
            String response = newsCommandHandler.handle(keyword, event.getUser().getId());
            String[] parts  = splitMessage(response, 2000);
            for (String part : parts) {
                event.getHook().sendMessage(part).queue();
            }
        } catch (Exception e) {
            log.error("[DISCORD] Error handling /news command: {}", e.getMessage(), e);
            event.getHook().sendMessage("❌ Đã xảy ra lỗi khi lấy tin tức. Thử lại sau nhé!").queue();
        }
    }

    private void handleCategoriesCommand(SlashCommandInteractionEvent event) {
        String response = newsCommandHandler.handle("", event.getUser().getId());
        event.reply(response.length() <= 2000 ? response : response.substring(0, 2000))
                .setEphemeral(true).queue();
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    private String resolveChannelName(SlashCommandInteractionEvent event) {
        if (event.getChannel() instanceof TextChannel tc) {
            return "#" + tc.getName() + " (" + event.getGuild().getName() + ")";
        }
        return event.getChannel().getName();
    }

    /**
     * Split a long message into chunks of at most maxLen characters,
     * breaking at natural double-newline boundaries where possible.
     */
    private String[] splitMessage(String message, int maxLen) {
        if (message.length() <= maxLen) return new String[]{message};

        int splitAt = message.lastIndexOf("\n\n", maxLen);
        if (splitAt <= 0) splitAt = maxLen;

        String head = message.substring(0, splitAt).trim();
        String tail = message.substring(splitAt).trim();

        if (tail.isEmpty()) return new String[]{head};

        String[] tailParts = splitMessage(tail, maxLen);
        String[] result    = new String[1 + tailParts.length];
        result[0] = head;
        System.arraycopy(tailParts, 0, result, 1, tailParts.length);
        return result;
    }
}
