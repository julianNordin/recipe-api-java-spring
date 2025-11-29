package com.recipeapi.service;

import com.recipeapi.repository.CategoryRepository;
import com.recipeapi.web.dto.CategoryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reads the category vocabulary. There is no write side: categories are curated reference data
 * seeded by a migration, and a recipe attaches itself to the existing rows rather than inventing
 * one - which is why an unknown category name is a 400 rather than something to create.
 */
@Service
public class CategoryService {

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categories.findAllWithRecipeCount().stream()
                .map(row -> new CategoryResponse(row.getId(), row.getName(), row.getRecipeCount()))
                .toList();
    }
}
