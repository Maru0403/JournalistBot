package com.maru.journalistbot.notification.service;

import com.maru.journalistbot.common.model.Platform;
import com.maru.journalistbot.notification.persistence.jpa.UserSubscription;
import com.maru.journalistbot.notification.persistence.jpa.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Manages chat/channel subscriptions per platform.
 * Persisted in PostgreSQL (journalist_bot.user_subscriptions).
 *
 * SRP: only handles subscribe/unsubscribe/query — not command execution.
 * Used by both DiscordCommandListener and TelegramBot.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final UserSubscriptionRepository repository;

    /**
     * Subscribe a chat/channel to receive auto-broadcast news.
     *
     * @param platformUserId chatId (Telegram) or channelId (Discord)
     * @param platform       DISCORD or TELEGRAM
     * @param displayName    human-readable name for logging
     * @return true if newly subscribed or re-activated, false if already active
     */
    @Transactional
    public boolean subscribe(String platformUserId, Platform platform, String displayName) {
        Optional<UserSubscription> existing = repository.findByPlatformUserIdAndPlatform(
                platformUserId, platform.name());

        if (existing.isPresent()) {
            UserSubscription sub = existing.get();
            if (sub.getActive()) {
                log.info("[SUBSCRIPTION] {} {} already subscribed", platform, platformUserId);
                return false;
            }
            // Re-activate
            sub.setActive(true);
            sub.setDisplayName(displayName);
            repository.save(sub);
            log.info("[SUBSCRIPTION] {} {} re-activated", platform, platformUserId);
            return true;
        }

        repository.save(UserSubscription.builder()
                .platformUserId(platformUserId)
                .platform(platform.name())
                .displayName(displayName)
                .active(true)
                .build());
        log.info("[SUBSCRIPTION] {} {} subscribed (name={})", platform, platformUserId, displayName);
        return true;
    }

    /**
     * Unsubscribe (soft-delete — sets active=false).
     *
     * @return true if successfully unsubscribed, false if was not subscribed
     */
    @Transactional
    public boolean unsubscribe(String platformUserId, Platform platform) {
        Optional<UserSubscription> existing = repository.findByPlatformUserIdAndPlatform(
                platformUserId, platform.name());

        if (existing.isEmpty() || !existing.get().getActive()) {
            log.info("[SUBSCRIPTION] {} {} not found or already inactive", platform, platformUserId);
            return false;
        }

        existing.get().setActive(false);
        repository.save(existing.get());
        log.info("[SUBSCRIPTION] {} {} unsubscribed", platform, platformUserId);
        return true;
    }

    public boolean isSubscribed(String platformUserId, Platform platform) {
        return repository.findByPlatformUserIdAndPlatform(platformUserId, platform.name())
                .map(UserSubscription::getActive)
                .orElse(false);
    }

    /** Get all active subscribers for a platform (used by broadcast) */
    public List<String> getActiveSubscribers(Platform platform) {
        return repository.findByPlatformAndActiveTrue(platform.name())
                .stream()
                .map(UserSubscription::getPlatformUserId)
                .toList();
    }
}
