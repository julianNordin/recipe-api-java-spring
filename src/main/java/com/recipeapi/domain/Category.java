package com.recipeapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * Inverse side - {@code mappedBy} means this owns no join table and writing to it alone
     * persists nothing. It exists so Phase 15 can LEFT JOIN from Category to count recipes,
     * including the categories that have none, which a join from the Recipe side cannot see.
     */
    @ManyToMany(mappedBy = "categories", fetch = FetchType.LAZY)
    private List<Recipe> recipes = new ArrayList<>();

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

    /**
     * Mutable by design: {@link Recipe#addCategory} writes through this to keep both sides in
     * step. Application code should go through {@code Recipe}, not here.
     */
    List<Recipe> getRecipes() {
        return recipes;
    }

    @Override
    public String toString() {
        return "Category{id=%d, name='%s'}".formatted(id, name);
    }
}
