package com.recipeapi.exception;

/**
 * Thrown when an ingredient with that name already exists. Mapped to 409.
 *
 * <p>Matching is case-insensitive, because that is how the database defines uniqueness:
 * {@code ux_ingredients_name_lower} would reject "flour" alongside "Flour" anyway. Answering 409
 * here rather than letting the constraint fire turns a 500 carrying SQL into a conflict that
 * names the ingredient.
 *
 * <p>Not 200-with-the-existing-row. Creating something that is already there is a client mistake
 * worth reporting; recipes get the forgiving behaviour instead, resolving an ingredient by name
 * and reusing it, because there the name is a reference rather than a request to create.
 */
public class IngredientAlreadyExistsException extends RuntimeException {

    public IngredientAlreadyExistsException(String name) {
        super("Ingredient '%s' already exists. Ingredient names are unique, ignoring case.".formatted(name));
    }
}
