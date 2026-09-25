package com.dcdev.pt.integration.controller;

import com.dcdev.pt.config.IntegrationTestBase;
import com.dcdev.pt.config.SqlStatementRecorder.RecordedStatement;
import com.dcdev.pt.item.ItemRepository;
import com.dcdev.pt.item.ItemSpecifications;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Locale;
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
 * <p>Two levels of evidence per test:
 * <ul>
 *   <li><b>outcome</b> — the response is what plain-text handling predicts, and
 *       the table still holds exactly the fixture rows;</li>
 *   <li><b>mechanism</b> — from the statements actually sent to the JDBC driver
 *       ({@code SqlStatementRecorder}): accepted input arrives only as a bound
 *       parameter and never appears in the SQL text; rejected input ({@code sort}
 *       outside the allow-list, non-enum {@code category}, non-numeric paging or
 *       id) is refused before any SQL runs at all.</li>
 * </ul>
 * The outcome alone could not tell "bound" apart from "concatenated but happened
 * to be harmless"; the mechanism check can.
 *
 * <p>The second, subtler risk — LIKE wildcards in user input widening a search —
 * is covered by escaping {@code %}, {@code _} and the escape character itself,
 * and checked here through the exact pattern that gets bound.
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
    @DataSet(DATASET)
    @DisplayName("SQL in the search text is bound as a LIKE parameter, never part of the SQL text")
    void sqlInSearchIsTreatedAsPlainText(String payload) throws Exception {
        performRecorded(get("/api/items").param("q", payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));

        assertBoundAsLikePattern(likePatternFor(payload));
        assertNotInSqlText(payload);
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
    @DataSet(DATASET)
    @DisplayName("LIKE wildcards and quotes in the search text are escaped, bound, and match literally")
    void specialCharactersMatchLiterally(String query, int expectedId) throws Exception {
        performRecorded(get("/api/items").param("q", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(expectedId));

        // No SQL-text check for single characters: '_' occurs in Hibernate's table
        // aliases and ' / \ in the literal ESCAPE clause. The bound, escaped
        // pattern is the proof here.
        assertBoundAsLikePattern(likePatternFor(query));
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
    @DataSet(DATASET)
    @DisplayName("anything outside the sort allow-list is a 400 and no SQL runs")
    void sortOutsideAllowListIsRejected(String sort) throws Exception {
        performRecorded(get("/api/items").param("sort", sort))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertNoSqlRan();
        assertTableIntact();
    }

    @Test
    @DataSet(DATASET)
    @DisplayName("SQL in the category parameter is rejected by enum binding before any SQL runs")
    void sqlInCategoryIsRejected() throws Exception {
        performRecorded(get("/api/items").param("category", "BIRTHDAY' OR '1'='1"))
                .andExpect(status().isBadRequest());

        assertNoSqlRan();
        assertTableIntact();
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("numericParameterPayloads")
    @DataSet(DATASET)
    @DisplayName("SQL in numeric paging parameters is rejected by type binding before any SQL runs")
    void sqlInPagingParametersIsRejected(String parameter, String payload) throws Exception {
        performRecorded(get("/api/items").param(parameter, payload))
                .andExpect(status().isBadRequest());

        assertNoSqlRan();
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
    @DataSet(DATASET)
    @DisplayName("SQL in the path id is rejected by type binding before any SQL runs")
    void sqlInPathIdIsRejected(String payload) throws Exception {
        performRecorded(get("/api/items/{id}", payload))
                .andExpect(status().isBadRequest());

        assertNoSqlRan();
        assertTableIntact();
    }

    @Test
    @DataSet(DATASET)
    @DisplayName("SQL and markup in a created item are bound in the INSERT and stored verbatim")
    void payloadInCreateBodyIsStoredVerbatim() throws Exception {
        String name = "Robert'); DROP TABLE items; --";
        String description = "<script>alert('x')</script>";
        String body = objectMapper.writeValueAsString(
                Map.of("name", name, "category", "OTHER", "description", description));

        MvcResult created = performRecorded(post("/api/items").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andReturn();

        List<RecordedStatement> inserts = sqlRecorder.statements().stream()
                .filter(RecordedStatement::isInsert)
                .filter(statement -> statement.touchesTable("items"))
                .toList();
        assertThat(inserts).as("the create ran an INSERT into items").hasSize(1);
        assertThat(inserts.get(0).sql()).doesNotContain(name).doesNotContain(description);
        assertThat(inserts.get(0).parameters()).contains(name, description);

        mockMvc.perform(get(created.getResponse().getHeader("Location")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.description").value(description));

        assertThat(repository.count()).isEqualTo(FIXTURE_ROWS + 1);
    }

    /** Clears the recorder (Rider's fixture load is recorded too) and runs the request. */
    private ResultActions performRecorded(RequestBuilder request) throws Exception {
        sqlRecorder.reset();
        return mockMvc.perform(request);
    }

    /** The exact pattern the service must bind for {@code q}: lower-cased, escaped, wrapped in %. */
    private static String likePatternFor(String q) {
        return "%" + ItemSpecifications.escapeLikeWildcards(q.strip().toLowerCase(Locale.ROOT)) + "%";
    }

    private List<RecordedStatement> itemQueries() {
        List<RecordedStatement> itemQueries = sqlRecorder.statements().stream()
                .filter(statement -> statement.touchesTable("items"))
                .toList();
        assertThat(itemQueries).as("the search reached the database").isNotEmpty();
        return itemQueries;
    }

    private void assertBoundAsLikePattern(String expectedBoundValue) {
        assertThat(itemQueries()).allSatisfy(statement -> assertThat(statement.parameters())
                .as("data query and count query both receive the input as a bound LIKE pattern")
                .contains(expectedBoundValue));
    }

    /** Inlining would put the input in the SQL text, as typed or with quotes doubled by literal escaping. */
    private void assertNotInSqlText(String userInput) {
        String input = userInput.strip();
        assertThat(itemQueries()).allSatisfy(statement -> assertThat(statement.sql())
                .as("user input must never appear in the SQL text")
                .doesNotContainIgnoringCase(input)
                .doesNotContainIgnoringCase(input.replace("'", "''")));
    }

    private void assertNoSqlRan() {
        assertThat(sqlRecorder.statements()).as("invalid input is rejected before any SQL runs").isEmpty();
    }

    private void assertTableIntact() {
        assertThat(repository.count()).isEqualTo(FIXTURE_ROWS);
    }
}
