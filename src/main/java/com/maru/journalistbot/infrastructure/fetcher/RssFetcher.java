package com.maru.journalistbot.infrastructure.fetcher;

import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.domain.model.NewsCategory;
import com.maru.journalistbot.domain.model.RssSource;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class RssFetcher {

    @Retryable(
        retryFor = Exception.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2.0)
    )
    public List<NewsArticle> fetch(RssSource source, NewsCategory category) {
        log.debug("Fetching RSS from [{}]: {}", source.getName(), source.getUrl());
        try {
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(new URL(source.getUrl())));
            List<NewsArticle> articles = feed.getEntries().stream()
                    .map(entry -> mapToArticle(entry, source.getName(), category))
                    .toList();
            log.debug("Fetched {} articles from [{}]", articles.size(), source.getName());
            return articles;
        } catch (Exception e) {
            log.warn("Failed to fetch RSS from [{}]: {}", source.getName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private NewsArticle mapToArticle(SyndEntry entry, String sourceName, NewsCategory category) {
        return NewsArticle.builder()
                .title(cleanText(entry.getTitle()))
                .url(entry.getLink())
                .description(extractDescription(entry))
                .sourceName(sourceName)
                .category(category)
                .publishedAt(toLocalDateTime(entry.getPublishedDate()))
                .build();
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]+>", "").trim();
    }

    private String extractDescription(SyndEntry entry) {
        if (entry.getDescription() != null) {
            String desc = entry.getDescription().getValue();
            if (desc != null) {
                desc = desc.replaceAll("<[^>]+>", "").trim();
                return desc.length() > 300 ? desc.substring(0, 297) + "..." : desc;
            }
        }
        return "";
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return LocalDateTime.now();
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
