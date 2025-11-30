package com.recipeapi.web;

import com.recipeapi.service.CategoryService;
import com.recipeapi.web.dto.CategoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/categories", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Categories", description = """
        Curated reference data, seeded by a migration. A recipe attaches itself to existing \
        categories and never creates one, which is why naming an unknown category is a 400 rather \
        than something the API invents. Read-only.""")
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
    @Operation(summary = "List every category with its recipe count",
            description = """
                    A plain array rather than a paged envelope: the set is small and closed, and a \
                    caller filling a filter control wants all of it. Categories no recipe uses are \
                    included, with a count of zero - those are usually the ones worth knowing \
                    about.""")
    @ApiResponse(responseCode = "200", description = "Every category, in name order")
    @GetMapping
    public List<CategoryResponse> list() {
        return categories.findAll();
    }
}
