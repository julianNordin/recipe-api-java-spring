package com.recipeapi.web;

import com.recipeapi.service.RecipeService;
import com.recipeapi.web.dto.RecipeResponse;
import com.recipeapi.web.dto.RecipeSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/{id}")
    public ResponseEntity<RecipeResponse> byId(@PathVariable Long id) {
        return recipes.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
