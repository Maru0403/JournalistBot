package com.maru.journalistbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Map;

@Configuration
public class RetryConfig {

    /**
     * RetryTemplate for external API calls (NewsAPI, RSS feeds).
     * Retries up to 3 times with exponential backoff: 1s → 2s → 4s.
     */
    @Bean
    public RetryTemplate apiRetryTemplate() {
        RetryTemplate template = new RetryTemplate();

        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(1000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(8000L);
        template.setBackOffPolicy(backOff);

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3,
                Map.of(Exception.class, true));
        template.setRetryPolicy(retryPolicy);

        return template;
    }
}
