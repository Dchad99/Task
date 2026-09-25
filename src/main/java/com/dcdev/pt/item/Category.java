package com.dcdev.pt.item;

/**
 * Fixed catalogue taxonomy. Kept as an enum: the set is small and stable, so a
 * lookup table plus a {@code @ManyToOne} would add a join for no current benefit.
 * See README for how this would change if categories became user-managed data.
 */
public enum Category {
    BIRTHDAY,
    WEDDING,
    ANNIVERSARY,
    THANK_YOU,
    CHRISTMAS,
    OTHER
}
