package com.dcdev.pt.item;

import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * One independent filter predicate per method. The service ANDs together whichever
 * ones a request asked for. Adding a new filter means adding a method here.
 */
public final class ItemSpecifications {

    private static final char ESCAPE_CHAR = '\\';

    private ItemSpecifications() {
    }

    /** Case-insensitive substring match across name and description. */
    public static Specification<Item> textMatches(String rawQuery) {
        String pattern = "%" + escapeLikeWildcards(rawQuery.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern, ESCAPE_CHAR),
                cb.like(cb.lower(root.get("description")), pattern, ESCAPE_CHAR));
    }

    public static Specification<Item> hasCategory(Category category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    /** Stops input like {@code 50%} being interpreted as a LIKE wildcard. */
    public static String escapeLikeWildcards(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 4);
        for (char c : value.toCharArray()) {
            if (c == '%' || c == '_' || c == ESCAPE_CHAR) {
                escaped.append(ESCAPE_CHAR);
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
