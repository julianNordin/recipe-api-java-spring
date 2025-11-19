package com.recipeapi.repository;

import com.recipeapi.domain.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * {@link JpaSpecificationExecutor} is what lets the filters compose.
 *
 * <p>The alternative is a derived query method per combination of filters -
 * {@code findByTitleContainingAndDifficultyAndCategories_Name...} - which grows combinatorially
 * and forces every caller to supply a value for every filter. Specifications let each filter be
 * written once, independently, and be present or absent per request.
 */
public interface RecipeRepository extends JpaRepository<Recipe, Long>, JpaSpecificationExecutor<Recipe> {
}
