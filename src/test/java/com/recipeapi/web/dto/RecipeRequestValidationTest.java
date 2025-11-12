package com.recipeapi.web.dto;

import com.recipeapi.domain.Difficulty;
import com.recipeapi.domain.MeasurementUnit;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the constraints through the Bean Validation API directly - no Spring, no MVC, no
 * database. Phase 10 covers what the HTTP layer does with a violation; this covers whether the
 * violation happens at all.
 */
class RecipeRequestValidationTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    private static RecipeRequest valid() {
        return new RecipeRequest("Pancakes", "Fluffy", "Mix. Fry.", 10, 15, 4, Difficulty.EASY,
                List.of(new RecipeIngredientRequest("Flour", new BigDecimal("300.00"), MeasurementUnit.GRAM)),
                List.of("Breakfast"));
    }

    private static Set<String> messagesFor(RecipeRequest request) {
        return VALIDATOR.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Test
    void aWellFormedRequestHasNoViolations() {
        assertThat(VALIDATOR.validate(valid())).isEmpty();
    }

    @Test
    void titleMustNotBeBlank() {
        RecipeRequest request = new RecipeRequest("   ", null, "Mix.", 10, 15, 4, Difficulty.EASY,
                valid().ingredients(), List.of());

        assertThat(messagesFor(request)).contains("title is required");
    }

    @Test
    void omittedNumbersAreRejectedRatherThanSilentlyBecomingZero() {
        RecipeRequest request = new RecipeRequest("Pancakes", null, "Mix.", null, null, null,
                Difficulty.EASY, valid().ingredients(), List.of());

        assertThat(messagesFor(request))
                .contains("prepMinutes is required", "cookMinutes is required", "servings is required");
    }

    @Test
    void aRecipeMustHaveAtLeastOneIngredient() {
        RecipeRequest request = new RecipeRequest("Pancakes", null, "Mix.", 10, 15, 4, Difficulty.EASY,
                List.of(), List.of());

        assertThat(messagesFor(request)).contains("a recipe must have at least one ingredient");
    }

    /**
     * The reason {@code @Valid} on the ingredients list matters. Bean Validation does not descend
     * into collection elements on its own, so without it a line could carry a blank name and a
     * negative quantity and still validate clean.
     */
    @Test
    void constraintsCascadeIntoEachIngredientLine() {
        RecipeRequest request = new RecipeRequest("Pancakes", null, "Mix.", 10, 15, 4, Difficulty.EASY,
                List.of(new RecipeIngredientRequest("  ", new BigDecimal("-5"), MeasurementUnit.GRAM)),
                List.of());

        Set<ConstraintViolation<RecipeRequest>> violations = VALIDATOR.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("ingredients[0].name", "ingredients[0].quantity");
        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .contains("ingredient name is required", "quantity must be greater than zero");
    }

    /**
     * quantity is numeric(10,2) in the schema. Without @Digits an over-precise value passes
     * validation and is then rejected by Postgres, turning a 400 into a 500.
     */
    @Test
    void quantityCannotBeMorePreciseThanTheColumn() {
        RecipeRequest request = new RecipeRequest("Pancakes", null, "Mix.", 10, 15, 4, Difficulty.EASY,
                List.of(new RecipeIngredientRequest("Flour", new BigDecimal("1.23456"), MeasurementUnit.GRAM)),
                List.of());

        assertThat(messagesFor(request))
                .contains("quantity must have at most 8 whole digits and 2 decimals");
    }
}
