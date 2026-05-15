package com.maru.journalistbot.notification.persistence.jpa;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * JPA entity — bot command definitions stored in PostgreSQL.
 * Commands are loaded from DB → no restart needed when adding/updating commands.
 * Schema: journalist_bot.bot_commands
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

    /** Command name WITHOUT slash, e.g. "news", "start", "stop" */
    @Column(nullable = false, length = 50, unique = true)
    private String name;

    /** Short description (Telegram limit: 256 chars) */
    @Column(nullable = false, length = 256)
    private String description;

    /** Usage example shown in /help: "/news <từ khóa>" */
    @Column(length = 100)
    private String usage;

    /**
     * Platforms this command is available on.
     * Values: DISCORD, TELEGRAM, ALL (comma-separated or single)
     */
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String platforms = "ALL";

    /** Whether this command is currently active */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** Sort order in /help output (lower = first) */
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
