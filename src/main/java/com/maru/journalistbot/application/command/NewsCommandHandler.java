package com.maru.journalistbot.application.command;

import com.maru.journalistbot.application.ai.AISummarizerService;
import com.maru.journalistbot.application.news.NewsServiceRegistry;
import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.domain.port.NewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Handles /news <keyword> commands from all platforms.
 * SRP: only responsible for command parsing and response formatting.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NewsCommandHandler {

    private final NewsServiceRegistry registry;
    private final AISummarizerService summarizerService;

    @Value("${bot.schedule.news-fetch-limit:5}")
    private int fetchLimit;

    public String handle(String keyword, String requesterId) {
        log.info("[COMMAND] /news '{}' from user={}", keyword, requesterId);

        if (keyword == null || keyword.isBlank()) {
            return buildHelpMessage();
        }

        Optional<NewsService> serviceOpt = registry.findByKeyword(keyword.trim());
        if (serviceOpt.isEmpty()) {
            return buildNotFoundMessage(keyword);
        }

        try {
            List<NewsArticle> articles = serviceOpt.get().fetchLatestNews(fetchLimit);
            if (articles.isEmpty()) {
                return "🔍 Khong tim thay tin tuc moi ve *" + keyword + "* luc nay. Thu lai sau nhe!";
            }
            return summarizerService.summarize(articles, keyword);
        } catch (Exception e) {
            log.error("[COMMAND] Failed to fetch news for keyword '{}': {}", keyword, e.getMessage());
            return "❌ Da xay ra loi khi lay tin tuc. Thu lai sau nhe!";
        }
    }

    private String buildHelpMessage() {
        List<String> keywords = registry.getAllSupportedKeywords();
        return """
                📖 *Cach dung lenh /news:*
                `/news <tu khoa>`

                *Vi du:*
                `/news claude` — tin tuc ve Claude AI
                `/news java` — tin tuc ve Java

                *Tu khoa ho tro:*
                %s
                """.formatted(String.join(", ", keywords));
    }

    private String buildNotFoundMessage(String keyword) {
        return "❓ Khong tim thay category cho tu khoa: *%s*\n\nThu: %s"
                .formatted(keyword, String.join(", ", registry.getAllSupportedKeywords()));
    }
}
