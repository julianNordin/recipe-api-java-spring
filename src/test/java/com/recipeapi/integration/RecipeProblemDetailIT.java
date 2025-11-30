package com.recipeapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.recipeapi.repository.IngredientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every way this API says no, over real HTTP.
 *
 * <p>{@code RecipeControllerTest} asserts these bodies already, against a mocked service. Two
 * things it cannot assert: that {@code application/problem+json} is the content type the servlet
 * container actually wrote, and that the rules raised <em>below</em> the web layer survive the
 * trip out. A mocked service is told to throw; a real one has to reach the same conclusion from
 * data, inside a transaction, and get the exception past Hibernate's flush.
 *
 * <p>The last test here is the one no other tier can write at all: a request rejected halfway
 * through must leave nothing behind.
 */
class RecipeProblemDetailIT extends AbstractIntegrationTest {

    /**
     * Read directly, because the last assertion in this class is about a row the API deliberately
     * does not expose. There is no ingredients endpoint until Phase 15, and asking which recipes
     * use an ingredient cannot tell an ingredient that was never created apart from one that
     * exists but is referenced by nothing - which is the exact distinction being made.
     */
    @Autowired
    private IngredientRepository ingredients;

    @Test
    void reportsAMissingRecipeAsAProblemDetail() {
        ResponseEntity<String> response = get("/api/recipes/9999");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        JsonNode problem = body(response);
        assertThat(problem.get("type").asText()).isEqualTo("urn:recipe-api:problem:not-found");
        assertThat(problem.get("title").asText()).isEqualTo("Not Found");
        assertThat(problem.get("status").asInt()).isEqualTo(404);
        assertThat(problem.get("detail").asText()).isEqualTo("Recipe 9999 was not found");
        assertThat(problem.get("timestamp").asText()).isNotBlank();
    }

    /**
     * The write verbs answer 404 the same way, rather than one of them quietly succeeding or
     * reporting a 500. DELETE is the one worth pinning: "delete something that is not there"
     * reads like a no-op until a caller needs to know whether their delete did anything.
     */
    @Test
    void reportsAMissingRecipeOnEveryVerbThatAddressesOne() {
        assertThat(put("/api/recipes/9999", A_RECIPE).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete("/api/recipes/9999").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body(delete("/api/recipes/9999")).get("type").asText())
                .isEqualTo("urn:recipe-api:problem:not-found");
    }

    @Test
    void namesEveryFieldThatFailedValidation() {
        ResponseEntity<String> response = post("/api/recipes", """
                {
                  "title": "   ",
                  "instructions": "",
                  "servings": 0,
                  "difficulty": "EASY",
                  "ingredients": []
                }""");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        JsonNode problem = body(response);
        assertThat(problem.get("type").asText()).isEqualTo("urn:recipe-api:problem:validation-failed");
        assertThat(fieldsIn(problem)).contains(
                "title", "instructions", "servings", "ingredients", "prepMinutes", "cookMinutes");
    }

    /**
     * Bean Validation does not descend into collection elements on its own - {@code @Valid} on
     * the list is what makes it - so a line carrying a blank name and a negative quantity would
     * otherwise validate clean. Asserting the property <em>paths</em> is what proves the cascade
     * happened, rather than merely that something failed.
     */
    @Test
    void reportsViolationsInsideAnIngredientLineByTheirPath() {
        JsonNode problem = body(post("/api/recipes", """
                {
                  "title": "Pancakes",
                  "instructions": "Whisk, rest, fry.",
                  "prepMinutes": 10, "cookMinutes": 15, "servings": 4, "difficulty": "EASY",
                  "ingredients": [{"name": "  ", "quantity": -1, "unit": "GRAM"}]
                }"""));

        assertThat(fieldsIn(problem)).contains("ingredients[0].name", "ingredients[0].quantity");
    }

