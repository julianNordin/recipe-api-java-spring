package com.recipeapi.web;

import com.recipeapi.service.RecipeService;
import com.recipeapi.domain.Difficulty;
import com.recipeapi.web.dto.PagedResponse;
import com.recipeapi.web.dto.RecipeFilter;
import com.recipeapi.web.dto.RecipeRequest;
import com.recipeapi.web.dto.RecipeResponse;
import com.recipeapi.web.dto.RecipeSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
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
@RequestMapping(value = "/api/recipes", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Recipes", description = """
        Recipes, their ingredient lines and the categories they are filed under. Every error is an \
        RFC 9457 problem detail; the `type` URN names the rule that was broken.""")
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
    @Operation(summary = "List recipes, filtered and paged",
            description = """
                    Every filter is independently optional and they combine with AND. The summary \
                    shape omits instructions and ingredient lines - fetch a single recipe for \
                    those. Page size is capped at 100.""")
    @ApiResponse(responseCode = "200", description = "A page of matching recipes")
    @ApiResponse(responseCode = "400", description =
            "An unsortable sort property, or a filter value that is not valid",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public PagedResponse<RecipeSummaryResponse> list(
            @Parameter(description = "Case-insensitive substring of the title or description")
            @RequestParam(required = false) String search,
            @Parameter(description = "Category name, matched ignoring case")
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Difficulty difficulty,
            @Parameter(description = "Upper bound on prep + cook minutes, inclusive")
            @RequestParam(required = false) @Positive Integer maxMinutes,
            @Parameter(description = "Ingredient name, matched ignoring case")
            @RequestParam(required = false) String ingredient,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return recipes.findAll(new RecipeFilter(search, category, difficulty, maxMinutes, ingredient), pageable);
    }

    /**
     * 201 with a Location header pointing at the new resource, and the created recipe in the
     * body. Returning the body as well as the Location saves the caller an immediate follow-up
     * GET just to learn the generated id and timestamps.
     */
    @Operation(summary = "Create a recipe",
            description = """
                    Ingredients are named rather than referenced by id: an unknown name creates the \
                    ingredient, a known one - ignoring case - reuses the existing row. Categories \
                    must already exist. The response carries the generated id and timestamps, so \
                    no follow-up GET is needed.""")
    @ApiResponse(responseCode = "201", description = "Created; Location points at the new recipe")
    @ApiResponse(responseCode = "400", description =
            "Validation failed, a category does not exist, or an ingredient is listed twice",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    public ResponseEntity<RecipeResponse> create(@Valid @RequestBody RecipeRequest request) {
        RecipeResponse created = recipes.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * {@code ?servings=6} restates every ingredient quantity for that many servings.
     *
     * <p>A read, not an edit: the stored recipe is untouched, and omitting the parameter returns
     * it as written. The bounds mirror the ones on the field itself, so the API cannot be asked
     * to scale to a number it would refuse to store.
     */
    @Operation(summary = "Fetch one recipe, optionally rescaled",
            description = """
                    The full shape, including instructions and every ingredient line. Adding \
                    `?servings=N` restates the quantities for that many servings without changing \
                    the stored recipe; times are not scaled, because doubling a recipe does not \
                    double how long it bakes.""")
    @ApiResponse(responseCode = "200", description = "The recipe, scaled if servings was given")
    @ApiResponse(responseCode = "400", description = "servings is not a positive number up to 100",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No recipe has that id",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}")
    public RecipeResponse byId(
            @PathVariable Long id,
            @Parameter(description = "Restate quantities for this many servings, 1 to 100")
            @RequestParam(required = false)
            @Positive(message = "servings must be greater than zero")
            @Max(value = 100, message = "servings cannot exceed 100") Integer servings) {
        return recipes.findById(id, servings);
    }

    @Operation(summary = "Replace a recipe",
            description = """
                    A full replacement, as PUT implies: the payload is the whole recipe, not a \
                    patch. The ingredient list is replaced outright - lines dropped from it are \
                    deleted.""")
    @ApiResponse(responseCode = "200", description = "The recipe as it now stands")
    @ApiResponse(responseCode = "400", description =
            "Validation failed, a category does not exist, or an ingredient is listed twice",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "No recipe has that id",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @PutMapping("/{id}")
    public RecipeResponse update(@PathVariable Long id, @Valid @RequestBody RecipeRequest request) {
        return recipes.update(id, request);
    }

    /** 204, not 200: there is nothing meaningful to return once the recipe is gone. */
    @Operation(summary = "Delete a recipe",
            description = """
                    Takes the recipe's ingredient lines and category links with it, and leaves the \
                    shared ingredient and category rows alone.""")
    @ApiResponse(responseCode = "204", description = "Deleted; no body")
    @ApiResponse(responseCode = "404", description = "No recipe has that id",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        recipes.delete(id);
    }
}
