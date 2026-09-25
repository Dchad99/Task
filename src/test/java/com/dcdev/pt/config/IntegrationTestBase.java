package com.dcdev.pt.config;

import com.github.database.rider.core.api.configuration.DBUnit;
import com.github.database.rider.core.api.configuration.Orthography;
import com.github.database.rider.core.api.connection.ConnectionHolder;
import com.github.database.rider.junit5.api.DBRider;
import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;
import org.dbunit.ext.postgresql.PostgresqlDataTypeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Shared base for every integration test in this project: a real PostgreSQL
 * Testcontainer, real Flyway migrations run against it, Database Rider for
 * fixture data, MockMvc for HTTP-level tests, and a SQL statement counter reset
 * before each test.
 *
 * <p>The container is started once, in a static initializer, and never
 * explicitly stopped — Testcontainers registers its own JVM shutdown hook. Every
 * subclass registers the exact same {@code spring.datasource.*} properties
 * (same container, same credentials, every time this JVM runs), so Spring's test
 * context cache treats them as one configuration and boots the context once for
 * the whole suite rather than once per test class.
 *
 * <p>Flyway runs through Spring Boot's own auto-configuration (see
 * {@code spring.flyway.*} in {@code application.yml}) against the connection
 * {@link #registerDatasourceProperties} points it at — the same real
 * {@code V1__create_items_table.sql} migration production uses, not a
 * hand-rolled test schema. It is not also triggered manually anywhere in this
 * test tree; running it twice would either double-apply or (with Flyway's own
 * safeguards) silently no-op the second time, which would hide a broken
 * migration.
 */
@IT
@DBRider
@DBUnit(
        caseInsensitiveStrategy = Orthography.LOWERCASE,
        cacheConnection = false,
        dataTypeFactoryClass = PostgresqlDataTypeFactory.class)
@Import(TestConfigToCountSqlQueries.class)
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    /** DBUnitExtension locates this by reflection (a field or method returning ConnectionHolder). */
    private ConnectionHolder connectionHolder;

    @BeforeEach
    void initConnectionHolderAndResetQueryCount() {
        connectionHolder = dataSource::getConnection;
        SQLStatementCountValidator.reset();
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
