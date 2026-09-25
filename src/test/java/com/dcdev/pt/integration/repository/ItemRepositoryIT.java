package com.dcdev.pt.integration.repository;

import com.dcdev.pt.config.IntegrationTestBase;
import com.dcdev.pt.item.Category;
import com.dcdev.pt.item.Item;
import com.dcdev.pt.item.ItemQueryParser;
import com.dcdev.pt.item.ItemRepository;
import com.dcdev.pt.item.ItemSpecifications;
import com.github.database.rider.core.api.dataset.DataSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filtering, sorting and paging executed against the real database schema
 * (in-memory H2, Flyway-migrated), through {@link ItemRepository} directly. Every test loads
 * its own small, deterministic {@code @DataSet} fixture (Rider's default
 * CLEAN_INSERT replaces the rows of {@code items}), and the base class empties
 * {@code items} afterwards, so tests never depend on execution order or on each other's data.
 */
class ItemRepositoryIT extends IntegrationTestBase {

    @Autowired
    private ItemRepository repository;

    @Test
    @DataSet(value = "datasets/pagination-items.yml")
    @DisplayName("unfiltered listing returns one page and the full total")
    void unfilteredListing() {
        Page<Item> page = findAll(null, 0, 5, "createdAt,asc");

        assertThat(page.getContent()).hasSize(5);
        assertThat(page.getTotalElements()).isEqualTo(12);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DataSet(value = "datasets/search-items.yml")
    @DisplayName("text search matches both name and description")
    void textSearchMatchesNameAndDescription() {
        assertThat(findAll(ItemSpecifications.textMatches("sunset"), 0, 50, null).getTotalElements()).isEqualTo(2);
        assertThat(findAll(ItemSpecifications.textMatches("confetti"), 0, 50, null).getTotalElements()).isEqualTo(1);
    }

    @Test
    @DataSet(value = "datasets/search-items.yml")
    @DisplayName("text search is case-insensitive")
    void textSearchIsCaseInsensitive() {
        Page<Item> upper = findAll(ItemSpecifications.textMatches("SUNSET"), 0, 50, null);
        Page<Item> lower = findAll(ItemSpecifications.textMatches("sunset"), 0, 50, null);

        assertThat(upper.getTotalElements()).isEqualTo(lower.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DataSet(value = "datasets/search-items.yml")
    @DisplayName("text search does not choke on rows with a null description")
    void textSearchToleratesNullDescription() {
        // Ids 102 and 106 both have a null description. A naive
        // "description LIKE ..." without null-safety would throw for every row
        // (in SQL, NULL LIKE anything is NULL, not an error, but a predicate
        // built without the OR-across-columns structure here could still miss
        // matches on those rows). "gift" matches via id 101's description and
        // id 104/106's name; id 102 (null description, no "gift" in its name)
        // correctly does not match — the point is that the query completes and
        // returns exactly the rows that do match, without erroring on the nulls.
        Page<Item> page = findAll(ItemSpecifications.textMatches("gift"), 0, 50, null);

        assertThat(page.getContent()).extracting(Item::getId).containsExactlyInAnyOrder(101L, 104L, 106L);
    }

    @Test
    @DataSet(value = "datasets/search-items.yml")
    @DisplayName("category filter is exact")
    void categoryFilterIsExact() {
        Page<Item> page = findAll(ItemSpecifications.hasCategory(Category.WEDDING), 0, 50, null);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(103L);
    }

    @Test
    @DataSet(value = "datasets/search-items.yml")
    @DisplayName("combined search and category filters are ANDed")
    void combinedFiltersAreAnded() {
        Specification<Item> specification = Specification.allOf(
                ItemSpecifications.textMatches("card"), ItemSpecifications.hasCategory(Category.BIRTHDAY));

        Page<Item> page = findAll(specification, 0, 50, null);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(101L);
    }

    @Test
    @DataSet(value = "datasets/pagination-items.yml")
    @DisplayName("first, middle and last pages together cover every row exactly once, with no duplicates")
    void firstMiddleAndLastPagesCoverEveryRowOnce() {
        Set<Long> seen = new HashSet<>();
        int lastPageSize = -1;

        for (int pageNumber = 0; pageNumber < 3; pageNumber++) {
            Page<Item> page = findAll(null, pageNumber, 5, "createdAt,asc");
            page.getContent().forEach(item ->
                    assertThat(seen.add(item.getId())).as("item %s appeared twice", item.getId()).isTrue());
            lastPageSize = page.getContent().size();
        }

        assertThat(seen).hasSize(12);
        assertThat(lastPageSize).as("last page holds the remainder").isEqualTo(2);
    }

    @Test
    @DataSet(value = "datasets/search-items.yml")
    @DisplayName("a filter matching nothing returns an empty page, not an error")
    void filterMatchingNothingReturnsEmptyPage() {
        Page<Item> page = findAll(ItemSpecifications.textMatches("no-such-item"), 0, 10, null);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getTotalPages()).isZero();
    }

    @Test
    @DataSet(value = "datasets/pagination-items.yml")
    @DisplayName("totalElements reflects the full result set, not just the current page")
    void totalElementsReflectsFullResultSet() {
        Page<Item> page = findAll(null, 0, 3, null);

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(12);
    }

    @Test
    @DataSet(value = "datasets/pagination-items.yml")
    @DisplayName("ordering is stable and repeatable across separate queries")
    void orderingIsStableAcrossRepeatedQueries() {
        List<Long> firstRun = idsOf(findAll(null, 0, 12, "createdAt,asc"));
        List<Long> secondRun = idsOf(findAll(null, 0, 12, "createdAt,asc"));

        assertThat(firstRun).isEqualTo(secondRun).isSorted();
    }

    @Test
    @DataSet(value = "datasets/items-with-equal-created-at.yml")
    @DisplayName("rows sharing the same sort value are still ordered totally, by the id tie-breaker")
    void equalSortValuesAreDisambiguatedByIdTieBreaker() {
        List<Long> ascending = idsOf(findAll(null, 0, 10, "createdAt,asc"));
        List<Long> descending = idsOf(findAll(null, 0, 10, "createdAt,desc"));

        assertThat(ascending).containsExactly(301L, 302L, 303L, 304L, 305L);
        assertThat(descending).containsExactly(305L, 304L, 303L, 302L, 301L);
    }

    @Test
    @DataSet(value = "datasets/items-with-equal-created-at.yml")
    @DisplayName("paging across a fully-tied sort value never repeats or loses a row")
    void pagingAcrossTiedSortValueHasNoDuplicates() {
        Set<Long> seen = new HashSet<>();

        for (int pageNumber = 0; pageNumber < 3; pageNumber++) {
            findAll(null, pageNumber, 2, "createdAt,asc").getContent()
                    .forEach(item -> assertThat(seen.add(item.getId())).as("item %s appeared twice", item.getId()).isTrue());
        }

        assertThat(seen).hasSize(5);
    }

    /**
     * Accepts {@code null} to mean "no filter" for readability at call sites, but
     * never forwards a bare null to {@code repository.findAll} — see the comment
     * in {@code ItemService.search} for why that specific call is ambiguous.
     */
    private Page<Item> findAll(Specification<Item> specification, int page, int size, String sort) {
        Specification<Item> effective = specification != null ? specification : Specification.allOf(List.of());
        return repository.findAll((Specification<Item>) effective, ItemQueryParser.toPageable(page, size, sort));
    }

    private static List<Long> idsOf(Page<Item> page) {
        return page.getContent().stream().map(Item::getId).toList();
    }
}
