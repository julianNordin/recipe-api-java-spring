package com.recipeapi.web.support;

import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * The properties {@code GET /api/ingredients} will sort by.
 *
 * <p>Two, because an ingredient is two columns. The default is {@code name} rather than
 * {@code id}: it is what a caller browsing a vocabulary wants, and it is still a total ordering,
 * so a row cannot appear on two pages while another appears on none.
 */
public final class IngredientSorts {

    private static final SortWhitelist WHITELIST = SortWhitelist.of("id", "name");

    private IngredientSorts() {
    }

    public static Pageable validate(Pageable pageable) {
        return WHITELIST.validate(pageable);
    }

    public static List<String> sortable() {
        return WHITELIST.sortable();
    }
}
