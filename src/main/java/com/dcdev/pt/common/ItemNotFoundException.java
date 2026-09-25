package com.dcdev.pt.common;

/** An item referenced by id does not exist. Maps to 404. */
public class ItemNotFoundException extends RuntimeException {

    public ItemNotFoundException(long id) {
        super("Item " + id + " was not found.");
    }
}
