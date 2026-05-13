package com.maru.journalistbot.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA Repository for BotCommand entity.
 * Queries are simple — commands table is small and rarely changes.
 */
@Repository
public interface BotCommandRepository extends JpaRepository<BotCommand, Long> {

    /** Get all active commands (used for /help display + bot registration) */
    List<BotCommand> findByActiveTrueOrderBySortOrderAsc();

    /** Find active commands available on a specific platform (or "ALL") */
    List<BotCommand> findByActiveTrueAndPlatformsContainingOrderBySortOrderAsc(String platform);

    /** Look up a command by name (case-sensitive, stored lowercase) */
    Optional<BotCommand> findByNameAndActiveTrue(String name);
}
