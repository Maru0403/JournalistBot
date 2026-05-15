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
public class GameDevNewsService extends AbstractNewsService {

    public GameDevNewsService(RssFetcher rssFetcher, NewsApiFetcher newsApiFetcher,
                              RedditFetcher redditFetcher, HnFetcher hnFetcher) {
        super(rssFetcher, newsApiFetcher, redditFetcher, hnFetcher);
    }

    @Override public NewsCategory getCategory() { return NewsCategory.GAME_DEV; }
    @Override public String getCategoryDisplayName() { return NewsCategory.GAME_DEV.getDisplayName(); }

    @Override
    public List<String> getSupportedKeywords() {
        return List.of("gamedev", "game dev", "unity", "unreal engine", "godot",
                "indie game", "game design", "game programming");
    }

    @Override
    protected List<RssSource> getRssSources() {
        return List.of(
                RssSource.of("Game Developer", "https://www.gamedeveloper.com/rss.xml"),
                RssSource.of("Unity Blog",     "https://blog.unity.com/rss.xml"),
                RssSource.of("Godot Blog",     "https://godotengine.org/rss.xml")
        );
    }

    @Override
    protected List<String> getNewsApiKeywords() {
        return List.of("Unity game engine 2026", "Godot game development", "indie game release");
    }

    @Override
    protected List<String> getRedditSubreddits() {
        return List.of("gamedev", "indiegaming", "Unity3D", "godot");
    }

    @Override
    protected List<String> getHnQueries() {
        return List.of("game development engine");
    }
}
