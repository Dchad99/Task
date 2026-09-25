# pt — item listing service

Small Spring Boot service with a plain HTML/JS listing page that stays responsive with 1,000+ items.

## Requirements
- JDK 21 (`java -version` / `mvn -v` should report 21)
- Maven 3.9+

Nothing else: the database is an embedded in-memory H2, created and migrated by Flyway on startup.

## Run
```
mvn spring-boot:run
```
App: http://localhost:8080 · H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:pt`, user `sa`, no password)

## Test
| Command | Runs |
| --- | --- |
| `mvn test` | Unit tests (`*Test`, no Spring context) |
| `mvn verify` | Unit + integration tests (`*IT`, Spring context + H2) |

## Decisions and trade-offs
See [docs/decisions.md](docs/decisions.md).
