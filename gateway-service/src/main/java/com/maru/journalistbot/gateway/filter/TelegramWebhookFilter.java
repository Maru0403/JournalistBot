package com.maru.journalistbot.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway filter — verifies Telegram webhook secret token.
 *
 * Telegram sends the header: X-Telegram-Bot-Api-Secret-Token: <secret>
 * If the header is missing or incorrect → 401 Unauthorized.
 *
 * This filter is applied ONLY to the /api/telegram/webhook route.
 * Other routes (admin, health) are not affected.
 *
 * Config:
 *   bot.telegram.webhook-secret: ${TELEGRAM_WEBHOOK_SECRET:}
 *
 * If TELEGRAM_WEBHOOK_SECRET is blank (not configured) → filter is BYPASSED.
 * This allows local development without webhook setup.
 */
@Component
@Slf4j
public class TelegramWebhookFilter extends AbstractGatewayFilterFactory<TelegramWebhookFilter.Config> {

    private static final String TELEGRAM_SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    @Value("${bot.telegram.webhook-secret:}")
    private String webhookSecret;

    public TelegramWebhookFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Bypass if secret not configured (local dev mode)
            if (webhookSecret == null || webhookSecret.isBlank()) {
                log.debug("[TELEGRAM-FILTER] Webhook secret not configured — bypassing verification");
                return chain.filter(exchange);
            }

            String receivedToken = exchange.getRequest().getHeaders().getFirst(TELEGRAM_SECRET_HEADER);

            if (!webhookSecret.equals(receivedToken)) {
                log.warn("[TELEGRAM-FILTER] Invalid webhook token from IP={}",
                        exchange.getRequest().getRemoteAddress());
                return unauthorized(exchange);
            }

            log.debug("[TELEGRAM-FILTER] Webhook token verified OK");
            return chain.filter(exchange);
        };
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    public static class Config {
        // No config fields needed — uses @Value injection from application.yml
    }
}
