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
 * HTTP client cho Google Gemini API.
 * Model: gemini-1.5-flash — free tier: 1,500 req/day, 1M tokens/min.
 *
 * Dùng làm FALLBACK khi Claude không configured hoặc Circuit Breaker mở.
 * Chain: Claude → Gemini → plain text (không bao giờ crash).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiApiClient {

    private final WebClient webClient;

    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models";
    private static final String MODEL    = "gemini-1.5-flash";

    @Value("${bot.ai.gemini.api-key:}")
    private String apiKey;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("your-");
    }

    @CircuitBreaker(name = "gemini", fallbackMethod = "fallbackChat")
    public String chat(String prompt, int maxTokens) {
        if (!isConfigured()) {
            log.debug("[GEMINI] API key not configured — skipping");
            return null;
        }

        String url = API_BASE + "/" + MODEL + ":generateContent?key=" + apiKey;

        GeminiRequest request = new GeminiRequest(
                List.of(new Content(List.of(new Part(prompt)))),
                new GenerationConfig(maxTokens, 0.7f)
        );

        GeminiResponse response = webClient.post()
                .uri(url)
                .header("content-type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GeminiResponse.class)
                .block();

        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            log.warn("[GEMINI] Empty response");
            return null;
        }

        Candidate first = response.candidates().get(0);
        if (first.content() == null || first.content().parts() == null || first.content().parts().isEmpty()) {
            log.warn("[GEMINI] No content parts");
            return null;
        }

        String text = first.content().parts().get(0).text();
        log.debug("[GEMINI] OK — {} chars", text != null ? text.length() : 0);
        return text;
    }

    private String fallbackChat(String prompt, int maxTokens, Throwable ex) {
        log.warn("[GEMINI] Circuit OPEN — returning null → fallback to plain text. Reason: {}", ex.getMessage());
        return null;
    }

    // ── Request/Response records ──────────────────────────────────────────────

    record GeminiRequest(List<Content> contents, GenerationConfig generationConfig) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(List<Part> parts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(String text) {}

    record GenerationConfig(int maxOutputTokens, float temperature) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiResponse(List<Candidate> candidates, Map<String, Object> usageMetadata) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content, String finishReason, int index) {}
}
