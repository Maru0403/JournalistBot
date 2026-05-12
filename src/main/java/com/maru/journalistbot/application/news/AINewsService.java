package com.maru.journalistbot.application.news;

import com.maru.journalistbot.domain.model.NewsCategory;
import com.maru.journalistbot.domain.model.RssSource;
import com.maru.journalistbot.infrastructure.fetcher.NewsApiFetcher;
import com.maru.journalistbot.infrastructure.fetcher.RedditFetcher;
import com.maru.journalistbot.infrastructure.fetcher.RssFetcher;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI & Machine Learning news service.
 * Covers: Claude, Gemini, Anthropic, OpenAI, LLMs.
 *
 * Phase 2: Added Reddit sources (r/MachineLearning, r/artificial, r/LocalLLaMA).
 */
@Service
public class AINewsService extends AbstractNewsService {

    public AINewsService(RssFetcher rssFetcher, NewsApiFetcher newsApiFetcher, RedditFetcher redditFetcher) {
        super(rssFetcher, newsApiFetcher, redditFetcher);
    }

    @Override
    public NewsCategory getCategory() { return NewsCategory.AI; }

    @Override
    public String getCategoryDisplayName() { return NewsCategory.AI.getDisplayName(); }

    @Override
    public List<String> getSupportedKeywords() {
        return List.of(
                "ai", "claude", "claude code", "gemini", "openai",
                "codex", "anthropic", "chatgpt", "llm", "gpt",
                "machine learning", "ml", "deepmind", "mistral", "llama"
        );
    }

    @Override
    protected List<RssSource> getRssSources() {
        return List.of(
                RssSource.of("Anthropic Blog",     "https://www.anthropic.com/rss.xml"),
                RssSource.of("Google AI Blog",      "https://blog.google/technology/ai/rss/"),
                RssSource.of("OpenAI Blog",         "https://openai.com/blog/rss.xml"),
                RssSource.of("Hugging Face Blog",   "https://huggingface.co/blog/feed.xml"),
                RssSource.of("The Verge - AI",      "https://www.theverge.com/ai-artificial-intelligence/rss/index.xml"),
                RssSource.of("MIT Tech Review AI",  "https://www.technologyreview.com/topic/artificial-intelligence/feed/"),
                RssSource.of("VentureBeat AI",      "https://venturebeat.com/category/ai/feed/")
        );
    }

    @Override
    protected List<String> getNewsApiKeywords() {
        return List.of(
                "Claude AI Anthropic",
                "Gemini Google AI",
                "large language model"
        );
    }

    /** Phase 2: AI-focused subreddits */
    @Override
    protected List<String> getRedditSubreddits() {
        return List.of("MachineLearning", "artificial", "LocalLLaMA", "singularity");
    }
}
