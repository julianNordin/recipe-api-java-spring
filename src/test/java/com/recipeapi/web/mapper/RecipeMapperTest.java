package com.recipeapi.web.mapper;

import com.recipeapi.domain.Category;
import com.recipeapi.domain.Difficulty;
import com.recipeapi.domain.Ingredient;
import com.recipeapi.domain.MeasurementUnit;
import com.recipeapi.domain.Recipe;
import com.recipeapi.web.dto.RecipeIngredientResponse;
import com.recipeapi.web.dto.RecipeResponse;
import com.recipeapi.web.dto.RecipeSummaryResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Plain unit tests - no Spring, no database. The mapper is pure translation, so it should be
 * testable against a hand-built object graph, and the fact that it is confirms it holds no
 * hidden dependency on a persistence context of its own.
 */
class RecipeMapperTest {

    private final RecipeMapper mapper = new RecipeMapper();

    private static Recipe pancakes() {
        Recipe recipe = new Recipe("Pancakes", "Fluffy American pancakes", "Mix. Fry.", 10, 15, 4, Difficulty.EASY);
        recipe.addIngredient(new Ingredient("Flour"), new BigDecimal("300.00"), MeasurementUnit.GRAM);
        recipe.addIngredient(new Ingredient("Milk"), new BigDecimal("0.50"), MeasurementUnit.LITRE);
        return recipe;
    }

    @Test
    void mapsEveryFieldOfTheDetailResponse() {
        RecipeResponse response = mapper.toResponse(pancakes());

        assertThat(response.title()).isEqualTo("Pancakes");
        assertThat(response.instructions()).isEqualTo("Mix. Fry.");
        assertThat(response.prepMinutes()).isEqualTo(10);
        assertThat(response.cookMinutes()).isEqualTo(15);
        assertThat(response.servings()).isEqualTo(4);
        assertThat(response.difficulty()).isEqualTo(Difficulty.EASY);
    }

    @Test
    void exposesTotalMinutesSoCallersDoNotHaveToAddItUp() {
        assertThat(mapper.toResponse(pancakes()).totalMinutes()).isEqualTo(25);
        assertThat(mapper.toSummary(pancakes()).totalMinutes()).isEqualTo(25);
    }

    @Test
    void flattensTheIngredientLineAndIncludesTheUnitSymbol() {
        RecipeResponse response = mapper.toResponse(pancakes());

        assertThat(response.ingredients())
                .extracting(RecipeIngredientResponse::name,
                        RecipeIngredientResponse::quantity,
                        RecipeIngredientResponse::unit,
                        RecipeIngredientResponse::unitSymbol)
                .containsExactly(
                        tuple("Flour", new BigDecimal("300.00"), MeasurementUnit.GRAM, "g"),
                        tuple("Milk", new BigDecimal("0.50"), MeasurementUnit.LITRE, "l"));
    }

    @Test
    void sortsCategoryNamesSoTheResponseOrderIsStable() {
        Recipe recipe = pancakes();
        recipe.addCategory(new Category("Vegetarian"));
        recipe.addCategory(new Category("Breakfast"));

        assertThat(mapper.toResponse(recipe).categories()).containsExactly("Breakfast", "Vegetarian");
    }

    @Test
    void theSummaryShapeOmitsInstructionsAndIngredientLines() {
        RecipeSummaryResponse summary = mapper.toSummary(pancakes());

        assertThat(summary.title()).isEqualTo("Pancakes");
        assertThat(RecipeSummaryResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("instructions", "ingredients");
    }
}
