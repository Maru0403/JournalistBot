package com.maru.journalistbot.summarizer.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * HTTP client cho Anthropic Claude API.
 * Model: claude-haiku-4-5-20251001 — nhanh, rẻ, phù hợp summarization.
 *
 * @CircuitBreaker("claude"):
 *   failureRate > 50% trong 10 calls → circuit OPEN
 *   → fallbackChat() trả về null ngay (không gọi HTTP)
 *   → AISummarizerService nhận null → thử Gemini tiếp
 *   → Sau 60s HALF-OPEN → test recovery
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

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("your-");
    }

    @CircuitBreaker(name = "claude", fallbackMethod = "fallbackChat")
    public String chat(String userPrompt, int maxTokens) {
        if (!isConfigured()) {
            log.debug("[CLAUDE] API key not configured — skipping");
            return null;
        }

        ClaudeRequest request = new ClaudeRequest(
                MODEL, maxTokens,
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
            log.warn("[CLAUDE] Empty response");
            return null;
        }

        String text = response.content().get(0).text();
        log.debug("[CLAUDE] OK — {} chars, stop_reason={}", text != null ? text.length() : 0, response.stopReason());
        return text;
    }

    private String fallbackChat(String userPrompt, int maxTokens, Throwable ex) {
        log.warn("[CLAUDE] Circuit OPEN — returning null → fallback to Gemini. Reason: {}", ex.getMessage());
        return null;
    }

    // ── Request/Response records ──────────────────────────────────────────────

    record ClaudeRequest(String model, int max_tokens, List<Map<String, String>> messages) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ClaudeResponse(List<ContentBlock> content, String stopReason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentBlock(String type, String text) {}
}
