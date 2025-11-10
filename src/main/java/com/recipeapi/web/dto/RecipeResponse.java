package com.recipeapi.web.dto;

import com.recipeapi.domain.Difficulty;

import java.time.Instant;
import java.util.List;

/**
 * The full representation of a recipe.
 *
 * <p>A record, so it is immutable and cannot be handed back to the persistence layer by accident.
 * The whole point of this type is that it holds plain data - by the time one exists, every lazy
 * association has already been read inside a transaction, so Jackson can never trip over a proxy.
 *
 * <p>totalMinutes is included even though it is derived. Clients should not have to know that it
 * is prep + cook.
 */
public record RecipeResponse(
        Long id,
        String title,
        String description,
        String instructions,
        int prepMinutes,
        int cookMinutes,
        int totalMinutes,
        int servings,
        Difficulty difficulty,
        List<String> categories,
        List<RecipeIngredientResponse> ingredients,
        Instant createdAt,
        Instant updatedAt
) {
}
