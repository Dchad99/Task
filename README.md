# Item Listing Service

A small Spring Boot service with a plain HTML/JS front end, built around a listing page that stays
responsive with thousands of items. It supports search, filtering, sorting and paging (all done in
the database), plus adding and removing items. 2,000 realistic greeting-card-shop items are seeded
on startup.

Built as a take-home exercise: the aim is clear, pragmatic decisions rather than many features.

---

## Quick start

**Option A: Maven** (needs JDK 21 and Maven 3.9+)

```bash
mvn spring-boot:run
```

**Option B: Docker** (needs only Docker)

```bash
docker compose up --build
```

Then open **http://localhost:8080**.

| What | Where |
| --- | --- |
| Listing page | http://localhost:8080 |
| REST API | http://localhost:8080/api/items |
| H2 console | http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:items`, user `sa`, empty password) |

There is no front-end build or dev server: the page is static files served by the same Spring Boot app.

Useful overrides:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--app.seed.count=5000   # bigger catalogue
APP_PORT=8081 APP_SEED_COUNT=500 docker compose up --build              # other port / seed size
```

The database is in-memory. Every restart gives a fresh, identically seeded catalogue (fixed random seed).

---

## Running the tests

| Command | Runs |
| --- | --- |
| `mvn test` | Unit tests (`*Test`), plain JUnit, no Spring context, a few seconds |
| `mvn verify` | Unit tests + integration tests (`*IT`): full Spring context, real H2 schema from Flyway, MockMvc |
| `mvn test -Dtest=ItemQueryParserTest` | One unit test class |
| `mvn verify -Dit.test=ItemControllerIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false` | One integration test class |

What the integration tests cover:

- **`ItemControllerIT`**: the HTTP contract (page envelope, create/delete status codes, `Location` header, error bodies).
- **`ItemRepositoryIT`**: filtering, sorting and paging against the real schema, including stable ordering when sort values tie.
- **`ItemListingQueryCountIT`**: a listing request runs at most 2 SQL statements (data + count), whatever the page size.
- **`InputInjectionIT`**: SQL in any parameter is treated as data or rejected, and LIKE wildcards (`%`, `_`) match literally.

---

## Using the app

The page has two panels:

- **Add item**: name (required), category (required), description and price (optional). Server-side
  validation errors are shown under the form, per field.
- **Listing**: text search (name and description, case-insensitive), category filter, sort (newest/oldest,
  name A–Z/Z–A, price low/high), page size (10/25/50/100), Previous/Next paging, and a Delete button per row.

How it stays responsive with large data:

- The browser only ever holds **one page** of items. Search, filter, sort and paging all happen in the
  database.
- Search input is **debounced** (300 ms), and a new request **aborts the previous one**, so a
  slow old response can't overwrite a newer one.
- Changing any filter or sort goes **back to page 1**. Deleting the last row on a page steps back one page.
- User data is rendered with `textContent` only, so stored markup is displayed, never executed.

---

## API

Base path: `/api`. JSON in and out.

### `GET /api/items`: list, search, filter

| Parameter | Default | Rules |
| --- | --- | --- |
| `q` | none | Case-insensitive substring match on name **or** description. `%` and `_` match literally. |
| `category` | none | One of `BIRTHDAY`, `WEDDING`, `ANNIVERSARY`, `THANK_YOU`, `CHRISTMAS`, `OTHER` |
| `page` | `0` | 0-based, ≥ 0 |
| `size` | `25` | 1–100 (capped; larger values are a 400, not silently clamped) |
| `sort` | `createdAt,desc` | `field[,asc\|desc]`, field one of `createdAt`, `name`, `price`. `id` is always added as a tie-breaker. |

```bash
curl 'http://localhost:8080/api/items?q=floral&category=BIRTHDAY&sort=price,asc&size=2'
```

```json
{
  "content": [
    {
      "id": 1234,
      "name": "Floral Birthday Card",
      "category": "BIRTHDAY",
      "description": "A floral card for birthday.",
      "price": 3.25,
      "createdAt": "2026-08-02T14:00:00Z"
    }
  ],
  "page": { "number": 0, "size": 2, "totalElements": 57, "totalPages": 29 }
}
```

(Values are illustrative.)

### `GET /api/items/{id}`
Returns one item, or `404`.

### `POST /api/items`: create

```bash
curl -i -X POST http://localhost:8080/api/items \
  -H 'Content-Type: application/json' \
  -d '{"name":"Botanical Thank You Card","category":"THANK_YOU","description":"Pressed-flower design","price":3.95}'
```

| Field | Rules |
| --- | --- |
| `name` | required, not blank, ≤ 255 chars (surrounding whitespace stripped) |
| `category` | required, one of the categories above |
| `description` | optional, ≤ 1000 chars (blank becomes `null`) |
| `price` | optional, ≥ 0, at most 8 integer and 2 fraction digits |

Returns `201 Created` with a `Location: /api/items/{id}` header and the created item. `id` and
`createdAt` are set by the server. A client can't supply them.

### `DELETE /api/items/{id}`
Returns `204 No Content`, or `404` if the item doesn't exist (see trade-offs below).

### `GET /api/categories`
Returns the list of category values, which the UI uses to fill its dropdowns.

