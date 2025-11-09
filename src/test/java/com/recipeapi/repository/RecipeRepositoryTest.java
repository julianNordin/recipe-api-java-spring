package com.recipeapi.repository;

import com.recipeapi.domain.Category;
import com.recipeapi.domain.Difficulty;
import com.recipeapi.domain.Ingredient;
import com.recipeapi.domain.MeasurementUnit;
import com.recipeapi.domain.Recipe;
import com.recipeapi.domain.RecipeIngredient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the cascade and orphan-removal semantics declared on {@link Recipe} actually behave
 * that way against Postgres, rather than only reading as though they do.
 */
class RecipeRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private RecipeRepository recipes;

    @Autowired
    private IngredientRepository ingredients;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private TestEntityManager entityManager;

    private static Recipe pancakes() {
        return new Recipe("Pancakes", "Fluffy American pancakes", "Mix. Fry.", 10, 15, 4, Difficulty.EASY);
    }

    private long countRows(String table) {
        return ((Number) entityManager.getEntityManager()
                .createNativeQuery("select count(*) from " + table)
                .getSingleResult()).longValue();
    }

    @Test
    void savingARecipeCascadesItsIngredientLines() {
        Ingredient flour = ingredients.findByNameIgnoreCase("Flour").orElseThrow();
        Recipe recipe = pancakes();
        recipe.addIngredient(flour, new BigDecimal("300.00"), MeasurementUnit.GRAM);

        Long id = recipes.saveAndFlush(recipe).getId();
        entityManager.clear();

        Recipe reloaded = recipes.findById(id).orElseThrow();
        assertThat(reloaded.getIngredients()).hasSize(1);
        RecipeIngredient line = reloaded.getIngredients().get(0);
        assertThat(line.getIngredient().getName()).isEqualTo("Flour");
        assertThat(line.getQuantity()).isEqualByComparingTo("300.00");
        assertThat(line.getUnit()).isEqualTo(MeasurementUnit.GRAM);
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void droppingALineFromTheCollectionDeletesTheRowThroughOrphanRemoval() {
        Recipe recipe = pancakes();
        recipe.addIngredient(ingredients.findByNameIgnoreCase("Flour").orElseThrow(),
                new BigDecimal("300.00"), MeasurementUnit.GRAM);
        recipe.addIngredient(ingredients.findByNameIgnoreCase("Milk").orElseThrow(),
                new BigDecimal("500.00"), MeasurementUnit.MILLILITRE);
        Long id = recipes.saveAndFlush(recipe).getId();
        long before = countRows("recipe_ingredients");

        Recipe managed = recipes.findById(id).orElseThrow();
        managed.removeIngredient(managed.getIngredients().get(0));
        recipes.saveAndFlush(managed);

        assertThat(countRows("recipe_ingredients")).isEqualTo(before - 1);
    }

    @Test
    void deletingARecipeDeletesItsLinesButLeavesTheIngredientsAlone() {
        Recipe recipe = pancakes();
        recipe.addIngredient(ingredients.findByNameIgnoreCase("Flour").orElseThrow(),
                new BigDecimal("300.00"), MeasurementUnit.GRAM);
        Long id = recipes.saveAndFlush(recipe).getId();
        long ingredientsBefore = ingredients.count();

        recipes.deleteById(id);
        entityManager.flush();

        assertThat(recipes.findById(id)).isEmpty();
        assertThat(countRows("recipe_ingredients")).isZero();
        assertThat(ingredients.count())
                .as("ingredients are shared reference data and must survive the recipe")
                .isEqualTo(ingredientsBefore);
    }

    @Test
    void categoriesAreLinkedThroughTheJoinTableAndOutliveTheRecipe() {
        Category breakfast = categories.findByNameIgnoreCase("Breakfast").orElseThrow();
        Recipe recipe = pancakes();
        recipe.addCategory(breakfast);
        Long id = recipes.saveAndFlush(recipe).getId();
        entityManager.clear();

        assertThat(recipes.findById(id).orElseThrow().getCategories())
                .extracting(Category::getName)
                .containsExactly("Breakfast");

        long categoriesBefore = categories.count();
        recipes.deleteById(id);
        entityManager.flush();

        assertThat(countRows("recipe_categories")).isZero();
        assertThat(categories.count())
                .as("no cascade to categories - deleting a recipe must not delete Breakfast")
                .isEqualTo(categoriesBefore);
    }
}
