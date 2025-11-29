package com.recipeapi.service;

import com.recipeapi.domain.Ingredient;
import com.recipeapi.exception.IngredientAlreadyExistsException;
import com.recipeapi.exception.IngredientInUseException;
import com.recipeapi.exception.InvalidSortException;
import com.recipeapi.exception.NotFoundException;
import com.recipeapi.repository.IngredientRepository;
import com.recipeapi.web.dto.IngredientRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link IngredientService} with its repository mocked.
 *
 * <p>Most of what this class asserts is an <em>interaction</em>, because that is the only place
 * the rules live. "Rejected the duplicate" and "created a second Flour" produce indistinguishable
 * exceptions-or-responses on their own; what separates them is whether {@code save} was called.
 * Whether the SQL underneath works is a different question, answered against real Postgres.
 */
@ExtendWith(MockitoExtension.class)
class IngredientServiceTest {

    @Mock
    private IngredientRepository ingredients;

    @InjectMocks
    private IngredientService service;

    @Captor
    private ArgumentCaptor<String> searchedName;

    @Test
    void createsAnIngredientThatDoesNotExistYet() {
        when(ingredients.existsByNameIgnoreCase("Saffron")).thenReturn(false);
        when(ingredients.save(any(Ingredient.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(service.create(new IngredientRequest("  Saffron  ")).name()).isEqualTo("Saffron");

        ArgumentCaptor<Ingredient> saved = ArgumentCaptor.forClass(Ingredient.class);
        verify(ingredients).save(saved.capture());
        assertThat(saved.getValue().getName())
                .as("the name is trimmed before it is stored, not only before it is compared")
                .isEqualTo("Saffron");
    }

    /**
     * The rule is "already exists", not "already exists with this exact spelling" - the database
     * defines uniqueness on {@code lower(name)}. Asserting only the exception would not separate
     * this from an implementation that rejects afterwards, having already written the row, so the
     * assertion that carries the rule is {@code never()).save}.
     */
    @Test
    void rejectsANameThatAlreadyExistsInAnotherCaseWithoutWritingAnything() {
        when(ingredients.existsByNameIgnoreCase("flour")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new IngredientRequest("flour")))
                .isInstanceOf(IngredientAlreadyExistsException.class)
                .hasMessageContaining("flour");

        verify(ingredients, never()).save(any(Ingredient.class));
    }

    @Test
    void refusesToDeleteAnIngredientARecipeStillUses() {
        when(ingredients.findById(1L)).thenReturn(Optional.of(new Ingredient("Flour")));
        when(ingredients.isUsedByAnyRecipe(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(IngredientInUseException.class)
                .hasMessageContaining("Flour");

        verify(ingredients, never()).delete(any(Ingredient.class));
    }

    @Test
    void deletesAnIngredientNobodyUses() {
        Ingredient saffron = new Ingredient("Saffron");
        when(ingredients.findById(1L)).thenReturn(Optional.of(saffron));
        when(ingredients.isUsedByAnyRecipe(1L)).thenReturn(false);

        service.delete(1L);

        verify(ingredients).delete(saffron);
    }

    /**
     * A missing ingredient is a 404, and the usage check must not be what decides it: an id that
     * does not exist is used by nothing, so checking usage first would report 204 for a delete
     * that deleted nothing.
     */
    @Test
    void reportsAMissingIngredientRatherThanDeletingNothing() {
        when(ingredients.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(9999L)).isInstanceOf(NotFoundException.class);

        verify(ingredients, never()).delete(any(Ingredient.class));
    }

    /**
     * An absent or blank search must not become {@code like '%%'} - that is a full scan wearing a
     * search's clothes, and it would return the right answer while costing the wrong amount.
     */
    @Test
    void listsEverythingWithoutSearchingWhenNoSearchTermIsGiven() {
        when(ingredients.findAll(any(Pageable.class))).thenReturn(Page.empty());

        service.findAll("   ", PageRequest.of(0, 20));

        verify(ingredients, never()).findByNameContainingIgnoreCase(anyString(), any(Pageable.class));
    }

    @Test
    void trimsTheSearchTermBeforeQuerying() {
        when(ingredients.findByNameContainingIgnoreCase(anyString(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.findAll("  sug  ", PageRequest.of(0, 20));

        verify(ingredients).findByNameContainingIgnoreCase(searchedName.capture(), any(Pageable.class));
        assertThat(searchedName.getValue()).isEqualTo("sug");
    }

    /**
     * The whitelist runs before the query, not after it fails. Spring Data would otherwise raise
     * PropertyReferenceException from inside query construction, which reaches the caller as a
     * 500 for an obviously bad request.
     */
    @Test
    void rejectsAnUnknownSortPropertyBeforeTouchingTheRepository() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("DROP"));

        assertThatThrownBy(() -> service.findAll(null, pageable))
                .isInstanceOf(InvalidSortException.class)
                .hasMessageContaining("Sortable properties are: id, name");

        verify(ingredients, never()).findAll(any(Pageable.class));
        verify(ingredients, never()).findByNameContainingIgnoreCase(anyString(), any(Pageable.class));
    }

    @Test
    void doesNotCheckUsageForAnIngredientThatDoesNotExist() {
        when(ingredients.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(9999L)).isInstanceOf(NotFoundException.class);

        verify(ingredients, never()).isUsedByAnyRecipe(anyLong());
    }
}
