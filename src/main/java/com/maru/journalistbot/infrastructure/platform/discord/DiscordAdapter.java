package com.maru.journalistbot.infrastructure.platform.discord;

import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.domain.model.NewsCategory;
import com.maru.journalistbot.domain.model.Platform;
import com.maru.journalistbot.domain.port.NewsMessagePort;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Discord platform adapter — implements NewsMessagePort (DIP).
 *
 * Phase 2: Full JDA integration.
 *   - JDA initialized lazily via @EventListener(ApplicationReadyEvent) to avoid blocking startup.
 *   - If Discord token is absent/placeholder → stub mode (logs only, no crash).
 *   - Slash commands are registered in DiscordCommandListener.onReady().
 *
 * Why @EventListener instead of @PostConstruct:
 *   @PostConstruct blocks the Spring startup thread. Using ApplicationReadyEvent
 *   lets the entire context start first, then JDA connects asynchronously.
 */
@Component
@Slf4j
public class DiscordAdapter implements NewsMessagePort {

    @Value("${bot.discord.token:}")
    private String token;

    @Value("${bot.discord.news-channel-id:}")
    private String newsChannelId;

    private final DiscordCommandListener commandListener;

    private JDA jda;

    @Autowired
    public DiscordAdapter(DiscordCommandListener commandListener) {
        this.commandListener = commandListener;
    }

    /**
     * Initialize JDA after the Spring context is fully ready.
     * JDA connects asynchronously — slash commands are registered in onReady().
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initJda() {
        if (!isTokenValid()) {
            log.warn("[DISCORD] Token not configured — running in stub mode. Set bot.discord.token to enable.");
            return;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.DIRECT_MESSAGES
                    )
                    .addEventListeners(commandListener)
                    .build();
            // Do NOT call awaitReady() — JDA connects in the background.
            // DiscordCommandListener.onReady() fires when connection is established.
            log.info("[DISCORD] JDA connecting asynchronously...");
        } catch (Exception e) {
            log.error("[DISCORD] Failed to initialize JDA: {}", e.getMessage(), e);
        }
    }

    // ── NewsMessagePort implementation ───────────────────────────────────────

    @Override
    public Platform getPlatform() {
        return Platform.DISCORD;
    }

    @Override
    public void sendNews(List<NewsArticle> articles, NewsCategory category, String formattedMessage) {
        if (!isConnected()) {
            log.warn("[DISCORD] Not connected — skipping broadcast for {}", category.getDisplayName());
            return;
        }
        if (newsChannelId == null || newsChannelId.isBlank()) {
            log.warn("[DISCORD] news-channel-id not set — cannot broadcast. Set bot.discord.news-channel-id");
            return;
        }

        TextChannel channel = jda.getTextChannelById(newsChannelId);
        if (channel == null) {
            log.error("[DISCORD] Channel {} not found. Check bot has VIEW_CHANNEL + SEND_MESSAGES permission.", newsChannelId);
            return;
        }

        // Discord message limit: 2000 chars. Split if needed.
        sendInChunks(channel, formattedMessage);
        log.info("[DISCORD] Sent {} articles to channel #{} for {}", articles.size(),
                channel.getName(), category.getDisplayName());
    }

    @Override
    public void replyToUser(String userId, String message) {
        if (!isConnected()) {
            log.warn("[DISCORD] Not connected — cannot reply to user {}", userId);
            return;
        }
        // Open DM channel and send message
        jda.retrieveUserById(userId).queue(
                user -> user.openPrivateChannel().queue(
                        channel -> sendInChunks(channel, message),
                        err -> log.warn("[DISCORD] Cannot open DM for user {}: {}", userId, err.getMessage())
                ),
                err -> log.warn("[DISCORD] User {} not found: {}", userId, err.getMessage())
        );
    }

    @Override
    public boolean isConnected() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    // ── Private utilities ────────────────────────────────────────────────────

    private boolean isTokenValid() {
        return token != null && !token.isBlank() && !token.startsWith("your-");
    }

    /**
     * Split message into ≤2000 char chunks and send sequentially.
     * Discord rejects messages over 2000 characters.
     */
    private void sendInChunks(MessageChannel channel, String message) {
        if (message.length() <= 2000) {
            channel.sendMessage(message).queue(
                    ok  -> {},
                    err -> log.error("[DISCORD] Failed to send message: {}", err.getMessage())
            );
            return;
        }

        // Split at last double-newline before 2000 chars
        int splitAt = message.lastIndexOf("\n\n", 2000);
        if (splitAt <= 0) splitAt = 2000;

        channel.sendMessage(message.substring(0, splitAt).trim()).queue();
        sendInChunks(channel, message.substring(splitAt).trim()); // recursive for >4000 chars
    }
}
