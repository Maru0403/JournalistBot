package com.maru.journalistbot.infrastructure.persistence.jpa;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * JPA entity stored in PostgreSQL (journalist_bot schema).
 * Tracks which users/chats want to receive news on which platform.
 *
 * Placed in infrastructure.persistence.jpa (not domain) because it carries
 * JPA-specific annotations (@Entity, @Table) — infrastructure concern.
 *
 * NOTE: @Data intentionally NOT used on JPA entities:
 *   1. @Data includes @EqualsAndHashCode — causes TypeTag::UNKNOWN on Java 21
 *      when combined with @Builder + @NoArgsConstructor + @AllArgsConstructor.
 *   2. @EqualsAndHashCode is dangerous for JPA entities (lazy loading issues).
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

    @Column(name = "platform_user_id", nullable = false)
    private String platformUserId;

    @Column(nullable = false, length = 20)
    private String platform;

    @Column(name = "display_name")
    private String displayName;

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
