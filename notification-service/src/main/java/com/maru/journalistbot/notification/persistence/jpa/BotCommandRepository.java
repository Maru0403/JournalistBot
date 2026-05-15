package com.maru.journalistbot.notification.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BotCommandRepository extends JpaRepository<BotCommand, Long> {

    List<BotCommand> findByActiveTrueOrderBySortOrderAsc();

    Optional<BotCommand> findByNameAndActiveTrue(String name);
}
