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

    @Column(nullable = false, length = 100, unique = true)
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

    @Override
    public String toString() {
        return "Ingredient{id=%d, name='%s'}".formatted(id, name);
    }
}
