package com.recipeapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The published spec, checked against the application that publishes it.
 *
 * <p>Documentation is the part of an API that rots without anyone noticing, because nothing
 * breaks when it does. These tests exist to make it break: an endpoint added without annotations,
 * an error response left described as if it returned the success shape, or a Swagger UI that
 * quietly starts fetching from a CDN would all be caught here rather than by whoever reads the
 * spec first.
 */
class OpenApiIT extends AbstractIntegrationTest {

    /** Anything outside a data: URI - the only non-relative reference the page is allowed. */
    private static final Pattern EXTERNAL_URL = Pattern.compile("\\bhttps?://[^\"'\\s)]+");

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void publishesTheSpecWithTheDocumentLevelInformationNothingCanInfer() {
        ResponseEntity<String> response = get("/v3/api-docs");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode spec = body(response);
        assertThat(spec.get("openapi").asText()).startsWith("3.1");
        assertThat(spec.get("info").get("title").asText()).isEqualTo("Recipe API");
        assertThat(spec.get("info").get("version").asText()).isEqualTo("1.0");
        assertThat(spec.get("info").get("description").asText()).contains("RFC 9457");
    }

    /**
     * The assertion that keeps the spec honest as the API grows: every {@code /api} path the
     * application actually maps has to appear in it. A controller added later without a thought
     * for documentation fails here, which is the only moment anyone would notice.
     */
    @Test
    void documentsEveryApiPathTheApplicationMaps() {
        Set<String> documented = new TreeSet<>();
        body(get("/v3/api-docs")).get("paths").fieldNames().forEachRemaining(documented::add);

        assertThat(documented).containsExactlyInAnyOrderElementsOf(mappedApiPaths());
    }

    /**
     * Every operation carries a tag and a summary, which is what Swagger UI shows before anything
     * is expanded. Without the tag springdoc files an operation under the controller's class
     * name, naming this project's internals rather than the API's subjects.
     */
    @Test
    void givesEveryOperationATagAndASummary() {
        forEachOperation((path, method, operation) -> {
            assertThat(operation.get("tags")).as("%s %s has no tag", method, path).isNotNull();
            assertThat(operation.get("tags").get(0).asText())
                    .as("%s %s is tagged by class name", method, path)
                    .isIn("Recipes", "Ingredients", "Categories");
            assertThat(operation.get("summary")).as("%s %s has no summary", method, path).isNotNull();
        });
    }

    /**
     * Undeclared, springdoc describes an error response with the operation's success schema - so
     * the spec would claim a 404 returns a recipe. That is not merely unhelpful: a client
     * generated from it parses the wrong shape on exactly the paths that matter.
     */
    @Test
    void describesEveryErrorResponseAsAProblemDetail() {
        forEachOperation((path, method, operation) -> operation.get("responses").properties()
                .forEach(response -> {
                    String status = response.getKey();
                    JsonNode content = response.getValue().get("content");
                    if (status.startsWith("2")) {
                        return;
                    }
                    // Not "skip if absent": a missing content block is the failure this test is
                    // most likely to meet. The declaration is shared through a meta-annotation,
                    // and were that ever to stop being merged the responses would lose their
                    // bodies rather than gain a wrong one - which a skip would wave through.
                    assertThat(content)
                            .as("%s %s -> %s documents no response body at all", method, path, status)
                            .isNotNull();
                    assertThat(content.fieldNames()).toIterable()
                            .as("%s %s -> %s is not documented as a problem detail", method, path, status)
                            .containsExactly("application/problem+json");
                }));
    }

    @Test
    void servesSwaggerUiFromTheJar() {
        // /swagger-ui.html is a 302 to /swagger-ui/index.html, which the client follows. The
        // assertion is on where it lands rather than on the redirect: the documented URL has to
        // reach the UI, and by what mechanism is springdoc's business.
        ResponseEntity<String> documented = get("/swagger-ui.html");
        assertThat(documented.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(documented.getBody()).contains("swagger-ui-bundle.js");

        ResponseEntity<String> index = get("/swagger-ui/index.html");
        assertThat(index.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(index.getBody()).contains("swagger-ui-bundle.js");

        for (String asset : List.of("swagger-ui.css", "swagger-ui-bundle.js",
                "swagger-ui-standalone-preset.js", "swagger-initializer.js")) {
            assertThat(get("/swagger-ui/" + asset).getStatusCode())
                    .as("%s is not served locally", asset)
                    .isEqualTo(HttpStatus.OK);
        }
    }

    /**
     * The documentation has to work with the network unplugged, and the assets come from a
     * third-party webjar - so this is worth pinning rather than looking at once.
     *
     * <p>The page itself may name no absolute URL. <strong>Its initializer may, and does:</strong>
     * the webjar ships {@code swagger-initializer.js} with
     * {@code url: "https://petstore.swagger.io/v2/swagger.json"} hard-coded, and springdoc
     * neutralises it by writing a {@code configUrl} alongside, which SwaggerUIBundle fetches and
     * whose {@code url} wins. Grepping that file for absolute URLs would therefore be a test that
     * either fails for no reason or, worse, is quietly weakened until it passes. The honest
     * assertion is on the override: the config link is present, it points at the local spec, and
     * {@code validatorUrl} is empty so the UI does not call validator.swagger.io either.
     *
     * <p>Confirmed in a real browser when this landed: loading the page issues nine requests,
     * eight to localhost and one inline {@code data:} URI, and nothing else.
     */
    @Test
    void loadsTheSwaggerUiWithoutReachingTheNetwork() {
        assertThat(externalUrlsIn(get("/swagger-ui/index.html").getBody()))
                .as("the Swagger UI page itself must name no absolute URL")
                .isEmpty();

        assertThat(get("/swagger-ui/swagger-initializer.js").getBody())
                .as("without a configUrl the initializer's hard-coded petstore URL is what loads")
                .contains("configUrl", "/v3/api-docs/swagger-config");

        JsonNode config = body(get("/v3/api-docs/swagger-config"));
        assertThat(config.get("url").asText()).isEqualTo("/v3/api-docs");
        assertThat(config.get("validatorUrl").asText())
                .as("a non-empty validatorUrl makes the UI call validator.swagger.io")
                .isEmpty();
    }

    private static List<String> externalUrlsIn(String page) {
        Matcher matcher = EXTERNAL_URL.matcher(page == null ? "" : page);
        return matcher.results().map(java.util.regex.MatchResult::group).toList();
    }

    private Set<String> mappedApiPaths() {
        Set<String> paths = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            info.getPathPatternsCondition().getPatternValues().stream()
                    .filter(pattern -> pattern.startsWith("/api/"))
                    .forEach(paths::add);
        }
        return paths;
    }

    private void forEachOperation(OperationAssertion assertion) {
        JsonNode paths = body(get("/v3/api-docs")).get("paths");
        for (Map.Entry<String, JsonNode> path : paths.properties()) {
            for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
                assertion.check(path.getKey(), operation.getKey(), operation.getValue());
            }
        }
    }

    @FunctionalInterface
    private interface OperationAssertion {
        void check(String path, String method, JsonNode operation);
    }
}
