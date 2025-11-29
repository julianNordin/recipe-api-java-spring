package com.recipeapi.web;

import com.recipeapi.domain.Difficulty;
import com.recipeapi.domain.MeasurementUnit;
import com.recipeapi.exception.DuplicateIngredientException;
import com.recipeapi.exception.InvalidSortException;
import com.recipeapi.exception.NotFoundException;
import com.recipeapi.exception.UnknownCategoryException;
import com.recipeapi.service.RecipeService;
import com.recipeapi.web.dto.PagedResponse;
import com.recipeapi.web.dto.RecipeIngredientRequest;
import com.recipeapi.web.dto.RecipeIngredientResponse;
import com.recipeapi.web.dto.RecipeRequest;
import com.recipeapi.web.dto.RecipeResponse;
import com.recipeapi.web.dto.RecipeSummaryResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The web layer on its own: routing, status codes, serialisation and error mapping, with the
 * service mocked out.
 *
 * <p>{@code @WebMvcTest} loads only the MVC infrastructure and the controller under test - no JPA,
 * no datasource, no container - so nothing here can accidentally depend on the database. It does
 * pick up {@code @RestControllerAdvice}, which is what makes the problem-detail assertions
 * meaningful rather than a test of an isolated handler class.
 *
 * <p>{@code @MockitoBean}, not {@code @MockBean}: the latter has been deprecated since Boot 3.4.
 */
