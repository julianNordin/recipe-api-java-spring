package com.recipeapi.web.dto;

import com.recipeapi.domain.Difficulty;

import java.util.List;

/**
 * The list-endpoint shape: enough to render a row, without the instructions or the ingredient
 * lines. Keeping the collection endpoint deliberately lighter than the detail endpoint means
 * listing a hundred recipes does not ship a hundred sets of instructions.
 */
public record RecipeSummaryResponse(
        Long id,
        String title,
        String description,
        int totalMinutes,
        int servings,
        Difficulty difficulty,
        List<String> categories
) {
}
