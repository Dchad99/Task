# Original prompts — Claude session, 2026-09-25

Every prompt I sent in the session, **verbatim** and in order (Europe/Lisbon time). What the
assistant did with each one, and what I kept or changed, is in the
[annotated log](2026-09-25-claude-session.md). The full conversation, including the assistant's
replies, is at the session link in [README.md](README.md#how-to-open-the-full-conversation).

The exercise brief was pasted in full into prompts 2 and 3. It is reproduced once, below, and
marked where it appeared.

---

## The exercise brief (as pasted)

```text
Technical Exercise & Interview — Backend Java Developer
About the exercise
We’d like you to build a small, runnable Spring Boot service with a lightweight UI.
The aim isn’t to produce a polished product. The exercise is intended to give us something practical
to discuss during the interview: how you design APIs, structure code, handle data at a modest
scale, and use AI tooling as part of your development process.
We are more interested in clear, pragmatic decisions than a large number of features.
What to build
Build a small service and front end centred around a listing page that can comfortably work with
1,000+ items.
Backend
• A list/search endpoint
• Sensible handling of 1,000+ records
• Endpoints to add and remove items
Persistence can be lightweight. An in-memory or embedded database, such as H2, is fine. The
important thing is that the service is easy to run and reason about.
Front end
• A listing page
• The ability to add and remove items
• A responsive experience with 1,000+ records in the data set
Running the application
Please include a clear README so we can get the application running quickly.
• mvn spring-boot:run
• a simple front-end dev server command
• or docker compose up
Please include seed/sample data so the listing page has a realistic volume of data when the
application starts.
Deliverables
• A link to a Git repository with full commit history
• Any AI assistant transcripts you used while building the solution
• A README covering setup instructions, run instructions, and any important decisions or trade-offs
We are interested in how the solution evolved, so prefer incremental, meaningful commits over a
single final drop.
AI use is actively encouraged. We just want visibility into how you used it: what you asked, what it
produced, and how you applied or changed the output.
Scope and time
Keep the exercise small and focused.
We would expect this to take roughly 3–4 hours. Please do not over-invest beyond that.
If you run out of time, make a note of what you would do next.
What we are looking for

• Clear, sensible API design
• Server-side handling of search, filtering and pagination, rather than loading everything into the
browser
• Readable, well-structured code
• A codebase that is easy to navigate and extend
• Good Git hygiene, with a history that tells a coherent story
• Transparent and effective use of AI
• A simple but functional UI
• Pragmatic choices appropriate to a small service
The interview
The interview will be a 45-minute live pairing session with engineers at thortful.
During the session, we will ask you to add a feature to your solution.
AI tools are allowed and encouraged, so please bring your normal setup. We are interested in how
you navigate and extend your own codebase, reason through decisions, and work with AI in real
time.
Please have your environment ready before the session so you can run and edit the code.
A few notes
Technology choices are yours, apart from the backend, which should use Spring Boot.
```

---

## 1 · 14:09 — project setup

*(With the project folder `/Users/dcdev/Task` attached.)*

```text
Add Maven as the build tool for the project and prepare a minimal starter skeleton.
```

## 2 · 14:21 — CLAUDE.md

```text
it's enough for start
Create CLAUDE.md for this repository. It will be read at the start of every AI session (including a live pairing interview where we'll add features), so it must be a short, practical checklist — not a design doc. Design rationale goes to docs/decisions.md, not here. Target: under ~120 lines.

Context — the exercise brief:
[the exercise brief, pasted in full — see above]

Conventions to encode (they come from an earlier version of this project and worked well):

Stack & run
- Java 21, Spring Boot 3.5, Maven, base package com.dcdev.pt. H2 + Flyway for now (we may move to
  PostgreSQL later — write the Commands section so it's easy to update).
- Frontend: vanilla HTML/CSS/JS in src/main/resources/static, served by Spring Boot, no build step.

Structure
- Package by feature: item/ (ItemController, CategoryController, ItemService, ItemRepository, Item,
  Category, ItemSpecifications, ItemQueryParser, dto/), common/ (ApiError, GlobalExceptionHandler,  exceptions), 
config/ (SeedDataConfiguration, ClockConfiguration). A new domain concept gets its own
  sibling package with the same shape.
- No generic base repositories, no CQRS/event sourcing, no interfaces with a single implementation.

API
- DTOs only at the HTTP boundary; request and response DTOs are separate types; never expose the entity.
- Listing envelope { content, page: { number, size, totalElements, totalPages } }.
- One error shape (ApiError) for every failure via GlobalExceptionHandler; no stack traces in responses.
- Input normalisation lives in the request DTO's compact constructor, before validation.
- Time comes from the injected Clock, never Instant.now().

Querying
- Search, filtering, sorting and pagination happen in the database, never in memory.
- One Specification method per filter in ItemSpecifications; the service combines the ones that apply.
  User text for LIKE is escaped (%, _ and the escape char).
- Sort only via the allow-list in ItemQueryParser; id is always appended as the tie-breaker.
  Page size is capped (100). Defaults live only in ItemQueryParser.
- A listing request costs at most 2 SQL statements (data + count). If a to-many relation is ever added:
  filter via EXISTS, load the collection in a second bounded query for the page's ids, never JOIN FETCH
  with pagination.

Persistence
- Schema only via new Flyway migrations (V{n}__description.sql); never edit an applied migration;
  ddl-auto stays validate. SEQUENCE ids with allocationSize matching the sequence increment.
- open-in-view=false; read methods are @Transactional(readOnly = true).

Tests
- Every behaviour change ships with a test that fails without it.
- Unit tests (no Spring) under unit/, integration tests (*IT, MockMvc, real DB, seed disabled, small
  fixtures) under integration/. Name the command that runs each.

Frontend
- One page of data in the browser at a time; debounce search; abort stale requests; any filter/sort change
  resets to page 0; render user data with textContent only.

Change discipline
- Small steps; stop for review after each; suggest a commit message; the human commits.
- Don't add a dependency without asking and saying why; use the current stable version.
- Stay in scope: no Docker/Kafka/Elasticsearch/auth/microservices unless asked — propose the smallest thing
  that satisfies the requirement and say what you'd do at larger scale instead.
- No destructive git operations; never commit or push unless explicitly asked.
- After a meaningful change, add one line to docs/decisions.md if a decision was made.
```

## 3 · 14:31 — pairing set-up (the main task prompt)

```text
You're pairing with me as a senior Java/Spring developer on a take-home exercise.
I drive and review; you propose, implement and explain. The brief:

[the exercise brief, pasted in full — see above]

## Constraints
- Java 21, Spring Boot 3.5, Maven, base package com.dcdev.pt.
- Start with H2 + Flyway so `mvn spring-boot:run` works with nothing installed.
  We may move to PostgreSQL later as a separate.
- Frontend: plain HTML/CSS/JS in src/main/resources/static, served by Spring Boot. No npm build.
- Time box 3–4 hours for the core. Simplest correct thing; no speculative abstractions,
  no generic base repositories, no interfaces with a single implementation.

## Code layout
- Package by feature: item/ (controller, service, repository, entity, specifications,
  query parsing, dto/), common/ (exceptions, global error handler, ApiError),
  config/ (seed data, Clock).
- DTOs are the only types crossing the HTTP boundary; request and response DTOs are separate types.
- One Specification method per filter in ItemSpecifications.
- Sorting via an explicit allow-list in ItemQueryParser, with id always appended as tie-breaker.
- Schema only via Flyway migrations, ddl-auto=validate.

## Keep it extensible
We'll keep adding features later, including live in the interview. Maintain as we go:
- CLAUDE.md — short checklist of codebase conventions for future sessions;
- docs/decisions.md — one line per decision: chosen, why, what we gave up.
```

*(Between this prompt and the next, I implemented the core myself: item package, error handling,
migration, frontend, Database Rider integration tests.)*

## 4 · 15:39 — context failure

```text
Help me diagnose why the integration tests fail to load the Spring application context. Inspect the first root cause in the test output, identify the failing configuration or dependency, and suggest a fix.
```

## 5 · 15:43 — SQL injection review

```text
Good catch, thanks
As for sql inject problem, could you please verify the current impl, does it eliminate the chance or there're still have some cases that aren't covered?
```

## 6 · 15:56 — Flyway history table

*(With the failing test log attached: `Table "FLYWAY_SCHEMA_HISTORY" not found (candidates are:
"flyway_schema_history"); SQL statement: DELETE FROM PUBLIC.flyway_schema_history where 1=1`.)*

```text
Let's verify the test logs, why do we get it 

Is it due to the DBRIDER tries to remove flyway schema history table in lowercase, but it's in uppercase? or vice-versa?
```

## 7 · 16:04 — slow tests

````text
after adding additional configuration for dbunit lib

```
tableType:
  - TABLE
excludeTables:
  - flyway_schema_history
```

tests started to take more time to finish
````

## 8 · 16:08

```text
ok, let's try it
```

## 9 · 16:27

```text
Polish the CLAUDE.md and create descriptive README, please
```

## 10 · 16:31

```text
Please, fix it: places where the code breaks your own conventions
```

## 11 · 16:33

```text
What is One class: `mvn test -Dtest=ItemQueryParserTest`?
```

## 12 · 16:39 — review against the brief

```text
Let's verify the input requirements with the final solution. Estimate the result, investigate code and specify locations for improvements
```

## 13 · 17:07 — hardening

```text
implement & fix next options from the list: Should fix in the code
7 - enrich the default profile with configuration, 8 - verify the mechanism(let's look into it), 9 - fix it
```

## 14 · 17:17 — git sync

```text
GitHub already have `Initial commit`  sync with master
```

The assistant asked how to reconcile the unrelated histories. My answer (a choice from three
options): **"Merge, keep both"**, meaning `git merge -s ours --allow-unrelated-histories origin/master`.

## 15 · 17:22

```text
Please, commit the changes
```

## 16 · 17:24

```text
Push please
```

## 17 · 17:26

*(With a screenshot of IntelliJ's "Push Commits to Task" dialog.)*

```text
verify it again, please
```

## 18 · 17:28

```text
Please, also attach the current chat via "share" option and add AI assistant transcripts, please
```

## 19 · 2026-09-28 13:14

```text
Could you please add it to the project’s `docs/ai` folder so the reviewer can see the original prompt used for the task, including the “share” options shown here?
```
