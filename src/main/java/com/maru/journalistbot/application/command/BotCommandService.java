package com.maru.journalistbot.application.command;

import com.maru.journalistbot.domain.model.Platform;
import com.maru.journalistbot.infrastructure.persistence.jpa.BotCommand;
import com.maru.journalistbot.infrastructure.persistence.jpa.BotCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Application service for managing bot commands loaded from DB.
 *
 * Responsibilities:
 *   - Provide commands to Discord/Telegram adapters for registration
 *   - Generate /help text dynamically from DB
 *   - Cache command list (invalidated on update)
 *
 * SRP: only handles command metadata retrieval — not command execution.
 * Command execution logic stays in NewsCommandHandler.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BotCommandService {

    private final BotCommandRepository repository;

    /**
     * Get all active commands available for a given platform.
     * Returns commands where platforms = "ALL" or contains the platform name.
     *
     * @param platform DISCORD or TELEGRAM
     * @return sorted list of active BotCommand
     */
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

    /**
     * Get all active commands regardless of platform.
     * Used for /help fallback and admin inspection.
     */
    @Cacheable("bot-commands-all")
    public List<BotCommand> getAllActiveCommands() {
        return repository.findByActiveTrueOrderBySortOrderAsc();
    }

    /**
     * Build a formatted /help message from DB commands.
     *
     * @param platform determines which commands to show
     * @param markdownStyle "discord" uses **bold**, "telegram" uses *bold*
     * @return formatted help string
     */
    public String buildHelpMessage(Platform platform, String markdownStyle) {
        List<BotCommand> commands = getCommandsForPlatform(platform);

        if (commands.isEmpty()) {
            return "📖 Không có lệnh nào được cấu hình.";
        }

        boolean isDiscord = "discord".equalsIgnoreCase(markdownStyle);
        String bold = isDiscord ? "**" : "*";

        StringBuilder sb = new StringBuilder();
        sb.append("📖 ").append(bold).append("JournalistBot — Danh sách lệnh:").append(bold).append("\n\n");

        for (BotCommand cmd : commands) {
            String usage = cmd.getUsage() != null ? cmd.getUsage() : "/" + cmd.getName();
            sb.append(bold).append(usage).append(bold)
              .append(" — ").append(cmd.getDescription())
              .append("\n");
        }

        sb.append("\n💡 _Lệnh này được cập nhật tự động từ hệ thống._");
        return sb.toString();
    }

    /**
     * Evict command caches (call after admin updates a command in DB).
     * Can be triggered via a future admin API endpoint.
     */
    @CacheEvict(value = {"bot-commands", "bot-commands-all"}, allEntries = true)
    public void refreshCache() {
        log.info("[BOT-CMD] Command cache evicted — will reload from DB on next call");
    }
}
