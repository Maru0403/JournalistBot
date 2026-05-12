package com.maru.journalistbot.application.news;

import com.maru.journalistbot.domain.model.NewsCategory;
import com.maru.journalistbot.domain.model.RssSource;
import com.maru.journalistbot.infrastructure.fetcher.NewsApiFetcher;
import com.maru.journalistbot.infrastructure.fetcher.RedditFetcher;
import com.maru.journalistbot.infrastructure.fetcher.RssFetcher;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Programming Languages & Frameworks news service.
 * Covers: Java, Python, Rust, Go, JavaScript, TypeScript, Spring...
 *
 * Phase 2: Added Reddit sources (r/programming, r/java, r/Python).
 */
@Service
public class ProgrammingNewsService extends AbstractNewsService {

    public ProgrammingNewsService(RssFetcher rssFetcher, NewsApiFetcher newsApiFetcher, RedditFetcher redditFetcher) {
        super(rssFetcher, newsApiFetcher, redditFetcher);
    }

    @Override
    public NewsCategory getCategory() { return NewsCategory.PROGRAMMING; }

    @Override
    public String getCategoryDisplayName() { return NewsCategory.PROGRAMMING.getDisplayName(); }

    @Override
    public List<String> getSupportedKeywords() {
        return List.of(
                "java", "python", "rust", "golang", "go",
                "javascript", "typescript", "kotlin", "swift",
                "spring", "spring boot", "react", "vue", "angular",
                "programming", "developer", "backend", "frontend"
        );
    }

    @Override
    protected List<RssSource> getRssSources() {
        return List.of(
                RssSource.of("Dev.to",             "https://dev.to/feed"),
                RssSource.of("InfoQ",              "https://feed.infoq.com/"),
                RssSource.of("Hacker News Front",  "https://hnrss.org/frontpage"),
                RssSource.of("Hacker News - Java", "https://hnrss.org/newest?q=java"),
                RssSource.of("Spring Blog",        "https://spring.io/blog.atom"),
                RssSource.of("This Week in Rust",  "https://this-week-in-rust.org/rss.xml"),
                RssSource.of("Python Weekly",      "https://www.pythonweekly.com/rss/index.xml")
        );
    }

    @Override
    protected List<String> getNewsApiKeywords() {
        return List.of(
                "Java Spring Boot",
                "Python programming 2026",
                "Rust programming language"
        );
    }

    /** Phase 2: Programming-focused subreddits */
    @Override
    protected List<String> getRedditSubreddits() {
        return List.of("programming", "java", "Python", "rust");
    }
}
