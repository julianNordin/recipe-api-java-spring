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
}
