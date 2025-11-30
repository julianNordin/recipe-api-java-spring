package com.recipeapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The health endpoint, and the two things about it that are easy to get wrong.
 *
 * <p>The first is that <strong>UP on its own proves nothing</strong>. An application with no
 * health indicators at all reports UP just as cheerfully as one that has checked everything, so a
 * test asserting only the aggregate would keep passing if the database probe quietly stopped
 * contributing - which is the single thing this endpoint exists to tell anyone.
 *
 * <p>The second is exposure. Actuator ships endpoints that describe the inside of the application
 * - env, beans, mappings, configprops - and the natural way to fix a missing endpoint later is to
 * set {@code include: "*"}. That is pinned here so it fails a test rather than a review.
 */
class ActuatorHealthIT extends AbstractIntegrationTest {

    @Autowired
    private HealthEndpoint health;

    @Test
    void reportsUpOverHttp() {
        ResponseEntity<String> response = get("/actuator/health");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(response).get("status").asText()).isEqualTo("UP");
    }

    /**
     * The assertion that gives the one above its meaning: the database is genuinely among the
     * things being checked, so an application that is running but cannot reach Postgres reports
     * DOWN rather than a 200 that only means the servlet container is alive.
     *
     * <p>Read through the endpoint rather than over HTTP, because the components are deliberately
     * not published - see below.
     */
    @Test
    void countsTheDatabaseAmongTheThingsItChecks() {
        HealthComponent db = health.healthForPath("db");

        assertThat(db).as("no database health indicator is contributing to /actuator/health").isNotNull();
        assertThat(db.getStatus()).isEqualTo(Status.UP);
    }

    /**
     * The aggregate status and nothing else. Per-component detail names the database product and
     * its validation query, which is free reconnaissance for anyone who can reach the port. The
     * dev profile turns it back on, where reading it is the entire point.
     */
    @Test
    void doesNotDescribeItsComponentsToWhoeverAsks() {
        JsonNode body = body(get("/actuator/health"));

        assertThat(body.has("components")).isFalse();
        assertThat(body.fieldNames()).toIterable().containsExactly("status");
    }

    @Test
    void exposesNothingButHealth() {
        for (String endpoint : new String[]{"env", "beans", "mappings", "configprops", "loggers"}) {
            assertThat(get("/actuator/" + endpoint).getStatusCode())
                    .as("/actuator/%s is exposed", endpoint)
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
