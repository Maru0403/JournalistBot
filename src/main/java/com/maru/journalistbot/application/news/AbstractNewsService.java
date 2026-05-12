package com.maru.journalistbot.application.news;

import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.domain.model.RssSource;
import com.maru.journalistbot.domain.port.NewsService;
import com.maru.journalistbot.infrastructure.fetcher.NewsApiFetcher;
import com.maru.journalistbot.infrastructure.fetcher.RedditFetcher;
import com.maru.journalistbot.infrastructure.fetcher.RssFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Template Method pattern — shared fetch/merge/dedup logic for all news services.
 * Subclasses only define their RSS sources, NewsAPI keywords, and Reddit subreddits.
 *
 * Phase 2: Added RedditFetcher support via getRedditSubreddits() hook.
 *   - Default returns empty list → backward compatible (OCP respected).
 *   - Subclasses opt in by overriding getRedditSubreddits() only.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractNewsService implements NewsService {

    protected final RssFetcher rssFetcher;
    protected final NewsApiFetcher newsApiFetcher;
    protected final RedditFetcher redditFetcher;

    @Override
    public List<NewsArticle> fetchLatestNews(int limit) {
        log.info("[{}] Fetching latest {} articles...", getCategory(), limit);

        List<NewsArticle> all = new ArrayList<>();

        // ── Layer 1: RSS feeds ─────────────────────────────────────────────
        for (RssSource source : getRssSources()) {
            List<NewsArticle> fetched = rssFetcher.fetch(source, getCategory());
            all.addAll(fetched);
            log.debug("[{}] RSS [{}]: {} articles", getCategory(), source.getName(), fetched.size());
        }

        // ── Layer 2: NewsAPI keyword search ───────────────────────────────
        for (String keyword : getNewsApiKeywords()) {
            List<NewsArticle> fetched = newsApiFetcher.searchByKeyword(keyword, 5, getCategory());
            all.addAll(fetched);
            log.debug("[{}] NewsAPI [{}]: {} articles", getCategory(), keyword, fetched.size());
        }

        // ── Layer 3: Reddit (Phase 2) ──────────────────────────────────────
        for (String subreddit : getRedditSubreddits()) {
            List<NewsArticle> fetched = redditFetcher.fetchTopPosts(subreddit, 5, getCategory());
            all.addAll(fetched);
            log.debug("[{}] Reddit [r/{}]: {} posts", getCategory(), subreddit, fetched.size());
        }

        // ── Dedup by URL + sort newest first ──────────────────────────────
        List<NewsArticle> unique = all.stream()
                .distinct()
                .sorted(Comparator.comparing(
                        NewsArticle::getPublishedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(limit)
                .toList();

        log.info("[{}] Total after dedup: {} articles (from {} raw)", getCategory(), unique.size(), all.size());
        return unique;
    }

    // ── Subclass hooks ─────────────────────────────────────────────────────

    protected abstract List<RssSource> getRssSources();

    protected abstract List<String> getNewsApiKeywords();

    /**
     * Reddit subreddits to fetch from.
     * Default: empty list — subclasses opt in by overriding.
     */
    protected List<String> getRedditSubreddits() {
        return List.of();
    }
}
