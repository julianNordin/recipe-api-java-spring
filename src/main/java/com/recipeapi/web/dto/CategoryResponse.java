package com.recipeapi.web.dto;

/**
 * A category together with how many recipes are filed under it.
 *
 * <p>The count is the reason this endpoint exists at all - a bare list of ten seeded names would
 * tell a client nothing it could not hard-code.
 */
public record CategoryResponse(Long id, String name, long recipeCount) {
}
