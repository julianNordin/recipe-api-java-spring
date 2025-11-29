package com.recipeapi.repository;

import com.recipeapi.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByNameIgnoreCase(String name);

    /**
     * Resolves a batch of category names in one query.
     *
     * <p>Written out as JPQL because Spring Data's derived-query grammar has no way to combine
     * {@code In} with {@code IgnoreCase} - {@code findByNameInIgnoreCase} is not a thing. Callers
     * pass already-lower-cased names, which keeps the lower() on one side only.
     */
    @Query("select c from Category c where lower(c.name) in :lowerCasedNames")
    List<Category> findAllByLowerCasedNameIn(@Param("lowerCasedNames") Collection<String> lowerCasedNames);

    /**
     * Every category with the number of recipes filed under it, in one query.
     *
     * <p><strong>LEFT JOIN, and count(recipe) rather than count(*).</strong> Both halves matter. An
     * inner join would drop every category nobody has used yet - and a category with no recipes is
     * exactly the row a client filtering by category most needs to know about. The left join keeps
     * it, producing one row with a null recipe; {@code count(*)} would count that row and report 1,
     * while {@code count(recipe)} ignores the null and correctly reports 0. This is the reason
     * {@code Category.recipes} exists at all: counting from the Recipe side cannot see a category
     * that no recipe points at.
     *
     * <p>An interface projection rather than a constructor expression, so this stays free of
     * {@code web.dto} types - a repository should not import the shape of an HTTP response.
     */
    @Query("""
            select c.id as id, c.name as name, count(recipe) as recipeCount
            from Category c
            left join c.recipes recipe
            group by c.id, c.name
            order by c.name
            """)
    List<CategoryWithRecipeCount> findAllWithRecipeCount();

    /** The row shape of {@link #findAllWithRecipeCount()}; the aliases above are the binding. */
    interface CategoryWithRecipeCount {
        Long getId();

        String getName();

        long getRecipeCount();
    }
}
