package com.dcdev.pt.unit.service;


import com.dcdev.pt.common.InvalidRequestException;
import com.dcdev.pt.item.ItemQueryParser;
import com.dcdev.pt.item.ItemSpecifications;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for request normalisation. No Spring context: these are the
 * decisions most worth pinning down, and they run instantly.
 */
class ItemQueryParserTest {

    @Test
    @DisplayName("defaults to newest first with id as tie-breaker")
    void defaultsToCreatedAtDescWithIdTieBreaker() {
        Sort sort = ItemQueryParser.parseSort(null);

        assertThat(sort).containsExactly(
                new Sort.Order(Sort.Direction.DESC, "createdAt"),
                new Sort.Order(Sort.Direction.DESC, "id"));
    }

    @Test
    @DisplayName("blank sort is treated as absent")
    void blankSortFallsBackToDefault() {
        assertThat(ItemQueryParser.parseSort("   ")).isEqualTo(ItemQueryParser.parseSort(null));
    }

    @Test
    @DisplayName("always appends id so the ordering is total")
    void appendsIdTieBreakerToEverySort() {
        assertThat(ItemQueryParser.parseSort("name,asc")).containsExactly(
                new Sort.Order(Sort.Direction.ASC, "name"),
                new Sort.Order(Sort.Direction.ASC, "id"));
    }

    @Test
    @DisplayName("direction defaults to ascending when omitted")
    void directionDefaultsToAscending() {
        assertThat(ItemQueryParser.parseSort("price")).containsExactly(
                new Sort.Order(Sort.Direction.ASC, "price"),
                new Sort.Order(Sort.Direction.ASC, "id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "secret", "id", "createdAt,desc,extra", "name,sideways",
            // SQL smuggled through the sort parameter: none of it reaches the query
            "name;DROP TABLE items", "(select 1)", "lower(name)", "price desc",
            "name,asc;DROP TABLE items", "createdAt,desc nulls first"})
    @DisplayName("rejects anything outside the sort allow-list")
    void rejectsUnsupportedSort(String sort) {
        assertThatThrownBy(() -> ItemQueryParser.parseSort(sort)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("applies the documented pagination defaults")
    void buildsPageableWithDefaults() {
        Pageable pageable = ItemQueryParser.toPageable(0, ItemQueryParser.DEFAULT_PAGE_SIZE, null);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(25);
    }

    @Test
    @DisplayName("rejects a negative page")
    void rejectsNegativePage() {
        assertThatThrownBy(() -> ItemQueryParser.toPageable(-1, 25, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("page");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    @DisplayName("rejects a non-positive page size")
    void rejectsNonPositiveSize(int size) {
        assertThatThrownBy(() -> ItemQueryParser.toPageable(0, size, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("size");
    }

    @Test
    @DisplayName("caps the page size so the endpoint stays bounded")
    void rejectsSizeAboveMaximum() {
        assertThatThrownBy(() -> ItemQueryParser.toPageable(0, ItemQueryParser.MAX_PAGE_SIZE + 1, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining(String.valueOf(ItemQueryParser.MAX_PAGE_SIZE));
    }

    @Test
    @DisplayName("accepts exactly the maximum page size")
    void acceptsMaximumSize() {
        assertThat(ItemQueryParser.toPageable(0, ItemQueryParser.MAX_PAGE_SIZE, null).getPageSize())
                .isEqualTo(ItemQueryParser.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("escapes LIKE wildcards so user input cannot widen the search")
    void escapesLikeWildcards() {
        assertThat(ItemSpecifications.escapeLikeWildcards("50%_off")).isEqualTo("50\\%\\_off");
    }

    @Test
    @DisplayName("escapes the escape character itself, so a trailing backslash cannot break the pattern")
    void escapesTheEscapeCharacter() {
        assertThat(ItemSpecifications.escapeLikeWildcards("back\\slash")).isEqualTo("back\\\\slash");
    }

    @Test
    @DisplayName("absent page, size and sort fall back to the defaults")
    void absentParametersFallBackToDefaults() {
        Pageable pageable = ItemQueryParser.toPageable(null, null, null);

        assertThat(pageable.getPageNumber()).isEqualTo(ItemQueryParser.DEFAULT_PAGE);
        assertThat(pageable.getPageSize()).isEqualTo(ItemQueryParser.DEFAULT_PAGE_SIZE);
        assertThat(pageable.getSort()).isEqualTo(ItemQueryParser.parseSort(null));
    }
}
