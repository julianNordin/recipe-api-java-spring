package com.recipeapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The document-level half of the spec - everything springdoc cannot read off the controllers.
 *
 * <p>springdoc generates the paths, the schemas and the parameter types by reflecting over the
 * mappings and the records, which is why they cannot drift from the code. The title, the version
 * and the prose below are the part nothing can infer, so they live here.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI recipeApi() {
        return new OpenAPI().info(new Info()
                .title("Recipe API")
                .version("1.0")
                .description("""
                        A recipe catalogue: recipes with their ingredient lines and categories, \
                        searchable and filterable, plus the shared ingredient and category \
                        vocabularies they draw on.

                        Errors are RFC 9457 problem details - `application/problem+json` with a \
                        `type` URN identifying the rule that was broken, and, where it helps, an \
                        `errors` array naming the offending fields or parameters.

                        Quantities are stored to two decimals. `GET /api/recipes/{id}?servings=N` \
                        restates them for a different number of servings without changing the \
                        stored recipe."""));
    }
}
