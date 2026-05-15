package com.maru.journalistbot.fetcher.domain;

import com.maru.journalistbot.common.model.NewsCategory;

import java.util.List;

/**
 * Input port — contract cho tất cả news category services trong fetcher-service.
 *
 * DIP: NewsJob (scheduler) phụ thuộc vào interface này,
 *      không phụ thuộc vào concrete AINewsService/ProgrammingNewsService.
 *
 * OCP: Thêm category mới chỉ cần tạo class mới implement interface này.
 */
public interface NewsService {

    NewsCategory getCategory();

    String getCategoryDisplayName();

    List<NewsArticle> fetchLatestNews(int limit);

    /** Keywords hỗ trợ cho /news command routing (dùng ở notification-service) */
    List<String> getSupportedKeywords();
}
