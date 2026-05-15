package com.maru.journalistbot.notification.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    Optional<UserSubscription> findByPlatformUserIdAndPlatform(String platformUserId, String platform);

    List<UserSubscription> findByPlatformAndActiveTrue(String platform);

    boolean existsByPlatformUserIdAndPlatform(String platformUserId, String platform);
}
