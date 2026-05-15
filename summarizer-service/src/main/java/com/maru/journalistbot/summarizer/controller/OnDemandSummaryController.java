package com.maru.journalistbot.summarizer.controller;

import com.maru.journalistbot.common.event.ArticleItemDto;
import com.maru.journalistbot.summarizer.service.AISummarizerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoint for on-demand article summarization.
 * Used by notification-service for /news command handling.
 *
 * Consumers:
 *   notification-service.NewsCommandService → POST /api/summarize
 *
 * Note: Synchronous REST call (not Kafka) because /news is a request-response command.
 */
@RestController
@RequestMapping("/api/summarize")
@RequiredArgsConstructor
@Slf4j
public class OnDemandSummaryController {

    private final AISummarizerService summarizerService;

    /**
     * Summarize a list of articles on-demand.
     *
     * @param request contains articles + category keyword
     * @return plain text summary string
     */
    @PostMapping
    public ResponseEntity<String> summarize(@RequestBody SummarizeRequest request) {
        log.info("[ON-DEMAND] Summarize request for category='{}', {} articles",
                request.category(), request.articles().size());

        AISummarizerService.SummaryResult result =
                summarizerService.summarize(request.articles(), request.category());

        log.info("[ON-DEMAND] Summarized via provider={}", result.providerUsed());
        return ResponseEntity.ok(result.summary());
    }

    /** Request body from notification-service */
    public record SummarizeRequest(List<ArticleItemDto> articles, String category) {}
}
