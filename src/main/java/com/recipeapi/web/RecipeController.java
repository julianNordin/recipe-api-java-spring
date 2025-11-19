package com.recipeapi.web;

import com.recipeapi.service.RecipeService;
import com.recipeapi.domain.Difficulty;
import com.recipeapi.web.dto.PagedResponse;
import com.recipeapi.web.dto.RecipeFilter;
import com.recipeapi.web.dto.RecipeRequest;
import com.recipeapi.web.dto.RecipeResponse;
import com.recipeapi.web.dto.RecipeSummaryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@Validated
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipes;

    public RecipeController(RecipeService recipes) {
        this.recipes = recipes;
    }

    /**
     * Supports page, size and sort - for example sort=title,asc with page=1 and size=10.
     *
     * <p>The default sort is by id, so paging is deterministic. Without a total ordering the
     * database may return rows in any order it likes, and the same row can appear on two
     * different pages while another appears on none.
     */
    @GetMapping
    public PagedResponse<RecipeSummaryResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(required = false) @Positive Integer maxMinutes,
            @RequestParam(required = false) String ingredient,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return recipes.findAll(new RecipeFilter(search, category, difficulty, maxMinutes, ingredient), pageable);
    }

    /**
     * 201 with a Location header pointing at the new resource, and the created recipe in the
     * body. Returning the body as well as the Location saves the caller an immediate follow-up
     * GET just to learn the generated id and timestamps.
     */
    @PostMapping
    public ResponseEntity<RecipeResponse> create(@Valid @RequestBody RecipeRequest request) {
        RecipeResponse created = recipes.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public RecipeResponse byId(@PathVariable Long id) {
        return recipes.findById(id);
    }

    @PutMapping("/{id}")
    public RecipeResponse update(@PathVariable Long id, @Valid @RequestBody RecipeRequest request) {
        return recipes.update(id, request);
    }

    /** 204, not 200: there is nothing meaningful to return once the recipe is gone. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        recipes.delete(id);
    }
}
