package com.recipeapi.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The payload for creating an ingredient.
 *
 * <p>Only a name: quantity and unit describe how one <em>recipe</em> uses an ingredient, not the
 * ingredient itself, and live on the association row.
 *
 * <p>The 100-character maximum matches the column width in the Flyway migration. Where the API
 * and the schema disagree the database wins, and a 400 the caller can act on becomes a 500 they
 * cannot.
 */
public record IngredientRequest(

        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name
) {
}
