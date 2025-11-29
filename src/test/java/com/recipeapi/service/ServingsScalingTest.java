package com.recipeapi.service;

import com.recipeapi.domain.Difficulty;
import com.recipeapi.domain.Ingredient;
import com.recipeapi.domain.MeasurementUnit;
import com.recipeapi.domain.Recipe;
import com.recipeapi.repository.CategoryRepository;
import com.recipeapi.repository.IngredientRepository;
import com.recipeapi.repository.RecipeRepository;
import com.recipeapi.web.dto.RecipeIngredientResponse;
import com.recipeapi.web.dto.RecipeResponse;
import com.recipeapi.web.mapper.RecipeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.when;

/**
 * Scaling a recipe to a different number of servings.
 *
 * <p>No Spring context and no database: the arithmetic has no collaborators, and the service
 * tests below reach it through mocked repositories. Everything that can go wrong here is
 * arithmetic, and all of it goes wrong quietly - so the fixtures deliberately avoid the small
 * round numbers that would let a wrong implementation pass.
 */
@ExtendWith(MockitoExtension.class)
class ServingsScalingTest {

    @Mock
    private RecipeRepository recipes;

    @Mock
    private IngredientRepository ingredients;

    @Mock
    private CategoryRepository categories;

    @Spy
    private RecipeMapper mapper = new RecipeMapper();

    @InjectMocks
    private RecipeService service;

    @Test
    void scalesUp() {
        assertThat(ServingsScaler.scale(new BigDecimal("300.00"), 4, 6)).isEqualByComparingTo("450.00");
    }

    @Test
    void scalesDown() {
        assertThat(ServingsScaler.scale(new BigDecimal("300.00"), 4, 2)).isEqualByComparingTo("150.00");
    }

    /**
     * Scaling to the servings a recipe already has must return the quantity <em>exactly</em>, not
     * approximately. It is what lets the plain response and the scaled one share one code path -
     * if this drifted, every unscaled read would quietly drift with it.
     */
    @Test
    void scalingToTheSameNumberOfServingsIsExact() {
        assertThat(ServingsScaler.scale(new BigDecimal("260.00"), 3, 3)).isEqualByComparingTo("260.00");
        assertThat(ServingsScaler.scale(new BigDecimal("0.50"), 7, 7)).isEqualByComparingTo("0.50");
        assertThat(ServingsScaler.scale(new BigDecimal("1.33"), 6, 6)).isEqualByComparingTo("1.33");
    }

    /**
     * The counterexample the implementation is written around.
     *
     * <p>Computing the ratio first means rounding it: 1/3 becomes 0.33, and 260 x 0.33 is 85.80.
     * Multiplying before dividing gives 86.67. Both look plausible in isolation, and the error
     * grows with the quantity - a kilogram would be out by more than three grams - so a fixture
     * of 100s and 200s scaled by 2 would never show it.
     */
    @Test
    void multipliesBeforeDividingSoTheRatioIsNeverRounded() {
        assertThat(ServingsScaler.scale(new BigDecimal("260.00"), 3, 1))
                .isEqualByComparingTo("86.67")
                .isNotEqualByComparingTo("85.80");

        assertThat(ServingsScaler.scale(new BigDecimal("1000.00"), 3, 1))
                .isEqualByComparingTo("333.33")
                .isNotEqualByComparingTo("330.00");
    }

    @Test
    void roundsHalfUpToTheTwoDecimalsTheColumnStores() {
        // 2 x 1 / 3 = 0.666... -> 0.67, not 0.66.
        assertThat(ServingsScaler.scale(new BigDecimal("2.00"), 3, 1)).isEqualByComparingTo("0.67");
        // 5 x 1 / 2 = 2.5 exactly, so the tie goes up.
        assertThat(ServingsScaler.scale(new BigDecimal("0.05"), 2, 1)).isEqualByComparingTo("0.03");
    }

    /**
     * Pinned as a decision rather than left as a surprise: the smallest quantity the schema can
     * store is 0.01, and a quarter of it is not representable at two decimals. Reporting 0.00 is
     * the honest answer - inventing a floor would be the API naming an amount nobody measured.
     */
    @Test
    void reportsZeroWhenAQuantityScalesBelowWhatTheColumnCanHold() {
        assertThat(ServingsScaler.scale(new BigDecimal("0.01"), 4, 1)).isEqualByComparingTo("0.00");
    }

    @Test
    void scalesEveryIngredientOfARecipeReadThroughTheService() {
        when(recipes.findWithAssociationsById(1L)).thenReturn(Optional.of(waffles()));

        RecipeResponse response = service.findById(1L, 1);

        assertThat(response.servings()).isEqualTo(1);
        assertThat(response.ingredients())
                .extracting(RecipeIngredientResponse::name, RecipeIngredientResponse::quantity)
                .containsExactly(
                        tuple("Flour", new BigDecimal("86.67")),
                        tuple("Egg", new BigDecimal("0.67")));
    }

    /**
     * Times describe the cooking, not the quantity, so they must survive scaling untouched -
     * doubling a recipe does not double how long it bakes.
     */
    @Test
    void leavesTimesAloneWhenScaling() {
        when(recipes.findWithAssociationsById(1L)).thenReturn(Optional.of(waffles()));

        RecipeResponse response = service.findById(1L, 12);

        assertThat(response.prepMinutes()).isEqualTo(10);
        assertThat(response.cookMinutes()).isEqualTo(20);
        assertThat(response.totalMinutes()).isEqualTo(30);
    }

    /** No servings parameter means the recipe as written, down to the last decimal. */
    @Test
    void returnsTheRecipeAsWrittenWhenNoServingsAreRequested() {
        when(recipes.findWithAssociationsById(1L)).thenReturn(Optional.of(waffles()));

        RecipeResponse response = service.findById(1L);

        assertThat(response.servings()).isEqualTo(3);
        assertThat(response.ingredients())
                .extracting(RecipeIngredientResponse::quantity)
                .containsExactly(new BigDecimal("260.00"), new BigDecimal("2.00"));
    }

    /** Serves 3, so nothing divides evenly and a rounded ratio would show. */
    private static Recipe waffles() {
        Recipe recipe = new Recipe("Belgian Waffles", "Crisp outside", "Batter, iron, wait.",
                10, 20, 3, Difficulty.MEDIUM);
        recipe.addIngredient(new Ingredient("Flour"), new BigDecimal("260.00"), MeasurementUnit.GRAM);
        recipe.addIngredient(new Ingredient("Egg"), new BigDecimal("2.00"), MeasurementUnit.PIECE);
        return recipe;
    }
}
