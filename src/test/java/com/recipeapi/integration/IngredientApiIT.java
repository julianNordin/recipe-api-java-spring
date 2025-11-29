package com.recipeapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ingredient vocabulary over real HTTP, against the 24 ingredients V2 seeds.
 *
 * <p>The test worth the tier is the last one. "Deleting an in-use ingredient is refused" is a
 * claim about a foreign key that carries no {@code ON DELETE CASCADE}, and about a service check
 * that runs before it - a mocked repository will agree to anything, so neither layer can be
 * trusted on its own word here.
 */
class IngredientApiIT extends AbstractIntegrationTest {

    private static final String PANCAKES = """
            {
              "title": "Pancakes",
              "instructions": "Whisk, rest, fry.",
              "prepMinutes": 10, "cookMinutes": 15, "servings": 4, "difficulty": "EASY",
              "ingredients": [{"name": "Flour", "quantity": 300, "unit": "GRAM"}],
              "categories": ["Breakfast"]
            }""";

    @Test
    void listsTheSeededVocabularySortedByName() {
        JsonNode page = body(get("/api/ingredients?size=3"));

        assertThat(page.get("totalElements").asLong()).isEqualTo(24);
        assertThat(names(page)).containsExactly("Baking Powder", "Basil", "Black Pepper");
    }

    @Test
    void searchesByAFragmentOfANameIgnoringCase() {
        assertThat(names(body(get("/api/ingredients?search=SUG"))))
                .containsExactly("Brown Sugar", "Sugar");
    }

    @Test
    void createsAnIngredientAndPointsAtItWithLocation() {
        ResponseEntity<String> created = post("/api/ingredients", """
                {"name": "Saffron"}""");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).isNotNull();
        assertThat(created.getHeaders().getLocation().getPath())
                .isEqualTo("/api/ingredients/" + body(created).get("id").asLong());

        assertThat(body(get("/api/ingredients?search=Saffron")).get("totalElements").asLong()).isEqualTo(1);
        assertThat(body(get("/api/ingredients")).get("totalElements").asLong()).isEqualTo(25);
    }

    /** The database defines uniqueness on lower(name), so the API has to agree with it. */
    @Test
    void refusesToCreateAnIngredientThatAlreadyExistsInAnotherCase() {
        ResponseEntity<String> response = post("/api/ingredients", """
                {"name": "fLoUr"}""");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body(response).get("type").asText()).isEqualTo("urn:recipe-api:problem:ingredient-exists");
        assertThat(body(get("/api/ingredients")).get("totalElements").asLong())
                .as("a refused create must not have written anything")
                .isEqualTo(24);
    }

    @Test
    void deletesAnIngredientNobodyUses() {
        long id = body(post("/api/ingredients", """
                {"name": "Saffron"}""")).get("id").asLong();

        assertThat(delete("/api/ingredients/" + id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(delete("/api/ingredients/" + id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body(get("/api/ingredients")).get("totalElements").asLong()).isEqualTo(24);
    }

    /**
     * The 409 this endpoint exists for, and the reason
     * {@code fk_recipe_ingredients_ingredient} is the one foreign key in the schema without
     * {@code ON DELETE CASCADE}. Cascading here would turn one DELETE into silent data loss
     * across every recipe that used the ingredient - so the assertion is not only the status,
     * but that the recipe still has its Flour afterwards.
     */
    @Test
    void refusesToDeleteAnIngredientARecipeStillCallsFor() {
        long recipeId = body(post("/api/recipes", PANCAKES)).get("id").asLong();
        long flourId = body(get("/api/ingredients?search=Flour")).get("content").get(0).get("id").asLong();

        ResponseEntity<String> refused = delete("/api/ingredients/" + flourId);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode problem = body(refused);
        assertThat(problem.get("type").asText()).isEqualTo("urn:recipe-api:problem:ingredient-in-use");
        assertThat(problem.get("detail").asText()).contains("Flour");

        assertThat(body(get("/api/recipes/" + recipeId)).get("ingredients").findValuesAsText("name"))
                .as("the refused delete must leave the recipe exactly as it was")
                .containsExactly("Flour");
        assertThat(body(get("/api/ingredients?search=Flour")).get("totalElements").asLong()).isEqualTo(1);
    }

    /**
     * Deleting the recipe releases the ingredient: the join rows go with the recipe through their
     * own cascade, and the ingredient becomes deletable. Without this, the 409 above would be
     * indistinguishable from an ingredient that can never be removed.
     */
    @Test
    void allowsTheDeleteOnceNoRecipeUsesTheIngredientAnyMore() {
        long recipeId = body(post("/api/recipes", PANCAKES)).get("id").asLong();
        long flourId = body(get("/api/ingredients?search=Flour")).get("content").get(0).get("id").asLong();

        assertThat(delete("/api/ingredients/" + flourId).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(delete("/api/recipes/" + recipeId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(delete("/api/ingredients/" + flourId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void rejectsABlankNameWithAFieldLevelError() {
        ResponseEntity<String> response = post("/api/ingredients", """
                {"name": "   "}""");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(response).get("errors").findValuesAsText("field")).containsExactly("name");
    }

    private static List<String> names(JsonNode page) {
        return page.get("content").findValuesAsText("name");
    }
}
