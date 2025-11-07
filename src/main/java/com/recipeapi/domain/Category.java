package com.recipeapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A label a recipe can be filed under, e.g. "Breakfast" or "Vegetarian".
 *
 * <p>Like {@link Ingredient} this is shared reference data rather than something a recipe owns.
 * Categories are seeded by the Flyway migration and a recipe attaches itself to the existing
 * rows; creating a recipe never creates a category.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60, unique = true)
    private String name;

    /** Required by JPA. Not for application use. */
    protected Category() {
    }

    public Category(String name) {
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
        return "Category{id=%d, name='%s'}".formatted(id, name);
    }
}
