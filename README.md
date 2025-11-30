# recipe-api

[![CI](https://github.com/julianNordin/recipe-api-java-spring/actions/workflows/ci.yml/badge.svg)](https://github.com/julianNordin/recipe-api-java-spring/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-informational)](https://adoptium.net/temurin/releases/?version=21)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5.16-informational)](https://spring.io/projects/spring-boot)

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
- [x] **14** · Integration tests end to end
- [x] **15** · Ingredients, categories & servings scaling
- [x] **16** · OpenAPI / Swagger UI
- [x] **17** · GitHub Actions CI
- [ ] **18** · Refactor, actuator, README, `v1.0`

## Building

```bash
docker compose up -d db   # the development database
./mvnw verify             # both test tiers
```

Requires JDK 21 and Docker. The Maven wrapper downloads Maven itself, so no local Maven
install is needed.

`verify` runs two tiers and prints a `Results:` block for each:

| Tier | Plugin | What it runs |
|---|---|---|
| `*Test` | Surefire | Mockito unit tests, `@WebMvcTest` slices, `@DataJpaTest` against Testcontainers |
| `*IT` | Failsafe | `@SpringBootTest` on a random port, end to end over real HTTP |

Both must be green for the build to pass, and a failing unit test stops the build before the
slower tier starts. The `@DataJpaTest` and `*IT` tiers start a real `postgres:16-alpine`
container, so Docker has to be running.

## Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs `./mvnw -B -ntp verify` on every
push and pull request against `main` — the same command as above, so CI cannot pass on a build
that does not reproduce locally. Testcontainers uses the Docker daemon `ubuntu-latest` already
provides, so there is no `services:` block to keep in step with the tests.

Each run uploads the Surefire and Failsafe reports as an artifact and writes a per-tier summary
table to the job summary.
