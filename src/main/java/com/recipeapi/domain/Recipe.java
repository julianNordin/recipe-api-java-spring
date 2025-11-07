package com.recipeapi.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A recipe: what to cook, how long it takes and how hard it is.
 *
 * <p>This class carries no relationships yet - ingredients and categories arrive in Phase 03.
 */
@Entity
@Table(name = "recipes")
public class Recipe {

    /**
     * IDENTITY rather than SEQUENCE. It maps directly onto the Postgres identity column the
     * Flyway migration creates, which keeps the hand-written schema and the mapping obviously
     * in step. The cost is that Hibernate cannot batch inserts under IDENTITY, because it has
     * to round-trip to the database for every generated key - irrelevant at this write volume.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 5000)
    private String instructions;

    @Column(name = "prep_minutes", nullable = false)
    private int prepMinutes;

    @Column(name = "cook_minutes", nullable = false)
    private int cookMinutes;

    @Column(nullable = false)
    private int servings;

    /**
     * STRING, not the JPA default of ORDINAL. Storing {@code 0}/{@code 1}/{@code 2} would make
     * the rows unreadable in psql and would corrupt their meaning the moment a constant is
     * inserted into the middle of {@link Difficulty}.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Difficulty difficulty;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * The owning side of the many-to-many, so this is where the join table is declared.
     *
     * <p>An explicit {@code @JoinTable} rather than Hibernate's defaults: the Flyway migration
     * writes this table by hand, so its name and both column names have to be stated here or the
     * mapping and the schema drift apart silently.
     *
     * <p>No cascade. Categories are shared reference data - cascading a recipe delete into them
     * would delete "Breakfast" out from under every other recipe using it.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "recipe_categories",
            joinColumns = @JoinColumn(name = "recipe_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private List<Category> categories = new ArrayList<>();

    /**
     * The recipe's ingredient lines. Unlike categories these <em>are</em> owned by the recipe:
     * a "300 g of flour" line has no meaning apart from the recipe it belongs to.
     *
     * <p>Hence {@code cascade = ALL} plus {@code orphanRemoval = true} - saving a recipe saves
     * its lines, deleting a recipe deletes them, and dropping a line from this collection
     * deletes that row. Phase 07's PUT relies on exactly that to replace an ingredient list.
     */
    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    /** Required by JPA. Not for application use. */
    protected Recipe() {
    }

    public Recipe(String title,
                  String description,
                  String instructions,
                  int prepMinutes,
                  int cookMinutes,
                  int servings,
                  Difficulty difficulty) {
        this.title = title;
        this.description = description;
        this.instructions = instructions;
        this.prepMinutes = prepMinutes;
        this.cookMinutes = cookMinutes;
        this.servings = servings;
        this.difficulty = difficulty;
    }

    /**
     * Total hands-on plus cooking time.
     *
     * <p>Derived on read rather than stored, so it can never drift out of step with the two
     * columns it is computed from. Phase 12 filters on it with {@code maxMinutes}, which has to
     * express the same sum as a SQL predicate because there is no column to compare against.
     */
    public int totalMinutes() {
        return prepMinutes + cookMinutes;
    }

    /**
     * Attaches a category, keeping both sides of the association in step.
     *
     * <p>JPA only persists what the owning side says, but an object graph where one side has
     * been updated and the other has not is a bug waiting for the next read from the same
     * persistence context, which serves the stale in-memory collection rather than re-querying.
     */
    public void addCategory(Category category) {
        if (!categories.contains(category)) {
            categories.add(category);
            category.getRecipes().add(this);
        }
    }

    public void removeCategory(Category category) {
        categories.remove(category);
        category.getRecipes().remove(this);
    }

    /** Adds an ingredient line, setting the back-reference so orphanRemoval behaves. */
    public RecipeIngredient addIngredient(Ingredient ingredient, BigDecimal quantity, MeasurementUnit unit) {
        RecipeIngredient line = new RecipeIngredient(this, ingredient, quantity, unit);
        ingredients.add(line);
        return line;
    }

    public void removeIngredient(RecipeIngredient line) {
        ingredients.remove(line);
        line.setRecipe(null);
    }

    /**
     * Empties the ingredient list in place.
     *
     * <p>Deliberately mutates the existing collection instead of assigning a fresh
     * {@code ArrayList}. Replacing the instance that Hibernate handed us throws
     * <em>"A collection with cascade=all-delete-orphan was no longer referenced by the owning
     * entity instance"</em>, which is the single easiest way to break an orphanRemoval mapping.
     */
    public void clearIngredients() {
        for (RecipeIngredient line : new ArrayList<>(ingredients)) {
            line.setRecipe(null);
        }
        ingredients.clear();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public int getPrepMinutes() {
        return prepMinutes;
    }

    public void setPrepMinutes(int prepMinutes) {
        this.prepMinutes = prepMinutes;
    }

    public int getCookMinutes() {
        return cookMinutes;
    }

    public void setCookMinutes(int cookMinutes) {
        this.cookMinutes = cookMinutes;
    }

    public int getServings() {
        return servings;
    }

    public void setServings(int servings) {
        this.servings = servings;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Unmodifiable on purpose: mutate through {@link #addCategory} / {@link #removeCategory}. */
    public List<Category> getCategories() {
        return List.copyOf(categories);
    }

    /** Unmodifiable on purpose: mutate through {@link #addIngredient} / {@link #removeIngredient}. */
    public List<RecipeIngredient> getIngredients() {
        return List.copyOf(ingredients);
    }

    @Override
    public String toString() {
        return "Recipe{id=%d, title='%s', difficulty=%s, totalMinutes=%d, categories=%s, ingredients=%s}"
                .formatted(id, title, difficulty, totalMinutes(), categories, ingredients);
    }
}
