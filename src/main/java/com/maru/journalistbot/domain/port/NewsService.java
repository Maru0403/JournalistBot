package com.maru.journalistbot.domain.port;

import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.domain.model.NewsCategory;

import java.util.List;

/**
 * Input port — contract for all news category services.
 * Each category (AI, Programming, GameDev...) implements this independently.
 *
 * OCP: adding a new category only requires a new @Service class — zero changes here.
 * DIP: application layer depends on this interface, not on concrete implementations.
 */
public interface NewsService {

    NewsCategory getCategory();

    String getCategoryDisplayName();

    /**
     * Fetch latest articles for this category.
     * @param limit max number of articles to return
     * @return sorted list (newest first), deduplicated by URL
     */
    List<NewsArticle> fetchLatestNews(int limit);

    /**
     * Keywords this service supports for /news command routing.
     * Example: ["claude", "gemini", "ai", "llm"] for AINewsService
     */
    List<String> getSupportedKeywords();
}
