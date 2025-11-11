package com.recipeapi.service;

import com.recipeapi.domain.Difficulty;
import com.recipeapi.domain.MeasurementUnit;
import com.recipeapi.repository.IngredientRepository;
import com.recipeapi.support.AbstractPostgresTest;
import com.recipeapi.web.dto.RecipeIngredientRequest;
import com.recipeapi.web.dto.RecipeRequest;
import com.recipeapi.web.dto.RecipeResponse;
import com.recipeapi.web.mapper.RecipeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The write path against a real database.
 *
 * <p>A {@code @DataJpaTest} slice with the service and mapper imported, rather than a full
 * {@code @SpringBootTest}: these behaviours are all persistence behaviours, and the slice keeps
 * the run fast while still exercising genuine SQL.
 */
@DataJpaTest
@Import({RecipeService.class, RecipeMapper.class})
class RecipeServicePersistenceTest extends AbstractPostgresTest {

    @Autowired
    private RecipeService service;

    @Autowired
    private IngredientRepository ingredients;

    private static RecipeRequest request(List<RecipeIngredientRequest> lines, List<String> categories) {
        return new RecipeRequest("Waffles", "Crisp", "Batter. Iron.", 15, 10, 2,
                Difficulty.MEDIUM, lines, categories);
    }

    private static RecipeIngredientRequest line(String name, String quantity, MeasurementUnit unit) {
        return new RecipeIngredientRequest(name, new BigDecimal(quantity), unit);
    }

    @Test
    void reusesAnExistingIngredientWhateverCaseTheCallerUsed() {
        long before = ingredients.count();

        RecipeResponse created = service.create(request(
                List.of(line("fLoUr", "250", MeasurementUnit.GRAM)), List.of()));

        assertThat(ingredients.count())
                .as("an existing ingredient must not be duplicated")
                .isEqualTo(before);
        assertThat(created.ingredients())
                .singleElement()
                .satisfies(it -> assertThat(it.name())
                        .as("the canonical stored name wins over the caller's casing")
                        .isEqualTo("Flour"));
    }

    @Test
    void createsAnIngredientOnlyWhenTheNameIsGenuinelyNew() {
        long before = ingredients.count();

        service.create(request(List.of(line("Sourdough Starter", "100", MeasurementUnit.GRAM)), List.of()));

        assertThat(ingredients.count()).isEqualTo(before + 1);
        assertThat(ingredients.findByNameIgnoreCase("sourdough starter")).isPresent();
    }

    /**
     * The regression test for the flush-ordering bug.
     *
     * <p>Flour is deliberately kept across the replacement. Hibernate orders a flush by action
     * type and issues the collection inserts before the collection deletes, so without the
     * intervening flush in the service this fails with
     * {@code duplicate key value violates unique constraint
     * "ux_recipe_ingredients_recipe_ingredient"}. A test that replaced every ingredient would
     * pass either way and prove nothing.
     */
    @Test
    void replacingTheIngredientListWhileKeepingOneOfThem() {
        RecipeResponse created = service.create(request(
                List.of(line("Flour", "250", MeasurementUnit.GRAM),
                        line("Milk", "300", MeasurementUnit.MILLILITRE)),
                List.of("Breakfast")));

        RecipeResponse updated = service.update(created.id(), request(
                List.of(line("Flour", "260", MeasurementUnit.GRAM),
                        line("Egg", "2", MeasurementUnit.PIECE)),
                List.of("Breakfast"))).orElseThrow();

        assertThat(updated.ingredients())
                .extracting(it -> it.name())
                .containsExactlyInAnyOrder("Flour", "Egg");
        assertThat(updated.ingredients())
                .filteredOn(it -> it.name().equals("Flour"))
                .singleElement()
                .satisfies(it -> assertThat(it.quantity()).isEqualByComparingTo("260"));
        assertThat(updated.categories())
                .as("keeping a category across a replacement must not collide either")
                .containsExactly("Breakfast");
    }

    @Test
    void rejectsACategoryThatDoesNotExist() {
        assertThatThrownBy(() -> service.create(request(
                List.of(line("Flour", "250", MeasurementUnit.GRAM)),
                List.of("Definitely Not A Category"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Definitely Not A Category");
    }

    @Test
    void deleteReportsWhetherThereWasAnythingToDelete() {
        RecipeResponse created = service.create(request(
                List.of(line("Salt", "1", MeasurementUnit.PINCH)), List.of()));

        assertThat(service.delete(created.id())).isTrue();
        assertThat(service.delete(created.id())).isFalse();
        assertThat(service.findById(created.id())).isEmpty();
    }
}
