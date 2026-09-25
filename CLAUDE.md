# CLAUDE.md

Spring Boot service + vanilla JS page listing 1,000+ items: search/filter/sort/page, add, remove.
Take-home exercise, extended live in a pairing interview: keep changes small, pragmatic, easy to follow.
This file is a checklist. Rationale → `docs/decisions.md`. User-facing docs → `README.md`.

## Stack
- Java 21, Spring Boot 3.5 (parent POM), Maven. Base package `com.dcdev.pt`, main class `ListingApplication`.
- H2 in-memory + Flyway, JPA/Hibernate. May move to PostgreSQL later (only the DB rows below change).
- Frontend: `src/main/resources/static/` (`index.html`, `app.js`, `styles.css`), served by Spring Boot, no build.
- Tests: JUnit 5, MockMvc, Database Rider (fixtures), datasource-proxy + hypersistence-utils (SQL counting).

## Commands
| Task | Command |
| --- | --- |
| Run (http://localhost:8080) | `mvn spring-boot:run` |
| Run without JDK/Maven | `docker compose up --build` |
| Unit tests | `mvn test` |
| Unit + integration tests | `mvn verify` |
| One unit test class | `mvn test -Dtest=ItemQueryParserTest` |
| One IT class | `mvn verify -Dit.test=ItemControllerIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false` |
| DB: engine | H2 in-memory `jdbc:h2:mem:items`, fresh + re-seeded every start |
| DB: console | http://localhost:8080/h2-console (user `sa`, empty password) |
| DB: schema | `src/main/resources/db/migration/V{n}__description.sql` |
| Seed | `app.seed.count` (default 2000), `app.seed.enabled` (false in `test` profile) |

## Map
```
com.dcdev.pt
├── item/     ItemController (/api/items), CategoryController (/api/categories), ItemService,
│             ItemQueryParser, ItemSpecifications, ItemRepository, Item, Category,
│             dto/ CreateItemRequest, ItemResponse, PageResponse
├── common/   ApiError, ErrorHandler (@RestControllerAdvice), InvalidRequestException, ItemNotFoundException
└── config/   ClockConfiguration, SeedDataConfiguration
test: unit/{common,dto,service} *Test · integration/{controller,repository} *IT · config/ (@IT, IntegrationTestBase)
test resources: datasets/*.yml (Rider fixtures) · response/{errors,success}/*.json (expected bodies)
```
- A new domain concept gets its own sibling package shaped like `item/`.
- No generic base repositories, no CQRS/event sourcing, no interface with a single implementation.

## API rules
- DTOs only at the HTTP boundary. Separate request and response types. Never expose an entity.
- Listing envelope: `{ "content": [...], "page": { "number", "size", "totalElements", "totalPages" } }`.
- Every failure → `ApiError` via `ErrorHandler`. New exception type → new handler there. No stack traces.
- Normalise input (strip, blank → null) in the request DTO's compact constructor, before validation.
- Time comes from the injected `Clock`, never `Instant.now()`.

## Querying rules
- Search, filtering, sorting and paging happen in the database, never in memory.
- One `Specification` method per filter in `ItemSpecifications`. `ItemService` combines only those requested.
- Search text: `ItemQueryParser.toSearchText` (strip, blank → no filter, max 100 chars). Escape it for LIKE
  (`%`, `_`, escape char). Never concatenate input into JPQL/SQL.
- Sort only through the allow-list in `ItemQueryParser`. `id` is always appended as the tie-breaker.
- Page size max 100. Defaults (page 0, size 25, `createdAt,desc`) belong in `ItemQueryParser` only.
- A listing request = at most 2 SQL statements (data + count). For a future to-many relation: filter via
  `EXISTS`, load the collection in a second bounded query for the page's ids, never `JOIN FETCH` + paging.

## Persistence rules
- Schema only via a new Flyway migration. Never edit an applied migration. `ddl-auto=validate` stays.
- SEQUENCE ids. `allocationSize` = sequence `INCREMENT BY` (currently 50).
- `open-in-view=false`. Read service methods are `@Transactional(readOnly = true)`.
- `Instant` is stored as UTC `TIMESTAMP` (`hibernate.jdbc.time_zone: UTC`). Keep it that way.
- Inserts are JDBC-batched (`hibernate.jdbc.batch_size: 50` = `allocationSize`). Pinned by `ItemInsertBatchingIT`.

## Recipes (common interview-style changes)
- **New filter**: Specification method → `@RequestParam` in `ItemController` → add in `ItemService.search`
  → UI control in `index.html` + `app.js` state/params (reset to page 0) → unit/IT tests + dataset rows.
- **New sortable field**: add to `SORTABLE_FIELDS` → index in a new migration if needed → UI `<option>`
  → `ItemQueryParserTest` + an ordering IT.
- **New column**: `V{n}__...sql` → `Item` → `CreateItemRequest` (+ validation) / `ItemResponse` → seed
  → datasets + expected JSON → UI.
- **New endpoint**: controller method → service method (transactional) → DTOs → `ErrorHandler` if new
  exception → `ItemControllerIT`.

## Tests
- Every behaviour change ships with a test that fails without it.
- Unit (`*Test`, no Spring) → `mvn test`. Integration (`*IT`, extend `IntegrationTestBase`) → `mvn verify`.
- ITs: seed disabled, small Rider `@DataSet` per test (default CLEAN_INSERT). `IntegrationTestBase`
  empties `items` after each test. Never `cleanBefore`/`cleanAfter` (clears Flyway history too).
  A new table must be added to that cleanup.
- Dataset YAML uses DB column names (`created_at`). Expected bodies live in `response/` (`readResponse`).
- SQL checks: `SQLStatementCountValidator` for counts, `sqlRecorder` (SQL text + bound values, batch sizes)
  for how input reaches the DB. Both reset in `IntegrationTestBase`. Call `sqlRecorder.reset()` again right
  before the checked action, since Rider's fixture load is recorded too.
- When reporting a change, name the command that proves it.

## Frontend rules
- One page of data in the browser. Server does search/filter/sort/paging.
- Debounce search (300 ms). Abort stale requests (`AbortController`). Filter/sort change → page 0.
- Render user data with `textContent` only. Never `innerHTML` with data.

## Change discipline
- Small steps. Stop for review after each, suggest a commit message. The human commits.
- Never commit or push unless asked. No destructive git (reset --hard, force push, clean -f).
- No new dependency without asking and saying why. Use the current stable version.
- Stay in scope: no Kafka/Elasticsearch/auth/microservices/new infrastructure unless asked. Propose
  the smallest thing that works and say what you'd do at larger scale.
- Decision made → one line in `docs/decisions.md`: **chosen** — why — what we gave up.
- Behaviour, API or commands changed → update `README.md` (API section, "What I'd do next").

## Before saying "done"
- [ ] `mvn verify` passes (or say clearly that it wasn't run).
- [ ] New behaviour has a test that fails without it.
- [ ] No entity in the API. Errors go through `ApiError`.
- [ ] No in-memory filtering/sorting/paging. Listing still ≤ 2 SQL statements.
- [ ] Schema changed only via a new migration.
- [ ] README / decisions updated if needed. Commit message suggested.
