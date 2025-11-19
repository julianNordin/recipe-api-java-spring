package com.recipeapi.repository;

import com.recipeapi.domain.Difficulty;
import com.recipeapi.domain.MeasurementUnit;
import com.recipeapi.domain.Recipe;
import com.recipeapi.support.AbstractPostgresTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each predicate against real SQL.
 *
 * <p>Specifications are only worth testing against a database. They compile to SQL, and the
 * interesting failures - a join that multiplies rows, a comparison that turns out to be
 * case-sensitive, an expression the dialect rejects - are invisible to any test that stubs the
 * repository out.
 */
@DataJpaTest
class RecipeSpecificationsTest extends AbstractPostgresTest {

    @Autowired
    private RecipeRepository recipes;

    @Autowired
    private IngredientRepository ingredients;

    @Autowired
    private CategoryRepository categories;

    @BeforeEach
    void seedRecipes() {
        save("Pancakes", "Fluffy American pancakes", Difficulty.EASY, 10, 15,
                List.of("Breakfast"), List.of("Flour", "Milk"));
        save("Quick Salad", "Fresh and fast", Difficulty.EASY, 8, 0,
                List.of("Lunch", "Vegetarian"), List.of("Tomato", "Olive Oil"));
        save("Beef Wellington", "A serious project", Difficulty.HARD, 90, 45,
                List.of("Dinner"), List.of("Butter", "Flour"));
        save("Garlic Bread", "Crisp edges", Difficulty.EASY, 10, 12,
                List.of("Side", "Vegetarian"), List.of("Garlic", "Butter"));
    }

    private void save(String title, String description, Difficulty difficulty, int prep, int cook,
                      List<String> categoryNames, List<String> ingredientNames) {
        Recipe recipe = new Recipe(title, description, "Instructions.", prep, cook, 4, difficulty);
        categoryNames.forEach(name ->
                recipe.addCategory(categories.findByNameIgnoreCase(name).orElseThrow()));
        ingredientNames.forEach(name ->
                recipe.addIngredient(ingredients.findByNameIgnoreCase(name).orElseThrow(),
                        new BigDecimal("1.00"), MeasurementUnit.PIECE));
        recipes.save(recipe);
    }

    private List<String> titlesMatching(Specification<Recipe> specification) {
        return recipes.findAll(specification).stream().map(Recipe::getTitle).sorted().toList();
    }

    // --- search -----------------------------------------------------------------------------

    @Test
    void matchesOnTheTitle() {
        assertThat(titlesMatching(RecipeSpecifications.matchesText("pan"))).containsExactly("Pancakes");
    }

    @Test
    void matchesOnTheDescriptionToo() {
        assertThat(titlesMatching(RecipeSpecifications.matchesText("serious")))
                .containsExactly("Beef Wellington");
    }

    @ParameterizedTest
    @ValueSource(strings = {"garlic", "GARLIC", "GaRlIc"})
    void matchesRegardlessOfCase(String term) {
        assertThat(titlesMatching(RecipeSpecifications.matchesText(term))).containsExactly("Garlic Bread");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void anAbsentSearchTermRestrictsNothing(String term) {
        assertThat(titlesMatching(RecipeSpecifications.matchesText(term))).hasSize(4);
    }

    // --- category, difficulty, ingredient ---------------------------------------------------

    @Test
    void filtersByCategoryCaseInsensitively() {
        assertThat(titlesMatching(RecipeSpecifications.inCategory("vegetarian")))
                .containsExactly("Garlic Bread", "Quick Salad");
    }

    @Test
    void filtersByDifficulty() {
        assertThat(titlesMatching(RecipeSpecifications.hasDifficulty(Difficulty.EASY)))
                .containsExactly("Garlic Bread", "Pancakes", "Quick Salad");
        assertThat(titlesMatching(RecipeSpecifications.hasDifficulty(Difficulty.HARD)))
                .containsExactly("Beef Wellington");
    }

    @Test
    void filtersByIngredientCaseInsensitivelyThroughTwoJoins() {
        assertThat(titlesMatching(RecipeSpecifications.usesIngredient("bUtTeR")))
                .containsExactly("Beef Wellington", "Garlic Bread");
    }

    // --- derived total time -----------------------------------------------------------------

    /**
     * Pancakes is exactly 25 minutes, so this also pins that the comparison is inclusive. An
     * off-by-one would silently drop the boundary recipe rather than fail loudly.
     */
    @Test
    void filtersOnTotalTimeInclusivelyEvenThoughThereIsNoSuchColumn() {
        assertThat(titlesMatching(RecipeSpecifications.totalMinutesAtMost(25)))
                .containsExactly("Garlic Bread", "Pancakes", "Quick Salad");
        assertThat(titlesMatching(RecipeSpecifications.totalMinutesAtMost(24)))
                .containsExactly("Garlic Bread", "Quick Salad");
    }

    // --- composition ------------------------------------------------------------------------

    @Test
    void combinesEveryFilterWithAnd() {
        Specification<Recipe> specification = Specification.allOf(
                RecipeSpecifications.matchesText("a"),
                RecipeSpecifications.inCategory("Vegetarian"),
                RecipeSpecifications.hasDifficulty(Difficulty.EASY),
                RecipeSpecifications.totalMinutesAtMost(25),
                RecipeSpecifications.usesIngredient("Garlic"));

        assertThat(titlesMatching(specification)).containsExactly("Garlic Bread");
    }

    @Test
    void contradictoryFiltersMatchNothingRatherThanFallingBackToEverything() {
        Specification<Recipe> specification = Specification.allOf(
                RecipeSpecifications.hasDifficulty(Difficulty.HARD),
                RecipeSpecifications.totalMinutesAtMost(10));

        assertThat(titlesMatching(specification)).isEmpty();
    }

    /**
     * The property that makes the filters optional at all: five absent filters must mean no
     * restriction, not no matches. Returning an empty predicate instead of unrestricted() would
     * fail exactly here and nowhere else.
     */
    @Test
    void everyFilterAbsentMatchesEverything() {
        Specification<Recipe> specification = Specification.allOf(
                RecipeSpecifications.matchesText(null),
                RecipeSpecifications.inCategory(null),
                RecipeSpecifications.hasDifficulty(null),
                RecipeSpecifications.totalMinutesAtMost(null),
                RecipeSpecifications.usesIngredient(null));

        assertThat(titlesMatching(specification)).hasSize(4);
    }

    /**
     * Paging a joined query is where a missing distinct would show up: the count query
     * over-reports and a recipe can appear on more than one page.
     */
    @Test
    void pagesAJoinedQueryWithoutDuplicatingRowsOrMiscountingTheTotal() {
        Specification<Recipe> specification = RecipeSpecifications.inCategory("Vegetarian");

        Page<Recipe> firstPage = recipes.findAll(specification, PageRequest.of(0, 1, Sort.by("title")));
        Page<Recipe> secondPage = recipes.findAll(specification, PageRequest.of(1, 1, Sort.by("title")));

        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).extracting(Recipe::getTitle).containsExactly("Garlic Bread");
        assertThat(secondPage.getContent()).extracting(Recipe::getTitle).containsExactly("Quick Salad");
    }
}
