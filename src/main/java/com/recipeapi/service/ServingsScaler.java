package com.recipeapi.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Restates an ingredient quantity for a different number of servings.
 *
 * <p>Pure arithmetic with no collaborators, no state and no persistence, which is why it is a
 * handful of lines with a page of reasoning attached: everything that can go wrong here is
 * arithmetic, and all of it goes wrong quietly.
 */
public final class ServingsScaler {

    /**
     * Two decimals, matching {@code quantity numeric(10,2)} in the schema. Returning more
     * precision than the column can hold would mean the API quoting a quantity that could not be
     * stored back.
     */
    private static final int SCALE = 2;

    private ServingsScaler() {
    }

    /**
     * Multiply first, then divide - never compute a ratio and multiply by it.
     *
     * <p>A ratio has to be rounded to some precision before it can be used, and every quantity
     * then inherits that rounding error, multiplied by its own size. Scaling 1000 g from 3
     * servings to 1: the ratio 1/3 rounds to 0.33, and 1000 x 0.33 is <strong>330.00</strong>
     * against a true 333.33. The error grows with the quantity, which is exactly the behaviour
     * nobody notices in a test fixture using small round numbers.
     *
     * <p>Doing it in this order also makes scaling to the number of servings a recipe already has
     * <em>exact</em> rather than merely close: {@code q x n / n} recovers {@code q} for any
     * {@code q}. That is what lets the unscaled response and the scaled one share a single code
     * path instead of branching.
     *
     * <p>The rounding is HALF_UP at two decimals, so a quantity below 0.015 scaled down far
     * enough reports 0.00. That is the honest consequence of quoting quantities at the precision
     * the schema stores; inventing a floor of 0.01 would be the API making up an amount nobody
     * measured. It is pinned by a test so it stays a decision rather than a surprise.
     *
     * <p>{@code fromServings} cannot be zero: {@code servings} is {@code @Positive} on the way in
     * and NOT NULL in the schema, so no persisted recipe can carry one.
     */
    public static BigDecimal scale(BigDecimal quantity, int fromServings, int toServings) {
        return quantity
                .multiply(BigDecimal.valueOf(toServings))
                .divide(BigDecimal.valueOf(fromServings), SCALE, RoundingMode.HALF_UP);
    }
}
