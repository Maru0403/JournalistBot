package com.maru.journalistbot.notification.platform.discord;

import com.maru.journalistbot.common.event.ArticleItemDto;
import com.maru.journalistbot.common.model.Platform;
import com.maru.journalistbot.notification.platform.NewsMessagePort;
import com.maru.journalistbot.notification.service.SubscriptionService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Discord platform adapter — Phase 5 notification-service version.
 *
 * Phase 5 changes vs. monolith:
 *   - sendNews() receives List<ArticleItemDto> (from common) instead of List<NewsArticle>
 *   - Receives pre-summarized message (formattedMessage) from BroadcastService
 *   - No longer depends on AISummarizerService (done upstream)
 *
 * Multi-server support:
 *   - Static newsChannelId from config (backward compat)
 *   - All active DISCORD subscriptions from DB (via /start command)
 *
 * @CircuitBreaker("discord"): if send fails repeatedly → circuit opens → log and skip
 */
@Component
@Slf4j
public class DiscordAdapter implements NewsMessagePort {

    @Value("${bot.discord.token:}")
    private String token;

    @Value("${bot.discord.news-channel-id:}")
    private String newsChannelId;

    private final DiscordCommandListener commandListener;
    private final SubscriptionService subscriptionService;

    private JDA jda;

    @Autowired
    public DiscordAdapter(DiscordCommandListener commandListener,
                          SubscriptionService subscriptionService) {
        this.commandListener   = commandListener;
        this.subscriptionService = subscriptionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initJda() {
        if (!isTokenValid()) {
            log.warn("[DISCORD] Token not configured — running in stub mode. Set DISCORD_BOT_TOKEN to enable.");
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
            log.info("[DISCORD] JDA connecting asynchronously...");
        } catch (Exception e) {
            log.error("[DISCORD] Failed to initialize JDA: {}", e.getMessage(), e);
        }
    }

    // ── NewsMessagePort ───────────────────────────────────────────────────────

    @Override
    public Platform getPlatform() { return Platform.DISCORD; }

    @Override
    @CircuitBreaker(name = "discord", fallbackMethod = "fallbackSendNews")
    public void sendNews(List<ArticleItemDto> articles, String category, String formattedMessage) {
        if (!isConnected()) {
            log.warn("[DISCORD] Not connected — skipping broadcast for {}", category);
            return;
        }

        List<String> targets = resolveTargetChannels();
        if (targets.isEmpty()) {
            log.warn("[DISCORD] No target channels. Use /start in a channel or set DISCORD_NEWS_CHANNEL_ID");
            return;
        }

        log.info("[DISCORD] Broadcasting {} articles to {} channel(s) for {}",
                articles.size(), targets.size(), category);

        int successCount = 0;
        for (String channelId : targets) {
            MessageChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                log.warn("[DISCORD] Channel {} not found (bot lacks permissions or was removed)", channelId);
                continue;
            }
            sendInChunks(channel, formattedMessage);
            successCount++;
        }

        log.info("[DISCORD] Broadcast complete: {}/{} channels for {}", successCount, targets.size(), category);
    }

    @Override
    public void replyToUser(String userId, String message) {
        if (!isConnected()) {
            log.warn("[DISCORD] Not connected — cannot reply to user {}", userId);
            return;
        }
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

    // ── Private ───────────────────────────────────────────────────────────────

    private void fallbackSendNews(List<ArticleItemDto> articles, String category,
                                  String formattedMessage, Throwable ex) {
        log.warn("[DISCORD] Circuit OPEN for {} — skipping. Reason: {}", category, ex.getMessage());
    }

    private boolean isTokenValid() {
        return token != null && !token.isBlank() && !token.startsWith("your-");
    }

    private List<String> resolveTargetChannels() {
        List<String> targets = new ArrayList<>();
        if (newsChannelId != null && !newsChannelId.isBlank()) {
            targets.add(newsChannelId);
        }
        for (String sub : subscriptionService.getActiveSubscribers(Platform.DISCORD)) {
            if (!targets.contains(sub)) targets.add(sub);
        }
        return targets;
    }

    private void sendInChunks(MessageChannel channel, String message) {
        if (message.length() <= 2000) {
            channel.sendMessage(message).queue(
                    ok  -> {},
                    err -> log.error("[DISCORD] Send failed: {}", err.getMessage())
            );
            return;
        }
        int splitAt = message.lastIndexOf("\n\n", 2000);
        if (splitAt <= 0) splitAt = 2000;
        channel.sendMessage(message.substring(0, splitAt).trim()).queue();
        sendInChunks(channel, message.substring(splitAt).trim());
    }
}
