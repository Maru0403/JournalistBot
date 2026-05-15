package com.maru.journalistbot.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO đại diện cho một bài viết bên trong Kafka event.
 *
 * Tại sao tách ra DTO riêng thay vì dùng domain NewsArticle?
 *   - Domain model (NewsArticle) thuộc về fetcher-service — không được share
 *   - DTO này là contract giữa các services qua Kafka
 *   - @JsonIgnoreProperties(ignoreUnknown=true) đảm bảo backward compatibility:
 *     nếu fetcher thêm field mới, summarizer/notification cũ không bị crash
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArticleItemDto {

    private String title;
    private String url;
    private String description;
    private String sourceName;
    private LocalDateTime publishedAt;
}
