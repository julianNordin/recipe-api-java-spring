package com.recipeapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The write path, end to end: a real HTTP client, the real servlet container, the real service
 * and Hibernate, and Postgres underneath.
 *
 * <p>Every rule asserted here is already covered somewhere in the faster tiers - by a Mockito
 * test, a {@code @WebMvcTest} slice or a {@code @DataJpaTest} slice. That is the point rather
 * than a redundancy: those tiers each replace part of the system with a stand-in, so each of
 * them can be green while the assembled application is broken. This tier replaces nothing.
 */
class RecipeApiIT extends AbstractIntegrationTest {

    /**
     * Richer than the base class's minimal recipe, in two ways that both carry assertions: the
     * description proves an optional field survives the round trip, and the second ingredient is
     * what lets the replacement test tell a replaced list from a merged one - "Milk is gone" is
     * only meaningful if Milk was there to begin with.
     */
    private static final String PANCAKES = """
            {
              "title": "Pancakes",
              "description": "A fluffy weekend stack",
              "instructions": "Whisk the batter, rest it, fry until golden.",
              "prepMinutes": 10,
              "cookMinutes": 15,
              "servings": 4,
              "difficulty": "EASY",
              "ingredients": [
                {"name": "Flour", "quantity": 300, "unit": "GRAM"},
                {"name": "Milk", "quantity": 400, "unit": "MILLILITRE"}
              ],
              "categories": ["Breakfast"]
            }""";

