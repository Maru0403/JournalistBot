package com.maru.journalistbot.notification.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SentArticleRepository extends MongoRepository<SentArticle, String> {

    boolean existsByArticleHashAndPlatform(String articleHash, String platform);

    void deleteByExpireAtBefore(java.time.LocalDateTime dateTime);
}
