package com.recipeapi.web;

import com.recipeapi.domain.Recipe;
import com.recipeapi.repository.RecipeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeRepository recipes;

    public RecipeController(RecipeRepository recipes) {
        this.recipes = recipes;
    }

    @GetMapping
    public List<Recipe> list() {
        return recipes.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Recipe> byId(@PathVariable Long id) {
        return recipes.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
