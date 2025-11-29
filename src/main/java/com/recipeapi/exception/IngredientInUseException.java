package com.recipeapi.exception;

/**
 * Thrown when an ingredient cannot be deleted because a recipe still calls for it. Mapped to 409:
 * the request is well formed and the resource exists, it just conflicts with data that also does.
 *
 * <p>The schema agrees. {@code fk_recipe_ingredients_ingredient} deliberately carries no
 * {@code ON DELETE CASCADE}, unlike the other three foreign keys, so a delete that slipped past
 * this check is refused by Postgres rather than silently gutting every recipe that used the
 * ingredient.
 */
public class IngredientInUseException extends RuntimeException {

    public IngredientInUseException(String name) {
        super(("Ingredient '%s' is still used by at least one recipe. Remove it from those recipes "
                + "before deleting it.").formatted(name));
    }
}
