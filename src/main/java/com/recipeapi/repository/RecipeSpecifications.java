package com.recipeapi.repository;

import com.recipeapi.domain.Difficulty;
import com.recipeapi.domain.Recipe;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
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

    /** Filed under the given category, matched case-insensitively by name. */
    public static Specification<Recipe> inCategory(String name) {
        if (!StringUtils.hasText(name)) {
            return Specification.unrestricted();
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        return (root, query, builder) -> {
            Join<Recipe, Object> category = root.join("categories", JoinType.INNER);
            deduplicate(query);
            return builder.equal(builder.lower(category.get("name")), wanted);
        };
    }

    /** Calls for the given ingredient, matched case-insensitively by name. */
    public static Specification<Recipe> usesIngredient(String name) {
        if (!StringUtils.hasText(name)) {
            return Specification.unrestricted();
        }
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        return (root, query, builder) -> {
            Join<Recipe, Object> line = root.join("ingredients", JoinType.INNER);
            Join<Object, Object> ingredient = line.join("ingredient", JoinType.INNER);
            deduplicate(query);
            return builder.equal(builder.lower(ingredient.get("name")), wanted);
        };
    }

    public static Specification<Recipe> hasDifficulty(Difficulty difficulty) {
        if (difficulty == null) {
            return Specification.unrestricted();
        }
        return (root, query, builder) -> builder.equal(root.get("difficulty"), difficulty);
    }

    /**
     * Total time at or under the given number of minutes.
     *
     * <p>There is no total_minutes column - the value is derived in Java from prepMinutes and
     * cookMinutes - so the sum has to be restated here as a SQL expression. This is the price of
     * deriving it rather than storing it, and the reason it is also not sortable.
     */
    public static Specification<Recipe> totalMinutesAtMost(Integer maxMinutes) {
        if (maxMinutes == null) {
            return Specification.unrestricted();
        }
        return (root, query, builder) -> builder.lessThanOrEqualTo(
                builder.sum(root.<Integer>get("prepMinutes"), root.<Integer>get("cookMinutes")),
                maxMinutes);
    }

    /**
     * Joining a to-many association can return the same recipe once per matching child row, and
     * the count query would then over-report the total. The equality filters here happen to match
     * at most one row each, but that stops being true the moment a filter accepts several values,
     * and the bug would surface as duplicate rows in a page rather than as an error.
     *
     * <p>The null check is not defensive padding: Spring Data passes a null query on the delete
     * path, where there is no result set to deduplicate.
     */
    private static void deduplicate(CriteriaQuery<?> query) {
        if (query != null) {
            query.distinct(true);
        }
    }
}
