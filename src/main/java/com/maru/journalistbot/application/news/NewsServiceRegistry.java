package com.maru.journalistbot.application.news;

import com.maru.journalistbot.domain.model.NewsCategory;
import com.maru.journalistbot.domain.port.NewsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Registry holding all NewsService implementations.
 * Spring auto-injects every bean implementing NewsService.
 *
 * OCP: adding a new category only requires a new @Service class — zero changes here.
 */
@Component
@Slf4j
public class NewsServiceRegistry {

    private final List<NewsService> services;

    public NewsServiceRegistry(List<NewsService> services) {
        this.services = services;
        log.info("NewsServiceRegistry initialized with {} services: {}",
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

    public Optional<NewsService> findByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return Optional.empty();
        String lowerKeyword = keyword.toLowerCase().trim();
        return services.stream()
                .filter(s -> s.getSupportedKeywords().stream()
                        .anyMatch(k -> k.contains(lowerKeyword) || lowerKeyword.contains(k)))
                .findFirst();
    }

    public List<NewsCategory> getAllCategories() {
        return services.stream().map(NewsService::getCategory).toList();
    }

    public List<String> getAllSupportedKeywords() {
        return services.stream()
                .flatMap(s -> s.getSupportedKeywords().stream())
                .distinct()
                .sorted()
                .toList();
    }
}
