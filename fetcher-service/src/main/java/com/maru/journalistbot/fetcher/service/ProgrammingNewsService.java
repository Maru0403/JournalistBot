package com.maru.journalistbot.fetcher.service;

import com.maru.journalistbot.common.model.NewsCategory;
import com.maru.journalistbot.fetcher.domain.RssSource;
import com.maru.journalistbot.fetcher.infrastructure.HnFetcher;
import com.maru.journalistbot.fetcher.infrastructure.NewsApiFetcher;
import com.maru.journalistbot.fetcher.infrastructure.RedditFetcher;
import com.maru.journalistbot.fetcher.infrastructure.RssFetcher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProgrammingNewsService extends AbstractNewsService {

    public ProgrammingNewsService(RssFetcher rssFetcher, NewsApiFetcher newsApiFetcher,
                                  RedditFetcher redditFetcher, HnFetcher hnFetcher) {
        super(rssFetcher, newsApiFetcher, redditFetcher, hnFetcher);
    }

    @Override public NewsCategory getCategory() { return NewsCategory.PROGRAMMING; }
    @Override public String getCategoryDisplayName() { return NewsCategory.PROGRAMMING.getDisplayName(); }

    @Override
    public List<String> getSupportedKeywords() {
        return List.of("java", "python", "rust", "golang", "javascript", "typescript",
                "kotlin", "spring", "spring boot", "react", "programming", "backend");
    }

    @Override
    protected List<RssSource> getRssSources() {
        return List.of(
                RssSource.of("Dev.to",            "https://dev.to/feed"),
                RssSource.of("InfoQ",             "https://feed.infoq.com/"),
                RssSource.of("Hacker News Front", "https://hnrss.org/frontpage"),
                RssSource.of("Spring Blog",       "https://spring.io/blog.atom"),
                RssSource.of("This Week in Rust", "https://this-week-in-rust.org/rss.xml")
        );
    }

    @Override
    protected List<String> getNewsApiKeywords() {
        return List.of("Java Spring Boot", "Python programming 2026", "Rust programming language");
    }

    @Override
    protected List<String> getRedditSubreddits() {
        return List.of("programming", "java", "Python", "rust");
    }

    @Override
    protected List<String> getHnQueries() {
        return List.of("Java Spring Boot", "Rust programming");
    }
}
