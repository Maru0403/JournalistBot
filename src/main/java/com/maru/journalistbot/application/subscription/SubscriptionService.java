package com.maru.journalistbot.application.subscription;

import com.maru.journalistbot.domain.model.Platform;
import com.maru.journalistbot.infrastructure.persistence.jpa.UserSubscription;
import com.maru.journalistbot.infrastructure.persistence.jpa.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Application service for managing chat/channel subscriptions per platform.
 *
 * SRP: only handles subscribe / unsubscribe / query logic.
 * Used by both TelegramBot and DiscordCommandListener.
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
     * @param platform       target platform
     * @param displayName    human-readable name for logging (group name, channel name)
     * @return true if newly subscribed, false if already active
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
            log.info("[SUBSCRIPTION] {} {} re-activated (was inactive)", platform, platformUserId);
            return true;
        }

        UserSubscription newSub = UserSubscription.builder()
                .platformUserId(platformUserId)
                .platform(platform.name())
                .displayName(displayName)
                .active(true)
                .build();
        repository.save(newSub);
        log.info("[SUBSCRIPTION] {} {} subscribed (name={})", platform, platformUserId, displayName);
        return true;
    }

    /**
     * Unsubscribe (soft-delete — sets active = false).
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

    /**
     * Check if a given chat/channel is currently subscribed.
     */
    public boolean isSubscribed(String platformUserId, Platform platform) {
        return repository.findByPlatformUserIdAndPlatform(platformUserId, platform.name())
                .map(UserSubscription::getActive)
                .orElse(false);
    }

    /**
     * Get all active subscriptions for a platform (used by adapters to broadcast).
     */
    public List<String> getActiveSubscribers(Platform platform) {
        return repository.findByPlatformAndActiveTrue(platform.name())
                .stream()
                .map(UserSubscription::getPlatformUserId)
                .toList();
    }
}
