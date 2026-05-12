package com.maru.journalistbot.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Objects;

// @Data removed — @Data includes @EqualsAndHashCode which conflicts with our custom
// equals/hashCode below on Java 21 (causes TypeTag::UNKNOWN during annotation processing).
// Using @Getter + @Setter + @ToString separately is the safe pattern here.
@Getter
@Setter
@ToString
@Builder
public class NewsArticle {

    private String title;
    private String url;
    private String description;       // Short excerpt from the source
    private String sourceName;        // e.g. "Anthropic Blog", "Hacker News"
    private NewsCategory category;
    private LocalDateTime publishedAt;

    /**
     * Equality based on URL only (after normalization).
     * Prevents duplicates from different sources with same article.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NewsArticle other)) return false;
        return Objects.equals(normalizeUrl(this.url), normalizeUrl(other.url));
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizeUrl(this.url));
    }

    /**
     * Strip tracking params (utm_*, ref, source) to normalize URL for dedup.
     * Example: https://blog.anthropic.com/post?utm_source=twitter -> https://blog.anthropic.com/post
     */
    public static String normalizeUrl(String url) {
        if (url == null) return "";
        int queryIndex = url.indexOf('?');
        String normalized = queryIndex > 0 ? url.substring(0, queryIndex) : url;
        return normalized.toLowerCase().replaceAll("/$", "");
    }
}
