package com.recipeapi.repository;

import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for the repository slices: one Postgres container shared by every subclass.
 *
 * <p>The container is started from a static initialiser rather than through JUnit's
 * {@code @Testcontainers}/{@code @Container} pair, which is the deliberate part. That extension
 * stops a static container when its test class finishes, so the second slice to run would find a
 * dead database. Starting it once here and never stopping it means all the slices share a single
 * container - Testcontainers' Ryuk sidecar removes it when the JVM exits.
 *
 * <p>Sharing it also keeps them on one JDBC URL, so Spring caches a single application context
 * across the slices instead of building one per class.
 *
 * <p>{@code @ServiceConnection} points the datasource at the container. It is read from the
 * static field directly and does not need {@code @Container} to work.
 */
@DataJpaTest
abstract class AbstractRepositoryTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