    /**
     * An enum constant that does not exist is a bad request, not a server error - and the detail
     * must not echo Jackson's message, which carries its view of the payload and the
     * fully-qualified names of internal classes.
     */
    @Test
    void reportsAnUnknownEnumConstantAsAMalformedBodyWithoutLeakingInternals() {
        ResponseEntity<String> response = post("/api/recipes", A_RECIPE.replace("\"EASY\"", "\"TRIVIAL\""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        JsonNode problem = body(response);
        assertThat(problem.get("type").asText()).isEqualTo("urn:recipe-api:problem:malformed-body");
        assertThat(problem.get("detail").asText()).doesNotContain("com.recipeapi", "Difficulty");
    }

    @Test
    void reportsAnIdThatIsNotANumberAsABadRequest() {
        ResponseEntity<String> response = get("/api/recipes/abc");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(response).get("type").asText()).isEqualTo("urn:recipe-api:problem:invalid-parameter");
    }

    /**
     * Spring Data would raise PropertyReferenceException during query construction and reach the
     * caller as a 500. The message lists what is sortable, so the 400 is actionable.
     */
    @Test
    void rejectsAnUnknownSortPropertyWithoutReachingTheDatabase() {
        ResponseEntity<String> response = get("/api/recipes?sort=DROP");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        JsonNode problem = body(response);
        assertThat(problem.get("type").asText()).isEqualTo("urn:recipe-api:problem:invalid-sort");
        assertThat(problem.get("detail").asText()).contains("Cannot sort by 'DROP'", "title", "createdAt");
    }

    /**
     * A constraint on a query parameter raises ConstraintViolationException rather than
     * MethodArgumentNotValidException, so it needs its own handler; without one this is a 500.
     * The reported name is the parameter, not the {@code list.maxMinutes} property path, which
     * names a method the caller has no way to know about.
     */
    @Test
    void rejectsANegativeMaxMinutesNamingTheParameter() {
        ResponseEntity<String> response = get("/api/recipes?maxMinutes=-5");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        JsonNode problem = body(response);
        assertThat(problem.get("type").asText()).isEqualTo("urn:recipe-api:problem:invalid-parameter");
        assertThat(problem.get("errors").findValuesAsText("parameter")).containsExactly("maxMinutes");
    }

    /**
     * "Flour" and "flour" resolve to the same row, so listing both is a duplicate even though the
     * strings differ. Left to the database this is a 500 carrying the text of a unique-constraint
     * violation, at flush, after the rest of the recipe has been built.
     */
    @Test
    void rejectsTheSameIngredientListedTwiceInDifferentCase() {
        ResponseEntity<String> response = post("/api/recipes", """
                {
                  "title": "Pancakes",
                  "instructions": "Whisk, rest, fry.",
                  "prepMinutes": 10, "cookMinutes": 15, "servings": 4, "difficulty": "EASY",
                  "ingredients": [
                    {"name": "Flour", "quantity": 300, "unit": "GRAM"},
                    {"name": "flour", "quantity": 50, "unit": "GRAM"}
                  ]
                }""");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(response).get("type").asText())
                .isEqualTo("urn:recipe-api:problem:duplicate-ingredient");
    }

    /**
     * The assertion this whole tier exists for.
     *
     * <p>Categories are resolved after ingredients, so by the time an unknown one is rejected the
     * service has already inserted a brand-new ingredient row. Only the transaction rolling back
     * stops "Sourdough Starter" outliving the request that was refused - and neither a Mockito
     * test nor a MockMvc slice can see that, because in both of them nothing was ever written.
     *
     * <p>The recipe is checked as well as the ingredient: a 400 that still created the resource
     * is worse than either outcome on its own.
     */
    @Test
    void leavesNothingBehindWhenTheRequestIsRejectedHalfwayThrough() {
        ResponseEntity<String> response = post("/api/recipes", """
                {
                  "title": "Pancakes",
                  "instructions": "Whisk, rest, fry.",
                  "prepMinutes": 10, "cookMinutes": 15, "servings": 4, "difficulty": "EASY",
                  "ingredients": [{"name": "Sourdough Starter", "quantity": 200, "unit": "GRAM"}],
                  "categories": ["Brunch"]
                }""");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(response).get("type").asText()).isEqualTo("urn:recipe-api:problem:unknown-category");

        assertThat(body(get("/api/recipes")).get("totalElements").asLong())
                .as("a rejected request must not create the recipe")
                .isZero();
        assertThat(ingredients.findByNameIgnoreCase("Sourdough Starter"))
                .as("a rejected request must not leave a new ingredient row behind")
                .isEmpty();
    }

    private static List<String> fieldsIn(JsonNode problem) {
        return problem.get("errors").findValuesAsText("field");
    }
}
