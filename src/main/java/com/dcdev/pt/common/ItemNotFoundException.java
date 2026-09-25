package com.dcdev.pt.common;

public class ItemNotFoundException extends RuntimeException {

    public ItemNotFoundException(long id) {
        super("Item " + id + " was not found.");
    }
}
