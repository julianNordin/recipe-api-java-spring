package com.recipeapi.exception;

/** Thrown when a resource is addressed by an id that does not exist. Mapped to 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Object id) {
        super("%s %s was not found".formatted(resource, id));
    }
}
