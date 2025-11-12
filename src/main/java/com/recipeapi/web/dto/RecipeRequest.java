package com.recipeapi.web.dto;

import com.recipeapi.domain.Difficulty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The create/update payload for a recipe.
 *
 * <p>The numeric fields are boxed {@code Integer}, not {@code int}, and that is deliberate.
 * Jackson deserialises a missing JSON field into {@code 0} for a primitive, which is
 * indistinguishable from a caller genuinely sending zero - so "servings was omitted" and
 * "servings was 0" would collapse into the same value and {@code @NotNull} could never fire.
 *
 * <p>Every {@code @Size} maximum matches the column width in the Flyway migration. Where they
 * disagree the database wins, and a 400 the caller can act on becomes a 500 they cannot.
 */
public record RecipeRequest(

        @NotBlank(message = "title is required")
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,

        @Size(max = 1000, message = "description must be at most 1000 characters")
        String description,

        @NotBlank(message = "instructions are required")
        @Size(max = 5000, message = "instructions must be at most 5000 characters")
        String instructions,

        @NotNull(message = "prepMinutes is required")
        @Min(value = 0, message = "prepMinutes cannot be negative")
        @Max(value = 1440, message = "prepMinutes cannot exceed one day")
        Integer prepMinutes,

        @NotNull(message = "cookMinutes is required")
        @Min(value = 0, message = "cookMinutes cannot be negative")
        @Max(value = 1440, message = "cookMinutes cannot exceed one day")
        Integer cookMinutes,

        @NotNull(message = "servings is required")
        @Positive(message = "servings must be greater than zero")
        @Max(value = 100, message = "servings cannot exceed 100")
        Integer servings,

        @NotNull(message = "difficulty is required")
        Difficulty difficulty,

        /*
         * @NotEmpty is the whole "a recipe must have at least one ingredient" rule, stated
         * declaratively. @Valid cascades into each line - without it the element constraints on
         * RecipeIngredientRequest are never evaluated at all.
         */
        @NotEmpty(message = "a recipe must have at least one ingredient")
        @Valid
        List<RecipeIngredientRequest> ingredients,

        List<@NotBlank(message = "category names cannot be blank") String> categories
) {
}