    @Test
    @DisplayName("create, fetch, update and delete a recipe over real HTTP")
    void roundTripsARecipe() {
        // Create.
        ResponseEntity<String> created = post("/api/recipes", PANCAKES);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode createdBody = body(created);
        long id = createdBody.get("id").asLong();
        assertThat(id).isPositive();
        assertThat(createdBody.get("totalMinutes").asInt()).isEqualTo(25);
        assertThat(createdBody.get("createdAt").asText()).isNotBlank();

        // The Location header has to be an absolute URI a client can follow, which is a property
        // of the running server rather than of the controller: MockMvc invents the host.
        URI location = created.getHeaders().getLocation();
        assertThat(location).isNotNull();
        assertThat(location.getPath()).isEqualTo("/api/recipes/" + id);
        assertThat(location.getPort()).isNotEqualTo(-1);

        // Fetch, by following the Location header rather than by rebuilding the path.
        ResponseEntity<String> fetched = get(location.getPath());
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode detail = body(fetched);
        assertThat(detail.get("title").asText()).isEqualTo("Pancakes");
        assertThat(detail.get("instructions").asText()).contains("Whisk the batter");
        assertThat(names(detail.get("ingredients"))).containsExactlyInAnyOrder("Flour", "Milk");
        assertThat(detail.get("categories").get(0).asText()).isEqualTo("Breakfast");
        assertThat(detail.get("ingredients").get(0).get("unitSymbol").asText()).isNotBlank();

        // Update. PUT is a full replacement, so the payload is the whole recipe, not a patch.
        ResponseEntity<String> updated = put("/api/recipes/" + id, """
                {
                  "title": "Buttermilk Pancakes",
                  "description": "A fluffy weekend stack",
                  "instructions": "Whisk the batter, rest it, fry until golden.",
                  "prepMinutes": 10,
                  "cookMinutes": 20,
                  "servings": 6,
                  "difficulty": "MEDIUM",
                  "ingredients": [
                    {"name": "Flour", "quantity": 350, "unit": "GRAM"},
                    {"name": "Buttermilk", "quantity": 500, "unit": "MILLILITRE"}
                  ],
                  "categories": ["Breakfast", "Baking"]
                }""");
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode updatedBody = body(updated);
        assertThat(updatedBody.get("id").asLong()).isEqualTo(id);
        assertThat(updatedBody.get("title").asText()).isEqualTo("Buttermilk Pancakes");
        assertThat(updatedBody.get("totalMinutes").asInt()).isEqualTo(30);
        assertThat(names(updatedBody.get("ingredients"))).containsExactlyInAnyOrder("Flour", "Buttermilk");

        // updatedAt has to have moved past createdAt, which is only true if the UPDATE and its
        // @PreUpdate callback were flushed before the response was mapped. Parsed rather than
        // compared as text: ISO-8601 trims trailing zeros from the fraction, so a timestamp that
        // lands on a whole second serialises without one and sorts after every fractional value.
        assertThat(Instant.parse(updatedBody.get("updatedAt").asText()))
                .isAfter(Instant.parse(updatedBody.get("createdAt").asText()));

        // The replacement is a replacement: Milk is gone rather than merged in.
        assertThat(names(body(get("/api/recipes/" + id)).get("ingredients"))).doesNotContain("Milk");

        // Delete.
        ResponseEntity<String> deleted = delete("/api/recipes/" + id);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/api/recipes/" + id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * The ingredient-reuse rule, seen from outside.
     *
     * <p>The Mockito test proves the service does not call {@code save} a second time; this one
     * proves the row that comes back is genuinely the same row, under its canonical spelling,
     * after Postgres has had its say. Only the second recipe asks for "flour" in lower case -
     * the reuse has to survive the case difference that {@code ux_ingredients_name_lower}
     * enforces.
     */
    @Test
    void reusesOneIngredientRowAcrossTwoRecipesRegardlessOfCase() {
        long first = seedRecipe(PANCAKES);

        long second = body(post("/api/recipes", """
                {
                  "title": "Waffles",
                  "instructions": "Batter, iron, wait.",
                  "prepMinutes": 10,
                  "cookMinutes": 20,
                  "servings": 4,
                  "difficulty": "MEDIUM",
                  "ingredients": [
                    {"name": "flour", "quantity": 250, "unit": "GRAM"}
                  ],
                  "categories": ["Breakfast"]
                }""")).get("id").asLong();

        JsonNode fromFirst = ingredientNamed(first, "Flour");
        JsonNode fromSecond = ingredientNamed(second, "Flour");

        assertThat(fromSecond.get("ingredientId").asLong()).isEqualTo(fromFirst.get("ingredientId").asLong());
        assertThat(fromSecond.get("name").asText()).isEqualTo("Flour");
        assertThat(fromSecond.get("quantity").decimalValue()).isEqualByComparingTo("250");
    }

    /**
     * The flush-ordering fix, at the level a user would hit it.
     *
     * <p>Replacing the ingredient list while <em>keeping</em> one of its members is the case that
     * breaks: Hibernate orders a flush by action type, so the new lines are inserted before the
     * old ones are deleted and the kept ingredient collides with itself on
     * {@code ux_recipe_ingredients_recipe_ingredient}. A test that swapped every ingredient would
     * pass with the fix removed.
     */
    @Test
    void replacesTheIngredientListWhileKeepingOneOfItsIngredients() {
        long id = seedRecipe(PANCAKES);

        ResponseEntity<String> updated = put("/api/recipes/" + id, """
                {
                  "title": "Pancakes",
                  "instructions": "Whisk the batter, rest it, fry until golden.",
                  "prepMinutes": 10,
                  "cookMinutes": 15,
                  "servings": 4,
                  "difficulty": "EASY",
                  "ingredients": [
                    {"name": "Flour", "quantity": 300, "unit": "GRAM"},
                    {"name": "Egg", "quantity": 2, "unit": "PIECE"}
                  ],
                  "categories": ["Breakfast"]
                }""");

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(names(body(updated).get("ingredients"))).containsExactlyInAnyOrder("Flour", "Egg");
    }

    /**
     * Deleting a recipe takes its ingredient lines and category links with it - that is the
     * {@code ON DELETE CASCADE} on those two join tables - but must not touch the shared
     * ingredient and category rows themselves. Getting that backwards would remove "Flour" from
     * every other recipe in the database.
     */
    @Test
    void deletingARecipeLeavesTheSharedIngredientRowsInPlace() {
        long keeper = seedRecipe(PANCAKES);
        long doomed = body(post("/api/recipes", """
                {
                  "title": "Flatbread",
                  "instructions": "Knead, rest, griddle.",
                  "prepMinutes": 20,
                  "cookMinutes": 10,
                  "servings": 4,
                  "difficulty": "EASY",
                  "ingredients": [
                    {"name": "Flour", "quantity": 500, "unit": "GRAM"}
                  ],
                  "categories": ["Side"]
                }""")).get("id").asLong();

        long sharedIngredientId = ingredientNamed(doomed, "Flour").get("ingredientId").asLong();

        assertThat(delete("/api/recipes/" + doomed).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode survivor = body(get("/api/recipes/" + keeper));
        assertThat(survivor.get("categories").get(0).asText()).isEqualTo("Breakfast");
        assertThat(ingredientNamed(keeper, "Flour").get("ingredientId").asLong()).isEqualTo(sharedIngredientId);
    }

    /**
     * Scaling, after the quantity has been through the database.
     *
     * <p>{@code ServingsScalingTest} covers the arithmetic on BigDecimals the test itself built.
     * What only this tier can say is that the number survives the round trip - Java BigDecimal to
     * {@code numeric(10,2)} and back, then scaled, then serialised - and comes out at the
     * precision the schema actually stores rather than whatever the driver felt like returning.
     *
     * <p>Three servings into two ingredients, so nothing divides evenly: 260 to one serving is
     * 86.67, which an implementation that rounded the ratio first would report as 85.80.
     */
    @Test
    void scalesQuantitiesThatHaveBeenThroughThePostgresNumericColumn() {
        long id = body(post("/api/recipes", """
                {
                  "title": "Belgian Waffles",
                  "instructions": "Batter, iron, wait.",
                  "prepMinutes": 10, "cookMinutes": 20, "servings": 3, "difficulty": "MEDIUM",
                  "ingredients": [
                    {"name": "Flour", "quantity": 260, "unit": "GRAM"},
                    {"name": "Egg", "quantity": 2, "unit": "PIECE"}
                  ],
                  "categories": ["Breakfast"]
                }""")).get("id").asLong();

        JsonNode single = body(get("/api/recipes/" + id + "?servings=1"));
        assertThat(single.get("servings").asInt()).isEqualTo(1);
        assertThat(single.get("ingredients").get(0).get("quantity").decimalValue())
                .isEqualByComparingTo("86.67");
        assertThat(single.get("ingredients").get(1).get("quantity").decimalValue())
                .isEqualByComparingTo("0.67");

        // Times describe the cooking, not the quantity, and must survive untouched.
        assertThat(single.get("totalMinutes").asInt()).isEqualTo(30);

        // Asking for the servings it already has returns it exactly as written, and nothing was
        // written back - the next plain read still says three.
        JsonNode asWritten = body(get("/api/recipes/" + id));
        assertThat(asWritten.get("servings").asInt()).isEqualTo(3);
        assertThat(asWritten.get("ingredients").get(0).get("quantity").decimalValue())
                .isEqualByComparingTo("260.00");
    }

    @Test
    void rejectsAServingsCountItWouldRefuseToStore() {
        long id = seedRecipe(PANCAKES);

        assertThat(get("/api/recipes/" + id + "?servings=0").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/api/recipes/" + id + "?servings=101").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(get("/api/recipes/" + id + "?servings=0")).get("errors")
                .findValuesAsText("parameter")).containsExactly("servings");
    }

    private JsonNode ingredientNamed(long recipeId, String name) {
        JsonNode lines = body(get("/api/recipes/" + recipeId)).get("ingredients");
        for (JsonNode line : lines) {
            if (line.get("name").asText().equals(name)) {
                return line;
            }
        }
        throw new AssertionError("Recipe " + recipeId + " has no ingredient named " + name + ": " + lines);
    }

    private static List<String> names(JsonNode ingredients) {
        return ingredients.findValuesAsText("name");
    }
}
