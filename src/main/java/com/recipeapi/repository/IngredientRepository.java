package com.recipeapi.repository;

import com.recipeapi.domain.Ingredient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    /**
     * Looks an ingredient up the way the database defines uniqueness.
     *
     * <p>Spring Data turns {@code IgnoreCase} into {@code lower(name) = lower(?)}, which is
     * exactly the expression {@code ux_ingredients_name_lower} is built on - so this lookup can
     * use that index rather than scanning. It is the query behind the "reuse, never duplicate"
     * rule in Phase 07.
     */
    Optional<Ingredient> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    /**
     * The listing endpoint's search.
     *
     * <p>Unlike {@link #findByNameIgnoreCase} this one cannot use
     * {@code ux_ingredients_name_lower}: a substring match compiles to
     * {@code lower(name) like '%x%'}, and a leading wildcard leaves a B-tree index nothing to
     * seek on. That is fine for a vocabulary this size and would want a trigram index if it ever
     * stopped being.
     */
    Page<Ingredient> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * Whether any recipe still calls for this ingredient, asked of the association table directly
     * rather than by walking in from Recipe - the question is about one column of one table, and
     * a join from the recipe side would be two.
     *
     * <p>RecipeIngredient has no repository of its own and needs none: it is an entity, so JPQL
     * can name it here.
     */
    @Query("select count(line) > 0 from RecipeIngredient line where line.ingredient.id = :id")
    boolean isUsedByAnyRecipe(@Param("id") Long id);
}
