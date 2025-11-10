package com.recipeapi.service;

import com.recipeapi.repository.RecipeRepository;
import com.recipeapi.web.dto.RecipeResponse;
import com.recipeapi.web.dto.RecipeSummaryResponse;
import com.recipeapi.web.mapper.RecipeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * The transaction boundary for recipes.
 *
 * <p>This class exists because of where the mapping has to happen. Reading a recipe's categories
 * or ingredient lines needs an open persistence context, and with open-in-view disabled the
 * context closes when the transactional method returns - so entities must become DTOs *here*,
 * before the controller ever sees them. A controller that received entities would be holding
 * detached objects with uninitialised collections.
 */
@Service
public class RecipeService {

    private final RecipeRepository recipes;
    private final RecipeMapper mapper;

    public RecipeService(RecipeRepository recipes, RecipeMapper mapper) {
        this.recipes = recipes;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<RecipeSummaryResponse> findAll() {
        return recipes.findAll().stream().map(mapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public Optional<RecipeResponse> findById(Long id) {
        return recipes.findById(id).map(mapper::toResponse);
    }
}
