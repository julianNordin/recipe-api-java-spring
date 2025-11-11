package com.recipeapi.web;

import com.recipeapi.service.RecipeService;
import com.recipeapi.web.dto.RecipeRequest;
import com.recipeapi.web.dto.RecipeResponse;
import com.recipeapi.web.dto.RecipeSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipes;

    public RecipeController(RecipeService recipes) {
        this.recipes = recipes;
    }

    @GetMapping
    public List<RecipeSummaryResponse> list() {
        return recipes.findAll();
    }

    /**
     * 201 with a Location header pointing at the new resource, and the created recipe in the
     * body. Returning the body as well as the Location saves the caller an immediate follow-up
     * GET just to learn the generated id and timestamps.
     */
    @PostMapping
    public ResponseEntity<RecipeResponse> create(@RequestBody RecipeRequest request) {
        RecipeResponse created = recipes.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecipeResponse> byId(@PathVariable Long id) {
        return recipes.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
