package com.recipeapi.web;

import com.recipeapi.service.IngredientService;
import com.recipeapi.web.dto.IngredientRequest;
import com.recipeapi.web.dto.IngredientResponse;
import com.recipeapi.web.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
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
@RequestMapping(value = "/api/ingredients", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Ingredients", description = """
        The shared ingredient vocabulary. Recipes attach themselves to these rows and create one \
        only for a name never seen before, so this endpoint is for curating the vocabulary itself. \
        Names are unique ignoring case.""")
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
    @Operation(summary = "List ingredients, searchable and paged",
            description = "Sorted by name by default. Sortable properties are id and name.")
    @ApiResponse(responseCode = "200", description = "A page of ingredients")
    @ApiResponse(responseCode = "400", description = "An unsortable sort property",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public PagedResponse<IngredientResponse> list(
            @Parameter(description = "Case-insensitive substring of the ingredient name")
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ingredients.findAll(search, pageable);
    }

    @Operation(summary = "Add an ingredient to the vocabulary",
            description = """
                    Creating one that already exists is a conflict rather than a quiet success. \
                    Matching ignores case, because the database's uniqueness rule does.""")
    @ApiResponse(responseCode = "201", description = "Created; Location points at the new ingredient")
    @ApiResponse(responseCode = "400", description = "The name is blank or too long",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "An ingredient with that name already exists",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
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
    @Operation(summary = "Delete an ingredient nothing uses",
            description = """
                    Refused with 409 while any recipe still calls for it. Deleting the ingredient \
                    out from under those recipes would be silent data loss, so remove it from them \
                    first.""")
    @ApiResponse(responseCode = "204", description = "Deleted; no body")
    @ApiResponse(responseCode = "404", description = "No ingredient has that id",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "A recipe still uses this ingredient",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        ingredients.delete(id);
    }
}
