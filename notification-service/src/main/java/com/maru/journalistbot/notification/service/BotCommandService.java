package com.maru.journalistbot.notification.service;

import com.maru.journalistbot.common.model.Platform;
import com.maru.journalistbot.notification.persistence.jpa.BotCommand;
import com.maru.journalistbot.notification.persistence.jpa.BotCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Provides bot command metadata from PostgreSQL, cached in Redis.
 * Used by Discord and Telegram adapters to register commands + build /help.
 *
 * Cache is invalidated via refreshCache() — call after admin updates a command.
 * SRP: only handles command metadata retrieval.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BotCommandService {

    private final BotCommandRepository repository;

    @Cacheable("bot-commands")
    public List<BotCommand> getCommandsForPlatform(Platform platform) {
        String platformName = platform.name();
        List<BotCommand> all = repository.findByActiveTrueOrderBySortOrderAsc();

        List<BotCommand> filtered = all.stream()
                .filter(cmd -> {
                    String platforms = cmd.getPlatforms();
                    return "ALL".equalsIgnoreCase(platforms)
                            || platforms.toUpperCase().contains(platformName);
                })
                .toList();

        log.debug("[BOT-CMD] Loaded {} commands for platform={}", filtered.size(), platformName);
        return filtered;
    }

    @Cacheable("bot-commands-all")
    public List<BotCommand> getAllActiveCommands() {
        return repository.findByActiveTrueOrderBySortOrderAsc();
    }

    /**
     * Build formatted /help message from DB commands.
     *
     * @param platform      determines which commands to show
     * @param markdownStyle "discord" → **bold**, "telegram" → *bold*
     */
    public String buildHelpMessage(Platform platform, String markdownStyle) {
        List<BotCommand> commands = getCommandsForPlatform(platform);
        if (commands.isEmpty()) return "📖 Không có lệnh nào được cấu hình.";

        boolean isDiscord = "discord".equalsIgnoreCase(markdownStyle);
        String bold = isDiscord ? "**" : "*";

        StringBuilder sb = new StringBuilder();
        sb.append("📖 ").append(bold).append("JournalistBot — Danh sách lệnh:").append(bold).append("\n\n");

        for (BotCommand cmd : commands) {
            String usage = cmd.getUsage() != null ? cmd.getUsage() : "/" + cmd.getName();
            sb.append(bold).append(usage).append(bold)
              .append(" — ").append(cmd.getDescription()).append("\n");
        }

        sb.append("\n💡 _Lệnh được cập nhật tự động từ hệ thống._");
        return sb.toString();
    }

    @CacheEvict(value = {"bot-commands", "bot-commands-all"}, allEntries = true)
    public void refreshCache() {
        log.info("[BOT-CMD] Command cache evicted — will reload from DB on next call");
    }
}
