package com.recipeapi.repository;

import com.recipeapi.domain.Recipe;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@link JpaSpecificationExecutor} is what lets the filters compose.
 *
 * <p>The alternative is a derived query method per combination of filters -
 * {@code findByTitleContainingAndDifficultyAndCategories_Name...} - which grows combinatorially
 * and forces every caller to supply a value for every filter. Specifications let each filter be
 * written once, independently, and be present or absent per request.
 *
 * <p>Note what is <em>not</em> here: an {@code @EntityGraph} on {@code findAll(Specification,
 * Pageable)}. Combining a collection fetch with paging makes Hibernate give up on paging in SQL
 * and do it in memory instead - it warns
 * {@code HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory}
 * and then loads every matching row before discarding all but one page. The listing therefore
 * pages plain entities first and initialises their associations afterwards, by id.
 */
public interface RecipeRepository extends JpaRepository<Recipe, Long>, JpaSpecificationExecutor<Recipe> {

    /**
     * Initialises the categories of an already-loaded page.
     *
     * <p>The returned list is usually ignored. The point is the side effect: these recipes are
     * already managed in the same persistence context, so loading them again with the graph
     * populates the collections on the very same instances the caller is holding.
     *
     * <p>The ids come from a page that has already been narrowed and ordered in SQL, so this
     * cannot degrade into loading the whole table.
     */
    @EntityGraph(attributePaths = "categories")
    List<Recipe> findWithCategoriesByIdIn(Collection<Long> ids);

    /**
     * Same idea for the ingredient lines, including the ingredient behind each one - without
     * {@code ingredients.ingredient} in the graph, reading a line's ingredient name is a query per
     * distinct ingredient.
     *
     * <p>Kept separate from {@link #findWithCategoriesByIdIn} on purpose. Fetching two collections
     * in one query produces their cartesian product: a recipe with 3 categories and 10 ingredients
     * comes back as 30 rows, and across a page that multiplies. Two queries return
     * {@code categories + ingredients} rows instead of {@code categories * ingredients}.
     */
    @EntityGraph(attributePaths = {"ingredients", "ingredients.ingredient"})
    List<Recipe> findWithIngredientsByIdIn(Collection<Long> ids);

    /**
     * The detail endpoint, where both collections are needed at once.
     *
     * <p>Here a single query with both is fine: the cartesian product is bounded by one recipe's
     * own categories and ingredients, rather than multiplying across a page.
     */
    @EntityGraph(attributePaths = {"categories", "ingredients", "ingredients.ingredient"})
    Optional<Recipe> findWithAssociationsById(Long id);
}
