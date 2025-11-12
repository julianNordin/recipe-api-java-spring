package com.recipeapi.exception;

/**
 * Thrown when one recipe lists the same ingredient more than once.
 *
 * <p>The database would catch this too, via
 * {@code UNIQUE (recipe_id, ingredient_id)} - but only as a constraint violation at flush time,
 * which surfaces as a 500 with a SQL message in it. Catching it in the service turns it into a
 * 400 that names the ingredient.
 */
public class DuplicateIngredientException extends RuntimeException {

    public DuplicateIngredientException(String name) {
        super("Ingredient '%s' is listed more than once. Combine the quantities into one line.".formatted(name));
    }
}
