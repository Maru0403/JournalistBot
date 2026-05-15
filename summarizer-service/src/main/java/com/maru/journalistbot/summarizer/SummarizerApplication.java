package com.maru.journalistbot.summarizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Summarizer Service — Spring Boot entry point.
 *
 * Trách nhiệm duy nhất:
 *   Consume ArticleFetchedEvent từ Kafka "news.fetched"
 *   → Tóm tắt bằng AI (Claude haiku → Gemini fallback → plain text)
 *   → Publish ArticleSummarizedEvent lên "news.summarized"
 *   → Publish ArticleFailedEvent lên "news.failed" nếu lỗi không recover được
 *
 * API keys Claude/Gemini chỉ tồn tại ở service này — các service khác không biết.
 */
@SpringBootApplication
@EnableRetry
public class SummarizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SummarizerApplication.class, args);
    }
}
