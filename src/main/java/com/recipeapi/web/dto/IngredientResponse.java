package com.recipeapi.web.dto;

/**
 * An ingredient as the API returns it.
 *
 * <p>Deliberately without a count of the recipes that use it. Listing ingredients would then need
 * that count per row, which is either a query per ingredient or a group-by that most callers of
 * this endpoint have no use for.
 */
public record IngredientResponse(Long id, String name) {
}
