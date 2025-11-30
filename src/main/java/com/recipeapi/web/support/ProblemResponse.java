package com.recipeapi.web.support;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.core.annotation.AliasFor;
import org.springframework.http.ProblemDetail;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An {@link ApiResponse} for an error, already described as an RFC 9457 problem detail.
 *
 * <p>Every error this API returns has the same body, so declaring the media type and schema on
 * each one is twelve copies of a single fact - and the failure mode of a copy going missing is
 * silent: springdoc falls back to describing the error with the <em>operation's success schema</em>,
 * so the spec claims a 404 returns a recipe and a generated client parses the wrong shape on
 * exactly the paths that matter.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(ProblemResponses.class)
@ApiResponse(content = @Content(mediaType = "application/problem+json",
        schema = @Schema(implementation = ProblemDetail.class)))
public @interface ProblemResponse {

    @AliasFor(annotation = ApiResponse.class, attribute = "responseCode")
    String responseCode();

    @AliasFor(annotation = ApiResponse.class, attribute = "description")
    String description();
}
