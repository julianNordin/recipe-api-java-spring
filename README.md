# recipe-api-java-spring

[![CI](https://github.com/julianNordin/recipe-api-java-spring/actions/workflows/ci.yml/badge.svg)](https://github.com/julianNordin/recipe-api-java-spring/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-informational)](https://adoptium.net/temurin/releases/?version=21)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5.16-informational)](https://spring.io/projects/spring-boot)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)

A REST API for recipes, their ingredients and their categories, built with **Java 21** and
**Spring Boot 3.5**. Recipes can be searched, filtered, sorted and paged; ingredients are shared
across recipes rather than duplicated; quantities rescale to any serving count on the fly.

**Status:** ✅ Complete — all 18 phases. See [Roadmap](#roadmap) below.

## Why this project

The previous project in this series kept the application deliberately trivial so that the
pipeline around it could be the substance. This one is the opposite: **the application code is
the point.** It exists to be read.

So the interesting parts are the ones a CRUD tutorial skips — a join table with attributes on it,
a query layer composed from `Specification`s rather than a wall of derived method names, a fetch
strategy chosen by measuring query counts rather than by adding annotations until it looks right,
and a test suite with four tiers that each assert something the others structurally cannot.

Several of the decisions below exist because the obvious version was written first and observed
to be wrong. Those are called out where they happen.

## Tech stack

| Layer | Choice |
|---|---|
| Language | Java 21 (records, text blocks, sealed-free plain domain classes) |
| Framework | Spring Boot 3.5.16 — Web MVC, Data JPA, Validation, Actuator |
| Persistence | PostgreSQL 16, Hibernate 6.6, Flyway migrations, `ddl-auto=validate` |
| Query layer | Spring Data JPA repositories + `JpaSpecificationExecutor` |
| Build | Maven, via the committed wrapper (`./mvnw`) |
| Testing | JUnit 5, Mockito, MockMvc, AssertJ, Testcontainers |
| Docs | springdoc-openapi 2.9.0 → OpenAPI 3.1 + Swagger UI, served from the jar |
| CI | GitHub Actions |

## Architecture

```
                            +---------------------------------------------+
  client --- HTTP/JSON -->  |  web/                                       |
                            |    RecipeController                         |
  RFC 9457 problem  <------ |    IngredientController                     |
  detail on every error     |    CategoryController                       |
                            |      +- @Valid on the request records       |
                            |      +- @RestControllerAdvice -> RFC 9457   |
                            +----------------------+----------------------+
                                                   |
                            +----------------------+----------------------+
                            |  service/    the @Transactional boundary    |
                            |    RecipeService   IngredientService        |
                            |    CategoryService                          |
                            |      +- RecipeMapper   entity -> DTO, run   |
                            |      |                 in the transaction   |
                            |      +- ServingsScaler                      |
                            +----------------------+----------------------+
                                                   |
                            +----------------------+----------------------+
                            |  repository/   Spring Data JPA              |
                            |    RecipeRepository + Specifications        |
                            |    IngredientRepository  CategoryRepository |
                            +----------------------+----------------------+
                                                   |  Hibernate 6.6
                            +----------------------+----------------------+
                            |  PostgreSQL 16 - container, named volume    |
                            |  schema owned by Flyway; Hibernate only     |
                            |  validates it, never writes it              |
                            +---------------------------------------------+
```

**Mapping happens in the service, not the controller,** and that is load-bearing rather than
tidy. `spring.jpa.open-in-view` is off, so the persistence context closes when the transactional
method returns. Map any later than that and Jackson meets a detached entity:

```
HttpMessageNotWritableException: failed to lazily initialize a collection of role:
com.recipeapi.domain.Recipe.categories: could not initialize proxy - no Session
```

DTOs alone do not fix that. Only plain data leaves a transaction here.

## Data model

```
   recipes                        recipe_ingredients                ingredients
  +------------------+           +------------------------+        +--------------+
  | id               |<----+-----| recipe_id          FK  |   +--->| id           |
  | title            |     |     | ingredient_id      FK  |---+    | name         |
  | description      |     |     | quantity  NUMERIC(10,2)|        +--------------+
  | instructions     |     |     | unit      VARCHAR(20)  |
  | prep_minutes     |     |     +------------------------+     unique index on
  | cook_minutes     |     |      UNIQUE (recipe_id,            lower(name), so
  | servings         |     |              ingredient_id)        "Flour" and "flour"
  | difficulty       |     |      ON DELETE CASCADE (recipe)    are one ingredient
  | created_at       |     |      NO CASCADE        (ingredient)
  | updated_at       |     |
  +------------------+     |     recipe_categories                categories
                           |    +------------------------+       +--------------+
                           +----| recipe_id          FK  |  +--->| id           |
                                | category_id        FK  |--+    | name  UNIQUE |
                                +------------------------+       +--------------+
                                 PK (recipe_id, category_id)
                                 ON DELETE CASCADE both sides
```

Four decisions in that picture carry most of the weight:

- **`recipe_ingredients` is an entity, not a plain join table.** It carries `quantity` and
  `unit`, which is exactly the case `@ManyToMany` cannot express. `UNIQUE (recipe_id,
  ingredient_id)` is what stops one recipe listing flour twice.

- **Ingredient uniqueness is `lower(name)`, enforced by a functional index.** A plain
  `UNIQUE (name)` would let "Flour" and "flour" coexist as two rows that mean one thing. This is
  the rule behind ingredient reuse: `POST` a recipe naming `"flour"` and it resolves to the
  existing row and comes back as **"Flour"**.

- **`fk_recipe_ingredients_ingredient` deliberately has no `ON DELETE CASCADE`,** unlike the
  other three foreign keys. Deleting an in-use ingredient must *fail* — cascading would turn one
  `DELETE` into silent data loss across every recipe that used it. That is what makes
  `DELETE /api/ingredients/{id}` answer **409** rather than quietly gutting recipes.

- **Total time is derived in Java, never stored.** It cannot drift from its two columns — but it
  also means `?maxMinutes=` has no column to compare against, so that predicate restates
  `prep + cook` as SQL. It is filterable and, for the same reason, not sortable.

## Getting started

**Prerequisites:** JDK 21 and Docker. Maven is not needed — the committed wrapper fetches it.

```bash
git clone https://github.com/julianNordin/recipe-api-java-spring.git
cd recipe-api-java-spring
docker compose up -d db          # PostgreSQL 16, with a pg_isready healthcheck
./mvnw spring-boot:run           # http://localhost:8080
```

Flyway creates the schema and seeds the reference data on first start: **10 categories** and
**24 ingredients**. Credentials default to `recipes` / `recipes` / `recipes`; copy
`.env.example` to `.env` to change them.

- **Swagger UI** — <http://localhost:8080/swagger-ui.html>
- **OpenAPI 3.1 spec** — <http://localhost:8080/v3/api-docs>
- **Health** — <http://localhost:8080/actuator/health>

The UI is served entirely out of the jar. Loading it issues nine requests, eight to `localhost`
and one inline `data:` URI — no CDN, no `petstore.swagger.io`, no validator call.

### A walk through the API

```bash
# Create a recipe. Ingredients are named, not referenced by id: unknown names are created,
# known ones - ignoring case - reuse the existing row. 201 with a Location header.
curl -i -X POST localhost:8080/api/recipes -H 'Content-Type: application/json' -d '{
  "title": "Pancakes", "description": "A fluffy weekend stack",
  "instructions": "Whisk the batter, rest it, fry until golden.",
  "prepMinutes": 10, "cookMinutes": 15, "servings": 4, "difficulty": "EASY",
  "ingredients": [
    {"name": "Flour", "quantity": 300, "unit": "GRAM"},
    {"name": "Milk",  "quantity": 400, "unit": "MILLILITRE"}
  ],
  "categories": ["Breakfast"]
}'

# Search, filter and page. Every parameter is independently optional and they combine with AND.
curl -s "localhost:8080/api/recipes?search=pan&category=Breakfast&maxMinutes=30&page=0&size=10"

# Rescale to eight servings. A read - the stored recipe is untouched, and 300 g becomes 600.00 g.
curl -s "localhost:8080/api/recipes/1?servings=8"

# Refuse to sort by something that is not a column: 400 problem detail, not the 500
# Spring Data would otherwise throw during query construction.
curl -i "localhost:8080/api/recipes?sort=DROP"

# Refuse to delete an ingredient a recipe still calls for: 409, and the recipe keeps its Flour.
curl -i -X DELETE localhost:8080/api/ingredients/1

curl -s localhost:8080/actuator/health          # {"status":"UP"}
```

## Endpoints

| Method | Path | Behaviour |
|---|---|---|
| `GET` | `/api/recipes` | Paged summaries; `search` / `category` / `difficulty` / `maxMinutes` / `ingredient` |
| `POST` | `/api/recipes` | 201 + `Location`; creates or reuses each named ingredient |
| `GET` | `/api/recipes/{id}` | Full recipe; `?servings=N` rescales every quantity |
| `PUT` | `/api/recipes/{id}` | Full replacement — ingredient lines dropped from the payload are deleted |
| `DELETE` | `/api/recipes/{id}` | 204; takes its ingredient lines and category links with it |
| `GET` | `/api/ingredients` | Paged, `?search=` on the name, sorted by name by default |
| `POST` | `/api/ingredients` | 201, or **409** if the name already exists in any case |
| `DELETE` | `/api/ingredients/{id}` | 204, or **409** while any recipe still uses it |
| `GET` | `/api/categories` | Every category with its recipe count, including the zeroes |

The summary shape returned by `GET /api/recipes` deliberately omits `instructions` and the
ingredient lines. Listing a hundred recipes should not ship a hundred sets of instructions.

### Query parameters

`GET /api/recipes`

| Parameter | Type | Notes |
|---|---|---|
| `search` | string | Case-insensitive substring of **title or description** |
| `category` | string | Category name, matched ignoring case |
| `difficulty` | enum | `EASY` · `MEDIUM` · `HARD` |
| `maxMinutes` | int > 0 | Upper bound on `prepMinutes + cookMinutes`, **inclusive** |
| `ingredient` | string | Ingredient name, ignoring case — matched through two joins |
| `page` | int | 0-based, default `0` |
| `size` | int | Default `20`, **hard-capped at 100** |
| `sort` | string | `property,asc\|desc`. Default `id,asc` |

Sortable: `id`, `title`, `servings`, `difficulty`, `prepMinutes`, `cookMinutes`, `createdAt`,
`updatedAt`. Anything else is a **400**, not a 500 — and the message lists what is allowed.

`GET /api/recipes/{id}` takes `servings` (1–100). `GET /api/ingredients` takes `search`, `page`,
`size` and `sort` (`id`, `name`).

Two of those defaults are not cosmetic. **`size` is capped** because `?size=100000` is otherwise a
one-request denial of service — the query returns and the entire result set is mapped into memory
before a byte is written. And the **default sort is a total ordering**: without one the database
may return rows in any order it likes, so the same row can appear on two pages while another
appears on none.

## Errors

Every error is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem detail, served as
`application/problem+json`. The `type` is a URN naming the rule that was broken — a URN rather
than an `https://` URL, because inventing a documentation URL that 404s is worse than an
identifier honest about being one.

| Status | `type` (prefixed `urn:recipe-api:problem:`) | Raised when |
|---|---|---|
| 400 | `validation-failed` | Bean Validation failed; carries an `errors[]` of field and message |
| 400 | `invalid-parameter` | A query parameter or path variable is out of range or the wrong type |
| 400 | `invalid-sort` | `sort` names a property that is not on the whitelist |
| 400 | `malformed-body` | Unparseable JSON, or an enum constant that does not exist |
| 400 | `unknown-category` | A recipe named a category that does not exist |
| 400 | `duplicate-ingredient` | The same ingredient appears twice in one recipe |
| 404 | `not-found` | No recipe or ingredient has that id |
| 409 | `ingredient-exists` | Creating an ingredient whose name already exists, ignoring case |
| 409 | `ingredient-in-use` | Deleting an ingredient a recipe still calls for |
| 409 | `conflict` | A database constraint was reached that a rule should have caught first |

Validation errors report the **property path**, so a violation inside an ingredient line arrives
as `ingredients[0].quantity` rather than as an unlocated complaint. The malformed-body handler
deliberately does not echo Jackson's message, which carries its view of the payload and
fully-qualified internal class names.

## Testing

```bash
./mvnw verify        # both tiers; requires Docker
```

**181 tests**, and `verify` prints a `Results:` block for each tier:

| Tier | Plugin | Count | What it can assert that the others cannot |
|---|---|---|---|
| `*Test` | Surefire | **133** | Mockito service tests, `@WebMvcTest` slices, `@DataJpaTest` on Testcontainers |
| `*IT` | Failsafe | **48** | `@SpringBootTest` on a random port — the real container, real HTTP, real SQL |

A red unit test stops the build before the slow tier starts, which is the point of the split.
The `@DataJpaTest` and `*IT` tiers start a real `postgres:16-alpine`, so Docker must be running —
they never touch the Compose database.

The tiers overlap on purpose, because each one is blind to something:

- **Mockito interaction tests carry rules the response cannot show.** "Reused the existing Flour
  row" and "wrote a second identical one" produce the *same* response body — only
  `verify(ingredients, never()).save(any())` separates them. Same for a rejection that persisted
  half the request before refusing it.
- **`@WebMvcTest` catches what a service test cannot reach:** an id that fails during argument
  binding before any service call, and the fact that the summary shape stays light *on the wire*
  rather than merely in the record.
- **The integration tier catches what mocks structurally cannot.** Its best test asserts that a
  request rejected halfway through leaves nothing behind — categories resolve *after* ingredients,
  so an unknown category is refused only after a new ingredient row was already inserted. With a
  mock, nothing was ever written, so there is nothing to detect. Removing `@Transactional` from
  `create()` fails exactly that one assertion.

Where a test guards a fix, the fix was removed and the test re-run to confirm it actually fails.
A regression test that passes either way is worthless and there is no way to tell by reading it.

## Notes from the build

Four things that were measured rather than assumed, and are the reason the code looks the way it
does.

**The N+1 was fixed by shape, not by an annotation.** With `generate_statistics` on, listing five
recipes cost **20 statements**; the fix brought it to **2**. The assertion that defines "no N+1"
is not a magic number — it is that *5 recipes and 10 recipes both cost 2 statements*. A fixed
count passes just as happily with a per-row query as long as the fixture never changes size.

**The listing pages plain entities first and initialises associations by id in a second query,**
and there is deliberately no `@EntityGraph` on the paged finder. With one, Hibernate 6.6 logs
`HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory` — it
abandons SQL paging, loads every matching row and throws all but one page away. Invisible at five
rows, fatal at fifty thousand. Categories and ingredients are also fetched *separately*, because
one query for both produces their cartesian product.

**A `PUT` that keeps an ingredient it already had used to fail** with a duplicate-key violation on
`ux_recipe_ingredients_recipe_ingredient`. Hibernate orders a flush by action type rather than by
the order the code did things, so the collection INSERTs went to the wire before the DELETEs. The
fix is an explicit flush between emptying the list and refilling it — and note that *a PUT which
changed every ingredient would have passed and hidden it entirely*, which is why the regression
test deliberately keeps one.

**Servings scaling multiplies before it divides,** which is arithmetic rather than style. Rounding
the ratio first makes every quantity inherit that error scaled by its own size: 1/3 rounds to
0.33, so 260 g becomes 85.80 instead of **86.67**, and a kilogram is out by over three grams. A
fixture of round numbers halved would let either implementation pass.

## Health and CI

`/actuator/health` reports `UP`, and **`DOWN` with a 503 when Postgres is unreachable** — verified
by stopping the container. Only `health` is exposed; `env`, `beans`, `mappings` and friends
answer 404, and a test pins that so `include: "*"` fails a build rather than a review.
Per-component detail is hidden by default (it names the database product and an absolute
filesystem path) and switched on under the `dev` profile, where reading it is the point.

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs `./mvnw -B -ntp verify` on every push
and pull request to `main` — the same command as above, so CI cannot pass on a build that does not
reproduce locally. Testcontainers uses the Docker daemon `ubuntu-latest` already has, so there is
no `services:` block to keep in step with the tests. Each run uploads both reports as an artifact
and writes a per-tier table to the job summary. Superseded *pull request* runs cancel; pushes to
`main` do not, because a red commit has to keep its verdict.

## Project structure

```
pom.xml                          # parent 3.5.16; springdoc is the only explicit version
mvnw  mvnw.cmd  .mvn/            # committed 100755, so the Linux runner can execute it
src/main/java/com/recipeapi/
  domain/                        # Recipe, Ingredient, RecipeIngredient, Category, 2 enums
  repository/                    # 3 repositories + RecipeSpecifications
  service/                       # RecipeService, IngredientService, CategoryService,
                                 #   ServingsScaler   <- the transaction boundary lives here
  web/
    RecipeController  IngredientController  CategoryController
    dto/                         # Java records: requests, responses, PagedResponse
    mapper/RecipeMapper          # plain @Component, constructible with new
    support/                     # SortWhitelist, @ProblemResponse, Created
  exception/                     # 6 exceptions + GlobalExceptionHandler (RFC 9457)
  config/OpenApiConfig
src/main/resources/
  application.yml  application-dev.yml
  db/migration/                  # V1 schema, V2 reference data
src/test/java/com/recipeapi/
  service/  web/  repository/    # Surefire: Mockito, @WebMvcTest, @DataJpaTest
  integration/                   # Failsafe: @SpringBootTest on a random port
  support/AbstractPostgresTest   # one shared container for every tier that needs one
docker-compose.yml  .env.example
.github/workflows/ci.yml  .github/dependabot.yml  .github/scripts/test-summary.py
```

## Roadmap

- [x] **01** · Scaffold, Maven & toolchain
- [x] **02** · Domain entities
- [x] **03** · Relationships & the association entity
- [x] **04** · Dev Postgres in Compose + Flyway schema
- [x] **05** · Repositories + first Testcontainers test
- [x] **06** · DTOs, mapper, read endpoints
- [x] **07** · Write endpoints + service layer
- [x] **08** · Validation + RFC 9457 problem details
- [x] **09** · Unit tests: JUnit 5 + Mockito
- [x] **10** · Controller tests with `@WebMvcTest`
- [x] **11** · Pagination & sorting
- [x] **12** · Search & filtering with Specifications
- [x] **13** · Fetch strategy: N+1 and friends
- [x] **14** · Integration tests end to end
- [x] **15** · Ingredients, categories & servings scaling
- [x] **16** · OpenAPI / Swagger UI
- [x] **17** · GitHub Actions CI
- [x] **18** · Refactor, actuator, README, `v1.0`

## License

Personal learning/portfolio project — no license specified yet.
