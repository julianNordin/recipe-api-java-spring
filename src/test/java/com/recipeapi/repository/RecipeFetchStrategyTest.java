package com.recipeapi.repository;

import com.recipeapi.domain.Category;
import com.recipeapi.domain.Difficulty;
import com.recipeapi.domain.MeasurementUnit;
import com.recipeapi.domain.Recipe;
import com.recipeapi.support.AbstractPostgresTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Counts the SQL statements a recipe listing actually issues.
 *
 * <p>Reading the SQL log tells you what ran. Hibernate's {@link Statistics} tells you how many
 * times, which is the only way to see an N+1 for what it is rather than as "a lot of
 * similar-looking queries scrolling past".
 *
 * <p>These assertions are on a number, so they are blunt on purpose: a fetch strategy that
 * silently regresses does not throw, it just gets slower, and nothing else in the suite would
 * notice.
 */
@DataJpaTest
class RecipeFetchStrategyTest extends AbstractPostgresTest {

    private static final int RECIPE_COUNT = 5;

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
    void seedRecipes() {
        save("Pancakes", List.of("Breakfast"), List.of("Flour", "Milk"));
        save("Quick Salad", List.of("Lunch", "Vegetarian"), List.of("Tomato", "Olive Oil"));
        save("Beef Wellington", List.of("Dinner"), List.of("Butter", "Flour"));
        save("Garlic Bread", List.of("Side", "Vegetarian"), List.of("Garlic", "Butter"));
        save("Lemon Cake", List.of("Dessert", "Baking"), List.of("Sugar", "Lemon", "Egg"));
    }

    private void save(String title, List<String> categoryNames, List<String> ingredientNames) {
        Recipe recipe = new Recipe(title, "A recipe.", "Instructions.", 10, 15, 4, Difficulty.EASY);
        categoryNames.forEach(name ->
                recipe.addCategory(categories.findByNameIgnoreCase(name).orElseThrow()));
        ingredientNames.forEach(name ->
                recipe.addIngredient(ingredients.findByNameIgnoreCase(name).orElseThrow(),
                        new BigDecimal("1.00"), MeasurementUnit.PIECE));
        recipes.save(recipe);
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    /**
     * Reads a page the way the mapper does: every recipe, then its categories and its ingredient
     * lines including each line's ingredient name.
     */
    private long statementsToListAPageAndReadItsAssociations() {
        // Flush and clear first. @DataJpaTest runs the whole test in one transaction, so without
        // this the recipes seeded moments ago are still managed and findAll() hands back the very
        // same instances with their collections already populated - the measurement comes out as
        // a single statement and the N+1 is invisible. That is a property of the test, not of the
        // fetch strategy.
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = statistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        Page<Recipe> page = recipes.findAll(PageRequest.of(0, 20, Sort.by("id")));
        for (Recipe recipe : page) {
            recipe.getCategories().forEach(Category::getName);
            recipe.getIngredients().forEach(line -> line.getIngredient().getName());
        }

        return statistics.getPrepareStatementCount();
    }

    /**
     * The baseline, and it is deliberately an exact number.
     *
     * <p>Five recipes cost <strong>20</strong> statements and <strong>10 collection fetches</strong>
     * - two per recipe, one for its categories and one for its ingredient lines - plus a load per
     * distinct ingredient behind those lines. That is a textbook N+1: the cost grows with the
     * number of rows on the page, so a page of 20 recipes issues four times as many queries as a
     * page of 5 while looking identical in the logs.
     *
     * <p>Asserted exactly rather than as an upper bound so that improving it forces this number
     * down in the diff, where it can be seen. A fetch strategy that regresses does not throw - it
     * just gets slower - and nothing else in the suite would notice.
     */
    @Test
    void listingAPageCurrentlyCostsAQueryPerRecipePerCollection() {
        long statements = statementsToListAPageAndReadItsAssociations();
        Statistics statistics = statistics();

        System.out.println(">>> statements=" + statements
                + " collectionFetches=" + statistics.getCollectionFetchCount()
                + " entityLoads=" + statistics.getEntityLoadCount());

        assertThat(recipes.count()).isEqualTo(RECIPE_COUNT);
        assertThat(statistics.getCollectionFetchCount())
                .as("two lazy collections fetched separately for each of the %d recipes", RECIPE_COUNT)
                .isEqualTo(RECIPE_COUNT * 2L);
        assertThat(statements).isEqualTo(20L);
    }
}
