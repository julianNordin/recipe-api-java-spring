package com.recipeapi.service;

import com.recipeapi.domain.Category;
import com.recipeapi.domain.Ingredient;
import com.recipeapi.domain.Recipe;
import com.recipeapi.exception.DuplicateIngredientException;
import com.recipeapi.exception.NotFoundException;
import com.recipeapi.exception.UnknownCategoryException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    public RecipeResponse findById(Long id) {
        return mapper.toResponse(require(id));
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
     * Full replacement, as PUT implies: whatever the payload says is what the recipe becomes.
     *
     * <p>The ingredient list is replaced by emptying the existing collection and refilling it.
     * That is what {@code orphanRemoval = true} is for - lines dropped from the collection are
     * deleted rather than left behind pointing at nothing. It has to go through
     * {@code clearIngredients()}, which mutates in place; assigning a fresh list would throw
     * "A collection with cascade=all-delete-orphan was no longer referenced by the owning entity
     * instance".
     *
     * <p>The explicit flush is not decoration. The entity is managed, so the UPDATE would
     * otherwise be issued at commit - after this method has already mapped the response - and
     * {@code @PreUpdate} would not yet have refreshed {@code updatedAt}, so the caller would be
     * handed the old timestamp.
     */
    @Transactional
    public RecipeResponse update(Long id, RecipeRequest request) {
        Recipe recipe = require(id);
        recipe.setTitle(request.title());
        recipe.setDescription(request.description());
        recipe.setInstructions(request.instructions());
        recipe.setPrepMinutes(request.prepMinutes());
        recipe.setCookMinutes(request.cookMinutes());
        recipe.setServings(request.servings());
        recipe.setDifficulty(request.difficulty());

        recipe.clearIngredients();
        recipe.clearCategories();

        // Push the DELETEs out before the INSERTs go in. Hibernate orders a flush by action
        // type, not by the order the code did things, and the collection inserts are issued
        // ahead of the collection deletes - so replacing a list that keeps any of its members
        // re-inserts a row the old one is still occupying:
        //   duplicate key value violates unique constraint
        //   "ux_recipe_ingredients_recipe_ingredient"
        //   Detail: Key (recipe_id, ingredient_id)=(2, 1) already exists.
        // Only shows up when a PUT keeps an ingredient it already had, which is the common case
        // rather than an edge case.
        recipes.flush();

        applyIngredients(recipe, request.ingredients());
        applyCategories(recipe, request.categories());

        recipes.flush();
        return mapper.toResponse(recipe);
    }

    @Transactional
    public void delete(Long id) {
        recipes.delete(require(id));
    }

    /**
     * The single place a missing recipe becomes a 404.
     *
     * <p>Every caller used to unwrap an Optional and decide for itself what absence meant.
     * Funnelling it through one method means the status, the message and the problem-detail type
     * cannot drift apart between endpoints.
     */
    private Recipe require(Long id) {
        return recipes.findById(id).orElseThrow(() -> new NotFoundException("Recipe", id));
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
        Set<String> seen = new HashSet<>();
        for (RecipeIngredientRequest line : lines) {
            String name = line.name().trim();

            // Compared case-insensitively, because "Flour" and "flour" resolve to the same row -
            // so listing both is a duplicate even though the strings differ. Rejecting it here
            // rather than letting UNIQUE (recipe_id, ingredient_id) catch it at flush turns a
            // 500 carrying a SQL message into a 400 that names the ingredient.
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                throw new DuplicateIngredientException(name);
            }

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
                throw new UnknownCategoryException(name);
            }
            recipe.addCategory(category);
        }
    }
}
