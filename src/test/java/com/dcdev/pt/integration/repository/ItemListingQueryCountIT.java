package com.dcdev.pt.integration.repository;

import com.dcdev.pt.config.IntegrationTestBase;
import com.dcdev.pt.item.Category;
import com.dcdev.pt.item.Item;
import com.dcdev.pt.item.ItemQueryParser;
import com.dcdev.pt.item.ItemRepository;
import com.dcdev.pt.item.ItemService;
import com.dcdev.pt.item.dto.ItemResponse;
import io.hypersistence.utils.jdbc.validator.SQLStatementCountMismatchException;
import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Query-count regression tests for the listing endpoint's data-access path.
 * These are architectural-property checks, not exact-number pins: a paginated
 * {@code Page<T>} query legitimately runs one data SELECT plus one COUNT
 * SELECT. What must never happen is a count that grows with page size, or an
 * unbounded number of statements coming from lazy loading during DTO mapping.
 *
 * <p>Fixture rows here are inserted directly through {@link ItemRepository},
 * not a {@code @DataSet} — {@code @DataSet}'s own INSERT statements would
 * otherwise appear in the very count this class measures if a fixture load
 * ever overlapped a checked call. Every checked call resets the counter
 * immediately beforehand, once data setup has already finished.
 */
class ItemListingQueryCountIT extends IntegrationTestBase {

    private static final int FIXTURE_SIZE = 150;
    private static final long MAX_STATEMENTS_PER_PAGE = 2;

    @Autowired
    private ItemRepository repository;

    @Autowired
    private ItemService itemService;

    @BeforeEach
    void seedFixtures() {
        repository.deleteAll();

        List<Item> items = new ArrayList<>(FIXTURE_SIZE);
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < FIXTURE_SIZE; i++) {
            items.add(new Item(
                    "Item " + i,
                    Category.values()[i % Category.values().length],
                    "Description for item " + i,
                    BigDecimal.valueOf(i % 40 + 1),
                    base.plusSeconds(3600L * (i / 5))));
        }
        repository.saveAll(items);
    }


    @Test
    @DisplayName("a page of listing results costs a small, bounded number of SQL statements")
    void listingQueryCountIsBounded() {
        long statements = recordedSelectCount(() -> itemService.search(null, null, 0, 10, null));

        assertThat(statements)
                .as("expected at most one data query and one count query")
                .isGreaterThan(0)
                .isLessThanOrEqualTo(MAX_STATEMENTS_PER_PAGE);
    }

    @Test
    @DisplayName("statement count for size=10 and size=100 is identical — no per-row query growth")
    void statementCountIsIndependentOfPageSize() {
        long forTen = recordedSelectCount(() -> itemService.search(null, null, 0, 10, null));
        long forHundred = recordedSelectCount(() -> itemService.search(null, null, 0, 100, null));

        assertThat(forHundred)
                .as("a page of 100 must not cost more round trips than a page of 10")
                .isEqualTo(forTen);
    }

    @Test
    @DisplayName("filtering does not add extra queries beyond the bounded ceiling")
    void filteringDoesNotAddQueries() {
        long statements = recordedSelectCount(() -> itemService.search("Item 1", Category.BIRTHDAY, 0, 25, "name,asc"));

        assertThat(statements).isGreaterThan(0).isLessThanOrEqualTo(MAX_STATEMENTS_PER_PAGE);
    }

    @Test
    @DisplayName("reading every mapped field of every row (including DTO/JSON serialization) triggers no extra queries")
    void mappingAndSerializingResultsCausesNoExtraQueries() {
        long statements = recordedSelectCount(() -> {
            Page<ItemResponse> page = itemService.search(null, null, 0, ItemQueryParser.MAX_PAGE_SIZE, null);
            // Force access to every field the DTO mapping (and, in production,
            // Jackson serialization) touches. Nothing on Item is lazily loaded,
            // so this documents the property rather than being expected to
            // catch a regression on its own — a future to-many relationship is
            // exactly what would turn this into a real N+1.
            page.getContent().forEach(item -> {
                item.name();
                item.category();
                item.description();
                item.price();
                item.createdAt();
            });
            return page;
        });

        assertThat(statements).isGreaterThan(0).isLessThanOrEqualTo(MAX_STATEMENTS_PER_PAGE);
    }

    @Test
    @DisplayName("a large table (1,000+ rows) still costs a bounded number of statements and returns only one page")
    void largeDatasetStillCostsBoundedStatements() {
        repository.deleteAll();
        int totalRows = 1500;
        List<Item> bulk = new ArrayList<>(totalRows);
        Instant base = Instant.parse("2020-01-01T00:00:00Z");
        for (int i = 0; i < totalRows; i++) {
            bulk.add(new Item(
                    "Bulk Item " + i,
                    Category.values()[i % Category.values().length],
                    "Bulk description " + i,
                    BigDecimal.valueOf(i % 40 + 1),
                    base.plusSeconds(i)));
        }
        repository.saveAll(bulk);

        long statements = recordedSelectCount(() -> itemService.search(null, null, 0, 25, null));
        Page<ItemResponse> page = itemService.search(null, null, 0, 25, null);

        assertThat(page.getContent()).as("only one page's worth of rows is ever loaded").hasSize(25);
        assertThat(page.getTotalElements()).isEqualTo(totalRows);
        assertThat(statements)
                .as("statement count does not grow with table size")
                .isGreaterThan(0)
                .isLessThanOrEqualTo(MAX_STATEMENTS_PER_PAGE);
    }

    /**
     * Runs {@code action}, returning how many SELECT statements it caused.
     *
     * <p>{@link SQLStatementCountValidator} only exposes assert-and-throw
     * methods, not a plain getter, so this deliberately asserts a count of zero
     * — virtually guaranteed to be wrong whenever {@code action} runs a real
     * query — and reads the actual count off the resulting
     * {@link SQLStatementCountMismatchException}.
     */
    private static long recordedSelectCount(Supplier<?> action) {
        SQLStatementCountValidator.reset();
        action.get();
        try {
            SQLStatementCountValidator.assertSelectCount(0);
            return 0;
        } catch (SQLStatementCountMismatchException e) {
            return e.getRecorded();
        }
    }
}
