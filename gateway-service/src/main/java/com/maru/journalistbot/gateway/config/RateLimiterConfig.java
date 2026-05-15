package com.maru.journalistbot.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * IP-based rate limiting configuration.
 *
 * Uses Spring Cloud Gateway's built-in RequestRateLimiter filter backed by Redis.
 * The KeyResolver extracts the client IP from X-Forwarded-For (if behind a proxy)
 * or the direct remote address.
 *
 * Rate limits are configured per route in application.yml:
 *   redis-rate-limiter.replenishRate  → tokens added per second
 *   redis-rate-limiter.burstCapacity  → max burst tokens
 *
 * Example (default):
 *   replenishRate=10, burstCapacity=20
 *   → max 20 requests in a burst, sustained 10 req/sec per IP
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Resolve rate limit key from client IP address.
     * Falls back to "anonymous" if IP cannot be determined.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                // X-Forwarded-For may contain comma-separated IPs (proxy chain) — use first
                String clientIp = forwardedFor.split(",")[0].trim();
                return Mono.just(clientIp);
            }
            if (exchange.getRequest().getRemoteAddress() != null) {
                return Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
            }
            return Mono.just("anonymous");
        };
    }
}
