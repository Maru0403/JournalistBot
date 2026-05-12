package com.maru.journalistbot.infrastructure.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SentArticleRepository extends MongoRepository<SentArticle, String> {

    boolean existsByArticleHashAndPlatform(String articleHash, String platform);

    List<SentArticle> findByPlatformAndSentAtAfter(String platform, LocalDateTime after);

    void deleteByExpireAtBefore(LocalDateTime dateTime);
}
