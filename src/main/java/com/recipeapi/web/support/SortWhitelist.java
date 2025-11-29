package com.recipeapi.web.support;

import com.recipeapi.exception.InvalidSortException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

/**
 * The set of properties one endpoint will sort by.
 *
 * <p>A whitelist rather than a blacklist, for two reasons. Spring Data happily accepts
 * {@code sort=someTypo} and then fails with PropertyReferenceException during query
 * construction, which reaches the caller as a 500 for an obviously bad request. And an
 * unrestricted sort parameter lets a caller order by any property reachable from the entity,
 * traversing associations into joins nobody planned for.
 *
 * <p>Validation belongs behind the service rather than in a controller, so that every caller is
 * covered rather than only the one that happens to arrive over HTTP.
 */
public final class SortWhitelist {

    private final Set<String> sortable;

    private SortWhitelist(Set<String> sortable) {
        this.sortable = sortable;
    }

    public static SortWhitelist of(String... properties) {
        return new SortWhitelist(Set.of(properties));
    }

    /**
     * Returns the same pageable, once every requested sort property is known to be sortable.
     *
     * <p>Every order, not just the first: {@code sort=title&sort=nonsense} is two of them, and
     * checking only the leading one lets the second through to fail in query construction.
     */
    public Pageable validate(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!sortable.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty(), sortable());
            }
        }
        return pageable;
    }

    /** Sorted, so error messages and documentation list them in a stable order. */
    public List<String> sortable() {
        return sortable.stream().sorted().toList();
    }
}
