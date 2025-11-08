package com.recipeapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A single ingredient, e.g. "Flour".
 *
 * <p>Ingredients are reference data shared by every recipe that uses them, not rows owned by one
 * recipe. Two recipes calling for flour point at the same {@code ingredients} row; only the
 * quantity and unit differ, and those live on the association rather than here (Phase 03).
 *
 * <p>The name is unique <em>case-insensitively</em> - "Flour" and "flour" are the same
 * ingredient. A plain unique column cannot express that, so the real constraint is a functional
 * unique index on {@code lower(name)} created by the Flyway migration in Phase 04.
 */
@Entity
@Table(name = "ingredients")
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** Required by JPA. Not for application use. */
    protected Ingredient() {
    }

    public Ingredient(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Identity by primary key, with the two rules a JPA entity has to respect.
     *
     * <p>A transient instance (null id) is equal only to itself - two unsaved recipes are not
     * "the same recipe" just because their fields match. And the comparison goes through
     * {@code getId()} rather than the field, because {@code other} may be an uninitialised
     * Hibernate proxy whose field is still null while its getter returns the identifier.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Ingredient other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    /**
     * Constant, and deliberately so. An entity's hash must not change when Hibernate assigns its
     * id on flush, or the instance is lost inside any HashSet it was added to beforehand. Using
     * the class literal rather than {@code getClass()} keeps a proxy and its target in agreement.
     */
    @Override
    public int hashCode() {
        return Ingredient.class.hashCode();
    }

    @Override
    public String toString() {
        return "Ingredient{id=%d, name='%s'}".formatted(id, name);
    }
}
