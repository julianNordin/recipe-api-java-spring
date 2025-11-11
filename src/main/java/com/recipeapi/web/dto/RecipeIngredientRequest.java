package com.recipeapi.web.dto;

import com.recipeapi.domain.MeasurementUnit;

import java.math.BigDecimal;

/**
 * One ingredient line on the way in.
 *
 * <p>The ingredient is identified by <em>name</em>, not by id. Callers should not have to create
 * an ingredient resource before they can write a recipe that uses flour - the service resolves
 * the name against the existing rows and only creates one if it has genuinely never been seen.
 */
public record RecipeIngredientRequest(
        String name,
        BigDecimal quantity,
        MeasurementUnit unit
) {
}
