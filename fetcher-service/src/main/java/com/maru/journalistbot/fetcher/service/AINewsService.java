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
public class AINewsService extends AbstractNewsService {

    public AINewsService(RssFetcher rssFetcher, NewsApiFetcher newsApiFetcher,
                         RedditFetcher redditFetcher, HnFetcher hnFetcher) {
        super(rssFetcher, newsApiFetcher, redditFetcher, hnFetcher);
    }

    @Override public NewsCategory getCategory() { return NewsCategory.AI; }
    @Override public String getCategoryDisplayName() { return NewsCategory.AI.getDisplayName(); }

    @Override
    public List<String> getSupportedKeywords() {
        return List.of("ai", "claude", "gemini", "openai", "chatgpt", "llm", "gpt",
                "machine learning", "ml", "anthropic", "deepmind", "mistral");
    }

    @Override
    protected List<RssSource> getRssSources() {
        return List.of(
                RssSource.of("Anthropic Blog",    "https://www.anthropic.com/rss.xml"),
                RssSource.of("Google AI Blog",    "https://blog.google/technology/ai/rss/"),
                RssSource.of("OpenAI Blog",       "https://openai.com/blog/rss.xml"),
                RssSource.of("Hugging Face Blog", "https://huggingface.co/blog/feed.xml"),
                RssSource.of("The Verge - AI",    "https://www.theverge.com/ai-artificial-intelligence/rss/index.xml"),
                RssSource.of("VentureBeat AI",    "https://venturebeat.com/category/ai/feed/")
        );
    }

    @Override
    protected List<String> getNewsApiKeywords() {
        return List.of("Claude AI Anthropic", "Gemini Google AI", "large language model");
    }

    @Override
    protected List<String> getRedditSubreddits() {
        return List.of("MachineLearning", "artificial", "LocalLLaMA", "singularity");
    }

    @Override
    protected List<String> getHnQueries() {
        return List.of("large language model", "Claude Anthropic", "GPT OpenAI");
    }
}
