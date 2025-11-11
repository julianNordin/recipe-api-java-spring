package com.recipeapi.web.dto;

import com.recipeapi.domain.Difficulty;

import java.util.List;

/**
 * The create/update payload for a recipe.
 *
 * <p>The numeric fields are boxed {@code Integer}, not {@code int}, and that is deliberate.
 * Jackson deserialises a missing JSON field into {@code 0} for a primitive, which is
 * indistinguishable from a caller genuinely sending zero - so "servings was omitted" and
 * "servings was 0" would collapse into the same value and no validation could tell them apart.
 * Boxed, a missing field arrives as {@code null} and Phase 08's {@code @NotNull} can reject it.
 *
 * <p>Categories are given by name and must already exist; a recipe never creates a category.
 */
public record RecipeRequest(
        String title,
        String description,
        String instructions,
        Integer prepMinutes,
        Integer cookMinutes,
        Integer servings,
        Difficulty difficulty,
        List<RecipeIngredientRequest> ingredients,
        List<String> categories
) {
}
