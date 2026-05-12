package com.maru.journalistbot.infrastructure.persistence.mongo;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * MongoDB document — tracks which articles have already been sent per platform.
 * TTL index auto-deletes records after 7 days via MongoDB TTL mechanism.
 *
 * Placed in infrastructure.persistence.mongo (not domain) because it carries
 * MongoDB-specific annotations (@Document, @Indexed) — infrastructure concern.
 */
@Getter
@Setter
@ToString
@Builder
@Document(collection = "sent_articles")
public class SentArticle {

    @Id
    private String id;

    /** MD5 of normalized URL — primary dedup key */
    @Indexed
    private String articleHash;

    private String url;
    private String title;

    /** Platform name: DISCORD, TELEGRAM, ZALO */
    @Indexed
    private String platform;

    private LocalDateTime sentAt;

    /**
     * TTL field — MongoDB auto-deletes after 7 days.
     * Requires: @Indexed(expireAfterSeconds=0) + expireAt set at save time.
     */
    @Indexed(expireAfterSeconds = 0)
    private LocalDateTime expireAt;
}
