package com.maru.journalistbot.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Gateway Service — Phase 5 Step 5
 *
 * Single entry point from the outside world.
 * Responsibilities:
 *   - Route /fetcher/** → fetcher-service:8081
 *   - Route /summarizer/** → summarizer-service:8082
 *   - Route /notification/** → notification-service:8083
 *   - Route /api/telegram/webhook → notification-service:8083 (with secret token verification)
 *   - IP rate limiting (Redis token bucket) on all routes
 *   - Actuator health aggregation
 *
 * Spring Cloud Gateway is WebFlux-based — all filters are reactive (ServerWebExchange).
 */
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
