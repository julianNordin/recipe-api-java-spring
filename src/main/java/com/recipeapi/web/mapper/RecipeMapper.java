package com.recipeapi.web.mapper;

import com.recipeapi.domain.Category;
import com.recipeapi.domain.Recipe;
import com.recipeapi.domain.RecipeIngredient;
import com.recipeapi.web.dto.RecipeIngredientResponse;
import com.recipeapi.web.dto.RecipeResponse;
import com.recipeapi.web.dto.RecipeSummaryResponse;
import org.springframework.stereotype.Component;

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

    public RecipeResponse toResponse(Recipe recipe) {
        return new RecipeResponse(
                recipe.getId(),
                recipe.getTitle(),
                recipe.getDescription(),
                recipe.getInstructions(),
                recipe.getPrepMinutes(),
                recipe.getCookMinutes(),
                recipe.totalMinutes(),
                recipe.getServings(),
                recipe.getDifficulty(),
                categoryNames(recipe),
                recipe.getIngredients().stream().map(this::toResponse).toList(),
                recipe.getCreatedAt(),
                recipe.getUpdatedAt()
        );
    }

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
        return new RecipeIngredientResponse(
                line.getIngredient().getId(),
                line.getIngredient().getName(),
                line.getQuantity(),
                line.getUnit(),
                line.getUnit().symbol()
        );
    }

    private List<String> categoryNames(Recipe recipe) {
        return recipe.getCategories().stream().map(Category::getName).sorted().toList();
    }
}
