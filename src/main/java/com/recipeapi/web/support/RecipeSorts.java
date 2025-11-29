package com.recipeapi.web.support;

import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * The properties {@code GET /api/recipes} will sort by.
 *
 * <p>The rules live in {@link SortWhitelist}; what this class contributes is the list itself and
 * why it reads the way it does. {@code totalMinutes} is absent on purpose: it is derived in Java
 * from two columns, so there is nothing for ORDER BY to name. Sorting by it would need the sum
 * pushed into SQL, which the plain Pageable path cannot express - the same reason the
 * {@code maxMinutes} filter has to restate {@code prep + cook} as an expression.
 */
public final class RecipeSorts {

    private static final SortWhitelist WHITELIST = SortWhitelist.of(
            "id", "title", "servings", "difficulty", "prepMinutes", "cookMinutes", "createdAt", "updatedAt");

    private RecipeSorts() {
    }

    public static Pageable validate(Pageable pageable) {
        return WHITELIST.validate(pageable);
    }

    public static List<String> sortable() {
        return WHITELIST.sortable();
    }
}
