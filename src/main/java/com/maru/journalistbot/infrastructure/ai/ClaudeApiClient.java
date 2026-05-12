package com.maru.journalistbot.infrastructure.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * HTTP client for Anthropic's Claude API (Messages endpoint).
 *
 * API reference: https://docs.anthropic.com/en/api/messages
 * Model: claude-haiku-4-5-20251001 — fast, cheap, great for summarization.
 *
 * Returns null if API key is not configured → AISummarizerService falls back
 * to the plain-text formatter (no crash, graceful degradation).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClaudeApiClient {

    private final WebClient webClient;

    private static final String API_URL     = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final String MODEL       = "claude-haiku-4-5-20251001";

    @Value("${bot.ai.anthropic.api-key:}")
    private String apiKey;

    /** True only when a real API key is present (not a placeholder). */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("your-");
    }

    /**
     * Send a prompt to Claude and return the text response.
     *
     * @param userPrompt  the full prompt to send as the user turn
     * @param maxTokens   max tokens in the response (keep ≤ 1024 for Haiku to stay cheap)
     * @return response text, or null if the call failed
     */
    public String chat(String userPrompt, int maxTokens) {
        if (!isConfigured()) {
            log.debug("[CLAUDE] API key not configured — skipping AI call");
            return null;
        }

        try {
            ClaudeRequest request = new ClaudeRequest(
                    MODEL,
                    maxTokens,
                    List.of(Map.of("role", "user", "content", userPrompt))
            );

            ClaudeResponse response = webClient.post()
                    .uri(API_URL)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .header("content-type", "application/json")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ClaudeResponse.class)
                    .block();

            if (response == null || response.content() == null || response.content().isEmpty()) {
                log.warn("[CLAUDE] Empty response received");
                return null;
            }

            String text = response.content().get(0).text();
            log.debug("[CLAUDE] Response OK — {} chars, stop_reason={}", text.length(), response.stop_reason());
            return text;

        } catch (WebClientResponseException e) {
            // Log HTTP error details (4xx/5xx) but never expose the API key in logs
            log.error("[CLAUDE] HTTP {} error: {}", e.getStatusCode().value(), e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("[CLAUDE] Unexpected error during API call: {}", e.getMessage());
            return null;
        }
    }

    // ── Request / Response records ───────────────────────────────────────────

    /**
     * Anthropic Messages API request body.
     * Field names must match the API exactly (snake_case).
     */
    record ClaudeRequest(
            String model,
            int max_tokens,
            List<Map<String, String>> messages
    ) {}

    record ClaudeResponse(
            String id,
            String type,
            String role,
            List<ContentBlock> content,
            String model,
            String stop_reason
    ) {}

    record ContentBlock(String type, String text) {}
}
