package com.maru.journalistbot.fetcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Fetcher Service — Spring Boot entry point.
 *
 * Trách nhiệm duy nhất:
 *   Fetch news từ RSS / HN Algolia / Reddit / NewsAPI
 *   → publish ArticleFetchedEvent lên Kafka topic "news.fetched"
 *
 * Không có Discord/Telegram/AI ở đây — đúng nguyên tắc SRP.
 *
 * @EnableRetry: cho phép @Retryable annotation hoạt động trên fetcher classes
 * @EnableAsync: Virtual Threads (Java 21) cho các I/O operations bất đồng bộ
 */
@SpringBootApplication
@EnableRetry
@EnableAsync
public class FetcherApplication {

    public static void main(String[] args) {
        SpringApplication.run(FetcherApplication.class, args);
    }
}
