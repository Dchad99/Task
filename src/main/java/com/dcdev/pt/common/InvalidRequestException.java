package com.dcdev.pt.common;

/** Request parameters are syntactically valid but not acceptable. Maps to 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
