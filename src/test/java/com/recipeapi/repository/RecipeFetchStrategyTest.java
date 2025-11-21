package com.recipeapi.repository;

import com.recipeapi.domain.Difficulty;
import com.recipeapi.domain.MeasurementUnit;
import com.recipeapi.domain.Recipe;
import com.recipeapi.service.RecipeService;
import com.recipeapi.support.AbstractPostgresTest;
import com.recipeapi.web.dto.RecipeFilter;
import com.recipeapi.web.dto.RecipeResponse;
import com.recipeapi.web.mapper.RecipeMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Counts the SQL statements the listing and detail endpoints actually issue.
 *
 * <p>Reading the SQL log tells you what ran. Hibernate's {@link Statistics} tells you how many
 * times, which is the only way to see an N+1 as a shape rather than as a lot of similar-looking
 * queries scrolling past.
 *
 * <p>Measured through {@link RecipeService} rather than the repository, because the fetch strategy
 * is a property of the whole read path - which query is issued, and what the mapper then touches.
 * An earlier version measured {@code findAll(Pageable)} directly and quietly exercised a different
 * overload from the one production calls.
 */
@DataJpaTest
@Import({RecipeService.class, RecipeMapper.class})
class RecipeFetchStrategyTest extends AbstractPostgresTest {

    private static final RecipeFilter NO_FILTER = new RecipeFilter(null, null, null, null, null);

    @Autowired
    private RecipeService service;

    @Autowired
    private RecipeRepository recipes;

    @Autowired
    private IngredientRepository ingredients;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void seedFiveRecipes() {
        save("Pancakes", List.of("Breakfast"), List.of("Flour", "Milk"));
        save("Quick Salad", List.of("Lunch", "Vegetarian"), List.of("Tomato", "Olive Oil"));
        save("Beef Wellington", List.of("Dinner"), List.of("Butter", "Flour"));
        save("Garlic Bread", List.of("Side", "Vegetarian"), List.of("Garlic", "Butter"));
        save("Lemon Cake", List.of("Dessert", "Baking"), List.of("Sugar", "Lemon", "Egg"));
    }

    private Long save(String title, List<String> categoryNames, List<String> ingredientNames) {
        Recipe recipe = new Recipe(title, "A recipe.", "Instructions.", 10, 15, 4, Difficulty.EASY);
        categoryNames.forEach(name ->
                recipe.addCategory(categories.findByNameIgnoreCase(name).orElseThrow()));
        ingredientNames.forEach(name ->
                recipe.addIngredient(ingredients.findByNameIgnoreCase(name).orElseThrow(),
                        new BigDecimal("1.00"), MeasurementUnit.PIECE));
        return recipes.save(recipe).getId();
    }

    private Statistics resetStatistics() {
        // Flush and clear first. @DataJpaTest runs the whole test in one transaction, so without
        // this the recipes seeded moments ago are still managed and the query hands back the very
        // same instances with their collections already populated - the measurement comes out as a
        // single statement and an N+1 is invisible. A property of the test, not of the code.
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        return statistics;
    }

    private long statementsToListEverything() {
        Statistics statistics = resetStatistics();
        service.findAll(NO_FILTER, PageRequest.of(0, 50, Sort.by("id")));
        return statistics.getPrepareStatementCount();
    }

    /**
     * The assertion that actually defines "no N+1": the query count must not depend on how many
     * rows come back. A fixed magic number would pass just as happily with a per-row query, as
     * long as the fixture never changed size.
     */
    @Test
    void theListingCostsTheSameNumberOfQueriesWhateverTheNumberOfRecipes() {
        long forFive = statementsToListEverything();

        save("Risotto", List.of("Dinner", "Vegetarian"), List.of("Rice", "Parmesan", "Onion"));
        save("Omelette", List.of("Breakfast"), List.of("Egg", "Butter", "Cheddar Cheese"));
        save("Bruschetta", List.of("Snack", "Vegetarian"), List.of("Tomato", "Basil", "Olive Oil"));
        save("Carrot Soup", List.of("Lunch"), List.of("Carrot", "Onion"));
        save("Honey Cake", List.of("Dessert", "Baking"), List.of("Honey", "Flour", "Egg"));

        long forTen = statementsToListEverything();

        System.out.println(">>> listing 5 recipes: " + forFive + " statements; 10 recipes: " + forTen);

        assertThat(recipes.count()).isEqualTo(10);
        assertThat(forTen)
                .as("doubling the rows must not change the query count")
                .isEqualTo(forFive);
    }

    /**
     * Two statements: one for the page, one to initialise the categories.
     *
     * <p>No count query, because Spring Data omits it when the first page comes back shorter than
     * the requested size - the total is already known. The summary shape carries no ingredient
     * lines, so fetching those here would be work nobody asked for.
     */
    @Test
    void listingAShortFirstPageCostsTwoStatements() {
        assertThat(statementsToListEverything()).isEqualTo(2L);
    }

    /**
     * A full page does need the count query, so this is the three-statement case. Worth pinning
     * separately: it is the shape a real client with real data always sees, and asserting only
     * the short-page number would hide a regression in the count query.
     */
    @Test
    void listingAFullPageCostsThreeStatements() {
        Statistics statistics = resetStatistics();

        service.findAll(NO_FILTER, PageRequest.of(0, 2, Sort.by("id")));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3L);
    }

    /**
     * Zero lazy collection fetches, not one batched fetch: the categories arrive join-fetched as
     * part of the initialising query, so they are never "fetched" as a collection at all. Before
     * the entity graph this counted 10 - two per recipe.
     */
    @Test
    void theListingNeverLazilyFetchesACollection() {
        Statistics statistics = resetStatistics();

        service.findAll(NO_FILTER, PageRequest.of(0, 50, Sort.by("id")));

        assertThat(statistics.getCollectionFetchCount())
                .as("collections arrive with the query, rather than one lazy fetch per recipe")
                .isZero();
    }

    /**
     * The detail endpoint reads both collections and every ingredient name, and is still a single
     * statement: the ingredient behind each line is in the graph, so it costs nothing extra.
     */
    @Test
    void readingOneRecipeWithAllItsAssociationsIsASingleStatement() {
        Long id = recipes.findAll().stream()
                .filter(recipe -> recipe.getTitle().equals("Lemon Cake"))
                .findFirst().orElseThrow().getId();
        Statistics statistics = resetStatistics();

        RecipeResponse response = service.findById(id);

        assertThat(response.ingredients()).hasSize(3);
        assertThat(response.categories()).containsExactly("Baking", "Dessert");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1L);
    }
}
