package com.maru.journalistbot.notification.platform;

import com.maru.journalistbot.common.event.ArticleItemDto;
import com.maru.journalistbot.common.model.Platform;

import java.util.List;

/**
 * Output port — contract for all platform-specific message adapters.
 *
 * Phase 5 change vs. monolith:
 *   - sendNews() now takes List<ArticleItemDto> (from common, via Kafka event)
 *     instead of List<NewsArticle> (monolith domain object)
 *   - formattedMessage is the AI summary from ArticleSummarizedEvent
 *
 * DIP: BroadcastService depends on this port, not on concrete adapters.
 * ISP: focused solely on message delivery concerns.
 */
public interface NewsMessagePort {

    Platform getPlatform();

    /**
     * Send news to all subscribed channels/chats for this platform.
     *
     * @param articles        articles that have NOT been sent yet (filtered by DeduplicationService)
     * @param category        news category name (e.g. "AI", "PROGRAMMING")
     * @param formattedMessage AI-generated summary ready to send
     */
    void sendNews(List<ArticleItemDto> articles, String category, String formattedMessage);

    /**
     * Reply to a specific user/chat (used for /news command responses).
     *
     * @param chatId  platform-specific identifier (chatId for Telegram, userId for Discord DM)
     * @param message formatted message to send
     */
    void replyToUser(String chatId, String message);

    /** Whether this adapter is currently connected and ready to send */
    boolean isConnected();
}
