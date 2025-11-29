package com.recipeapi.repository;

import com.recipeapi.domain.Category;
import com.recipeapi.domain.Difficulty;
import com.recipeapi.domain.Ingredient;
import com.recipeapi.domain.Recipe;
import com.recipeapi.domain.MeasurementUnit;
import com.recipeapi.repository.CategoryRepository.CategoryWithRecipeCount;
import com.recipeapi.support.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The recipe count behind {@code GET /api/categories}, against real SQL.
 *
 * <p>It has to be real SQL. The whole question is what a LEFT JOIN and an aggregate do with a
 * category nothing points at, and no mock has an opinion about that. The V2 migration seeds ten
 * categories and this slice adds recipes to a few of them, so the untouched ones are the fixture
 * for the case that matters.
 */
@DataJpaTest
class CategoryRepositoryTest extends AbstractPostgresTest {

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private RecipeRepository recipes;

    @Autowired
    private IngredientRepository ingredients;

    /**
     * The assertion the query is written for.
     *
     * <p>An inner join would drop Snack and Vegan entirely; {@code count(*)} would keep them and
     * report 1, counting the row the outer join manufactured rather than a recipe. Only
     * {@code count(recipe)} over a LEFT JOIN reports 0 - and a category with no recipes is
     * precisely the row a client building a filter control needs to know about.
     */
    @Test
    void countsZeroForACategoryNoRecipeUsesRatherThanDroppingOrMiscountingIt() {
        recipeIn("Pancakes", "Breakfast");

        Map<String, Long> counts = countsByName();

        assertThat(counts).containsEntry("Breakfast", 1L);
        assertThat(counts)
                .as("every seeded category is listed, including the ones nothing points at")
                .containsEntry("Snack", 0L)
                .containsEntry("Vegan", 0L)
                .hasSize(10);
    }

    @Test
    void countsEveryRecipeFiledUnderACategory() {
        recipeIn("Pancakes", "Breakfast");
        recipeIn("Waffles", "Breakfast");
        recipeIn("Porridge", "Breakfast");

        assertThat(countsByName()).containsEntry("Breakfast", 3L);
    }

    /**
     * A recipe in several categories must count once in each, not once in total - the join fans
     * out and the group-by is what puts it back.
     */
    @Test
    void countsARecipeOnceUnderEachOfItsCategories() {
        recipeIn("Quick Salad", "Lunch", "Vegetarian", "Vegan");

        Map<String, Long> counts = countsByName();

        assertThat(counts).containsEntry("Lunch", 1L)
                .containsEntry("Vegetarian", 1L)
                .containsEntry("Vegan", 1L)
                .containsEntry("Dinner", 0L);
    }

    @Test
    void listsCategoriesInNameOrder() {
        assertThat(categories.findAllWithRecipeCount())
                .extracting(CategoryWithRecipeCount::getName)
                .isSorted()
                .startsWith("Baking", "Breakfast");
    }

    private Map<String, Long> countsByName() {
        return categories.findAllWithRecipeCount().stream()
                .collect(Collectors.toMap(CategoryWithRecipeCount::getName,
                        CategoryWithRecipeCount::getRecipeCount,
                        (first, second) -> first,
                        LinkedHashMap::new));
    }

    private void recipeIn(String title, String... categoryNames) {
        Recipe recipe = new Recipe(title, null, "Cook it.", 5, 5, 2, Difficulty.EASY);
        recipe.addIngredient(anIngredient(), new BigDecimal("1.00"), MeasurementUnit.PIECE);
        for (String name : categoryNames) {
            recipe.addCategory(category(name));
        }
        recipes.saveAndFlush(recipe);
    }

    private Ingredient anIngredient() {
        return ingredients.findByNameIgnoreCase("Flour").orElseThrow();
    }

    private Category category(String name) {
        return categories.findByNameIgnoreCase(name).orElseThrow();
    }
}
