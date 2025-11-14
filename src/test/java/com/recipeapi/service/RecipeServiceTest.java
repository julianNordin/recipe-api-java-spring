package com.recipeapi.service;

import com.recipeapi.domain.Difficulty;
import com.recipeapi.domain.Ingredient;
import com.recipeapi.domain.MeasurementUnit;
import com.recipeapi.repository.CategoryRepository;
import com.recipeapi.repository.IngredientRepository;
import com.recipeapi.repository.RecipeRepository;
import com.recipeapi.web.dto.RecipeIngredientRequest;
import com.recipeapi.web.dto.RecipeRequest;
import com.recipeapi.web.dto.RecipeResponse;
import com.recipeapi.web.mapper.RecipeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Test
    void reportsTotalTimeAsPrepPlusCook() {
        savePassesTheRecipeThrough();
        when(ingredients.findByNameIgnoreCase(anyString())).thenReturn(Optional.of(new Ingredient("Flour")));

        RecipeResponse created = service.create(request(List.of(line("Flour", "300", MeasurementUnit.GRAM))));

        assertThat(created.prepMinutes()).isEqualTo(10);
        assertThat(created.cookMinutes()).isEqualTo(15);
        assertThat(created.totalMinutes()).isEqualTo(25);
    }
}
