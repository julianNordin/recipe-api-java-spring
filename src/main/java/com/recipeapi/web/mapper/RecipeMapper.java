package com.recipeapi.web.mapper;

import com.recipeapi.domain.Category;
import com.recipeapi.domain.Recipe;
import com.recipeapi.domain.RecipeIngredient;
import com.recipeapi.service.ServingsScaler;
import com.recipeapi.web.dto.RecipeIngredientResponse;
import com.recipeapi.web.dto.RecipeResponse;
import com.recipeapi.web.dto.RecipeSummaryResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Turns entities into response records.
 *
 * <p>Every method here reads lazy associations, so it must be called while a persistence context
 * is still open - that is, from inside the service's transaction, never from a controller after
 * the transaction has committed.
 */
@Component
public class RecipeMapper {

    /** The recipe as stored, for the number of servings it was written for. */
    public RecipeResponse toResponse(Recipe recipe) {
        return toResponse(recipe, recipe.getServings());
    }

    /**
     * The recipe restated for a different number of servings.
     *
     * <p>There is deliberately no separate "unscaled" path. Scaling to the servings a recipe
     * already has is exact - see {@link ServingsScaler#scale} - so the ordinary response is just
     * this method asked for the number it already serves, and there is no branch that could be
     * right in one case and wrong in the other.
     *
     * <p>The response shape does not change: {@code servings} reports what was asked for, and a
     * caller who wants the recipe as written simply omits the parameter. Nothing is persisted -
     * this is a presentation of the same row.
     */
    public RecipeResponse toResponse(Recipe recipe, int servings) {
        return new RecipeResponse(
                recipe.getId(),
                recipe.getTitle(),
                recipe.getDescription(),
                recipe.getInstructions(),
                recipe.getPrepMinutes(),
                recipe.getCookMinutes(),
                recipe.totalMinutes(),
                servings,
                recipe.getDifficulty(),
                categoryNames(recipe),
                recipe.getIngredients().stream()
                        .map(line -> toResponse(line, ServingsScaler.scale(
                                line.getQuantity(), recipe.getServings(), servings)))
                        .toList(),
                recipe.getCreatedAt(),
                recipe.getUpdatedAt()
        );
    }

    /**
     * Times are not scaled, and that is a judgement rather than an omission. Doubling a recipe
     * does not double the time it takes to bake, and a caller can reason about a stated 25 minutes
     * far better than about an invented 50.
     */
    public RecipeSummaryResponse toSummary(Recipe recipe) {
        return new RecipeSummaryResponse(
                recipe.getId(),
                recipe.getTitle(),
                recipe.getDescription(),
                recipe.totalMinutes(),
                recipe.getServings(),
                recipe.getDifficulty(),
                categoryNames(recipe)
        );
    }

    public RecipeIngredientResponse toResponse(RecipeIngredient line) {
        return toResponse(line, line.getQuantity());
    }

    private RecipeIngredientResponse toResponse(RecipeIngredient line, BigDecimal quantity) {
        return new RecipeIngredientResponse(
                line.getIngredient().getId(),
                line.getIngredient().getName(),
                quantity,
                line.getUnit(),
                line.getUnit().symbol()
        );
    }

    private List<String> categoryNames(Recipe recipe) {
        return recipe.getCategories().stream().map(Category::getName).sorted().toList();
    }
}
