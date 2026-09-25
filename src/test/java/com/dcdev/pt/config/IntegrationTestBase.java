package com.dcdev.pt.config;


import com.github.database.rider.core.api.configuration.DBUnit;
import com.github.database.rider.core.api.configuration.Orthography;
import com.github.database.rider.core.api.connection.ConnectionHolder;
import com.github.database.rider.junit5.api.DBRider;
import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;
import org.dbunit.ext.h2.H2DataTypeFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Shared base for every integration test in this project: the full Spring
 * context on the same in-memory H2 database and the same Flyway migration the
 * app runs with, Database Rider for fixture data, MockMvc for HTTP-level tests,
 * and a SQL statement counter reset before each test.
 *
 * <p>Every subclass shares one configuration, so Spring's test-context cache
 * boots the context (and migrates the database) once for the whole suite rather
 * than once per test class. Flyway runs through Spring Boot's own
 * auto-configuration — the real {@code V1__create_items_table.sql}, not a
 * hand-rolled test schema.
 *
 * <p>Database Rider settings: H2 folds unquoted identifiers to upper case, so
 * dataset table/column names are matched in upper case; {@code cacheConnection
 * = false} because the {@link ConnectionHolder} is re-created per test;
 * {@link H2DataTypeFactory} is DBUnit's type factory for H2.
 */
@IT
@DBRider
@DBUnit(
        caseInsensitiveStrategy = Orthography.UPPERCASE,
        cacheConnection = false,
        dataTypeFactoryClass = H2DataTypeFactory.class)
@Import(TestConfigToCountSqlQueries.class)
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** DBUnitExtension locates this by reflection (a field or method returning ConnectionHolder). */
    private ConnectionHolder connectionHolder;

    @BeforeEach
    void initConnectionHolderAndResetQueryCount() {
        connectionHolder = dataSource::getConnection;
        SQLStatementCountValidator.reset();
    }

    /**
     * Empties the fixture table after every test, including rows a test created
     * through the API. Datasets rely on Rider's default CLEAN_INSERT, which only
     * touches the tables they list; never use {@code cleanBefore}/{@code cleanAfter},
     * which clear every table, Flyway's {@code flyway_schema_history} included.
     */
    @AfterEach
    void deleteAllItems() {
        jdbcTemplate.update("DELETE FROM items");
    }

    /**
     * Reads a JSON fixture from {@code src/test/resources/response/...} as text,
     * for {@code JSONAssert} comparisons against an actual response body.
     */
    protected static String readResponse(String relativePath) {
        String resourcePath = "response/" + relativePath;
        try (InputStream in = new ClassPathResource(resourcePath).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read response fixture: " + resourcePath, e);
        }
    }
}
