package com.maru.journalistbot.infrastructure.platform.discord;

import com.maru.journalistbot.application.command.NewsCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.springframework.stereotype.Component;

/**
 * JDA event listener for Discord slash commands.
 *
 * Extends ListenerAdapter (JDA) so it can be registered as a JDA event handler.
 * Also a Spring @Component so NewsCommandHandler can be injected.
 *
 * Commands registered:
 *   /news [keyword]  — fetch latest news for a topic
 *   /help            — show available commands
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscordCommandListener extends ListenerAdapter {

    private final NewsCommandHandler commandHandler;

    /**
     * Called when JDA is fully connected and ready.
     * Register slash commands here so they're available immediately.
     */
    @Override
    public void onReady(ReadyEvent event) {
        log.info("[DISCORD] Bot ready: {} ({})", event.getJDA().getSelfUser().getName(),
                event.getJDA().getSelfUser().getId());

        // Register global slash commands (takes ~1 hour to propagate globally;
        // use guild commands in development for instant updates)
        event.getJDA().updateCommands().addCommands(
                Commands.slash("news", "Lấy tin tức mới nhất")
                        .addOption(OptionType.STRING, "keyword",
                                "Chủ đề: ai, java, python, gamedev... (bỏ trống = AI news)", false),

                Commands.slash("categories", "Xem danh sách chủ đề tin tức hỗ trợ")
        ).queue(
                cmds -> log.info("[DISCORD] {} slash commands registered", cmds.size()),
                err  -> log.error("[DISCORD] Failed to register commands: {}", err.getMessage())
        );
    }

    /**
     * Handle /news and /categories slash commands.
     */
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command = event.getName();
        String userId  = event.getUser().getId();

        log.info("[DISCORD] /{} from user={} guild={}", command, userId,
                event.isFromGuild() ? event.getGuild().getName() : "DM");

        switch (command) {
            case "news" -> handleNewsCommand(event);
            case "categories" -> handleCategoriesCommand(event);
            default -> event.reply("❓ Lệnh không được nhận diện.").setEphemeral(true).queue();
        }
    }

    // ── Command handlers ─────────────────────────────────────────────────────

    private void handleNewsCommand(SlashCommandInteractionEvent event) {
        // Defer reply — fetching news may take >3 seconds (JDA default timeout)
        event.deferReply().queue();

        String keyword = event.getOption("keyword") != null
                ? event.getOption("keyword").getAsString()
                : "";

        try {
            String response = commandHandler.handle(keyword, event.getUser().getId());
            // Discord message limit is 2000 chars — split if needed
            if (response.length() <= 2000) {
                event.getHook().sendMessage(response).queue();
            } else {
                // Split at natural break points (double newline)
                String[] parts = splitMessage(response, 2000);
                for (String part : parts) {
                    event.getHook().sendMessage(part).queue();
                }
            }
        } catch (Exception e) {
            log.error("[DISCORD] Error handling /news command: {}", e.getMessage(), e);
            event.getHook().sendMessage("❌ Đã xảy ra lỗi khi lấy tin tức. Thử lại sau nhé!").queue();
        }
    }

    private void handleCategoriesCommand(SlashCommandInteractionEvent event) {
        String response = commandHandler.handle(null, event.getUser().getId());
        event.reply(response).setEphemeral(true).queue(); // ephemeral = only visible to user
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    /**
     * Split a long message at natural boundaries (double newlines) to stay under Discord's 2000-char limit.
     */
    private String[] splitMessage(String message, int maxLen) {
        if (message.length() <= maxLen) return new String[]{message};

        // Simple split: find last \n\n before maxLen
        int splitAt = message.lastIndexOf("\n\n", maxLen);
        if (splitAt <= 0) splitAt = maxLen;

        return new String[]{
            message.substring(0, splitAt).trim(),
            message.substring(splitAt).trim()
        };
    }
}