### Errors
Every failure has the same shape, never a stack trace:

```json
{
  "timestamp": "2026-09-25T15:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for the submitted item.",
  "path": "/api/items",
  "fieldErrors": { "name": "name must not be blank" }
}
```

`fieldErrors` is present only for body validation failures. Bad query parameters (unknown sort field,
`size` over 100, invalid category, non-numeric `page`) return a `400` whose `message` says what was
wrong and what's allowed.

---

## Project layout

Packaged by feature, so everything about items sits in one place:

```
src/main/java/com/dcdev/pt/
├── ListingApplication.java
├── item/                     the Item feature
│   ├── ItemController        HTTP: /api/items
│   ├── CategoryController    HTTP: /api/categories
│   ├── ItemService           use cases, transactions, combines filters
│   ├── ItemQueryParser       page/size/sort validation, defaults, sort allow-list
│   ├── ItemSpecifications    one JPA Specification per filter
│   ├── ItemRepository        Spring Data JPA + Specification executor
│   ├── Item, Category        entity and enum
│   └── dto/                  CreateItemRequest, ItemResponse, PageResponse
├── common/                   ApiError, ErrorHandler (@RestControllerAdvice), exceptions
└── config/                   ClockConfiguration, SeedDataConfiguration

src/main/resources/
├── application.yml
├── db/migration/             Flyway migrations (V1__create_items_table.sql)
└── static/                   index.html, app.js, styles.css

src/test/java/com/dcdev/pt/
├── unit/                     *Test, no Spring
├── integration/              *IT, full context + H2 + MockMvc
└── config/                   @IT annotation, IntegrationTestBase, SQL statement counter
src/test/resources/
├── datasets/                 Database Rider fixtures (YAML)
└── response/                 expected JSON bodies
```

A new domain concept would get its own sibling package with the same shape as `item/`.

---

## Decisions and trade-offs

The full log, one line per decision, is in [docs/decisions.md](docs/decisions.md). The main ones:

- **Server-side everything.** Search, filter, sort and paging happen in SQL through JPA Specifications,
  one per filter, combined by the service. The browser never holds more than one page. *Trade-off:*
  every keystroke (after the debounce) is a round trip, not an instant client-side filter.
- **Offset paging with a size cap of 100 and a deterministic order.** A sort allow-list, with `id` always added as a
  tie-breaker, so pages never repeat or skip rows when sort values tie. *Trade-off:* offset paging gets
  slower for very deep pages. At larger scale I'd switch to keyset (cursor) paging.
- **At most 2 SQL statements per listing request** (data + count), enforced by a test. The `Item`
  entity is deliberately flat, so there's no lazy loading or N+1.
- **H2 in-memory + Flyway, `ddl-auto=validate`.** Nothing to install, and the schema is versioned from
  day one, so moving to PostgreSQL is mostly configuration. *Trade-off:* data doesn't survive a restart.
- **Substring search with `LOWER(...) LIKE %q%`.** Simple and correct for a few thousand rows.
  *Trade-off:* it can't use an index. At scale: PostgreSQL trigram indexes or full-text search.
- **Explicit DTOs and page envelope.** The entity never crosses the HTTP boundary, and the JSON shape doesn't
  depend on Spring Data's `Page` serialisation, which has changed between versions.
- **Delete of an unknown id returns 404, not 204.** The UI can tell the user their view was stale.
  *Trade-off:* a repeated DELETE isn't strictly idempotent in its response code.
- **Vanilla JS, no build step.** One command runs everything. *Trade-off:* no component model. Fine
  for one page.
- **Tests at two levels.** Fast unit tests for parsing and validation rules. Integration tests against the real
  schema for queries, the HTTP contract, the query count and injection safety.

---

## What I'd do next

- **Keyset pagination** for deep pages, and maybe an estimated total instead of an exact `COUNT` at large sizes.
- **PostgreSQL + Testcontainers**, with a trigram index for search.
- **Restrict the H2 console** to a `dev` profile (it currently runs any SQL for whoever can reach it).
- **Edit (PUT/PATCH) with optimistic locking** (`@Version`), since add/remove is all the brief asked for.
- **OpenAPI description** of the API (springdoc), if the API gets outside consumers.

---

## How AI was used

AI assistance (Claude) was used throughout, with me driving and reviewing every change. Conventions
for AI sessions live in [CLAUDE.md](CLAUDE.md), a short checklist read at the start of each session.
Rationale goes to [docs/decisions.md](docs/decisions.md).

Examples of how it was used, and what I changed or rejected:

- **Project setup and conventions**: Maven/Spring Boot skeleton, CLAUDE.md, decision log.
- **Diagnosing test failures**:
  - a missing `Clock` bean hidden behind Spring's "context failure threshold" message;
  - Database Rider trying to clear Flyway's quoted lowercase `flyway_schema_history` table;
  - an invalid `dbunit.yml` that leaked a pooled connection in every test, so the last tests hung for 30 s each.
- **Security review**: checked every input path for SQL injection. The review found a missing test fixture and
  suggested asserting on the SQL actually run, not just the results.

Transcripts: see `docs/ai/` <!-- TODO: add exported transcripts before submitting -->
