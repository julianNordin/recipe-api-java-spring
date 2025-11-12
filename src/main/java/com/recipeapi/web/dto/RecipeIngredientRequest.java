package com.recipeapi.web.dto;

import com.recipeapi.domain.MeasurementUnit;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One ingredient line on the way in.
 *
 * <p>The ingredient is identified by <em>name</em>, not by id. Callers should not have to create
 * an ingredient resource before they can write a recipe that uses flour - the service resolves
 * the name against the existing rows and only creates one if it has genuinely never been seen.
 */
public record RecipeIngredientRequest(

        @NotBlank(message = "ingredient name is required")
        @Size(max = 100, message = "ingredient name must be at most 100 characters")
        String name,

        /*
         * @Digits mirrors numeric(10,2) in the schema. Without it an over-precise quantity is
         * accepted by the API and then rejected by Postgres, which turns a 400 into a 500.
         */
        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be greater than zero")
        @Digits(integer = 8, fraction = 2, message = "quantity must have at most 8 whole digits and 2 decimals")
        BigDecimal quantity,

        @NotNull(message = "unit is required")
        MeasurementUnit unit
) {
}
