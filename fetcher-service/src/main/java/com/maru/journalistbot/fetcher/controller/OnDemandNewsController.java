package com.maru.journalistbot.fetcher.controller;

import com.maru.journalistbot.common.event.ArticleItemDto;
import com.maru.journalistbot.fetcher.domain.NewsArticle;
import com.maru.journalistbot.fetcher.domain.NewsService;
import com.maru.journalistbot.fetcher.service.NewsServiceRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * REST endpoint for on-demand news fetching.
 * Used by notification-service for /news command handling.
 *
 * Consumers:
 *   notification-service.NewsCommandService → GET /api/news/on-demand?keyword=ai&limit=5
 *
 * Note: This is a synchronous REST call (not Kafka) because /news is a request-response command.
 * Results are returned directly — NOT published to Kafka (to avoid false broadcast triggers).
 */
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
@Slf4j
public class OnDemandNewsController {

    private final NewsServiceRegistry registry;

    /**
     * Fetch articles on-demand by keyword.
     *
     * @param keyword keyword to search for (e.g. "ai", "java", "gamedev")
     * @param limit   max articles to return (default 5)
     * @return list of ArticleItemDto, or empty list if keyword not found
     */
    @GetMapping("/on-demand")
    public ResponseEntity<List<ArticleItemDto>> fetchOnDemand(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "5") int limit) {

        log.info("[ON-DEMAND] Fetching news for keyword='{}' limit={}", keyword, limit);

        Optional<NewsService> serviceOpt = registry.findByKeyword(keyword);
        if (serviceOpt.isEmpty()) {
            log.info("[ON-DEMAND] No service found for keyword='{}'", keyword);
            return ResponseEntity.ok(List.of());
        }

        NewsService service = serviceOpt.get();
        List<NewsArticle> articles = service.fetchLatestNews(limit);

        List<ArticleItemDto> result = articles.stream()
                .map(a -> ArticleItemDto.builder()
                        .title(a.getTitle())
                        .url(a.getUrl())
                        .description(a.getDescription())
                        .sourceName(a.getSourceName())
                        .publishedAt(a.getPublishedAt())
                        .build())
                .toList();

        log.info("[ON-DEMAND] Returning {} articles for keyword='{}'", result.size(), keyword);
        return ResponseEntity.ok(result);
    }

    /**
     * List all supported keywords (for /categories command).
     */
    @GetMapping("/keywords")
    public ResponseEntity<List<String>> getSupportedKeywords() {
        return ResponseEntity.ok(registry.getAllSupportedKeywords());
    }
}
