package com.recipeapi.service;

import com.recipeapi.domain.Category;
import com.recipeapi.domain.Ingredient;
import com.recipeapi.domain.Recipe;
import com.recipeapi.repository.CategoryRepository;
import com.recipeapi.repository.IngredientRepository;
import com.recipeapi.repository.RecipeRepository;
import com.recipeapi.web.dto.RecipeIngredientRequest;
import com.recipeapi.web.dto.RecipeRequest;
import com.recipeapi.web.dto.RecipeResponse;
import com.recipeapi.web.dto.RecipeSummaryResponse;
import com.recipeapi.web.mapper.RecipeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The transaction boundary for recipes.
 *
 * <p>Reading a recipe's categories or ingredient lines needs an open persistence context, and
 * with open-in-view disabled that context closes when the transactional method returns - so
 * entities become DTOs here, before the controller ever sees them.
 */
@Service
public class RecipeService {

    private final RecipeRepository recipes;
    private final IngredientRepository ingredients;
    private final CategoryRepository categories;
    private final RecipeMapper mapper;

    public RecipeService(RecipeRepository recipes,
                         IngredientRepository ingredients,
                         CategoryRepository categories,
                         RecipeMapper mapper) {
        this.recipes = recipes;
        this.ingredients = ingredients;
        this.categories = categories;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<RecipeSummaryResponse> findAll() {
        return recipes.findAll().stream().map(mapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public Optional<RecipeResponse> findById(Long id) {
        return recipes.findById(id).map(mapper::toResponse);
    }

    @Transactional
    public RecipeResponse create(RecipeRequest request) {
        Recipe recipe = new Recipe(
                request.title(),
                request.description(),
                request.instructions(),
                request.prepMinutes(),
                request.cookMinutes(),
                request.servings(),
                request.difficulty());

        applyIngredients(recipe, request.ingredients());
        applyCategories(recipe, request.categories());

        return mapper.toResponse(recipes.save(recipe));
    }

    /**
     * Resolves each requested ingredient name to a row, creating one only if that name has never
     * been seen.
     *
     * <p>This is the rule that keeps {@code ingredients} a shared vocabulary rather than a pile
     * of near-duplicates. The lookup is case-insensitive because the database's uniqueness rule
     * is - inserting "flour" next to an existing "Flour" would be rejected by
     * {@code ux_ingredients_name_lower} anyway, so the service has to agree with the constraint
     * rather than discover it.
     */
    private void applyIngredients(Recipe recipe, List<RecipeIngredientRequest> lines) {
        if (lines == null) {
            return;
        }
        for (RecipeIngredientRequest line : lines) {
            String name = line.name().trim();
            Ingredient ingredient = ingredients.findByNameIgnoreCase(name)
                    .orElseGet(() -> ingredients.save(new Ingredient(name)));
            recipe.addIngredient(ingredient, line.quantity(), line.unit());
        }
    }

    /**
     * Attaches existing categories by name. A recipe never creates a category - they are curated
     * reference data - so an unrecognised name is a client error rather than something to invent.
     *
     * <p>One query for the whole batch rather than one per name, and
     * {@code toLowerCase(Locale.ROOT)} rather than the default locale: under a Turkish locale
     * "I".toLowerCase() is a dotless "i" and would silently stop matching.
     */
    private void applyCategories(Recipe recipe, List<String> names) {
        if (names == null || names.isEmpty()) {
            return;
        }
        List<String> wanted = names.stream()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList();

        Map<String, Category> byLowerName = new HashMap<>();
        for (Category category : categories.findAllByLowerCasedNameIn(
                wanted.stream().map(name -> name.toLowerCase(Locale.ROOT)).toList())) {
            byLowerName.put(category.getName().toLowerCase(Locale.ROOT), category);
        }

        for (String name : wanted) {
            Category category = byLowerName.get(name.toLowerCase(Locale.ROOT));
            if (category == null) {
                throw new IllegalArgumentException("Unknown category: " + name);
            }
            recipe.addCategory(category);
        }
    }
}
