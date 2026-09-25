package com.dcdev.pt.integration.controller;


import com.dcdev.pt.config.IntegrationTestBase;
import com.dcdev.pt.item.ItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.database.rider.core.api.dataset.DataSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves, against the real database schema, that no request input can change
 * the shape of the SQL the service runs.
 *
 * <p>Why it holds: {@code q} and {@code category} reach the database only as
 * JDBC bind parameters (Criteria API via {@code ItemSpecifications}); {@code sort}
 * is matched against a fixed allow-list before anything is built from it;
 * {@code page}, {@code size} and the path {@code id} are bound to numeric types;
 * and the create body is persisted through JPA parameters. The second, subtler
 * risk — LIKE wildcards in user input widening a search — is covered by escaping
 * {@code %}, {@code _} and the escape character itself.
 *
 * <p>Every test also checks the table still holds exactly the fixture rows, so a
 * payload that "succeeded" in dropping or rewriting data could not pass unnoticed.
 */
class InputInjectionIT extends IntegrationTestBase {

    private static final String DATASET = "datasets/injection-items.yml";
    private static final long FIXTURE_ROWS = 5;

    @Autowired
    private ItemRepository repository;

    @Autowired
    private ObjectMapper objectMapper;


    @ParameterizedTest(name = "q = {0}")
    @ValueSource(strings = {
            "' OR '1'='1",
            "' OR 1=1 --",
            "'; DROP TABLE items; --",
            "') OR ('a'='a",
            "\\' OR 1=1 --",
            "x' UNION SELECT id, name, category, description, price, created_at FROM items --",
            "'; SELECT pg_sleep(5); --"
    })
    @DataSet(value = DATASET)
    @DisplayName("SQL in the search text is matched as plain text, never executed")
    void sqlInSearchIsTreatedAsPlainText(String payload) throws Exception {
        mockMvc.perform(get("/api/items").param("q", payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));

        assertTableIntact();
    }

    static Stream<Arguments> specialCharacterSearches() {
        return Stream.of(
                Arguments.of("%", 900001),   // unescaped, this would match every row
                Arguments.of("_", 900002),   // unescaped, this would match every row
                Arguments.of("'", 900003),   // a quote that would break a concatenated query
                Arguments.of("\\", 900004)); // the LIKE escape character itself
    }

    @ParameterizedTest(name = "q = {0} matches only item {1}")
    @MethodSource("specialCharacterSearches")
    @DataSet(value = DATASET)
    @DisplayName("LIKE wildcards and quotes in the search text match literally")
    void specialCharactersMatchLiterally(String query, int expectedId) throws Exception {
        mockMvc.perform(get("/api/items").param("q", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(expectedId));

        assertTableIntact();
    }

    @ParameterizedTest(name = "sort = {0}")
    @ValueSource(strings = {
            "name;DROP TABLE items",
            "name,asc;DROP TABLE items",
            "(select 1)",
            "lower(name)",
            "price desc",
            "createdAt,desc nulls first",
            "id"
    })
    @DataSet(value = DATASET)
    @DisplayName("anything outside the sort allow-list is a 400 and never reaches the query")
    void sortOutsideAllowListIsRejected(String sort) throws Exception {
        mockMvc.perform(get("/api/items").param("sort", sort))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertTableIntact();
    }

    @Test
    @DataSet(value = DATASET)
    @DisplayName("SQL in the category parameter is rejected by enum binding")
    void sqlInCategoryIsRejected() throws Exception {
        mockMvc.perform(get("/api/items").param("category", "BIRTHDAY' OR '1'='1"))
                .andExpect(status().isBadRequest());

        assertTableIntact();
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("numericParameterPayloads")
    @DataSet(value = DATASET)
    @DisplayName("SQL in numeric paging parameters is rejected by type binding")
    void sqlInPagingParametersIsRejected(String parameter, String payload) throws Exception {
        mockMvc.perform(get("/api/items").param(parameter, payload))
                .andExpect(status().isBadRequest());

        assertTableIntact();
    }

    static Stream<Arguments> numericParameterPayloads() {
        return Stream.of(
                Arguments.of("page", "0; DROP TABLE items"),
                Arguments.of("page", "0 OR 1=1"),
                Arguments.of("size", "10 UNION SELECT 1"));
    }

    @ParameterizedTest(name = "id = {0}")
    @ValueSource(strings = {"1 OR 1=1", "1' OR '1'='1"})
    @DataSet(value = DATASET)
    @DisplayName("SQL in the path id is rejected by type binding")
    void sqlInPathIdIsRejected(String payload) throws Exception {
        mockMvc.perform(get("/api/items/{id}", payload))
                .andExpect(status().isBadRequest());

        assertTableIntact();
    }

    @Test
    @DataSet(value = DATASET)
    @DisplayName("SQL and markup in a created item are stored verbatim as data")
    void payloadInCreateBodyIsStoredVerbatim() throws Exception {
        String name = "Robert'); DROP TABLE items; --";
        String description = "<script>alert('x')</script>";
        String body = objectMapper.writeValueAsString(
                Map.of("name", name, "category", "OTHER", "description", description));

        MvcResult created = mockMvc.perform(post("/api/items").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andReturn();

        mockMvc.perform(get(created.getResponse().getHeader("Location")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.description").value(description));

        assertThat(repository.count()).isEqualTo(FIXTURE_ROWS + 1);
    }

    private void assertTableIntact() {
        assertThat(repository.count()).isEqualTo(FIXTURE_ROWS);
    }
}
