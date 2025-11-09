package com.recipeapi.repository;

import com.recipeapi.domain.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
