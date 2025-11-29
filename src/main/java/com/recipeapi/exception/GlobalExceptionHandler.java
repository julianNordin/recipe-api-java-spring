package com.recipeapi.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Turns exceptions into RFC 9457 problem details.
 *
 * <p>{@link ProblemDetail} is built into Spring 6, so this needs no extra dependency. Returning
 * one sets both the status and the {@code application/problem+json} content type, which means
 * every error from this API has the same shape instead of the ad-hoc JSON Boot produces by
 * default.
 *
 * <p>The {@code type} URIs are URNs rather than https URLs. An https type is only better if
 * something is actually published at it; inventing a URL that 404s would be worse than a URN
 * that is honest about being an identifier.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String TYPE_PREFIX = "urn:recipe-api:problem:";

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Not Found", "not-found", exception.getMessage());
    }

    @ExceptionHandler(UnknownCategoryException.class)
    public ProblemDetail handleUnknownCategory(UnknownCategoryException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Unknown Category", "unknown-category", exception.getMessage());
    }

    @ExceptionHandler(DuplicateIngredientException.class)
    public ProblemDetail handleDuplicateIngredient(DuplicateIngredientException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Duplicate Ingredient", "duplicate-ingredient", exception.getMessage());
    }

    /**
     * 409 rather than 400: the request is well formed and the ingredient exists. What it
     * conflicts with is other data that also exists, which is precisely what 409 is for.
     */
    @ExceptionHandler(IngredientInUseException.class)
    public ProblemDetail handleIngredientInUse(IngredientInUseException exception) {
        return problem(HttpStatus.CONFLICT, "Ingredient In Use", "ingredient-in-use", exception.getMessage());
    }

    @ExceptionHandler(IngredientAlreadyExistsException.class)
    public ProblemDetail handleIngredientAlreadyExists(IngredientAlreadyExistsException exception) {
        return problem(HttpStatus.CONFLICT, "Ingredient Already Exists", "ingredient-exists", exception.getMessage());
    }

    /**
     * Constraints on query parameters, e.g. {@code ?maxMinutes=-5}.
     *
     * <p>A different exception from the request-body case: parameter constraints are checked by
     * the @Validated proxy and raise ConstraintViolationException, not
     * MethodArgumentNotValidException. Handling only the latter leaves this one falling through
     * to a 500.
     *
     * <p>The property path is trimmed to its last segment, because the raw value reads
     * {@code list.maxMinutes} - the method name is an implementation detail the caller cannot act
     * on.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleParameterViolation(ConstraintViolationException exception) {
        List<Map<String, String>> errors = exception.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "parameter", lastSegmentOf(violation),
                        "message", violation.getMessage()))
                .sorted(Comparator.comparing(error -> error.get("parameter")))
                .toList();

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid Parameter", "invalid-parameter",
                "One or more query parameters are invalid. See 'errors' for details.");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static String lastSegmentOf(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot < 0 ? path : path.substring(lastDot + 1);
    }

    @ExceptionHandler(InvalidSortException.class)
    public ProblemDetail handleInvalidSort(InvalidSortException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid Sort", "invalid-sort", exception.getMessage());
    }

    /**
     * Bean-validation failures, reported field by field.
     *
     * <p>The default Spring response says only "validation failed", which tells a caller nothing
     * about which of fifteen fields to fix. The {@code errors} array is an RFC 9457 extension
     * member - the spec explicitly allows additional members, and this is what they are for.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid")))
                .sorted(Comparator.comparing(error -> error.get("field")))
                .toList();

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation Failed", "validation-failed",
                "The request body failed validation. See 'errors' for the specific fields.");
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * Malformed JSON, or a value that cannot become the target type - most often an enum
     * constant that does not exist, like {@code "difficulty": "TRIVIAL"}.
     *
     * <p>The exception message is not echoed back: it can carry the parser's view of the payload
     * and the fully-qualified names of internal classes.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        log.debug("Rejected an unreadable request body", exception);
        return problem(HttpStatus.BAD_REQUEST, "Malformed Request Body", "malformed-body",
                "The request body could not be read. Check that it is valid JSON and that every "
                        + "enum value is one of the documented constants.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid Parameter", "invalid-parameter",
                "'%s' is not a valid value for parameter '%s'.".formatted(exception.getValue(), exception.getName()));
    }

    /**
     * A constraint the service did not anticipate. 409 rather than 500 - it is a conflict with
     * existing data, not a broken server - but it is logged at error level because reaching here
     * means a rule exists only in the schema and should be checked earlier.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException exception) {
        log.error("A database constraint was violated that the service did not check first", exception);
        return problem(HttpStatus.CONFLICT, "Conflict", "conflict",
                "The request conflicts with data that already exists.");
    }

    private ProblemDetail problem(HttpStatus status, String title, String slug, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(TYPE_PREFIX + slug));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
