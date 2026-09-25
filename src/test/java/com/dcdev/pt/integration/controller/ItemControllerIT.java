package com.dcdev.pt.integration.controller;


import com.dcdev.pt.config.IntegrationTestBase;
import com.github.database.rider.core.api.dataset.DataSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full HTTP-to-database round trip against the in-memory H2 database.
 *
 * <p>Where a fixture's every field is deterministic (fixed dataset ids and
 * timestamps, {@code hibernate.jdbc.time_zone: UTC} pinned in
 * {@code application.yml}), the response body is compared against a JSON
 * fixture under {@code response/success/} or {@code response/errors/} with
 * {@link JSONAssert} — one file is the whole assertion, instead of a long
 * chain of {@code jsonPath} calls. The one field that is never deterministic,
 * an error response's {@code timestamp}, is excluded from that comparison via
 * a {@link Customization} and not checked separately — its exact value isn't
 * part of the API contract, only its presence, which the fixture's shape
 * already implies. Scenarios whose response is inherently non-deterministic
 * (a created item's generated id and timestamp) use {@code jsonPath}
 * assertions instead of a fixture, for the same reason.
 */
class ItemControllerIT extends IntegrationTestBase {

    @Test
    @DataSet(value = "datasets/pagination-items.yml")
    @DisplayName("default listing returns page metadata alongside content")
    void defaultListingReturnsPageMetadata() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/items").param("size", "5").param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andReturn();

        JSONAssert.assertEquals(
                readResponse("success/listing-page-metadata.json"),
                result.getResponse().getContentAsString(),
                JSONCompareMode.STRICT);
    }

    @Test
    @DataSet(value = "datasets/search-items.yml")
    @DisplayName("search matches across name and description")
    void searchMatchesAcrossNameAndDescription() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/items")
                        .param("q", "sunset").param("size", "50").param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andReturn();

        JSONAssert.assertEquals(
                readResponse("success/search-result.json"),
                result.getResponse().getContentAsString(),
                JSONCompareMode.STRICT);
    }

    @Test
    @DataSet(value = "datasets/search-items.yml")
    @DisplayName("category filter narrows the listing")
    void categoryFilterNarrowsListing() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/items").param("category", "WEDDING"))
                .andExpect(status().isOk())
                .andReturn();

        JSONAssert.assertEquals(
                readResponse("success/filter-result.json"),
                result.getResponse().getContentAsString(),
                JSONCompareMode.STRICT);
    }

    @Test
    @DataSet(value = "datasets/search-items.yml")
    @DisplayName("search and category filters combine")
    void searchAndCategoryFiltersCombine() throws Exception {
        mockMvc.perform(get("/api/items").param("q", "card").param("category", "BIRTHDAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(101));
    }

    @Test
    @DataSet(value = "datasets/pagination-items.yml")
    @DisplayName("an invalid page number is a 400, matching the shared error contract")
    void invalidPageIsBadRequest() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/items").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertMatchesErrorFixture("errors/invalid-page.json", result);
    }

    @Test
    @DisplayName("a page size above the maximum is a 400, matching the shared error contract")
    void pageSizeAboveMaximumIsBadRequest() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/items").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertMatchesErrorFixture("errors/max-size-exceeded.json", result);
    }

    @Test
    @DisplayName("an unsupported sort field is a 400, not a 500")
    void unsupportedSortFieldIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/items").param("sort", "secret,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("an unknown category value is a 400, not a 500")
    void unknownCategoryIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/items").param("category", "NOT_A_CATEGORY"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("creating a valid item returns 201 with a Location header")
    void createReturns201WithLocationHeader() throws Exception {
        String body = """
                {"name":"New Card","category":"THANK_YOU","description":"A note","price":6.50}
                """;

        mockMvc.perform(post("/api/items").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("New Card"))
                .andExpect(jsonPath("$.category").value("THANK_YOU"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    @DisplayName("creating an invalid item returns 400, matching the shared error contract")
    void createWithBlankNameIsBadRequest() throws Exception {
        String body = """
                {"name":"","category":"THANK_YOU"}
                """;

        MvcResult result = mockMvc.perform(post("/api/items").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertMatchesErrorFixture("errors/validation-error.json", result);
    }

    @Test
    @DisplayName("a created item is immediately visible through the listing API")
    void createdItemAppearsInListing() throws Exception {
        String body = """
                {"name":"Findable Item","category":"OTHER"}
                """;
        mockMvc.perform(post("/api/items").contentType("application/json").content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/items").param("q", "Findable Item"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Findable Item"));
    }

    @Test
    @DataSet(value = "datasets/items.yml")
    @DisplayName("deleting a known item returns 204, and it is gone from the listing")
    void deletingKnownItemReturns204AndDisappearsFromListing() throws Exception {
        mockMvc.perform(delete("/api/items/{id}", 1)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/items").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(6))
                .andExpect(jsonPath("$.content[?(@.id == 1)]").doesNotExist());
    }

    @Test
    @DisplayName("deleting an unknown id returns 404, matching the shared error contract")
    void deletingUnknownIdIsNotFound() throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/items/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andReturn();

        assertMatchesErrorFixture("errors/not-found.json", result);
    }

    private static void assertMatchesErrorFixture(String fixturePath, MvcResult result) throws Exception {
        JSONAssert.assertEquals(
                readResponse(fixturePath),
                result.getResponse().getContentAsString(),
                new CustomComparator(JSONCompareMode.STRICT, new Customization("timestamp", (o1, o2) -> true)));
    }

    @Test
    @DisplayName("search text longer than 100 characters is a 400 with the shared error shape")
    void overlongSearchTextIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/items").param("q", "a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/items"))
                .andExpect(jsonPath("$.message").value(
                        "Parameter 'q' must be at most 100 characters, but was 101."));
    }
}
