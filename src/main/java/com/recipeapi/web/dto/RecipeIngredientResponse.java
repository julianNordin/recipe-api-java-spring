package com.recipeapi.web.dto;

import com.recipeapi.domain.MeasurementUnit;

import java.math.BigDecimal;

/**
 * One ingredient line as the API returns it: the ingredient flattened into the line, because a
 * caller reading a recipe wants "300 g of Flour", not a nested ingredient resource.
 */
public record RecipeIngredientResponse(
        Long ingredientId,
        String name,
        BigDecimal quantity,
        MeasurementUnit unit,
        String unitSymbol
) {
}
