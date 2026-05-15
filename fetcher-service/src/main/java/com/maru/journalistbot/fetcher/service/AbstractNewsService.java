package com.maru.journalistbot.fetcher.service;

import com.maru.journalistbot.fetcher.domain.NewsArticle;
import com.maru.journalistbot.fetcher.domain.NewsService;
import com.maru.journalistbot.fetcher.domain.RssSource;
import com.maru.journalistbot.fetcher.infrastructure.HnFetcher;
import com.maru.journalistbot.fetcher.infrastructure.NewsApiFetcher;
import com.maru.journalistbot.fetcher.infrastructure.RedditFetcher;
import com.maru.journalistbot.fetcher.infrastructure.RssFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Template Method Pattern — shared fetch/merge/dedup logic.
 * Subclasses chỉ khai báo RSS sources, NewsAPI keywords, Reddit subreddits, HN queries.
 *
 * Layer thứ tự ưu tiên:
 *   1. RSS feeds (chất lượng cao, không giới hạn)
 *   2. NewsAPI (keyword search, rate limited 100/day)
 *   3. Reddit (community-driven, 60/min)
 *   4. HN Algolia (tech-focused, 10k/hour)
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractNewsService implements NewsService {

    protected final RssFetcher rssFetcher;
    protected final NewsApiFetcher newsApiFetcher;
    protected final RedditFetcher redditFetcher;
    protected final HnFetcher hnFetcher;

    @Override
    public List<NewsArticle> fetchLatestNews(int limit) {
        log.info("[{}] Fetching latest {} articles...", getCategory(), limit);
        List<NewsArticle> all = new ArrayList<>();

        for (RssSource source : getRssSources()) {
            List<NewsArticle> fetched = rssFetcher.fetch(source, getCategory());
            all.addAll(fetched);
            log.debug("[{}] RSS [{}]: {} articles", getCategory(), source.getName(), fetched.size());
        }

        for (String keyword : getNewsApiKeywords()) {
            List<NewsArticle> fetched = newsApiFetcher.searchByKeyword(keyword, 5, getCategory());
            all.addAll(fetched);
            log.debug("[{}] NewsAPI [{}]: {} articles", getCategory(), keyword, fetched.size());
        }

        for (String subreddit : getRedditSubreddits()) {
            List<NewsArticle> fetched = redditFetcher.fetchTopPosts(subreddit, 5, getCategory());
            all.addAll(fetched);
            log.debug("[{}] Reddit [r/{}]: {} posts", getCategory(), subreddit, fetched.size());
        }

        for (String query : getHnQueries()) {
            List<NewsArticle> fetched = hnFetcher.searchTopStories(query, 5, getCategory());
            all.addAll(fetched);
            log.debug("[{}] HN [{}]: {} posts", getCategory(), query, fetched.size());
        }

        List<NewsArticle> unique = all.stream()
                .distinct()
                .sorted(Comparator.comparing(
                        NewsArticle::getPublishedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(limit)
                .toList();

        log.info("[{}] Total after dedup: {}/{} raw", getCategory(), unique.size(), all.size());
        return unique;
    }

    // ── Subclass hooks ─────────────────────────────────────────────────────

    protected abstract List<RssSource> getRssSources();
    protected abstract List<String> getNewsApiKeywords();

    /** Default empty — subclass override nếu cần Reddit */
    protected List<String> getRedditSubreddits() { return List.of(); }

    /** Default empty — subclass override nếu cần HN Algolia */
    protected List<String> getHnQueries() { return List.of(); }
}