@WebMvcTest(RecipeController.class)
class RecipeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecipeService recipes;

    @Captor
    private ArgumentCaptor<RecipeRequest> submitted;

    @Captor
    private ArgumentCaptor<Pageable> requestedPage;

    private static final String VALID_BODY = """
            {
              "title": "Pancakes",
              "description": "Fluffy American pancakes",
              "instructions": "Mix. Fry.",
              "prepMinutes": 10,
              "cookMinutes": 15,
              "servings": 4,
              "difficulty": "EASY",
              "ingredients": [{"name": "Flour", "quantity": 300.00, "unit": "GRAM"}],
              "categories": ["Breakfast"]
            }
            """;

    private static RecipeResponse pancakes() {
        return new RecipeResponse(1L, "Pancakes", "Fluffy American pancakes", "Mix. Fry.",
                10, 15, 25, 4, Difficulty.EASY,
                List.of("Breakfast"),
                List.of(new RecipeIngredientResponse(1L, "Flour", new BigDecimal("300.00"),
                        MeasurementUnit.GRAM, "g")),
                Instant.parse("2025-11-17T10:00:00Z"),
                Instant.parse("2025-11-17T10:00:00Z"));
    }

    @Test
    void listsRecipesInTheSummaryShape() throws Exception {
        when(recipes.findAll(any(), any(Pageable.class))).thenReturn(new PagedResponse<>(
                List.of(new RecipeSummaryResponse(1L, "Pancakes", "Fluffy", 25, 4, Difficulty.EASY, List.of("Breakfast"))),
                0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/recipes"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.content", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Pancakes"))
                .andExpect(jsonPath("$.content[0].totalMinutes").value(25))
                .andExpect(jsonPath("$.content[0].categories[0]").value("Breakfast"))
                .andExpect(jsonPath("$.content[0].instructions").doesNotExist())
                .andExpect(jsonPath("$.content[0].ingredients").doesNotExist());
    }

    @Test
    void returnsTheFullShapeForASingleRecipe() throws Exception {
        when(recipes.findById(1L, null)).thenReturn(pancakes());

        mockMvc.perform(get("/api/recipes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Pancakes"))
                .andExpect(jsonPath("$.instructions").value("Mix. Fry."))
                .andExpect(jsonPath("$.totalMinutes").value(25))
                .andExpect(jsonPath("$.difficulty").value("EASY"))
                .andExpect(jsonPath("$.ingredients[0].name").value("Flour"))
                .andExpect(jsonPath("$.ingredients[0].unit").value("GRAM"))
                .andExpect(jsonPath("$.ingredients[0].unitSymbol").value("g"))
                .andExpect(jsonPath("$.createdAt").value("2025-11-17T10:00:00Z"));
    }

    /**
     * The stub above requires a null servings, which already pins "absent means as written". This
     * pins the other half: the parameter binds to the value asked for rather than being dropped.
     * Every other test of this endpoint would stay green if the controller ignored it entirely.
     */
    @Test
    void passesTheRequestedServingsThroughToTheService() throws Exception {
        when(recipes.findById(1L, 6)).thenReturn(pancakes());

        mockMvc.perform(get("/api/recipes/1?servings=6")).andExpect(status().isOk());

        verify(recipes).findById(1L, 6);
    }

    @Test
    void reportsAMissingRecipeAsAProblemDetail() throws Exception {
        when(recipes.findById(anyLong(), any())).thenThrow(new NotFoundException("Recipe", 9999L));

        mockMvc.perform(get("/api/recipes/9999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:recipe-api:problem:not-found"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Recipe 9999 was not found"))
                .andExpect(jsonPath("$.instance").value("/api/recipes/9999"));
    }

    @Test
    void rejectsAnIdThatIsNotANumberWithoutReachingTheService() throws Exception {
        mockMvc.perform(get("/api/recipes/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:recipe-api:problem:invalid-parameter"));
    }

    @Test
    void createsARecipeAndPointsAtItWithALocationHeader() throws Exception {
        when(recipes.create(any())).thenReturn(pancakes());

        mockMvc.perform(post("/api/recipes").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/recipes/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Pancakes"));
    }

    /**
     * Proves the JSON actually binds onto the record, which nothing else here does. Every other
     * test stubs the service with {@code any()}, so a payload that silently deserialised into
     * nulls would still produce a green 201.
     */
    @Test
    void bindsTheJsonBodyOntoTheRequestRecord() throws Exception {
        when(recipes.create(any())).thenReturn(pancakes());

        mockMvc.perform(post("/api/recipes").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated());

        verify(recipes).create(submitted.capture());
        RecipeRequest request = submitted.getValue();

        assertThat(request.title()).isEqualTo("Pancakes");
        assertThat(request.servings()).isEqualTo(4);
        assertThat(request.difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(request.categories()).containsExactly("Breakfast");
        assertThat(request.ingredients())
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.name()).isEqualTo("Flour");
                    assertThat(line.quantity()).isEqualByComparingTo("300.00");
                    assertThat(line.unit()).isEqualTo(MeasurementUnit.GRAM);
                });
    }

    @Test
    void updatesARecipe() throws Exception {
        when(recipes.update(eq(1L), any())).thenReturn(pancakes());

        mockMvc.perform(put("/api/recipes/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updatingAMissingRecipeIsANotFoundProblem() throws Exception {
        when(recipes.update(anyLong(), any())).thenThrow(new NotFoundException("Recipe", 9999L));

        mockMvc.perform(put("/api/recipes/9999").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:recipe-api:problem:not-found"));
    }

    /** 204 with an empty body - there is nothing meaningful to return once the recipe is gone. */
    @Test
    void deletesARecipeWithNoContent() throws Exception {
        mockMvc.perform(delete("/api/recipes/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(recipes).delete(1L);
    }

    @Test
    void deletingAMissingRecipeIsANotFoundProblem() throws Exception {
        doThrow(new NotFoundException("Recipe", 9999L)).when(recipes).delete(9999L);

        mockMvc.perform(delete("/api/recipes/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Recipe 9999 was not found"));
    }

    // ---------------------------------------------------------------------------------------
    // Error responses. @WebMvcTest picks up @RestControllerAdvice, and @Valid is evaluated by
    // the real MVC pipeline, so these exercise the genuine path rather than a handler called
    // directly.
    // ---------------------------------------------------------------------------------------

    @Test
    void reportsValidationFailuresFieldByField() throws Exception {
        String invalid = """
                {
                  "title": "   ",
                  "instructions": "",
                  "servings": 0,
                  "difficulty": "EASY",
                  "ingredients": []
                }
                """;

        mockMvc.perform(post("/api/recipes").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:recipe-api:problem:validation-failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[*].field",
                        Matchers.hasItems("title", "instructions", "servings", "ingredients",
                                "prepMinutes", "cookMinutes")))
                .andExpect(jsonPath("$.errors[?(@.field == 'ingredients')].message")
                        .value("a recipe must have at least one ingredient"))
                .andExpect(jsonPath("$.errors[?(@.field == 'servings')].message")
                        .value("servings must be greater than zero"));
    }

    /** The @Valid cascade, seen from the outside: a bad line must be reported by its index. */
    @Test
    void reportsAViolationInsideAnIngredientLineByItsPath() throws Exception {
        String badLine = """
                {
                  "title": "Pancakes",
                  "instructions": "Mix. Fry.",
                  "prepMinutes": 10,
                  "cookMinutes": 15,
                  "servings": 4,
                  "difficulty": "EASY",
                  "ingredients": [{"name": "  ", "quantity": -5, "unit": "GRAM"}],
                  "categories": []
                }
                """;

        mockMvc.perform(post("/api/recipes").contentType(MediaType.APPLICATION_JSON).content(badLine))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field",
                        Matchers.hasItems("ingredients[0].name", "ingredients[0].quantity")));
    }

    @Test
    void reportsAnUnknownEnumConstantAsAMalformedBodyRatherThanAServerError() throws Exception {
        String badEnum = VALID_BODY.replace("\"EASY\"", "\"TRIVIAL\"");

        mockMvc.perform(post("/api/recipes").contentType(MediaType.APPLICATION_JSON).content(badEnum))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:recipe-api:problem:malformed-body"));
    }

    @Test
    void reportsBrokenJsonAsAMalformedBody() throws Exception {
        mockMvc.perform(post("/api/recipes").contentType(MediaType.APPLICATION_JSON).content("{\"title\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:recipe-api:problem:malformed-body"))
                .andExpect(jsonPath("$.detail").value(Matchers.not(Matchers.containsString("com.recipeapi"))));
    }

    @Test
    void surfacesTheDuplicateIngredientRuleAsABadRequest() throws Exception {
        when(recipes.create(any())).thenThrow(new DuplicateIngredientException("Flour"));

        mockMvc.perform(post("/api/recipes").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:recipe-api:problem:duplicate-ingredient"))
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("Flour")));
    }

    @Test
    void surfacesAnUnknownCategoryAsABadRequest() throws Exception {
        when(recipes.create(any())).thenThrow(new UnknownCategoryException("Nope"));

        mockMvc.perform(post("/api/recipes").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:recipe-api:problem:unknown-category"));
    }

    // ---------------------------------------------------------------------------------------
    // Paging and sorting. What matters at this tier is that the query parameters are parsed and
    // handed on intact - whether the SQL honours them is the repository's problem.
    // ---------------------------------------------------------------------------------------

    @Test
    void parsesThePagingAndSortingParametersAndPassesThemOn() throws Exception {
        when(recipes.findAll(any(), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(), 1, 5, 0, 0, false, true));

        mockMvc.perform(get("/api/recipes").param("page", "1").param("size", "5").param("sort", "title,desc"))
                .andExpect(status().isOk());

        verify(recipes).findAll(any(), requestedPage.capture());
        Pageable pageable = requestedPage.getValue();

        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        assertThat(pageable.getSort().getOrderFor("title")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("title").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    /** No parameters at all must still produce a deterministic, bounded page. */
    @Test
    void appliesTheDefaultPageSizeAndSortWhenNothingIsAsked() throws Exception {
        when(recipes.findAll(any(), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0, 0, true, true));

        mockMvc.perform(get("/api/recipes")).andExpect(status().isOk());

        verify(recipes).findAll(any(), requestedPage.capture());
        Pageable pageable = requestedPage.getValue();

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void returnsThePagingEnvelopeAlongsideTheContent() throws Exception {
        when(recipes.findAll(any(), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(), 1, 5, 12, 3, false, false));

        mockMvc.perform(get("/api/recipes").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.sort").doesNotExist());
    }

    @Test
    void reportsAnUnsortablePropertyAsABadRequestRatherThanAServerError() throws Exception {
        when(recipes.findAll(any(), any(Pageable.class)))
                .thenThrow(new InvalidSortException("DROP", List.of("id", "title")));

        mockMvc.perform(get("/api/recipes").param("sort", "DROP"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:recipe-api:problem:invalid-sort"))
                .andExpect(jsonPath("$.detail").value(Matchers.containsString("Cannot sort by 'DROP'")));
    }
}
