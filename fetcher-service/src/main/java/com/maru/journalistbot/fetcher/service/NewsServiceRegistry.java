package com.maru.journalistbot.fetcher.service;

import com.maru.journalistbot.common.model.NewsCategory;
import com.maru.journalistbot.fetcher.domain.NewsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Registry chứa tất cả NewsService implementations.
 * Spring tự inject mọi bean implement NewsService — OCP: thêm category = thêm class mới, không sửa class này.
 */
@Component
@Slf4j
public class NewsServiceRegistry {

    private final List<NewsService> services;

    public NewsServiceRegistry(List<NewsService> services) {
        this.services = services;
        log.info("[REGISTRY] Loaded {} news services: {}",
                services.size(),
                services.stream().map(s -> s.getCategory().name()).toList());
    }

    public NewsService getByCategory(NewsCategory category) {
        return services.stream()
                .filter(s -> s.getCategory() == category)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No NewsService registered for category: " + category));
    }

    public List<NewsCategory> getAllCategories() {
        return services.stream().map(NewsService::getCategory).toList();
    }

    /**
     * Find a NewsService by keyword (case-insensitive).
     * Used by OnDemandNewsController for /news command REST API.
     */
    public Optional<NewsService> findByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return Optional.empty();
        String lowerKeyword = keyword.toLowerCase().trim();
        return services.stream()
                .filter(s -> s.getSupportedKeywords().stream()
                        .anyMatch(k -> k.toLowerCase().contains(lowerKeyword)
                                || lowerKeyword.contains(k.toLowerCase())))
                .findFirst();
    }

    public List<String> getAllSupportedKeywords() {
        return services.stream()
                .flatMap(s -> s.getSupportedKeywords().stream())
                .distinct()
                .toList();
    }
}
