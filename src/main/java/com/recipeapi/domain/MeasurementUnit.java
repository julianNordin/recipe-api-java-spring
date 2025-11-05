package com.recipeapi.domain;

/**
 * The unit a recipe measures an ingredient in.
 *
 * <p>Deliberately a closed set rather than free text: the servings-rescaling feature has to do
 * arithmetic on quantities, and "2 handfuls" does not scale. Like {@link Difficulty} these are
 * persisted by name, never by ordinal.
 */
public enum MeasurementUnit {

    GRAM("g"),
    KILOGRAM("kg"),
    MILLILITRE("ml"),
    LITRE("l"),
    TEASPOON("tsp"),
    TABLESPOON("tbsp"),
    CUP("cup"),
    PIECE("pc"),
    PINCH("pinch"),
    CLOVE("clove"),
    SLICE("slice");

    private final String symbol;

    MeasurementUnit(String symbol) {
        this.symbol = symbol;
    }

    /** The short form shown to humans, e.g. {@code "g"} for {@link #GRAM}. */
    public String symbol() {
        return symbol;
    }
}
