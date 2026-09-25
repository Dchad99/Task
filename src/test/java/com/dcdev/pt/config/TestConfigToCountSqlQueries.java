package com.dcdev.pt.config;


import net.ttddyy.dsproxy.listener.ChainListener;
import net.ttddyy.dsproxy.listener.DataSourceQueryCountListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Replaces the auto-configured {@code DataSource} with one wrapped by
 * datasource-proxy, so {@code io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator}
 * can see every SQL statement Hibernate issues against the database.
 *
 * <p>The same proxy also feeds {@link SqlStatementRecorder}, which keeps the SQL
 * text and bound values of every statement for tests that assert on them.
 *
 * <p>Builds its own connection from the same {@code spring.datasource.*}
 * properties the app itself uses — not from the DataSource bean Spring Boot's
 * own auto-configuration would otherwise create — so there is exactly one JDBC
 * pool talking to the container, not two. {@code @Primary} lets this bean win
 * without having to exclude Boot's DataSource auto-configuration outright.
 */
@TestConfiguration
public class TestConfigToCountSqlQueries {

    @Bean
    public SqlStatementRecorder sqlStatementRecorder() {
        return new SqlStatementRecorder();
    }

    @Bean
    @Primary
    public DataSource dataSource(
            SqlStatementRecorder sqlStatementRecorder,
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${spring.datasource.driver-class-name}") String driverClassName) {

        DataSource actualDataSource = DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName(driverClassName)
                .build();

        ChainListener listener = new ChainListener();
        listener.addListener(new DataSourceQueryCountListener());
        listener.addListener(sqlStatementRecorder);

        return ProxyDataSourceBuilder.create(actualDataSource)
                .name("SQL-COUNT-PROXY")
                .listener(listener)
                .build();
    }
}
