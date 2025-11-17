package com.recipeapi.web;

import com.recipeapi.domain.Difficulty;
import com.recipeapi.domain.MeasurementUnit;
import com.recipeapi.exception.NotFoundException;
import com.recipeapi.service.RecipeService;
import com.recipeapi.web.dto.RecipeIngredientResponse;
import com.recipeapi.web.dto.RecipeResponse;
import com.recipeapi.web.dto.RecipeSummaryResponse;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
        when(recipes.findAll()).thenReturn(List.of(
                new RecipeSummaryResponse(1L, "Pancakes", "Fluffy", 25, 4, Difficulty.EASY, List.of("Breakfast"))));

        mockMvc.perform(get("/api/recipes"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$", Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Pancakes"))
                .andExpect(jsonPath("$[0].totalMinutes").value(25))
                .andExpect(jsonPath("$[0].categories[0]").value("Breakfast"))
                .andExpect(jsonPath("$[0].instructions").doesNotExist())
                .andExpect(jsonPath("$[0].ingredients").doesNotExist());
    }

    @Test
    void returnsTheFullShapeForASingleRecipe() throws Exception {
        when(recipes.findById(1L)).thenReturn(pancakes());

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

    @Test
    void reportsAMissingRecipeAsAProblemDetail() throws Exception {
        when(recipes.findById(anyLong())).thenThrow(new NotFoundException("Recipe", 9999L));

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
}
