package com.maru.journalistbot.config;

import com.maru.journalistbot.infrastructure.persistence.jpa.BotCommand;
import com.maru.journalistbot.infrastructure.persistence.jpa.BotCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds default bot commands into the DB on first startup.
 *
 * Strategy: INSERT IGNORE (check by name before inserting).
 * - Existing rows are never overwritten → admin edits in DB are preserved.
 * - New commands added here are inserted only if they don't exist yet.
 *
 * To update a command description after deploy:
 *   UPDATE journalist_bot.bot_commands SET description = '...' WHERE name = '...';
 *   → No app restart needed (cache evicts on next /help call after TTL)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BotCommandInitializer {

    private final BotCommandRepository repository;

    @EventListener(ApplicationReadyEvent.class)
    public void seedDefaultCommands() {
        List<BotCommand> defaults = List.of(
            BotCommand.builder()
                .name("start")
                .description("Đăng ký kênh/chat này nhận tin tức tự động theo lịch")
                .usage("/start")
                .platforms("ALL")
                .active(true)
                .sortOrder(1)
                .build(),

            BotCommand.builder()
                .name("stop")
                .description("Hủy đăng ký — không nhận tin tức tự động nữa")
                .usage("/stop")
                .platforms("ALL")
                .active(true)
                .sortOrder(2)
                .build(),

            BotCommand.builder()
                .name("news")
                .description("Lấy tin tức mới nhất theo chủ đề (ai, java, python, gamedev...)")
                .usage("/news <từ khóa>")
                .platforms("ALL")
                .active(true)
                .sortOrder(3)
                .build(),

            BotCommand.builder()
                .name("categories")
                .description("Xem danh sách tất cả chủ đề tin tức được hỗ trợ")
                .usage("/categories")
                .platforms("ALL")
                .active(true)
                .sortOrder(4)
                .build(),

            BotCommand.builder()
                .name("status")
                .description("Kiểm tra trạng thái đăng ký của kênh/chat này")
                .usage("/status")
                .platforms("ALL")
                .active(true)
                .sortOrder(5)
                .build(),

            BotCommand.builder()
                .name("help")
                .description("Hiển thị danh sách lệnh và hướng dẫn sử dụng")
                .usage("/help")
                .platforms("TELEGRAM") // Discord has /categories for help
                .active(true)
                .sortOrder(6)
                .build()
        );

        int seeded = 0;
        for (BotCommand cmd : defaults) {
            boolean exists = repository.findByNameAndActiveTrue(cmd.getName()).isPresent()
                    || repository.findAll().stream()
                            .anyMatch(existing -> existing.getName().equals(cmd.getName()));
            if (!exists) {
                repository.save(cmd);
                seeded++;
                log.debug("[BOT-CMD] Seeded command: /{}", cmd.getName());
            }
        }

        if (seeded > 0) {
            log.info("[BOT-CMD] ✅ Seeded {} default bot commands into DB", seeded);
        } else {
            log.info("[BOT-CMD] ✅ Bot commands already initialized — no changes");
        }
    }
}
