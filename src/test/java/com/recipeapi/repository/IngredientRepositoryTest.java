package com.recipeapi.repository;

import com.recipeapi.domain.Ingredient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
class IngredientRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private IngredientRepository ingredients;

    @Autowired
    private DataSource dataSource;

    @Test
    void runsAgainstRealPostgresRatherThanAnEmbeddedDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            System.out.println(">>> DATABASE UNDER TEST: " + meta.getDatabaseProductName()
                    + " " + meta.getDatabaseProductVersion() + " via " + meta.getURL());
            assertThat(meta.getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(meta.getDatabaseMajorVersion()).isEqualTo(16);
        }
    }

    @Test
    void flywayMigrationsRanInsideTheContainer() {
        assertThat(ingredients.count()).isEqualTo(24);
    }

    /**
     * The seeded row is "Flour". Every one of these has to find it, because the database's
     * uniqueness rule is on lower(name) and the lookup has to agree with the constraint.
     */
    @ParameterizedTest
    @ValueSource(strings = {"Flour", "flour", "FLOUR", "FlOuR"})
    void findsASeededIngredientWhateverTheCaseOfTheQuery(String query) {
        assertThat(ingredients.findByNameIgnoreCase(query))
                .as("looking up %s", query)
                .isPresent()
                .get()
                .extracting(Ingredient::getName)
                .isEqualTo("Flour");
    }

    @Test
    void existsByNameIgnoreCaseAgreesWithTheIndex() {
        assertThat(ingredients.existsByNameIgnoreCase("PARMESAN")).isTrue();
        assertThat(ingredients.existsByNameIgnoreCase("Nothing At All")).isFalse();
    }

    /**
     * ux_ingredients_name_lower is what actually stops "Flour" and "FLOUR" coexisting. This is
     * the test that the constraint is really in the database and not just implied by the code
     * that usually calls findByNameIgnoreCase first.
     *
     * <p>saveAndFlush, not save: @DataJpaTest runs in a transaction, so without an explicit
     * flush the insert would sit in the persistence context until after the assertion and the
     * violation would never surface.
     */
    @Test
    void rejectsAnIngredientThatDiffersFromAnExistingOneOnlyByCase() {
        assertThatThrownBy(() -> ingredients.saveAndFlush(new Ingredient("FLOUR")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_ingredients_name_lower");
    }

    @Test
    void savesAndFindsAnIngredient() {
        Ingredient saved = ingredients.save(new Ingredient("Saffron"));

        assertThat(saved.getId()).isNotNull();
        assertThat(ingredients.findById(saved.getId())).contains(saved);
    }
}
