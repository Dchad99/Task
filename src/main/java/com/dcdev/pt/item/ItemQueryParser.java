package com.dcdev.pt.item;

import com.dcdev.pt.common.InvalidRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Turns raw listing query parameters into a validated {@link Pageable}. A small
 * pure class rather than controller logic, so the defaulting/validation rules are
 * unit-testable without a Spring context.
 */
public final class ItemQueryParser {

    public static final int DEFAULT_PAGE_SIZE = 25;

    /** Hard ceiling — without it a client can request the whole table in one call. */
    public static final int MAX_PAGE_SIZE = 100;

    /** Allow-list rather than an open sort parameter, which would leak entity field names. */
    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "name", "price");

    private static final String TIE_BREAKER = "id";

    private ItemQueryParser() {
    }

    public static Pageable toPageable(int page, int size, String sort) {
        if (page < 0) {
            throw new InvalidRequestException("Parameter 'page' must be 0 or greater, but was " + page + ".");
        }
        if (size < 1) {
            throw new InvalidRequestException("Parameter 'size' must be 1 or greater, but was " + size + ".");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException(
                    "Parameter 'size' must not exceed " + MAX_PAGE_SIZE + ", but was " + size + ".");
        }
        return PageRequest.of(page, size, parseSort(sort));
    }

    /**
     * Parses {@code field,direction}. Always appends {@code id} as a final sort key
     * so rows sharing a value (e.g. the same {@code createdAt}) still have a total,
     * repeatable order across pages.
     */
    public static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return sortBy("createdAt", Sort.Direction.DESC);
        }

        String[] parts = sort.split(",");
        String field = parts[0].trim();
        String rawDirection = parts.length > 1 ? parts[1].trim() : "asc";

        if (parts.length > 2) {
            throw new InvalidRequestException(
                    "Parameter 'sort' must be in the form 'field,direction', but was '" + sort + "'.");
        }
        if (!SORTABLE_FIELDS.contains(field)) {
            throw new InvalidRequestException(
                    "Unsupported sort field '" + field + "'. Supported fields: " + sorted(SORTABLE_FIELDS) + ".");
        }

        Sort.Direction direction;
        if ("asc".equalsIgnoreCase(rawDirection)) {
            direction = Sort.Direction.ASC;
        } else if ("desc".equalsIgnoreCase(rawDirection)) {
            direction = Sort.Direction.DESC;
        } else {
            throw new InvalidRequestException(
                    "Unsupported sort direction '" + rawDirection + "'. Use 'asc' or 'desc'.");
        }

        return sortBy(field, direction);
    }

    private static Sort sortBy(String field, Sort.Direction direction) {
        return Sort.by(new Sort.Order(direction, field), new Sort.Order(direction, TIE_BREAKER));
    }

    private static String sorted(Set<String> values) {
        return values.stream().sorted().reduce((a, b) -> a + ", " + b).orElse("");
    }
}
