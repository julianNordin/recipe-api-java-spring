package com.recipeapi.service;

import com.recipeapi.domain.Ingredient;
import com.recipeapi.exception.IngredientAlreadyExistsException;
import com.recipeapi.exception.IngredientInUseException;
import com.recipeapi.exception.NotFoundException;
import com.recipeapi.repository.IngredientRepository;
import com.recipeapi.web.dto.IngredientResponse;
import com.recipeapi.web.dto.IngredientRequest;
import com.recipeapi.web.dto.PagedResponse;
import com.recipeapi.web.support.IngredientSorts;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The transaction boundary for the shared ingredient vocabulary.
 *
 * <p>Ingredients are reference data. Recipes attach themselves to existing rows and create one
 * only when a name has never been seen, so this endpoint exists for curating the vocabulary
 * itself - browsing it, adding to it deliberately, and removing entries nothing uses.
 */
@Service
public class IngredientService {

    private final IngredientRepository ingredients;

    public IngredientService(IngredientRepository ingredients) {
        this.ingredients = ingredients;
    }

    @Transactional(readOnly = true)
    public PagedResponse<IngredientResponse> findAll(String search, Pageable pageable) {
        Pageable validated = IngredientSorts.validate(pageable);

        Page<Ingredient> page = StringUtils.hasText(search)
                ? ingredients.findByNameContainingIgnoreCase(search.trim(), validated)
                : ingredients.findAll(validated);

        return PagedResponse.from(page.map(IngredientService::toResponse));
    }

    /**
     * Creating one that already exists is a conflict, not a silent success.
     *
     * <p>The check is case-insensitive because the database's uniqueness rule is: the functional
     * index on {@code lower(name)} would reject "flour" next to "Flour" regardless. Checking
     * first turns a 500 carrying a constraint name into a 409 that names the ingredient - and if
     * a concurrent insert wins the race between the check and the flush, the index still refuses
     * it and the handler still answers 409.
     */
    @Transactional
    public IngredientResponse create(IngredientRequest request) {
        String name = request.name().trim();
        if (ingredients.existsByNameIgnoreCase(name)) {
            throw new IngredientAlreadyExistsException(name);
        }
        return toResponse(ingredients.save(new Ingredient(name)));
    }

    /**
     * Deleting an ingredient a recipe still calls for is refused with 409.
     *
     * <p>Two layers, deliberately. This check produces a message that says which ingredient and
     * what to do about it; {@code fk_recipe_ingredients_ingredient} carries no
     * {@code ON DELETE CASCADE}, so even a delete that raced past the check is refused by
     * Postgres rather than quietly removing an ingredient out from under every recipe using it.
     * The alternative - cascading - would turn one DELETE into silent data loss across the table.
     */
    @Transactional
    public void delete(Long id) {
        Ingredient ingredient = ingredients.findById(id)
                .orElseThrow(() -> new NotFoundException("Ingredient", id));

        if (ingredients.isUsedByAnyRecipe(id)) {
            throw new IngredientInUseException(ingredient.getName());
        }
        ingredients.delete(ingredient);
    }

    /**
     * Static, and not a {@code @Component} mapper like {@link com.recipeapi.web.mapper.RecipeMapper}.
     * That one exists because recipe mapping reads lazy associations, sorts category names and
     * flattens ingredient lines - decisions worth testing on their own. This copies two fields.
     */
    private static IngredientResponse toResponse(Ingredient ingredient) {
        return new IngredientResponse(ingredient.getId(), ingredient.getName());
    }
}
