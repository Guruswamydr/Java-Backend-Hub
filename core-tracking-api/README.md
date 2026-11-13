# Core Tracking API

A small REST service that tracks **returned engine cores** (cores a dealer sends back) through inspection, disassembly and
remanufacturing or scrapping. Java 17, Spring Boot 3.3, Spring Data JPA, H2 in-memory database, springdoc-openapi, JUnit 5, Mockito.

> **AI assistance disclosure.** This project (code, tests, sample data and this README) was written with the help of an AI
> assistant (Claude Code, model Claude Sonnet 5.5) working from the assessment brief. Everything described below as *run* was
> actually run on 2026-11-13 and the commands and output shown are real; what was **not** run is listed in
> [What was run and what was not](#what-was-run-and-what-was-not).

## Contents

1. [Prerequisites](#prerequisites)
2. [Build, run, test](#build-run-test)
3. [URLs: API, Swagger, H2 console](#urls)
4. [Endpoints](#endpoints)
5. [Status transition rules](#status-transition-rules)
6. [Error responses](#error-responses)
7. [Sample requests and responses](#sample-requests-and-responses)
8. [Tests](#tests)
9. [What was run and what was not](#what-was-run-and-what-was-not)
10. [Assumptions](#assumptions)
11. [Design decisions](#design-decisions)
12. [Possible improvements](#possible-improvements-not-done-on-purpose)
13. [Suggested GitHub repository structure](#suggested-github-repository-structure)

## Prerequisites

- **JDK 17 or newer.** The code is compiled for Java 17 (`release 17`). It was built and tested here on JDK 21.0.10 (Temurin); it
  was not run on a JDK 17 runtime. Make sure `JAVA_HOME` points to JDK 17+ (Spring Boot 3 does not build on Java 11).
- **Maven 3.9 or newer** (3.9.9 was used), or an IDE that opens Maven projects (Eclipse, IntelliJ, NetBeans).
- **curl** (optional, for the examples). Port **8080** must be free.

## Build, run, test

Run these from this folder (`core-tracking-api`):

```
mvn clean test                  # compile and run all tests (85)
mvn clean package               # build the runnable jar: target/core-tracking-api-1.0.0.jar
java -jar target/core-tracking-api-1.0.0.jar    # run the jar
mvn spring-boot:run             # or run straight from Maven
```

Stop the application with `Ctrl+C`. On JDK 21 the tests print a harmless Mockito warning ("A Java agent has been loaded dynamically").

## URLs

| What | URL |
|---|---|
| API base | `http://localhost:8080/api/cores` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` (redirects to `/swagger-ui/index.html`) |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| H2 console | `http://localhost:8080/h2-console` |

H2 console login: JDBC URL `jdbc:h2:mem:coredb`, user `sa`, empty password. The database is in memory: it is created when the app
starts, filled with 10 sample cores from `src/main/resources/data.sql`, and lost when the app stops. The H2 console is for development only.

## Endpoints

| Method | Path | Purpose | Success | Errors |
|---|---|---|---|---|
| `POST` | `/api/cores` | Register a returned core (status is always `RECEIVED`) | `201` + `Location` header | `400` |
| `GET` | `/api/cores/{id}` | Get one core | `200` | `404`, `400` (id not a number) |
| `GET` | `/api/cores` | List cores, paged, optional status filter | `200` | `400` (bad status or sort) |
| `PATCH` | `/api/cores/{id}/status` | Change a core's status (rules below) | `200` | `400`, `404`, `409` |

Query parameters of the list: `status` (`RECEIVED`, `INSPECTED`, `DISASSEMBLED`, `REMANUFACTURED` or `SCRAPPED`, upper case),
`page` (zero-based, default 0), `size` (default 10, maximum 100), `sort` (for example `sort=receivedDate,desc`; default is by `id`).
Out-of-range paging values are corrected rather than rejected (`size=500` becomes 100, `size=0` becomes 10, `page=-1` becomes 0).

Request bodies:

```json
POST  /api/cores                 {"partNumber": "PN-4001", "dealer": "Dealer North", "receivedDate": "2026-11-12"}
PATCH /api/cores/{id}/status     {"status": "INSPECTED"}
```

A core in a response looks like this (`statusDisplayName` is the friendly name of the status):

```json
{"id": 11, "partNumber": "PN-4001", "dealer": "Dealer North", "receivedDate": "2026-11-12", "status": "RECEIVED", "statusDisplayName": "Received"}
```

## Status transition rules

Statuses and display names: `RECEIVED` (Received), `INSPECTED` (Inspected), `DISASSEMBLED` (Disassembled),
`REMANUFACTURED` (Remanufactured), `SCRAPPED` (Scrapped).

| From \ To | RECEIVED | INSPECTED | DISASSEMBLED | REMANUFACTURED | SCRAPPED |
|---|---|---|---|---|---|
| **RECEIVED** | no | **yes** | no | no | **yes** |
| **INSPECTED** | no | no | **yes** | no | **yes** |
| **DISASSEMBLED** | no | no | no | **yes** | **yes** |
| **REMANUFACTURED** (final) | no | no | no | no | no |
| **SCRAPPED** (final) | no | no | no | no | no |

- 6 of the 25 combinations are allowed. `REMANUFACTURED` and `SCRAPPED` are final: nothing can follow them.
- A change to the **same** status is rejected too (the diagonal of the table). There is no special case for it: no status is in its own
  allowed set.
- Any rejected change returns **HTTP 409** with a message such as
  `Cannot change status from SCRAPPED to RECEIVED. Allowed next statuses: none`.

**Where the rules live (one place):** `CoreStatus.allowedNextStatuses()` in `src/main/java/com/example/coretracking/entity/CoreStatus.java`.
It is a `switch` expression, so adding a new status without deciding its rules does not compile. `canTransitionTo(...)` is built on it,
`CoreService.updateStatus` is the only caller, and the 409 message (`InvalidStatusTransitionException`) reads the allowed set from the
same method. `CoreStatusTest` holds an independent copy of the matrix and checks all 25 combinations against it.

## Error responses

Every error has the same JSON shape (class `ApiError`). `fieldErrors` appears only for validation errors.

```json
{"timestamp": "2026-11-13T16:44:24.543668500Z", "status": 409, "error": "Conflict",
 "message": "Cannot change status from SCRAPPED to RECEIVED. Allowed next statuses: none", "path": "/api/cores/10/status"}
```

| Code | When |
|---|---|
| `400` | Bean Validation failed (blank or missing field, date in the future, text too long), body missing or not valid JSON, a value of the wrong type (bad date, unknown status, id that is not a number), unknown `sort` property |
| `404` | No core with that id (`Core with id 9999 not found`) |
| `405` | HTTP method not supported on that URL (for example `DELETE`) |
| `409` | Status change not allowed by the transition rules |
| `500` | Anything unexpected. The client gets a generic message; the details are only in the log |

## Sample requests and responses

Real output captured on 2026-11-13 from the running application (jar started with `java -jar`), right after start-up with the 10
sample rows. JSON is re-formatted for readability only; the values are exactly as returned. The commands use Git Bash / Linux / macOS
quoting. In Windows PowerShell use `curl.exe` and escape the quotes inside `-d`, or use Swagger UI.

**List, first page of 3** (`totalElements` 10 = the sample data):

```
$ curl 'http://localhost:8080/api/cores?size=3'
HTTP/1.1 200
{"content": [
   {"id": 1, "partNumber": "PN-1001", "dealer": "Dealer North", "receivedDate": "2026-10-26", "status": "RECEIVED", "statusDisplayName": "Received"},
   {"id": 2, "partNumber": "PN-1002", "dealer": "Dealer North", "receivedDate": "2026-10-28", "status": "RECEIVED", "statusDisplayName": "Received"},
   {"id": 3, "partNumber": "PN-2001", "dealer": "Dealer South", "receivedDate": "2026-11-03", "status": "RECEIVED", "statusDisplayName": "Received"}],
 "page": 0, "size": 3, "totalElements": 10, "totalPages": 4}
```

**Last page** (`page=3&size=3` returns the single remaining core, id 10, which is `SCRAPPED`):

```
$ curl 'http://localhost:8080/api/cores?page=3&size=3'
HTTP/1.1 200
{"content": [
   {"id": 10, "partNumber": "PN-2003", "dealer": "Dealer South", "receivedDate": "2026-10-02", "status": "SCRAPPED", "statusDisplayName": "Scrapped"}],
 "page": 3, "size": 3, "totalElements": 10, "totalPages": 4}
```

**Status filter:**

```
$ curl 'http://localhost:8080/api/cores?status=INSPECTED'
HTTP/1.1 200
{"content": [
   {"id": 4, "partNumber": "PN-1001", "dealer": "Dealer East", "receivedDate": "2026-10-20", "status": "INSPECTED", "statusDisplayName": "Inspected"},
   {"id": 5, "partNumber": "PN-3002", "dealer": "Dealer West", "receivedDate": "2026-10-22", "status": "INSPECTED", "statusDisplayName": "Inspected"}],
 "page": 0, "size": 10, "totalElements": 2, "totalPages": 1}
```

**Get by id:**

```
$ curl 'http://localhost:8080/api/cores/1'
HTTP/1.1 200
{"id": 1, "partNumber": "PN-1001", "dealer": "Dealer North", "receivedDate": "2026-10-26", "status": "RECEIVED", "statusDisplayName": "Received"}
```

**Create (201 with a Location header):**

```
$ curl -X POST 'http://localhost:8080/api/cores' -H 'Content-Type: application/json' \
       -d '{"partNumber":"PN-4001","dealer":"Dealer North","receivedDate":"2026-11-12"}'
HTTP/1.1 201
Location: http://localhost:8080/api/cores/11
{"id": 11, "partNumber": "PN-4001", "dealer": "Dealer North", "receivedDate": "2026-11-12", "status": "RECEIVED", "statusDisplayName": "Received"}
```

**Allowed status change (200):**

```
$ curl -X PATCH 'http://localhost:8080/api/cores/11/status' -H 'Content-Type: application/json' -d '{"status":"INSPECTED"}'
HTTP/1.1 200
{"id": 11, "partNumber": "PN-4001", "dealer": "Dealer North", "receivedDate": "2026-11-12", "status": "INSPECTED", "statusDisplayName": "Inspected"}
```

**Invalid change, SCRAPPED to RECEIVED (409)** - core 10 is `SCRAPPED` in the sample data:

```
$ curl -X PATCH 'http://localhost:8080/api/cores/10/status' -H 'Content-Type: application/json' -d '{"status":"RECEIVED"}'
HTTP/1.1 409
{"timestamp": "2026-11-13T16:44:24.543668500Z", "status": 409, "error": "Conflict",
 "message": "Cannot change status from SCRAPPED to RECEIVED. Allowed next statuses: none", "path": "/api/cores/10/status"}
```

The other rejected cases returned the same shape (same-status, going backwards and skipping a step):

```
INSPECTED   -> INSPECTED     "Cannot change status from INSPECTED to INSPECTED. Allowed next statuses: DISASSEMBLED, SCRAPPED"
INSPECTED   -> RECEIVED      "Cannot change status from INSPECTED to RECEIVED. Allowed next statuses: DISASSEMBLED, SCRAPPED"
RECEIVED    -> DISASSEMBLED  "Cannot change status from RECEIVED to DISASSEMBLED. Allowed next statuses: INSPECTED, SCRAPPED"
```

**Unknown id (404):**

```
$ curl 'http://localhost:8080/api/cores/9999'
HTTP/1.1 404
{"timestamp": "2026-11-13T16:44:38.177593200Z", "status": 404, "error": "Not Found",
 "message": "Core with id 9999 not found", "path": "/api/cores/9999"}
```

**Validation error (400)** - blank part number, missing dealer, date in the future:

```
$ curl -X POST 'http://localhost:8080/api/cores' -H 'Content-Type: application/json' -d '{"partNumber":"","receivedDate":"2999-01-01"}'
HTTP/1.1 400
{"timestamp": "2026-11-13T16:44:39.813659800Z", "status": 400, "error": "Bad Request", "message": "Validation failed", "path": "/api/cores",
 "fieldErrors": [{"field": "dealer", "message": "is required"},
                 {"field": "partNumber", "message": "is required"},
                 {"field": "receivedDate", "message": "must not be in the future"}]}
```

**Unknown status value (400):**

```
$ curl -X PATCH 'http://localhost:8080/api/cores/11/status' -H 'Content-Type: application/json' -d '{"status":"SHIPPED"}'
HTTP/1.1 400
{"timestamp": "2026-11-13T16:44:41.347055100Z", "status": 400, "error": "Bad Request",
 "message": "Invalid value 'SHIPPED' for field 'status'. Allowed values: [RECEIVED, INSPECTED, DISASSEMBLED, REMANUFACTURED, SCRAPPED]",
 "path": "/api/cores/11/status"}
```

Also exercised in the same session (all returned the JSON error shape): missing `status` (400), `?status=SHIPPED` (400), `/api/cores/abc` (400),
`?sort=foo` (400) and `DELETE /api/cores/1` (405 with an `Allow: GET` header).

## Tests

85 tests, all in `src/test/java/com/example/coretracking/`:

| Class | Kind | What it covers | Tests |
|---|---|---|---|
| `entity/CoreStatusTest` | JUnit 5 unit, parameterised | **All 25 transitions** (6 allowed, 19 rejected) against an independent copy of the matrix, same-status, final statuses, display names | 39 |
| `service/CoreServiceTest` | Mockito (mocked repository) | create, get, list with and without filter, the 6 allowed transitions applied and saved, 6 rejected ones not saved, exact error messages, unknown id | 19 |
| `controller/CoreControllerIntegrationTest` | `@SpringBootTest` + MockMvc + H2 | create (201, Location), get, **404**, **pagination** (page size, default size, out-of-range values), sorting, **status filter**, filter + paging, **invalid transition 409** (final status, same status, skipped step), **validation 400** (blank, missing, future date, too long, unreadable date, unknown status, bad filter, bad sort, bad id), 405, a full lifecycle, Swagger and OpenAPI endpoints | 26 |
| `service/CoreStatusConcurrencyTest` | `@SpringBootTest` + threads | While one transaction holds a core's row lock, a PATCH waits, then sees the new status and gets 409 (no lost update) | 1 |

## What was run and what was not

**Run** on 2026-11-13 (Windows 11, JDK 21.0.10 Temurin, Maven 3.9.9, Spring Boot 3.3.13, springdoc 2.6.0):

- `mvn clean test`: **85 tests, 0 failures, 0 errors, BUILD SUCCESS** (15 main and 4 test source files compiled with `release 17`).
- `mvn package -DskipTests`, then the jar was started with `java -jar`: `Started CoreTrackingApplication in 7.63 seconds`, no errors in the log.
- `mvn spring-boot:run` also started the app (HTTP 200 from the list endpoint, 10 sample rows), and `?sort=receivedDate,desc` returned the newest core first.
- Every endpoint and every error path was called with curl (output above).
- Swagger: `/swagger-ui.html` answered `302` to `/swagger-ui/index.html` (`200`); `/v3/api-docs` lists the three paths and shows `status`, `page`, `size` and
  `sort` as separate query parameters of the list. The H2 console login page answered `200`.
- **Mutation check:** replacing the locking read with a plain `findById` made `CoreStatusConcurrencyTest` fail (`expected: <409> but was: <200>`); the original code was restored
  and verified identical, and the suite passes.

**Not run / not verified:**

- Not run on a JDK 17 runtime (JDK 21 was used; the compiler allows only Java 17 features and APIs).
- Not run on Linux or macOS.
- Swagger UI was checked by HTTP responses only, not opened in a browser. The H2 console login was not exercised, only reached.
- The generic `500` handler has no test (nothing in the app triggers it).
- `receivedDate` "not in the future" is checked against the server's clock and time zone; not tested across time zones.
- No performance or load testing.

## Assumptions

1. `dealer` is free text (a name or code), required, up to 100 characters. `partNumber` is required, up to 50 characters. The real part number format was not given, so there is no format check. The limits are my choice.
2. `receivedDate` is a date without a time, required, and today or earlier (checked against the server's date).
3. A new core is **always** `RECEIVED`. A `status` sent in the create request is ignored.
4. The same part may be returned by the same dealer on the same day more than once, so duplicates are allowed (Exercise 2(c) is about finding them).
5. Status values in requests are the exact upper-case names; matching is case-sensitive.
6. Only the current status is stored. There is no history of who changed it or when.
7. The real remanufacturing steps between disassembly and a finished core (cleaning, component evaluation, quality testing) are not separate statuses: only the five statuses in the brief exist.
8. A same-status update is treated like any other rejected change: **409** with the same message format.
9. No authentication or authorisation (not requested). The H2 console is open: development only.
10. `com.example.coretracking` is a placeholder package name. The sample part numbers and dealers are invented.
11. Pagination is zero-based and the default order is by `id`.

## Design decisions

- **Layers:** controller (HTTP only) -> service (rules, transactions, mapping) -> repository (JPA). Records are used for DTOs. The entity never leaves the
  service, so the API cannot leak persistence details.
- **Transition rules in an enum:** one place, exhaustive at compile time, trivially unit-testable, and the same source feeds the error message.
- **Status codes:** `409 Conflict` for a rule violation (a well-formed request that conflicts with the core's current state), `400` for malformed or invalid input, `404` for an unknown id.
- **Row lock on status update:** `findByIdForUpdate` runs `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`). Without it, two simultaneous PATCH calls could both validate against
  the same old status, and the second would overwrite the first, even out of a final status. Cost: a short wait on the same row. The alternative is optimistic locking (`@Version`), which
  would need a retry or an extra error response; here contention is tiny, so the simpler lock was chosen. Proven by `CoreStatusConcurrencyTest`.
- **One error shape:** `GlobalExceptionHandler` extends Spring's `ResponseEntityExceptionHandler`, so Spring's own errors (405, 415, ...) use the same JSON instead of falling into a catch-all 500.
- **Own page DTO:** `PageResponse` instead of serialising Spring Data's `Page`, whose JSON shape is not guaranteed to stay stable.
- **Schema by Hibernate, data by `data.sql`:** `ddl-auto=create-drop` builds the table from the entity; `data.sql` inserts 10 invented sample cores (3 RECEIVED, 2 INSPECTED, 2 DISASSEMBLED, 2 REMANUFACTURED, 1 SCRAPPED).
- **Logging (SLF4J):** `INFO` for changes (`Core 11 status changed RECEIVED -> INSPECTED`), `WARN` for rejected requests, `ERROR` with a stack trace for unexpected errors.
- **Small on purpose:** no Lombok, MapStruct, Flyway or security. `spring.jpa.open-in-view=false` because nothing is lazily loaded in the web layer.

## Possible improvements (not done on purpose)

PostgreSQL with Flyway migrations instead of H2 and `create-drop`; a status-history table (who, when, from, to); authentication; more filters (dealer, date range);
`@Version` optimistic locking if contention grows; API versioning; RFC 7807 problem details; a Maven wrapper.

## Suggested GitHub repository structure

The repository root is the `MyTodayInterview` folder:

```
MyTodayInterview/                  <- repository root
├── README.md                      overview, folder map and AI assistance note
├── core-tracking-api/             Exercise 1 - source code (this project)
│   ├── pom.xml
│   ├── README.md                  this file
│   ├── .gitignore                 ignores target/ and IDE files
│   └── src/
│       ├── main/java/com/example/coretracking/
│       │   ├── CoreTrackingApplication.java
│       │   ├── config/            OpenApiConfig
│       │   ├── controller/        CoreController
│       │   ├── dto/               ApiError, CoreResponse, CreateCoreRequest, PageResponse, UpdateStatusRequest
│       │   ├── entity/            Core, CoreStatus (transition rules)
│       │   ├── exception/         CoreNotFoundException, InvalidStatusTransitionException, GlobalExceptionHandler
│       │   ├── repository/        CoreRepository
│       │   └── service/           CoreService
│       ├── main/resources/        application.properties, data.sql
│       └── test/java/com/example/coretracking/   unit, Mockito, MockMvc and concurrency tests
├── sql/                           Exercise 2 - SQL scripts (01_schema ... 04_indexes) and README
└── docs/
    └── answers.docx               Exercises 2, 3, 4, 5 - written answers
```

Suggested repository name: `java-backend-assessment` (any name works). Commit message style: short and in the present tense.
