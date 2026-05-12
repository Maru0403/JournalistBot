package com.maru.journalistbot.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NewsCategory {

    AI("🤖 AI & Machine Learning",
            "Tin tức mới nhất về AI, LLM, Claude, Gemini, Codex..."),

    PROGRAMMING("💻 Programming Languages",
            "Tin tức mới nhất về các ngôn ngữ lập trình, frameworks, tools..."),

    GAME_DEV("🎮 Game Development",
            "Tin tức mới nhất về phát triển game, Unity, Unreal Engine...");

    private final String displayName;
    private final String description;
}
