package com.recipeapi.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Plain unit tests for the entity graph - no Spring context, no database. These pin the
 * behaviour of the association helpers, which are the only place both sides of a bidirectional
 * relationship are kept in step.
 */
class RecipeAssociationTest {

    private static Recipe pancakes() {
        return new Recipe("Pancakes", "Fluffy American pancakes", "Mix. Fry.", 10, 15, 4, Difficulty.EASY);
    }

    @Test
    void totalMinutesSumsPrepAndCook() {
        assertThat(pancakes().totalMinutes()).isEqualTo(25);
    }

    @Test
    void addIngredientSetsBothSidesOfTheAssociation() {
        Recipe recipe = pancakes();

        RecipeIngredient line = recipe.addIngredient(new Ingredient("Flour"), new BigDecimal("300"), MeasurementUnit.GRAM);

        assertThat(recipe.getIngredients()).containsExactly(line);
        assertThat(line.getRecipe()).isSameAs(recipe);
    }

    @Test
    void removeIngredientClearsTheBackReferenceSoOrphanRemovalCanDeleteIt() {
        Recipe recipe = pancakes();
        RecipeIngredient line = recipe.addIngredient(new Ingredient("Flour"), new BigDecimal("300"), MeasurementUnit.GRAM);

        recipe.removeIngredient(line);

        assertThat(recipe.getIngredients()).isEmpty();
        assertThat(line.getRecipe()).isNull();
    }

    @Test
    void clearIngredientsEmptiesEveryLineAndItsBackReference() {
        Recipe recipe = pancakes();
        RecipeIngredient flour = recipe.addIngredient(new Ingredient("Flour"), new BigDecimal("300"), MeasurementUnit.GRAM);
        RecipeIngredient milk = recipe.addIngredient(new Ingredient("Milk"), new BigDecimal("500"), MeasurementUnit.MILLILITRE);

        recipe.clearIngredients();

        assertThat(recipe.getIngredients()).isEmpty();
        assertThat(flour.getRecipe()).isNull();
        assertThat(milk.getRecipe()).isNull();
    }

    @Test
    void addCategorySetsBothSidesAndIsIdempotent() {
        Recipe recipe = pancakes();
        Category breakfast = new Category("Breakfast");

        recipe.addCategory(breakfast);
        recipe.addCategory(breakfast);

        assertThat(recipe.getCategories()).containsExactly(breakfast);
        assertThat(breakfast.getRecipes()).containsExactly(recipe);
    }

    @Test
    void removeCategoryUpdatesBothSides() {
        Recipe recipe = pancakes();
        Category breakfast = new Category("Breakfast");
        recipe.addCategory(breakfast);

        recipe.removeCategory(breakfast);

        assertThat(recipe.getCategories()).isEmpty();
        assertThat(breakfast.getRecipes()).isEmpty();
    }

    /**
     * Both sides of a bidirectional association printing each other is an infinite loop. This
     * failed with StackOverflowError, not an assertion error, before toString was narrowed to
     * scalar columns.
     */
    @Test
    void toStringDoesNotRecurseAcrossTheAssociation() {
        Recipe recipe = pancakes();
        recipe.addIngredient(new Ingredient("Flour"), new BigDecimal("300"), MeasurementUnit.GRAM);
        recipe.addCategory(new Category("Breakfast"));
        RecipeIngredient line = recipe.getIngredients().get(0);

        assertThatCode(recipe::toString).doesNotThrowAnyException();
        assertThatCode(line::toString).doesNotThrowAnyException();
    }

    /**
     * The rule that keeps the recursion gone: an entity's toString reports its own columns and
     * nothing reachable through an association. Anything else either loops or fires a lazy load
     * from inside a log statement.
     */
    @Test
    void toStringReportsOwnColumnsOnlyAndNeverReachesThroughAnAssociation() {
        Recipe recipe = pancakes();
        recipe.addIngredient(new Ingredient("Flour"), new BigDecimal("300"), MeasurementUnit.GRAM);
        recipe.addCategory(new Category("Breakfast"));

        assertThat(recipe.toString())
                .contains("Pancakes")
                .doesNotContain("Flour")
                .doesNotContain("Breakfast");

        assertThat(recipe.getIngredients().get(0).toString())
                .contains("GRAM")
                .contains("300");
    }

    @Test
    void transientEntitiesAreEqualOnlyToThemselves() {
        Recipe one = pancakes();
        Recipe other = pancakes();

        assertThat(one).isEqualTo(one);
        assertThat(one).isNotEqualTo(other);
    }

    /**
     * The hash must not move when Hibernate assigns an id on flush, or an entity added to a
     * HashSet before saving becomes unfindable in it afterwards. A constant hash guarantees it.
     */
    @Test
    void hashCodeIsIndependentOfMutableState() {
        Recipe one = pancakes();
        Recipe other = pancakes();
        other.setTitle("Waffles");
        other.setServings(12);

        assertThat(one).hasSameHashCodeAs(other);
    }
}
