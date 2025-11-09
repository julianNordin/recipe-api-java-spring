package com.recipeapi.repository;

import com.recipeapi.domain.Ingredient;
import org.junit.jupiter.api.Test;
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

    @Test
    void savesAndFindsAnIngredient() {
        Ingredient saved = ingredients.save(new Ingredient("Saffron"));

        assertThat(saved.getId()).isNotNull();
        assertThat(ingredients.findById(saved.getId())).contains(saved);
    }
}
