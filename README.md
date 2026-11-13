# Java Backend Developer take-home assessment

Engine remanufacturing / core-return business: dealers return used engine cores, which are inspected, disassembled, then remanufactured or scrapped.
This repository holds the five exercises.

> **AI assistance.** Everything in this repository (code, tests, SQL, and documents) was drafted with an AI assistant
> (Claude Code, model Claude Sonnet 5.5) from the assessment brief. Each folder's README says exactly what was run and what was not.

## Folder map

| Folder / file | Exercise | What it is |
|---|---|---|
| [`core-tracking-api/`](core-tracking-api/README.md) | 1 | **Source code.** Spring Boot REST API to track cores, with status transition rules, tests and Swagger |
| [`sql/`](sql/README.md) | 2 | **SQL scripts.** DDL, sample data, the three queries, recommended indexes and notes |
| [`docs/answers.docx`](docs/answers.docx) | 2, 3, 4, 5 | **Documentation.** Written answers as one Word file |

## Quick start (Exercise 1)

Needs JDK 17+ and Maven 3.9+ (details in [`core-tracking-api/README.md`](core-tracking-api/README.md)):

```
cd core-tracking-api
mvn clean test
java -jar target/core-tracking-api-1.0.0.jar     # after: mvn clean package
```

Then open `http://localhost:8080/swagger-ui.html`.

## What was verified (2026-11-13)

| Item | Result |
|---|---|
| Exercise 1: `mvn clean test` | 85 tests passed, 0 failures (JDK 21.0.10 with the compiler set to Java 17) |
| Exercise 1: app started, every endpoint and error path called with curl | Done, real output is in the project README |
| Exercise 2: SQL files executed | On **H2** in PostgreSQL mode only. **Not** run on PostgreSQL or SQL Server (T-SQL variants untested) |
| `docs/answers.docx` | Opens in Word without errors (8 pages); Exercise 3 is 259 words |
| Not run anywhere | A Java 17 runtime, Linux/macOS, `EXPLAIN` plans, load tests |
