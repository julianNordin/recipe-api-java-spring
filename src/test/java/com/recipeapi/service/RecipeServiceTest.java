package com.recipeapi.service;

import com.recipeapi.domain.Difficulty;
import com.recipeapi.domain.Ingredient;
import com.recipeapi.domain.Category;
import com.recipeapi.domain.MeasurementUnit;
import com.recipeapi.domain.Recipe;
import com.recipeapi.domain.RecipeIngredient;
import com.recipeapi.exception.DuplicateIngredientException;
import com.recipeapi.exception.NotFoundException;
import com.recipeapi.exception.UnknownCategoryException;
import com.recipeapi.repository.CategoryRepository;
import com.recipeapi.repository.IngredientRepository;
import com.recipeapi.repository.RecipeRepository;
import com.recipeapi.web.dto.RecipeIngredientRequest;
import com.recipeapi.web.dto.RecipeRequest;
import com.recipeapi.web.dto.RecipeResponse;
import com.recipeapi.web.mapper.RecipeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RecipeService} with every collaborator mocked.
 *
 * <p>No Spring context, no database, no Testcontainers - the whole class runs in milliseconds.
 * That is the point of it: these tests describe what the service <em>does with</em> its
 * repositories, which is a different question from whether the SQL works. The SQL is covered by
 * {@code RecipeServicePersistenceTest} against real Postgres.
 *
 * <p>The mapper is a {@code @Spy} around a real instance rather than a {@code @Mock}. It is pure
 * translation with no collaborators, so stubbing it would mean asserting against values the test
 * itself invented; using the real one means the assertions are about genuine output.
 */
