package com.maru.journalistbot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 — Lightweight sanity test.
 * Does NOT start Spring context (no @SpringBootTest) → no DB connections needed.
 *
 * Why: @SpringBootTest loads the full context which requires MongoDB + PostgreSQL + Redis
 * to be running. For a basic compile/sanity check, we don't need that overhead.
 *
 * Phase 2: Add slice tests like @DataMongoTest, @DataJpaTest for specific layers.
 */
class JournalistBotApplicationTests {

    @Test
    void sanityCheck() {
        // Verify the project compiles and JUnit is properly available.
        // No Spring context needed for this check.
        assertTrue(true, "Phase 1 scaffold compiles correctly");
    }
}
