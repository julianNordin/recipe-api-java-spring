package com.recipeapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /api/categories}, end to end.
 *
 * <p>{@code CategoryRepositoryTest} already pins the LEFT JOIN and the aggregate against real
 * SQL. What is left for this tier is the contract on the wire: a plain array rather than a paged
 * envelope, and a count that is a number rather than whatever a projection happens to serialise
 * into.
 */
class CategoryApiIT extends AbstractIntegrationTest {

    /**
     * Two categories rather than the base class's one, and that is the whole point: a recipe filed
     * under both has to raise both counts. With a single-category fixture, a count that really
     * meant "recipes having any category at all" would pass just as happily.
     */
    private static final String PANCAKES_IN_TWO_CATEGORIES = """
            {
              "title": "Pancakes",
              "instructions": "Whisk, rest, fry.",
              "prepMinutes": 10, "cookMinutes": 15, "servings": 4, "difficulty": "EASY",
              "ingredients": [{"name": "Flour", "quantity": 300, "unit": "GRAM"}],
              "categories": ["Breakfast", "Baking"]
            }""";

    /**
     * A bare JSON array, not a paged envelope. Categories are a closed curated set and the caller
     * is almost always filling a filter control, which needs all of them - paging would make every
     * client loop to rebuild a list that fits in one response.
     */
    @Test
    void returnsEverySeededCategoryAsAPlainArray() {
        JsonNode categories = body(get("/api/categories"));

        assertThat(categories.isArray()).isTrue();
        assertThat(categories).hasSize(10);
        assertThat(categories.findValuesAsText("name")).isSorted().startsWith("Baking", "Breakfast");
    }

    @Test
    void reportsZeroForEveryCategoryBeforeAnyRecipeExists() {
        assertThat(countsByName(body(get("/api/categories")))).containsOnlyKeys(
                        "Baking", "Breakfast", "Dessert", "Dinner", "Gluten-Free",
                        "Lunch", "Side", "Snack", "Vegan", "Vegetarian")
                .containsValue(0L)
                .doesNotContainValue(1L);
    }

    @Test
    void countsARecipeUnderEachOfItsCategoriesAndLeavesTheRestAtZero() {
        seedRecipe(PANCAKES_IN_TWO_CATEGORIES);

        Map<String, Long> counts = countsByName(body(get("/api/categories")));

        assertThat(counts).containsEntry("Breakfast", 1L)
                .containsEntry("Baking", 1L)
                .containsEntry("Dinner", 0L)
                .containsEntry("Snack", 0L)
                .hasSize(10);
    }

    /** The count drops back when the recipe goes, which is the join rows cascading with it. */
    @Test
    void stopsCountingARecipeOnceItIsDeleted() {
        long id = seedRecipe(PANCAKES_IN_TWO_CATEGORIES);
        assertThat(countsByName(body(get("/api/categories")))).containsEntry("Breakfast", 1L);

        delete("/api/recipes/" + id);

        assertThat(countsByName(body(get("/api/categories")))).containsEntry("Breakfast", 0L);
    }

    private static Map<String, Long> countsByName(JsonNode categories) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (JsonNode category : categories) {
            counts.put(category.get("name").asText(), category.get("recipeCount").asLong());
        }
        return counts;
    }
}
