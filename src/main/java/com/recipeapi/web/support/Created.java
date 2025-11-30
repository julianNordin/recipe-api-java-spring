package com.recipeapi.web.support;

import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * 201 responses whose {@code Location} header points at the resource that was just created.
 *
 * <p>The URI is built from the current request rather than assembled from a literal path, so it
 * carries the scheme, host and port the caller actually reached - which is the whole point of
 * the header. Hard-coding {@code /api/recipes/} instead would hand a client behind a proxy or a
 * non-default port a URL that does not resolve.
 *
 * <p>This assumes the handler is a POST to the collection, so that appending {@code /{id}} to
 * the request URI names the new member. Both creating endpoints are exactly that.
 */
public final class Created {

    private Created() {
    }

    public static <T> ResponseEntity<T> at(Long id, T body) {
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).body(body);
    }
}
