package com.recipeapi.web.support;

import com.recipeapi.exception.InvalidSortException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit tests - the whitelist is just a set membership check and needs no context. */
class RecipeSortsTest {

    @ParameterizedTest
    @ValueSource(strings = {"id", "title", "servings", "difficulty", "prepMinutes", "cookMinutes",
            "createdAt", "updatedAt"})
    void acceptsEverySortablePropertyItAdvertises(String property) {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(property));

        assertThatCode(() -> RecipeSorts.validate(pageable)).doesNotThrowAnyException();
    }

    @Test
    void returnsThePageableUnchangedWhenTheSortIsValid() {
        Pageable pageable = PageRequest.of(2, 15, Sort.by(Sort.Direction.DESC, "title"));

        assertThat(RecipeSorts.validate(pageable)).isSameAs(pageable);
    }

    @Test
    void acceptsAnUnsortedRequest() {
        assertThatCode(() -> RecipeSorts.validate(PageRequest.of(0, 20))).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnUnknownPropertyAndSaysWhatIsAllowed() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("DROP"));

        assertThatThrownBy(() -> RecipeSorts.validate(pageable))
                .isInstanceOf(InvalidSortException.class)
                .hasMessageContaining("Cannot sort by 'DROP'")
                .hasMessageContaining("title");
    }

    /**
     * Multi-property sorts are the case a naive check misses: validating only the first order
     * would let the second one through to blow up in query construction.
     */
    @Test
    void rejectsAnUnknownPropertyThatFollowsAValidOne() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("title").and(Sort.by("nonsense")));

        assertThatThrownBy(() -> RecipeSorts.validate(pageable))
                .isInstanceOf(InvalidSortException.class)
                .hasMessageContaining("nonsense");
    }

    /**
     * totalMinutes reads like a column but is computed in Java, so ORDER BY has nothing to name.
     * Accepting it would produce a 500 from deep inside query construction.
     */
    @Test
    void rejectsTotalMinutesBecauseItIsDerivedRatherThanStored() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("totalMinutes"));

        assertThatThrownBy(() -> RecipeSorts.validate(pageable)).isInstanceOf(InvalidSortException.class);
    }

    @Test
    void advertisesSortablePropertiesInAStableOrder() {
        assertThat(RecipeSorts.sortable())
                .isSorted()
                .contains("id", "title")
                .doesNotContain("totalMinutes");
    }
}
