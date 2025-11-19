package com.recipeapi.web.dto;

import com.recipeapi.domain.Difficulty;

/**
 * The optional query parameters of {@code GET /api/recipes}, gathered into one object.
 *
 * <p>Bundled rather than passed as five loose arguments so that adding a sixth filter does not
 * change the signature of every method between the controller and the repository. Every field is
 * nullable - each filter is independently optional.
 */
public record RecipeFilter(
        String search,
        String category,
        Difficulty difficulty,
        Integer maxMinutes,
        String ingredient
) {
}
