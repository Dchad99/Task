# Decisions

One line per decision: **chosen** — why — what we gave up. Newest at the bottom.

- **Maven + Spring Boot 3.5.16, Java 21** — per brief/constraints, latest 3.5 patch, Java 21 LTS — gave up Boot 4.x / newer Java features.
- **H2 in-memory + Flyway, `ddl-auto=validate`** — zero-install run, versioned schema from day one, PostgreSQL later is config + dialect check — gave up persistence across restarts.
- **Vanilla HTML/CSS/JS served by Spring Boot** — one command to run, no npm build — gave up components/typing a framework would give.
- **Package by feature (`item/`, `common/`, `config/`)** — easy to navigate; a new concept is a new sibling package — gave up the familiar layer-by-layer layout.
- **Server-side search/filter/sort/paging (Specifications, capped page size)** — browser holds one page regardless of dataset size — gave up instant client-side filtering.
- **Unit tests via surefire (`*Test`), integration via failsafe (`*IT`)** — fast feedback with `mvn test`, full check with `mvn verify` — gave up a single test command.
- **IT isolation via Rider CLEAN_INSERT + one `DELETE FROM items` after each test** — resets only fixture tables; Rider's `cleanBefore/After` also wiped `flyway_schema_history` (and failed on its quoted lowercase name) — gave up automatic cleanup of new tables: each new table must be added to the base `@AfterEach`.
- **Docker Compose as an alternative run option** (multi-stage build, JRE-only image) — reviewers without a local JDK/Maven can run it with one command — gave up nothing for the Maven path; image build is slower than `mvn spring-boot:run`.
- **JDBC batching (`batch_size: 50`, `order_inserts/updates`)** — seeding 2,000 rows becomes 40 round trips instead of 2,000; 50 equals the sequence allocation, so one sequence call covers one batch — gave up nothing measurable (single-row inserts are unaffected).
- **Injection tests assert the mechanism via a datasource-proxy statement recorder** — "0 results, table intact" can't distinguish bound from concatenated-but-harmless; checking bound values and "no SQL ran" for rejected input can — gave up a little test-infrastructure code (one listener class).
- **`q` capped at 100 characters (400 beyond, not truncated)** — bounds the LIKE pattern and matches the other explicit parameter limits; silent truncation would search for something the user didn't type — gave up arbitrarily long searches (no real use case).
