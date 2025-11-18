package com.recipeapi.exception;

import java.util.Collection;

/**
 * Thrown when a caller asks to sort by a property that is not sortable. Mapped to 400.
 *
 * <p>Without this, Spring Data raises PropertyReferenceException deep inside query construction
 * and the caller receives a 500 for what is plainly a bad request.
 */
public class InvalidSortException extends RuntimeException {

    public InvalidSortException(String property, Collection<String> allowed) {
        super("Cannot sort by '%s'. Sortable properties are: %s.".formatted(property, String.join(", ", allowed)));
    }
}
