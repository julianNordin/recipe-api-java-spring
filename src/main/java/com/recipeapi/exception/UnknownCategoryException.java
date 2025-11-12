package com.recipeapi.exception;

/**
 * Thrown when a recipe references a category that does not exist. Mapped to 400, not 404: the
 * request itself is wrong, and the resource the caller addressed - the recipe - is fine.
 */
public class UnknownCategoryException extends RuntimeException {

    public UnknownCategoryException(String name) {
        super("Unknown category: '%s'. Categories are reference data and must already exist.".formatted(name));
    }
}
