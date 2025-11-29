package com.recipeapi.web;

import com.recipeapi.service.CategoryService;
import com.recipeapi.web.dto.CategoryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    /**
     * A plain list rather than a paged envelope, and deliberately.
     *
     * <p>Categories are a closed, curated set - ten of them, seeded by a migration - and the
     * caller is almost always populating a filter control, which needs all of them. Paging here
     * would make every client loop to reassemble a list that fits in one response.
     */
    @GetMapping
    public List<CategoryResponse> list() {
        return categories.findAll();
    }
}
