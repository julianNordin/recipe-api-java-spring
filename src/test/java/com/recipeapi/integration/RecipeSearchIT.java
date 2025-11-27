package com.recipeapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The read path's query parameters, against SQL that Postgres actually planned.
 *
 * <p>{@code RecipeSpecificationsTest} already covers each predicate at the repository level. What
 * it cannot cover is the journey from a query string to that predicate: the binding of
 * {@code ?difficulty=HARD} onto an enum, the {@code Pageable} the controller resolves, the
 * whitelist the service applies to it, and the envelope the whole thing is serialised into. Those
 * five things sit between the caller and the predicate, and none of them exist in a repository
 * slice.
 *
 * <p>The fixture is created through the API rather than through the repositories, so the numbers
 * asserted below are numbers the published contract produced.
 */
class RecipeSearchIT extends AbstractIntegrationTest {

    /**
     * Five recipes chosen so that every filter has something to exclude as well as something to
     * find - a filter that matches everything proves nothing.
     *
     * <p>Total times are 25, 30, 8, 135 and 22 minutes. Pancakes sits at exactly 25 so that
     * {@code maxMinutes=25} pins the boundary as inclusive; an off-by-one on a derived SQL
     * expression drops that row silently.
     */
    private static final List<String> FIXTURE = List.of("""
            {
              "title": "Pancakes",
              "description": "A fluffy weekend stack",
              "instructions": "Whisk, rest, fry.",
              "prepMinutes": 10, "cookMinutes": 15, "servings": 4, "difficulty": "EASY",
              "ingredients": [
                {"name": "Flour", "quantity": 300, "unit": "GRAM"},
                {"name": "Milk", "quantity": 400, "unit": "MILLILITRE"}
              ],
              "categories": ["Breakfast"]
            }""", """
            {
              "title": "Belgian Waffles",
              "description": "Crisp outside, soft inside",
              "instructions": "Batter, iron, wait.",
              "prepMinutes": 10, "cookMinutes": 20, "servings": 4, "difficulty": "MEDIUM",
              "ingredients": [
                {"name": "Flour", "quantity": 250, "unit": "GRAM"},
                {"name": "Butter", "quantity": 100, "unit": "GRAM"}
              ],
              "categories": ["Breakfast"]
            }""", """
            {
              "title": "Quick Salad",
              "description": "Tomatoes and good oil, nothing else",
              "instructions": "Chop. Dress. Eat.",
              "prepMinutes": 8, "cookMinutes": 0, "servings": 2, "difficulty": "EASY",
              "ingredients": [
                {"name": "Tomato", "quantity": 3, "unit": "PIECE"},
                {"name": "Olive Oil", "quantity": 2, "unit": "TABLESPOON"}
              ],
              "categories": ["Lunch", "Vegetarian"]
            }""", """
            {
              "title": "Beef Wellington",
              "description": "The whole Sunday afternoon, in one dish",
              "instructions": "Sear, wrap, bake, rest.",
              "prepMinutes": 60, "cookMinutes": 75, "servings": 6, "difficulty": "HARD",
              "ingredients": [
                {"name": "Butter", "quantity": 50, "unit": "GRAM"},
                {"name": "Egg", "quantity": 2, "unit": "PIECE"}
              ],
              "categories": ["Dinner"]
            }""", """
            {
              "title": "Garlic Bread",
              "description": "Butter, garlic, and a very hot oven",
              "instructions": "Split, spread, bake.",
              "prepMinutes": 12, "cookMinutes": 10, "servings": 4, "difficulty": "EASY",
              "ingredients": [
                {"name": "Butter", "quantity": 80, "unit": "GRAM"},
                {"name": "Garlic", "quantity": 4, "unit": "CLOVE"}
              ],
              "categories": ["Side", "Vegetarian"]
            }""");

    @BeforeEach
    void seedTheFixture() {
        FIXTURE.forEach(recipe -> post("/api/recipes", recipe));
    }

