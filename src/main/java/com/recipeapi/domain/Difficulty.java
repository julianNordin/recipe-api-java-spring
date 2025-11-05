package com.recipeapi.domain;

/**
 * How demanding a recipe is to cook.
 *
 * <p>Persisted by name rather than ordinal (see the {@code @Enumerated(EnumType.STRING)} on
 * {@link Recipe#getDifficulty()}). Ordinal storage would mean that inserting a constant into
 * the middle of this list silently rewrites the meaning of every existing row.
 */
public enum Difficulty {

    EASY,
    MEDIUM,
    HARD
}
