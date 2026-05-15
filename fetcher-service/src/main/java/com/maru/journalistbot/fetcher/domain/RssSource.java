package com.maru.journalistbot.fetcher.domain;

import lombok.Value;

/**
 * Value Object đại diện cho một RSS feed source.
 * Immutable — dùng @Value (Lombok).
 */
@Value
public class RssSource {

    String name;
    String url;

    public static RssSource of(String name, String url) {
        return new RssSource(name, url);
    }
}
