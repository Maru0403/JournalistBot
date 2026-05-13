package com.maru.journalistbot.infrastructure.persistence.jpa;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * JPA entity — stores bot command definitions in PostgreSQL.
 *
 * Purpose: Commands are configurable from DB, no restart needed.
 *   - Add/update a command description → change DB row, reload cache.
 *   - Enable/disable a command per platform → set active = false.
 *   - Supports both DISCORD and TELEGRAM (comma-separated platforms field).
 *
 * Schema: journalist_bot.bot_commands
 *
 * Requirement: "Các command của bot, sẽ theo hướng đc lưu trong database
 *               (phục vụ về sau thay đổi dễ dàng)"
 */
@Entity
@Table(
    name = "bot_commands",
    schema = "journalist_bot",
    uniqueConstraints = @UniqueConstraint(columnNames = {"name"})
)
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Command name WITHOUT the slash, e.g. "news", "start", "stop".
     */
    @Column(nullable = false, length = 50, unique = true)
    private String name;

    /**
     * Short description shown in Telegram bot command menu and Discord help.
     * Max 256 chars (Telegram limit).
     */
    @Column(nullable = false, length = 256)
    private String description;

    /**
     * Usage example shown in /help output.
     * e.g. "/news <từ khóa>" or "/start"
     */
    @Column(length = 100)
    private String usage;

    /**
     * Comma-separated list of platforms this command is available on.
     * Values: DISCORD, TELEGRAM, ALL
     * e.g. "DISCORD,TELEGRAM" or "ALL"
     */
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String platforms = "ALL";

    /**
     * Whether this command is currently active.
     * Inactive commands are hidden from /help and not registered.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Sort order for display in /help output (lower = first).
     */
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 100;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
