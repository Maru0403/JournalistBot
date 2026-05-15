package com.maru.journalistbot.notification.platform.discord;

import com.maru.journalistbot.common.model.Platform;
import com.maru.journalistbot.notification.persistence.jpa.BotCommand;
import com.maru.journalistbot.notification.service.BotCommandService;
import com.maru.journalistbot.notification.service.NewsCommandService;
import com.maru.journalistbot.notification.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JDA event listener for Discord slash commands.
 * Phase 5: now uses NewsCommandService (REST calls to fetcher + summarizer)
 *          instead of NewsCommandHandler (which needed local fetcher access).
 *
 * Commands:
 *   /start      — subscribe channel to auto-broadcast
 *   /stop       — unsubscribe channel
 *   /status     — check subscription status
 *   /news [kw]  — on-demand news fetch via fetcher-service + summarizer-service
 *   /categories — show supported topics
 *   /help       — list all commands
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscordCommandListener extends ListenerAdapter {

    private final NewsCommandService newsCommandService;
    private final SubscriptionService subscriptionService;
    private final BotCommandService botCommandService;

    @Override
    public void onReady(ReadyEvent event) {
        log.info("[DISCORD] Bot ready: {} ({})",
                event.getJDA().getSelfUser().getName(),
                event.getJDA().getSelfUser().getId());

        List<SlashCommandData> slashCommands = buildSlashCommandsFromDb();
        event.getJDA().updateCommands().addCommands(slashCommands).queue(
                cmds -> log.info("[DISCORD] {} slash commands registered", cmds.size()),
                err  -> log.error("[DISCORD] Failed to register commands: {}", err.getMessage())
        );
    }

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
            case "help"       -> handleHelpCommand(event);
            default           -> event.reply("❓ Lệnh không được nhận diện.").setEphemeral(true).queue();
        }
    }

    // ── Subscription commands ─────────────────────────────────────────────────

    private void handleStartCommand(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("⚠️ /start chỉ dùng trong server channel.").setEphemeral(true).queue();
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
            event.reply("⚠️ /stop chỉ dùng trong server channel.").setEphemeral(true).queue();
            return;
        }
        boolean removed = subscriptionService.unsubscribe(event.getChannel().getId(), Platform.DISCORD);
        if (removed) {
            event.reply("✅ Đã hủy đăng ký. Dùng `/start` để đăng ký lại.").queue();
        } else {
            event.reply("ℹ️ Kênh này chưa được đăng ký. Dùng `/start` để bắt đầu.").setEphemeral(true).queue();
        }
    }

    private void handleStatusCommand(SlashCommandInteractionEvent event) {
        boolean subscribed = subscriptionService.isSubscribed(event.getChannel().getId(), Platform.DISCORD);
        if (subscribed) {
            event.reply("✅ **Bot đang hoạt động** — Kênh này đang nhận tin tức tự động.\nDùng `/stop` để hủy.")
                    .setEphemeral(true).queue();
        } else {
            event.reply("❌ **Chưa đăng ký** — Dùng `/start` để đăng ký nhận tin.")
                    .setEphemeral(true).queue();
        }
    }

    // ── News commands ─────────────────────────────────────────────────────────

    @Async
    protected void handleNewsCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        String keyword = event.getOption("keyword") != null
                ? event.getOption("keyword").getAsString()
                : "";

        try {
            String response = newsCommandService.handle(keyword, event.getUser().getId());
            for (String part : splitMessage(response, 2000)) {
                event.getHook().sendMessage(part).queue();
            }
        } catch (Exception e) {
            log.error("[DISCORD] Error handling /news: {}", e.getMessage(), e);
            event.getHook().sendMessage("❌ Đã xảy ra lỗi. Thử lại sau nhé!").queue();
        }
    }

    private void handleCategoriesCommand(SlashCommandInteractionEvent event) {
        String response = newsCommandService.handle("", event.getUser().getId());
        String msg = response.length() <= 2000 ? response : response.substring(0, 2000);
        event.reply(msg).setEphemeral(true).queue();
    }

    private void handleHelpCommand(SlashCommandInteractionEvent event) {
        String helpText = botCommandService.buildHelpMessage(Platform.DISCORD, "discord");
        String msg = helpText.length() <= 2000 ? helpText : helpText.substring(0, 2000);
        event.reply(msg).setEphemeral(true).queue();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

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

        if (result.isEmpty()) {
            log.warn("[DISCORD] No commands in DB — using hardcoded defaults");
            result.add(Commands.slash("start",      "Đăng ký kênh này nhận tin tức tự động"));
            result.add(Commands.slash("stop",       "Hủy đăng ký kênh này"));
            result.add(Commands.slash("status",     "Kiểm tra trạng thái đăng ký"));
            result.add(Commands.slash("news",       "Lấy tin tức mới nhất")
                    .addOption(OptionType.STRING, "keyword", "Chủ đề: ai, java, gamedev...", false));
            result.add(Commands.slash("categories", "Xem danh sách chủ đề hỗ trợ"));
            result.add(Commands.slash("help",       "Hướng dẫn sử dụng bot"));
        }
        return result;
    }

    private String resolveChannelName(SlashCommandInteractionEvent event) {
        if (event.getChannel() instanceof TextChannel tc) {
            return "#" + tc.getName() + " (" + event.getGuild().getName() + ")";
        }
        return event.getChannel().getName();
    }

    private String[] splitMessage(String message, int maxLen) {
        if (message.length() <= maxLen) return new String[]{message};
        int splitAt = message.lastIndexOf("\n\n", maxLen);
        if (splitAt <= 0) splitAt = maxLen;
        String head = message.substring(0, splitAt).trim();
        String tail = message.substring(splitAt).trim();
        if (tail.isEmpty()) return new String[]{head};
        String[] tailParts = splitMessage(tail, maxLen);
        String[] result = new String[1 + tailParts.length];
        result[0] = head;
        System.arraycopy(tailParts, 0, result, 1, tailParts.length);
        return result;
    }
}