    @Test
    void findsARecipeByAFragmentOfItsTitle() {
        assertThat(titlesFrom("/api/recipes?search=pan")).containsExactly("Pancakes");
    }

    /** The search covers the description too, and neither side of the match is case-sensitive. */
    @Test
    void searchesTheDescriptionAndIgnoresCase() {
        assertThat(titlesFrom("/api/recipes?search=CRISP")).containsExactly("Belgian Waffles");
    }

    @Test
    void filtersByCategoryIgnoringCase() {
        assertThat(titlesFrom("/api/recipes?category=vegetarian"))
                .containsExactlyInAnyOrder("Quick Salad", "Garlic Bread");
    }

    /**
     * Two joins deep - recipe to its ingredient lines, line to the ingredient - which is the
     * predicate most likely to be quietly wrong, because a broken join usually returns everything
     * rather than nothing.
     */
    @Test
    void filtersByIngredientAcrossTwoJoins() {
        assertThat(titlesFrom("/api/recipes?ingredient=butter"))
                .containsExactlyInAnyOrder("Belgian Waffles", "Beef Wellington", "Garlic Bread");
    }

    /** Pancakes is exactly 25 minutes: the boundary is inclusive. */
    @Test
    void filtersByTotalTimeInclusiveOfTheBoundary() {
        assertThat(titlesFrom("/api/recipes?maxMinutes=25"))
                .containsExactlyInAnyOrder("Pancakes", "Quick Salad", "Garlic Bread");
    }

    @Test
    void narrowsWhenSeveralFiltersAreCombined() {
        assertThat(titlesFrom("/api/recipes?category=Vegetarian&difficulty=EASY&maxMinutes=25"))
                .containsExactlyInAnyOrder("Quick Salad", "Garlic Bread");
    }

    /**
     * Filters compose with AND. Combining them wrongly - or dropping one - would return the four
     * HARD-or-quick recipes rather than none, and every other test here would still pass.
     */
    @Test
    void returnsNothingWhenTheFiltersContradictEachOther() {
        JsonNode page = body(get("/api/recipes?difficulty=HARD&maxMinutes=10"));
        assertThat(page.get("content")).isEmpty();
        assertThat(page.get("totalElements").asLong()).isZero();
    }

    @Test
    void pagesAndSortsWithTheEnvelopeItPublishes() {
        JsonNode page = body(get("/api/recipes?sort=title,desc&size=2"));

        assertThat(titles(page)).containsExactly("Quick Salad", "Pancakes");
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(2);
        assertThat(page.get("totalElements").asLong()).isEqualTo(5);
        assertThat(page.get("totalPages").asInt()).isEqualTo(3);
        assertThat(page.get("first").asBoolean()).isTrue();
        assertThat(page.get("last").asBoolean()).isFalse();

        // The envelope exists so that Spring's own Page shape - which Spring documents as
        // unstable across versions - never reaches a client.
        assertThat(page.has("pageable")).isFalse();
        assertThat(page.has("sort")).isFalse();
        assertThat(page.has("numberOfElements")).isFalse();
    }

    /** An unbounded page size is a denial of service in one request, so Spring clamps it. */
    @Test
    void clampsAnAbsurdPageSize() {
        assertThat(body(get("/api/recipes?size=100000")).get("size").asInt()).isEqualTo(100);
    }

    /**
     * The summary shape is a property of the response, not only of the record: a mapper change
     * could put the instructions back on the listing with every other assertion still green.
     */
    @Test
    void omitsInstructionsAndIngredientLinesFromTheListing() {
        JsonNode summary = body(get("/api/recipes?size=1")).get("content").get(0);

        assertThat(summary.has("instructions")).isFalse();
        assertThat(summary.has("ingredients")).isFalse();
        assertThat(summary.get("categories")).isNotEmpty();
        assertThat(summary.get("totalMinutes").asInt()).isPositive();
    }

    private List<String> titlesFrom(String path) {
        return titles(body(get(path)));
    }

    private static List<String> titles(JsonNode page) {
        return page.get("content").findValuesAsText("title");
    }
}
