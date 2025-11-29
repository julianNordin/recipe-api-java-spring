package com.recipeapi.web;

import com.recipeapi.service.IngredientService;
import com.recipeapi.web.dto.IngredientRequest;
import com.recipeapi.web.dto.IngredientResponse;
import com.recipeapi.web.dto.PagedResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/ingredients")
public class IngredientController {

    private final IngredientService ingredients;

    public IngredientController(IngredientService ingredients) {
        this.ingredients = ingredients;
    }

    /**
     * The default sort is by name rather than by id: it is the order someone browsing a
     * vocabulary expects, and it is still a <em>total</em> ordering, so no row can appear on two
     * pages while another appears on none.
     */
    @GetMapping
    public PagedResponse<IngredientResponse> list(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ingredients.findAll(search, pageable);
    }

    @PostMapping
    public ResponseEntity<IngredientResponse> create(@Valid @RequestBody IngredientRequest request) {
        IngredientResponse created = ingredients.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** 204 when it is gone, 404 when it never existed, 409 while a recipe still calls for it. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        ingredients.delete(id);
    }
}
