# CLAUDE.md

Small Spring Boot service + vanilla JS listing page that stays responsive with 1,000+ items
(list/search, add, remove). Take-home exercise: keep it small, pragmatic, easy to run and extend.
This file is a checklist. Rationale lives in `docs/decisions.md`.

## Stack
- Java 21, Spring Boot 3.5 (parent POM), Maven. Base package `com.dcdev.pt`, main class `PtApplication`.
- H2 (embedded) + Flyway. May move to PostgreSQL later (see Commands, DB lines).
- Frontend: vanilla HTML/CSS/JS in `src/main/resources/static`, served by Spring Boot. No build step.

## Commands
| Task | Command |
| --- | --- |
| Run app (http://localhost:8080) | `mvn spring-boot:run` |
| Unit tests (surefire) | `mvn test` |
| Unit + integration tests (failsafe) | `mvn verify` |
| One unit test | `mvn test -Dtest=ItemQueryParserTest` |
| One integration test | `mvn verify -Dit.test=ItemControllerIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false` |
| DB: engine | H2 in-memory, recreated on every start |
| DB: console | http://localhost:8080/h2-console (dev only) |
| DB: schema | `src/main/resources/db/migration/V{n}__description.sql` |

## Structure (package by feature)
```
com.dcdev.pt
├── item/     ItemController, CategoryController, ItemService, ItemRepository,
│             Item, Category, ItemSpecifications, ItemQueryParser, dto/
├── common/   ApiError, GlobalExceptionHandler, exceptions
└── config/   SeedDataConfiguration, ClockConfiguration
```
- A new domain concept gets its own sibling package with the same shape as `item/`.
- No generic base repositories, no CQRS/event sourcing, no interface with a single implementation.

## API
- DTOs only at the HTTP boundary. Request and response DTOs are separate types. Never expose an entity.
- Listing response envelope:
  `{ "content": [...], "page": { "number", "size", "totalElements", "totalPages" } }`
- Every failure returns `ApiError` via `GlobalExceptionHandler`. No stack traces in responses.
- Input normalisation (trim, blank → null, etc.) lives in the request DTO's compact constructor,
  so it runs before validation.
- Time comes from the injected `Clock`, never `Instant.now()`.

## Querying
- Search, filtering, sorting and pagination happen in the database, never in memory.
- One `Specification` method per filter in `ItemSpecifications`. The service combines only the ones that apply.
- Escape user text used in LIKE: `%`, `_` and the escape char itself.
- Sort only through the allow-list in `ItemQueryParser`. Always append `id` as the tie-breaker.
- Page size is capped at 100. Defaults (page, size, sort) live only in `ItemQueryParser`.
- A listing request costs at most 2 SQL statements (data + count). If a to-many relation is added:
  - filter via `EXISTS`,
  - load the collection in a second bounded query for the page's ids,
  - never `JOIN FETCH` with pagination.

## Persistence
- Schema changes only via a new Flyway migration `V{n}__description.sql`. Never edit an applied migration.
- `spring.jpa.hibernate.ddl-auto=validate`. Keep it that way.
- SEQUENCE ids. `allocationSize` must match the sequence `INCREMENT BY`.
- `spring.jpa.open-in-view=false`. Read service methods are `@Transactional(readOnly = true)`.
- Seed data (`SeedDataConfiguration`) gives the listing a realistic 1,000+ rows at startup.

## Tests
- Every behaviour change ships with a test that fails without it.
- Unit tests: plain JUnit, no Spring context, under `src/test/java/com/dcdev/pt/unit/`, named `*Test`.
  Run with `mvn test`.
- Integration tests: `*IT`, MockMvc against the real (H2) DB, seed data disabled, small fixtures per test,
  under `src/test/java/com/dcdev/pt/integration/`. Run with `mvn verify`.
- When you report a change, name the command that proves it.

## Frontend
- Only one page of data in the browser at a time. The server does search, filtering and paging.
- Debounce search input. Abort stale requests (`AbortController`).
- Any filter or sort change resets to page 0.
- Render user data with `textContent` only. Never use `innerHTML` with data.

## Change discipline
- Work in small steps. Stop for review after each step and suggest a commit message.
  The human commits.
- Never commit or push unless explicitly asked. No destructive git operations
  (reset --hard, force push, rebase of shared history, clean -f).
- Don't add a dependency without asking first and saying why. Use the current stable version.
- Stay in scope. No Docker, Kafka, Elasticsearch, auth or microservices unless asked. Propose the smallest
  thing that meets the requirement, and say what you'd do at larger scale instead.
- After a meaningful change where a decision was made, add one line to `docs/decisions.md`:
  **chosen** — why — what we gave up.
- Keep the README (setup, run, decisions/trade-offs, next steps) accurate when behaviour or commands change.

## Before saying "done"
- [ ] `mvn verify` passes.
- [ ] New or changed behaviour has a test that fails without the change.
- [ ] No entity leaks into the API. Errors go through `ApiError`.
- [ ] No in-memory filtering/sorting/paging. Listing is still ≤ 2 SQL statements.
- [ ] Schema changed only via a new migration.
- [ ] `docs/decisions.md` / README updated if needed. Commit message suggested.