@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

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

    @Captor
    private ArgumentCaptor<Recipe> persistedRecipe;

    @Captor
    private ArgumentCaptor<Ingredient> persistedIngredient;

    private static RecipeRequest request(List<RecipeIngredientRequest> lines) {
        return new RecipeRequest("Pancakes", "Fluffy", "Mix. Fry.", 10, 15, 4,
                Difficulty.EASY, lines, List.of());
    }

    private static RecipeIngredientRequest line(String name, String quantity, MeasurementUnit unit) {
        return new RecipeIngredientRequest(name, new BigDecimal(quantity), unit);
    }

    /** Hands back whatever was passed to save(), standing in for the persistence round trip. */
    private void savePassesTheRecipeThrough() {
        when(recipes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void reusesAnIngredientThatAlreadyExistsAndNeverSavesAnother() {
        savePassesTheRecipeThrough();
        when(ingredients.findByNameIgnoreCase("fLoUr")).thenReturn(Optional.of(new Ingredient("Flour")));

        service.create(request(List.of(line("fLoUr", "300", MeasurementUnit.GRAM))));

        verify(ingredients).findByNameIgnoreCase("fLoUr");
        verify(ingredients, never()).save(any());
    }

    @Test
    void savesAnIngredientOnlyWhenTheLookupFindsNothing() {
        savePassesTheRecipeThrough();
        when(ingredients.findByNameIgnoreCase("Sourdough Starter")).thenReturn(Optional.empty());
        when(ingredients.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request(List.of(line("Sourdough Starter", "100", MeasurementUnit.GRAM))));

        verify(ingredients).save(any(Ingredient.class));
    }

    @Test
    void resolvesEveryLineButSavesOnlyTheOneThatIsNew() {
        savePassesTheRecipeThrough();
        when(ingredients.findByNameIgnoreCase("Flour")).thenReturn(Optional.of(new Ingredient("Flour")));
        when(ingredients.findByNameIgnoreCase("Buttermilk")).thenReturn(Optional.empty());
        when(ingredients.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request(List.of(
                line("Flour", "250", MeasurementUnit.GRAM),
                line("Buttermilk", "300", MeasurementUnit.MILLILITRE))));

        verify(ingredients, times(2)).findByNameIgnoreCase(anyString());
        verify(ingredients, times(1)).save(any());
    }

    /**
     * What was handed to save(), rather than what came back out of the service.
     *
     * <p>The response is built by the mapper from the same object, so asserting on it would
     * partly be asserting that the mapper copied fields correctly - which its own test already
     * covers. The captured argument is the thing the database would actually have received.
     */
    @Test
    void persistsTheRecipeAndTheLinesTheRequestDescribed() {
        savePassesTheRecipeThrough();
        when(ingredients.findByNameIgnoreCase("Flour")).thenReturn(Optional.of(new Ingredient("Flour")));
        when(ingredients.findByNameIgnoreCase("Milk")).thenReturn(Optional.of(new Ingredient("Milk")));

        service.create(request(List.of(
                line("Flour", "300", MeasurementUnit.GRAM),
                line("Milk", "0.50", MeasurementUnit.LITRE))));

        verify(recipes).save(persistedRecipe.capture());
        Recipe saved = persistedRecipe.getValue();

        assertThat(saved.getTitle()).isEqualTo("Pancakes");
        assertThat(saved.getInstructions()).isEqualTo("Mix. Fry.");
        assertThat(saved.getServings()).isEqualTo(4);
        assertThat(saved.getDifficulty()).isEqualTo(Difficulty.EASY);
        assertThat(saved.getIngredients())
                .extracting(persisted -> persisted.getIngredient().getName(),
                        RecipeIngredient::getQuantity,
                        RecipeIngredient::getUnit)
                .containsExactly(
                        tuple("Flour", new BigDecimal("300"), MeasurementUnit.GRAM),
                        tuple("Milk", new BigDecimal("0.50"), MeasurementUnit.LITRE));
    }

    /**
     * Every line must point back at its recipe before the save. orphanRemoval and the
     * recipe_id NOT NULL column both depend on it, and a mocked repository will happily accept a
     * half-wired graph that Postgres would reject.
     */
    @Test
    void wiresBothSidesOfEachIngredientLineBeforePersisting() {
        savePassesTheRecipeThrough();
        when(ingredients.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(new Ingredient("Flour")));

        service.create(request(List.of(line("Flour", "300", MeasurementUnit.GRAM))));

        verify(recipes).save(persistedRecipe.capture());
        Recipe saved = persistedRecipe.getValue();

        assertThat(saved.getIngredients())
                .singleElement()
                .satisfies(persisted -> assertThat(persisted.getRecipe()).isSameAs(saved));
    }

    @Test
    void trimsAnIngredientNameBeforeCreatingIt() {
        savePassesTheRecipeThrough();
        when(ingredients.findByNameIgnoreCase("Sourdough Starter")).thenReturn(Optional.empty());
        when(ingredients.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request(List.of(line("   Sourdough Starter   ", "100", MeasurementUnit.GRAM))));

        verify(ingredients).save(persistedIngredient.capture());
        assertThat(persistedIngredient.getValue().getName()).isEqualTo("Sourdough Starter");
    }

    @Test
    void reportsTotalTimeAsPrepPlusCook() {
        savePassesTheRecipeThrough();
        when(ingredients.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(new Ingredient("Flour")));

        RecipeResponse created = service.create(request(List.of(line("Flour", "300", MeasurementUnit.GRAM))));

        assertThat(created.prepMinutes()).isEqualTo(10);
        assertThat(created.cookMinutes()).isEqualTo(15);
        assertThat(created.totalMinutes()).isEqualTo(25);
    }

    // ---------------------------------------------------------------------------------------
    // Failure paths. Each one also asserts that nothing was written, because a rule that
    // rejects the request only after persisting half of it is not much of a rule.
    // ---------------------------------------------------------------------------------------

    /**
     * Reads go through findWithAssociationsById, not findById - the detail endpoint needs the
     * entity graph. Stubbing the wrong one here would have passed silently before Mockito's
     * strict stubbing flagged the leftover stub.
     */
    @Test
    void findByIdReportsAMissingRecipeAsNotFound() {
        when(recipes.findWithAssociationsById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(404L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Recipe 404 was not found");
    }

    @Test
    void updateReportsAMissingRecipeAsNotFoundAndWritesNothing() {
        when(recipes.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(404L, request(List.of(line("Flour", "1", MeasurementUnit.GRAM)))))
                .isInstanceOf(NotFoundException.class);

        verify(recipes, never()).save(any());
        verify(recipes, never()).flush();
    }

    @Test
    void deleteReportsAMissingRecipeAsNotFoundAndDeletesNothing() {
        when(recipes.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(404L)).isInstanceOf(NotFoundException.class);

        verify(recipes, never()).delete(any(Recipe.class));
        verify(recipes, never()).deleteById(anyLong());
    }

    @Test
    void rejectsTheSameIngredientTwiceBeforePersistingAnything() {
        when(ingredients.findByNameIgnoreCase("Flour")).thenReturn(Optional.of(new Ingredient("Flour")));

        assertThatThrownBy(() -> service.create(request(List.of(
                line("Flour", "100", MeasurementUnit.GRAM),
                line("flour", "50", MeasurementUnit.GRAM)))))
                .isInstanceOf(DuplicateIngredientException.class)
                .hasMessageContaining("listed more than once");

        verify(recipes, never()).save(any());
    }

    @Test
    void rejectsAnUnknownCategoryBeforePersistingAnything() {
        when(ingredients.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(new Ingredient("Flour")));
        when(categories.findAllByLowerCasedNameIn(anyCollection())).thenReturn(List.of());

        RecipeRequest withBadCategory = new RecipeRequest("Pancakes", "Fluffy", "Mix. Fry.", 10, 15, 4,
                Difficulty.EASY, List.of(line("Flour", "300", MeasurementUnit.GRAM)), List.of("Nope"));

        assertThatThrownBy(() -> service.create(withBadCategory))
                .isInstanceOf(UnknownCategoryException.class)
                .hasMessageContaining("Nope");

        verify(recipes, never()).save(any());
    }

    /**
     * The batch-lookup contract. Resolving categories one name at a time would still pass every
     * behavioural assertion above while quietly turning one query into N, so the query count is
     * worth pinning as a property in its own right.
     */
    @Test
    void resolvesEveryCategoryInASingleQuery() {
        savePassesTheRecipeThrough();
        when(ingredients.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(new Ingredient("Flour")));
        when(categories.findAllByLowerCasedNameIn(anyCollection()))
                .thenReturn(List.of(new Category("Breakfast"), new Category("Vegetarian")));

        RecipeRequest withCategories = new RecipeRequest("Pancakes", "Fluffy", "Mix. Fry.", 10, 15, 4,
                Difficulty.EASY, List.of(line("Flour", "300", MeasurementUnit.GRAM)),
                List.of("Breakfast", "Vegetarian"));

        service.create(withCategories);

        verify(categories, times(1)).findAllByLowerCasedNameIn(anyCollection());
        verify(categories, never()).findByNameIgnoreCase(anyString());
    }

    // ---------------------------------------------------------------------------------------
    // Update. The ordering here is the unit-level counterpart to the flush-ordering bug caught
    // in Phase 07 - see replacingTheIngredientListWhileKeepingOneOfThem for the SQL-level proof.
    // ---------------------------------------------------------------------------------------

    @Test
    void replacesTheIngredientListRatherThanAppendingToIt() {
        Recipe existing = new Recipe("Waffles", "Crisp", "Batter. Iron.", 15, 10, 2, Difficulty.MEDIUM);
        existing.addIngredient(new Ingredient("Buttermilk"), new BigDecimal("300"), MeasurementUnit.MILLILITRE);
        when(recipes.findById(1L)).thenReturn(Optional.of(existing));
        when(ingredients.findByNameIgnoreCase("Flour")).thenReturn(Optional.of(new Ingredient("Flour")));

        RecipeResponse updated = service.update(1L, request(List.of(line("Flour", "250", MeasurementUnit.GRAM))));

        assertThat(updated.ingredients())
                .extracting(persisted -> persisted.name())
                .as("the old line must be gone, not merely joined by the new one")
                .containsExactly("Flour");
    }

    /**
     * The unit-level statement of the flush-ordering fix.
     *
     * <p>Hibernate issues collection inserts before collection deletes within one flush, so the
     * replacement lines must not be added until the removals have already been pushed out. Stated
     * as an ordering constraint on the collaborators, this fails the moment someone "tidies up"
     * the seemingly redundant first flush.
     */
    @Test
    void flushesTheRemovalsBeforeResolvingTheReplacementLines() {
        Recipe existing = new Recipe("Waffles", "Crisp", "Batter. Iron.", 15, 10, 2, Difficulty.MEDIUM);
        existing.addIngredient(new Ingredient("Flour"), new BigDecimal("250"), MeasurementUnit.GRAM);
        when(recipes.findById(1L)).thenReturn(Optional.of(existing));
        when(ingredients.findByNameIgnoreCase("Flour")).thenReturn(Optional.of(new Ingredient("Flour")));

        service.update(1L, request(List.of(line("Flour", "260", MeasurementUnit.GRAM))));

        InOrder ordered = inOrder(recipes, ingredients);
        ordered.verify(recipes).flush();
        ordered.verify(ingredients).findByNameIgnoreCase("Flour");
        ordered.verify(recipes).flush();
    }

    /**
     * The second flush exists so @PreUpdate has run before the entity is mapped; without it the
     * caller is handed a stale updatedAt.
     */
    @Test
    void flushesAgainBeforeMappingSoTheResponseCarriesFreshTimestamps() {
        Recipe existing = new Recipe("Waffles", "Crisp", "Batter. Iron.", 15, 10, 2, Difficulty.MEDIUM);
        when(recipes.findById(1L)).thenReturn(Optional.of(existing));
        when(ingredients.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(new Ingredient("Flour")));

        service.update(1L, request(List.of(line("Flour", "250", MeasurementUnit.GRAM))));

        verify(recipes, times(2)).flush();
    }
}
