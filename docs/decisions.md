# Decisions

One line per decision: **chosen** — why — what we gave up. Newest at the bottom.

- **Maven + Spring Boot 3.5.16, Java 21** — per brief/constraints, latest 3.5 patch, Java 21 LTS — gave up Boot 4.x / newer Java features.
- **H2 in-memory + Flyway, `ddl-auto=validate`** — zero-install run, versioned schema from day one, PostgreSQL later is config + dialect check — gave up persistence across restarts.
- **Vanilla HTML/CSS/JS served by Spring Boot** — one command to run, no npm build — gave up components/typing a framework would give.
- **Package by feature (`item/`, `common/`, `config/`)** — easy to navigate; a new concept is a new sibling package — gave up the familiar layer-by-layer layout.
- **Server-side search/filter/sort/paging (Specifications, capped page size)** — browser holds one page regardless of dataset size — gave up instant client-side filtering.
- **Unit tests via surefire (`*Test`), integration via failsafe (`*IT`)** — fast feedback with `mvn test`, full check with `mvn verify` — gave up a single test command.
