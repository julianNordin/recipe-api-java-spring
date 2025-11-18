package com.recipeapi.web.support;

import com.recipeapi.exception.InvalidSortException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

/**
 * The set of properties GET /api/recipes will sort by.
 *
 * <p>A whitelist rather than a blacklist, for two reasons. Spring Data happily accepts
 * {@code sort=someTypo} and then fails with PropertyReferenceException during query
 * construction, which reaches the caller as a 500 for an obviously bad request. And an
 * unrestricted sort parameter lets a caller order by any property reachable from the entity,
 * traversing associations into joins nobody planned for.
 *
 * <p>totalMinutes is absent on purpose: it is derived in Java from two columns, so there is
 * nothing for ORDER BY to name. Sorting by it would need the sum pushed into SQL, which the
 * plain Pageable path cannot express.
 */
public final class RecipeSorts {

    private static final Set<String> SORTABLE = Set.of(
            "id", "title", "servings", "difficulty", "prepMinutes", "cookMinutes", "createdAt", "updatedAt");

    private RecipeSorts() {
    }

    /** Returns the same pageable, once every requested sort property is known to be sortable. */
    public static Pageable validate(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty(), sortable());
            }
        }
        return pageable;
    }

    /** Sorted, so error messages and documentation list them in a stable order. */
    public static List<String> sortable() {
        return SORTABLE.stream().sorted().toList();
    }
}
