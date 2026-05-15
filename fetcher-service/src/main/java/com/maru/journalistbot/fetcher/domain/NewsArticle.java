package com.maru.journalistbot.fetcher.domain;

import com.maru.journalistbot.common.model.NewsCategory;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain model nội bộ của fetcher-service.
 * KHÔNG được share ra ngoài — các service khác nhận ArticleItemDto qua Kafka.
 *
 * Equals/hashCode dựa trên normalized URL để dedup khi merge từ nhiều source.
 */
@Getter
@Setter
@ToString
@Builder
public class NewsArticle {

    private String title;
    private String url;
    private String description;
    private String sourceName;
    private NewsCategory category;
    private LocalDateTime publishedAt;

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
     * Loại bỏ tracking params (utm_*, ref...) để normalize URL trước khi dedup.
     * "https://blog.anthropic.com/post?utm_source=twitter" → "https://blog.anthropic.com/post"
     */
    public static String normalizeUrl(String url) {
        if (url == null) return "";
        int queryIndex = url.indexOf('?');
        String normalized = queryIndex > 0 ? url.substring(0, queryIndex) : url;
        return normalized.toLowerCase().replaceAll("/$", "");
    }
}
