package com.maru.journalistbot.notification.persistence.jpa;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * JPA entity — tracks which chats/channels have subscribed to receive news per platform.
 * Schema: journalist_bot.user_subscriptions
 */
@Entity
@Table(
    name = "user_subscriptions",
    schema = "journalist_bot",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"platform_user_id", "platform"}
    )
)
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** chatId (Telegram) or channelId (Discord) */
    @Column(name = "platform_user_id", nullable = false)
    private String platformUserId;

    /** "DISCORD" or "TELEGRAM" */
    @Column(nullable = false, length = 20)
    private String platform;

    /** Human-readable name for logging (channel name, group title) */
    @Column(name = "display_name")
    private String displayName;

    /** Soft-delete flag — false = unsubscribed */
    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
