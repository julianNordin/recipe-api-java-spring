package com.recipeapi.repository;

import com.recipeapi.domain.Recipe;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * The composable predicates behind {@code GET /api/recipes}.
 *
 * <p>Every factory here returns {@link Specification#unrestricted()} when its argument is absent,
 * rather than null. That is what makes the filters genuinely optional: the caller can hand all of
 * them to {@link Specification#allOf} without first working out which ones were supplied, and an
 * absent filter contributes no restriction instead of removing every row.
 */
public final class RecipeSpecifications {

    private RecipeSpecifications() {
    }

    /**
     * Case-insensitive substring match against title or description.
     *
     * <p>lower() is applied to the column and the pattern is lower-cased in Java, so both sides
     * agree without depending on the database collation.
     *
     * <p>A recipe with a null description still matches on its title: in SQL, {@code TRUE OR
     * NULL} is TRUE. It is {@code FALSE OR NULL} that is unknown, which correctly excludes a
     * recipe matching on neither.
     */
    public static Specification<Recipe> matchesText(String term) {
        if (!StringUtils.hasText(term)) {
            return Specification.unrestricted();
        }
        String pattern = "%" + term.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, builder) -> {
            Predicate inTitle = builder.like(builder.lower(root.get("title")), pattern);
            Predicate inDescription = builder.like(builder.lower(root.get("description")), pattern);
            return builder.or(inTitle, inDescription);
        };
    }
}
