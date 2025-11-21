# recipe-api

A REST API for managing recipes, their ingredients and their categories, built with
**Java 21** and **Spring Boot 3.5**. Recipes can be searched, filtered, sorted and paged;
ingredients are shared across recipes rather than duplicated; quantities can be rescaled to
any serving count on the fly.

> **Status: in progress.** This README is a stub that grows with the build — the roadmap
> below is the plan of record. Full documentation lands in Phase 18.

## Tech stack

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.16 (Web MVC, Data JPA, Validation) |
| Persistence | PostgreSQL 16, Hibernate 6.6, Flyway migrations |
| Build | Maven (via the committed wrapper, `./mvnw`) |
| Testing | JUnit 5, Mockito, Testcontainers, MockMvc |
| Docs | springdoc-openapi / Swagger UI |
| CI | GitHub Actions |

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
- [ ] **14** · Integration tests end to end
- [ ] **15** · Ingredients, categories & servings scaling
- [ ] **16** · OpenAPI / Swagger UI
- [ ] **17** · GitHub Actions CI
- [ ] **18** · Refactor, actuator, README, `v1.0`

## Building

```bash
./mvnw verify
```

Requires JDK 21. The Maven wrapper downloads Maven itself, so no local Maven install is
needed. Docker is required from Phase 04 onward for the development database and the
Testcontainers-backed tests.
