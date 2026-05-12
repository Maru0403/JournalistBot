package com.maru.journalistbot.application.news;

import com.maru.journalistbot.domain.model.NewsCategory;
import com.maru.journalistbot.domain.model.RssSource;
import com.maru.journalistbot.infrastructure.fetcher.NewsApiFetcher;
import com.maru.journalistbot.infrastructure.fetcher.RedditFetcher;
import com.maru.journalistbot.infrastructure.fetcher.RssFetcher;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Game Development news service.
 * Covers: Unity, Unreal Engine, Godot, indie games, game design.
 *
 * Added in Phase 2 to resolve the GAME_DEV enum orphan from Phase 1,
 * and to support the project owner's goal of learning game programming.
 */
@Service
public class GameDevNewsService extends AbstractNewsService {

    public GameDevNewsService(RssFetcher rssFetcher, NewsApiFetcher newsApiFetcher, RedditFetcher redditFetcher) {
        super(rssFetcher, newsApiFetcher, redditFetcher);
    }

    @Override
    public NewsCategory getCategory() { return NewsCategory.GAME_DEV; }

    @Override
    public String getCategoryDisplayName() { return NewsCategory.GAME_DEV.getDisplayName(); }

    @Override
    public List<String> getSupportedKeywords() {
        return List.of(
                "gamedev", "game dev", "game development",
                "unity", "unreal engine", "godot",
                "indie game", "indiegame", "game design",
                "game programming", "game engine"
        );
    }

    @Override
    protected List<RssSource> getRssSources() {
        return List.of(
                RssSource.of("Gamasutra",       "https://www.gamedeveloper.com/rss.xml"),
                RssSource.of("Unity Blog",      "https://blog.unity.com/rss.xml"),
                RssSource.of("Godot Blog",      "https://godotengine.org/rss.xml"),
                RssSource.of("Game Developer",  "https://www.gamedeveloper.com/rss.xml")
        );
    }

    @Override
    protected List<String> getNewsApiKeywords() {
        return List.of(
                "Unity game engine 2026",
                "Godot game development",
                "indie game release"
        );
    }

    /** Game dev subreddits — very active communities */
    @Override
    protected List<String> getRedditSubreddits() {
        return List.of("gamedev", "indiegaming", "Unity3D", "godot");
    }
}
