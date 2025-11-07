package com.recipeapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/**
 * How much of one {@link Ingredient} a particular {@link Recipe} calls for.
 *
 * <p>This is an association <em>with attributes</em>, which is why it is a full entity rather
 * than an {@code @ElementCollection} or a plain {@code @ManyToMany}. "300" and "GRAM" are facts
 * about the pairing of this recipe with this ingredient - they belong to neither side alone.
 *
 * <p>The primary key is a surrogate id rather than a composite of the two foreign keys. A
 * composite {@code @EmbeddedId} would model the uniqueness directly, but it makes
 * {@code orphanRemoval} and equality fiddly for no gain here; the pairing is instead kept unique
 * by an explicit constraint on the table.
 */
@Entity
@Table(
        name = "recipe_ingredients",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_recipe_ingredients_recipe_ingredient",
                columnNames = {"recipe_id", "ingredient_id"}
        )
)
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * LAZY explicitly. {@code @ManyToOne} defaults to EAGER, which would mean every single load
     * of a RecipeIngredient silently drags its Recipe along with it.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    /**
     * BigDecimal, not double. Quantities get multiplied by a serving ratio in Phase 15 and then
     * shown to a human; binary floating point would turn 0.5 tsp scaled by 3 into
     * 1.5000000000000002.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeasurementUnit unit;

    /** Required by JPA. Not for application use. */
    protected RecipeIngredient() {
    }

    /** Package-private: instances are created through {@link Recipe#addIngredient}. */
    RecipeIngredient(Recipe recipe, Ingredient ingredient, BigDecimal quantity, MeasurementUnit unit) {
        this.recipe = recipe;
        this.ingredient = ingredient;
        this.quantity = quantity;
        this.unit = unit;
    }

    public Long getId() {
        return id;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    void setRecipe(Recipe recipe) {
        this.recipe = recipe;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public MeasurementUnit getUnit() {
        return unit;
    }

    public void setUnit(MeasurementUnit unit) {
        this.unit = unit;
    }

    @Override
    public String toString() {
        return "RecipeIngredient{id=%d, recipe=%s, ingredient=%s, quantity=%s, unit=%s}"
                .formatted(id, recipe, ingredient, quantity, unit);
    }
}
