package com.recipeapi.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One Postgres container shared by every test that needs a real database.
 *
 * <p>The container is started from a static initialiser rather than through JUnit's
 * {@code @Testcontainers}/{@code @Container} pair, which is the deliberate part. That extension
 * stops a static container when its declaring test class finishes, so the second test class to
 * run would find a dead database. Started once here and never stopped, Testcontainers' Ryuk
 * sidecar removes it when the JVM exits.
 *
 * <p>Sharing it also keeps every test on one JDBC URL, so Spring caches application contexts
 * across classes instead of rebuilding one per class.
 *
 * <p>This class carries no test-slice annotation of its own; subclasses declare whichever slice
 * they need ({@code @DataJpaTest}, {@code @SpringBootTest}). {@code @ServiceConnection} is read
 * from the static field and does not need {@code @Container} to work.
 */
public abstract class AbstractPostgresTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
