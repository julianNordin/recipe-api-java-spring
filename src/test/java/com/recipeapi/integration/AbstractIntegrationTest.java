package com.recipeapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipeapi.support.AbstractPostgresTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The base class for the end-to-end tier: the whole application, on a real port, talking to a
 * real Postgres.
 *
 * <p>{@code RANDOM_PORT} rather than {@code MOCK}: this tier exists to exercise the parts a
 * MockMvc slice cannot reach - the servlet container, real HTTP status lines and headers, and
 * the JSON as it actually crosses the wire - on top of SQL that Postgres actually planned. The
 * container itself comes from {@link AbstractPostgresTest} and is shared with the
 * {@code @DataJpaTest} slices, so the suite starts one database and Spring caches one context
 * for every class below this one.
 *
 * <p>Requests are sent as raw JSON text blocks and responses are read as a {@link JsonNode}
 * rather than bound to the response records. Binding would assert against Jackson's round trip
 * of this project's own types, which is exactly the thing that cannot detect a field quietly
 * disappearing from - or appearing in - the published contract.
 *
 * <p>{@code clean-disabled=false} is declared here rather than in any {@code application.yml},
 * so the ability to drop the schema exists only for classes that extend this one. Putting it in
 * a configuration file - even a test-scoped one - would arm it for the whole application.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.clean-disabled=false")
abstract class AbstractIntegrationTest extends AbstractPostgresTest {

    /**
     * A minimal valid recipe: one ingredient, one category, every required field present.
     *
     * <p>Most tests in this tier need <em>a</em> recipe to exist so they can exercise something
     * else - deleting the ingredient it uses, counting the category it is filed under, asking for
     * an id that does not exist. For those the payload is scenery, and one copy of the scenery is
     * enough.
     *
     * <p>Tests whose subject <em>is</em> the recipe keep a payload of their own, because there the
     * details are load-bearing: {@code RecipeApiIT} needs a second ingredient to show that a PUT
     * replaces the list rather than merging into it, and {@code CategoryApiIT} needs two
     * categories to show the count is per category rather than per recipe. Folding those into
     * this constant would leave their assertions depending on fields no reader of the test can
     * see.
     */
    protected static final String A_RECIPE = """
            {
              "title": "Pancakes",
              "instructions": "Whisk, rest, fry.",
              "prepMinutes": 10, "cookMinutes": 15, "servings": 4, "difficulty": "EASY",
              "ingredients": [{"name": "Flour", "quantity": 300, "unit": "GRAM"}],
              "categories": ["Breakfast"]
            }""";

    @Autowired
    protected TestRestTemplate http;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    /**
     * Puts the database back to its migrated state before every test.
     *
     * <p>This tier commits. The server handles each request on its own thread inside its own
     * transaction, so a {@code @Transactional} test method - the reflex that rolls a slice test
     * back - shares nothing with the work it is meant to be undoing and rolls back an empty
     * transaction while every write survives. Left alone, three recipes survive one test class
     * and reach the next, and assertions start depending on the order the suite happened to run
     * in.
     *
     * <p>Flyway's own clean plus migrate rather than a hand-written {@code TRUNCATE}: the tests
     * lean on the reference data V2 seeds, so a truncate would have to preserve some tables,
     * restart some sequences and re-insert the seed - three chances for the reset to drift away
     * from the migrations. Clean and migrate cannot drift, because it *is* the migrations. It
     * runs per test rather than per class so that no test can be made to pass by the test that
     * ran before it.
     *
     * <p>Cleaning is the reason for the guard below, which runs first and refuses to continue
     * unless the application under test is pointed at the throwaway container. A
     * {@code @SpringBootTest} loads the real {@code application.yml}, whose datasource is the
     * developer's Compose database on localhost:5432; the only thing redirecting it is
     * {@code @ServiceConnection}. Dropping the wrong schema is not a failure anyone should have
     * to diagnose after the fact.
     *
     * <p>JUnit runs a superclass {@code @BeforeEach} before the subclass's, which is what lets a
     * subclass seed its fixture in one of its own and know the database was empty first.
     */
    @BeforeEach
    void resetTheDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                    .as("integration tests must run against the Testcontainers Postgres, not a real database")
                    .contains(":" + POSTGRES.getFirstMappedPort() + "/");
        }
        flyway.clean();
        flyway.migrate();
    }

    /**
     * Creates a recipe and returns its generated id.
     *
     * <p>The status is asserted rather than assumed: a fixture that stops being accepted should
     * fail here, naming the rule it broke, instead of surfacing later as a null id in an
     * assertion that has nothing to do with the cause.
     */
    protected long seedRecipe(String json) {
        ResponseEntity<String> response = post("/api/recipes", json);
        assertThat(response.getStatusCode())
                .as("seeding a recipe failed: %s", response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        return body(response).get("id").asLong();
    }

    /** Creates {@link #A_RECIPE}, for a test that needs a recipe to exist but not a specific one. */
    protected long seedRecipe() {
        return seedRecipe(A_RECIPE);
    }

    protected ResponseEntity<String> get(String path) {
        return http.getForEntity(path, String.class);
    }

    protected ResponseEntity<String> post(String path, String json) {
        return http.postForEntity(path, jsonEntity(json), String.class);
    }

    protected ResponseEntity<String> put(String path, String json) {
        return http.exchange(path, HttpMethod.PUT, jsonEntity(json), String.class);
    }

    /**
     * {@code TestRestTemplate.delete} returns void, which leaves no way to assert on the 204 or
     * on a problem-detail body, so the exchange is spelled out.
     */
    protected ResponseEntity<String> delete(String path) {
        return http.exchange(path, HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
    }

    protected JsonNode body(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new AssertionError("Response body was not JSON: " + response.getBody(), e);
        }
    }

    private static HttpEntity<String> jsonEntity(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(json, headers);
    }
}
