package com.interlunium.pdfinspector.service;

/**
 * Thrown when a requested resource (document, revision, signature) does not exist.
 */
public class NotFoundException extends RuntimeException {

    /**
     * @param message description of the missing resource
     */
    public NotFoundException(String message) {
        super(message);
    }
}
