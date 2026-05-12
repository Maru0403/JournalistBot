package com.maru.journalistbot.domain.port;

import com.maru.journalistbot.domain.model.NewsArticle;
import com.maru.journalistbot.domain.model.NewsCategory;
import com.maru.journalistbot.domain.model.Platform;

import java.util.List;

/**
 * Output port — contract for all platform-specific message adapters.
 * Each platform (Discord, Telegram, Zalo) implements this.
 *
 * ISP: interface is focused solely on message delivery concerns.
 * DIP: BroadcastService depends on this port, not on concrete adapters.
 */
public interface NewsMessagePort {

    Platform getPlatform();

    /**
     * Send news articles to the default channel/chat for this platform.
     */
    void sendNews(List<NewsArticle> articles, NewsCategory category, String formattedMessage);

    /**
     * Reply to a specific user/chat (for /news command responses).
     * @param chatId  platform-specific chat or user identifier
     * @param message formatted message to send
     */
    void replyToUser(String chatId, String message);

    /** Whether this adapter is currently connected and ready */
    boolean isConnected();
}
