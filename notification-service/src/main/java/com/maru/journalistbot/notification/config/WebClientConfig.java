package com.maru.journalistbot.notification.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * WebClient configuration for notification-service.
 * Used by NewsCommandService to call fetcher-service and summarizer-service REST APIs
 * for /news on-demand command handling.
 */
@Configuration
public class WebClientConfig {

    @Value("${services.fetcher-url:http://fetcher-service:8081}")
    private String fetcherServiceUrl;

    @Value("${services.summarizer-url:http://summarizer-service:8082}")
    private String summarizerServiceUrl;

    private HttpClient httpClient() {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(30));
    }

    @Bean("fetcherWebClient")
    public WebClient fetcherWebClient() {
        return WebClient.builder()
                .baseUrl(fetcherServiceUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient()))
                .build();
    }

    @Bean("summarizerWebClient")
    public WebClient summarizerWebClient() {
        return WebClient.builder()
                .baseUrl(summarizerServiceUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient()))
                .build();
    }
}
