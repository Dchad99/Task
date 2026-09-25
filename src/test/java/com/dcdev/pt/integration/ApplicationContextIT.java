package com.dcdev.pt.integration;

import com.dcdev.pt.config.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Canary test: the full Spring context starts against a real PostgreSQL
 * Testcontainer, with Flyway migrations applied and seeding disabled. Every
 * other {@code *IT} class already implies this by extending
 * {@link IntegrationTestBase}; this one exists so a context-startup failure
 * (a bad migration, a missing bean, a misconfigured property) is reported by
 * one small, fast-failing test rather than by every integration test at once.
 */
class ApplicationContextIT extends IntegrationTestBase {

    @Test
    @DisplayName("the application context starts against a real Postgres database")
    void contextLoads() {
    }
}
