package com.maru.journalistbot.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * HTTP client for Google Gemini API (generateContent endpoint).
 *
 * API reference: https://ai.google.dev/api/generate-content
 * Model: gemini-1.5-flash — fast, free tier (1,500 req/day, 1M tokens/min).
 * Get API key: https://aistudio.google.com/app/apikey
 *
 * Used as FALLBACK when Claude API key is not configured or call fails.
 * Strategy in AISummarizerService: Claude → Gemini → plain-text format.
 *
 * Returns null on failure → AISummarizerService falls back to plain text (no crash).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiApiClient {

    private final WebClient webClient;

    private static final String API_BASE  = "https://generativelanguage.googleapis.com/v1beta/models";
    private static final String MODEL     = "gemini-1.5-flash";

    @Value("${bot.ai.gemini.api-key:}")
    private String apiKey;

    /** True only when a real API key is present */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("your-");
    }

    /**
     * Send a prompt to Gemini and return the text response.
     *
     * @CircuitBreaker("gemini"): if this method fails repeatedly
     *   → circuit opens → fallbackChat() returns null
     *   → AISummarizerService falls back to plain text
     *
     * @param prompt    the full prompt text
     * @param maxTokens max output tokens
     * @return response text, or null if the call failed / circuit is open
     */
    @CircuitBreaker(name = "gemini", fallbackMethod = "fallbackChat")
    public String chat(String prompt, int maxTokens) {
        if (!isConfigured()) {
            log.debug("[GEMINI] API key not configured — skipping AI call");
            return null;
        }

        String url = API_BASE + "/" + MODEL + ":generateContent";

        GeminiRequest request = new GeminiRequest(
                List.of(new Content(List.of(new Part(prompt)))),
                new GenerationConfig(maxTokens, 0.7f)
        );

        GeminiResponse response = webClient.post()
                .uri(url + "?key=" + apiKey)
                .header("content-type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GeminiResponse.class)
                .block();

        if (response == null
                || response.candidates() == null
                || response.candidates().isEmpty()) {
            log.warn("[GEMINI] Empty response received");
            return null;
        }

        Candidate first = response.candidates().get(0);
        if (first.content() == null
                || first.content().parts() == null
                || first.content().parts().isEmpty()) {
            log.warn("[GEMINI] No content parts in response");
            return null;
        }

        String text = first.content().parts().get(0).text();
        log.debug("[GEMINI] Response OK — {} chars, finish_reason={}",
                text != null ? text.length() : 0, first.finishReason());
        return text;
    }

    /** Circuit Breaker fallback — returns null so AISummarizerService uses plain text. */
    private String fallbackChat(String prompt, int maxTokens, Throwable ex) {
        log.warn("[GEMINI] Circuit OPEN or error — returning null for fallback chain. Reason: {}",
                ex.getMessage());
        return null;
    }

    // ── Request / Response records ────────────────────────────────────────────

    /**
     * Gemini generateContent request body.
     * Field names must match the API exactly.
     */
    record GeminiRequest(
            List<Content> contents,
            GenerationConfig generationConfig
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)   // Gemini returns "role":"model" in Content
    record Content(List<Part> parts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String text) {}

    record GenerationConfig(
            int maxOutputTokens,
            float temperature
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiResponse(
            List<Candidate> candidates,
            Map<String, Object> usageMetadata
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(
            Content content,
            String finishReason,
            int index
    ) {}
}
