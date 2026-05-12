package com.maru.journalistbot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Confirms the 'journalist_bot' PostgreSQL schema exists after startup.
 *
 * Note: Schema creation is handled by Hibernate via
 * spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true
 * which runs BEFORE entity DDL — so there is no timing issue.
 *
 * This class just logs a confirmation that everything is wired correctly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void confirmSchema() {
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = 'journalist_bot')",
                Boolean.class
            );
            if (Boolean.TRUE.equals(exists)) {
                log.info("✅ PostgreSQL schema 'journalist_bot' is ready");
            } else {
                // Fallback: create manually if Hibernate didn't create it
                log.warn("Schema 'journalist_bot' not found — creating manually...");
                jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS journalist_bot");
                log.info("✅ PostgreSQL schema 'journalist_bot' created successfully");
            }
        } catch (Exception e) {
            log.warn("Could not verify schema 'journalist_bot': {}", e.getMessage());
        }
    }
}
