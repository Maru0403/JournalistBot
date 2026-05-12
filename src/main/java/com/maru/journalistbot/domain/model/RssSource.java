package com.maru.journalistbot.domain.model;

import lombok.Value;

@Value
public class RssSource {

    String name;
    String url;

    public static RssSource of(String name, String url) {
        return new RssSource(name, url);
    }
}
