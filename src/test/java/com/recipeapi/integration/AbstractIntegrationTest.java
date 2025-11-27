package com.recipeapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipeapi.support.AbstractPostgresTest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractIntegrationTest extends AbstractPostgresTest {

    @Autowired
    protected TestRestTemplate http;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    /**
     * Refuses to run unless the application under test is pointed at the throwaway container.
     *
     * <p>Unlike the {@code @DataJpaTest} slices, a {@code @SpringBootTest} loads the real
     * {@code application.yml}, whose datasource URL is the developer's Compose database on
     * localhost:5432. Nothing but {@code @ServiceConnection} stands between these tests and that
     * database, and these tests write. If the override ever stops applying, the failure has to
     * be this assertion rather than a developer noticing their data has moved.
     */
    @BeforeEach
    void assertPointedAtTheThrowawayContainer() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                    .as("integration tests must run against the Testcontainers Postgres, not a real database")
                    .contains(":" + POSTGRES.getFirstMappedPort() + "/");
        }
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
