package com.maru.journalistbot.infrastructure.platform.discord;

import com.maru.journalistbot.application.subscription.SubscriptionService;
import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.domain.model.NewsCategory;
import com.maru.journalistbot.domain.model.Platform;
import com.maru.journalistbot.domain.port.NewsMessagePort;
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
 * Discord platform adapter — implements NewsMessagePort (DIP).
 *
 * Phase 3 update:
 *   sendNews() now broadcasts to BOTH:
 *     1. The static news-channel-id from application.yml (backward compatible)
 *     2. All active DISCORD subscriptions stored in DB (via /start command)
 *   This lets each server/channel subscribe independently.
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

    /**
     * Broadcast news to all target channels:
     *   - Static news-channel-id from config (if configured)
     *   - All active DB subscriptions registered via /start
     * Deduplicates channelIds to avoid double-posting.
     *
     * @CircuitBreaker("discord"): if JDA send fails repeatedly
     *   → circuit opens → fallbackSendNews() called → log and skip
     *   → next broadcast cycle will retry automatically
     */
    @Override
    @CircuitBreaker(name = "discord", fallbackMethod = "fallbackSendNews")
    public void sendNews(List<NewsArticle> articles, NewsCategory category, String formattedMessage) {
        if (!isConnected()) {
            log.warn("[DISCORD] Not connected — skipping broadcast for {}", category.getDisplayName());
            return;
        }

        List<String> targetChannelIds = resolveTargetChannels();
        if (targetChannelIds.isEmpty()) {
            log.warn("[DISCORD] No target channels configured. Use /start in a channel or set bot.discord.news-channel-id");
            return;
        }

        log.info("[DISCORD] Broadcasting {} articles to {} channel(s) for {}",
                articles.size(), targetChannelIds.size(), category.getDisplayName());

        int successCount = 0;
        for (String channelId : targetChannelIds) {
            MessageChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                log.warn("[DISCORD] Channel {} not found (bot may lack permissions or was removed)", channelId);
                continue;
            }
            sendInChunks(channel, formattedMessage);
            successCount++;
        }
        log.info("[DISCORD] Broadcast complete: {}/{} channels received news for {}",
                successCount, targetChannelIds.size(), category.getDisplayName());
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

    /** Circuit Breaker fallback — log and skip; dedup not marked so retry next cycle */
    private void fallbackSendNews(List<NewsArticle> articles, NewsCategory category,
                                  String formattedMessage, Throwable ex) {
        log.warn("[DISCORD] Circuit OPEN or send error for {} — skipping broadcast. Reason: {}",
                category.getDisplayName(), ex.getMessage());
    }

    // ── Private utilities ────────────────────────────────────────────────────

    private boolean isTokenValid() {
        return token != null && !token.isBlank() && !token.startsWith("your-");
    }

    /**
     * Collect all target channel IDs for broadcasting:
     *   1. Static config channel (backward compatibility)
     *   2. DB-subscribed channels (via /start)
     * Deduplicates to prevent double-posting.
     */
    private List<String> resolveTargetChannels() {
        List<String> targets = new ArrayList<>();

        // 1. Static config
        if (newsChannelId != null && !newsChannelId.isBlank()) {
            targets.add(newsChannelId);
        }

        // 2. DB subscriptions (add only if not already in list)
        List<String> dbSubs = subscriptionService.getActiveSubscribers(Platform.DISCORD);
        for (String sub : dbSubs) {
            if (!targets.contains(sub)) {
                targets.add(sub);
            }
        }
        return targets;
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

        int splitAt = message.lastIndexOf("\n\n", 2000);
        if (splitAt <= 0) splitAt = 2000;

        channel.sendMessage(message.substring(0, splitAt).trim()).queue();
        sendInChunks(channel, message.substring(splitAt).trim());
    }
}
