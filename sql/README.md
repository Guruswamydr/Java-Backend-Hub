# Exercise 2 - SQL

Tables: `Cores(Id, PartNumber, DealerId, ReceivedDate, Status)` and `Inspections(Id, CoreId, InspectedDate, Result)`.
Dialect: **PostgreSQL**, with **SQL Server (T-SQL)** variants in comments (`-- T-SQL: ...`) where the syntax differs.

> **AI assistance:** these scripts and this note were drafted with an AI assistant (Claude Code). What was actually
> run, and what was not, is listed below. Review everything before relying on it.

## Files

| File | What it contains |
|---|---|
| `01_schema.sql` | DDL for both tables (primary keys, foreign key, status check) |
| `02_sample_data.sql` | 30 cores and 11 inspections of **invented** data, built to show every edge case |
| `03_queries.sql` | The three queries (a), (b), (c), plus the tie variant (a2) and the LEFT JOIN alternative (b2) |
| `04_indexes.sql` | Recommended indexes, one line of reasoning each |

Run them in that order from this folder, for example with PostgreSQL:

```
psql -d <your_database> -f 01_schema.sql -f 02_sample_data.sql -f 03_queries.sql -f 04_indexes.sql
```

## What was run, and what was not

| Item | Status |
|---|---|
| `01` to `04` executed, in order, with no errors | **Run** on **H2 2.2.224** in PostgreSQL compatibility mode (`MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH`), on 2026-11-13 |
| The result tables below | **Real output** of that H2 run |
| Same results with the indexes created | **Run**: the five queries returned the same row counts (5, 6, 7, 7, 3) after `04_indexes.sql` |
| Execution on a real **PostgreSQL** server | **Not run** - PostgreSQL is not installed on this machine |
| The **T-SQL** variants (comments) | **Not run** - no SQL Server available. They were written by hand and should be tested |
| `EXPLAIN` plans, timings, index usage | **Not run** - the data set is tiny and PostgreSQL was not available, so nothing is claimed about plans |

The statements use only standard SQL that H2 and PostgreSQL both accept (for example `INTERVAL '30' DAY` instead of
`INTERVAL '30 days'`), so a PostgreSQL run is expected to behave the same. That expectation is **not verified**.

## Assumptions

- `ReceivedDate` and `InspectedDate` are `DATE` values with no time part.
- `DealerId` is a plain integer (no `Dealers` table was given, so no foreign key). `Status` uses the same five values as Exercise 1.
- **"Last 30 days"** = `ReceivedDate` from (today - 30 days) up to today. A core received exactly 30 days ago counts; 31 days ago does not.
- **"More than 14 days ago"** = `ReceivedDate` strictly earlier than (today - 14 days). Exactly 14 days ago is not "more than".
- Every core counts in (a), whatever its `Status`. Cores can legitimately repeat (a dealer can return two cores of the same part on one day), so there is no unique constraint; (c) exists to find such repeats.
- A core can have no, one or several inspection rows.
- The sample data is expressed as "days ago", so the dates (but not the ids or counts) change with the day you run it.

## Queries and results

Results below are from the H2 run on 2026-11-13 (so "today" was 2026-11-13).

### (a) Five dealers that returned the most cores in the last 30 days

```sql
SELECT   DealerId,
         COUNT(*) AS CoresReturned
FROM     Cores
WHERE    ReceivedDate >= CURRENT_DATE - INTERVAL '30' DAY
GROUP BY DealerId
ORDER BY CoresReturned DESC,
         DealerId ASC
LIMIT    5;
```

| dealerid | coresreturned |
|---|---|
| 1 | 6 |
| 2 | 5 |
| 3 | 4 |
| 4 | 3 |
| 5 | 2 |

**Tie handling:** dealers 5 and 6 both have 2 cores. The query adds `DealerId` as a tie-breaker, so it always returns exactly five
rows and the same five every time: the lower `DealerId` wins (dealer 5). If the business wants every dealer that ties for
fifth place, use (a2): `FETCH FIRST 5 ROWS WITH TIES` (PostgreSQL 13+), or `SELECT TOP (5) WITH TIES` in T-SQL. It returned six
rows here (dealer 6 as well). Dealer 7 has 8 cores in total but only one in the window, so it is correctly absent.

### (b) Cores received more than 14 days ago with no inspection record

```sql
SELECT   c.Id, c.PartNumber, c.DealerId, c.ReceivedDate, c.Status
FROM     Cores c
WHERE    c.ReceivedDate < CURRENT_DATE - INTERVAL '14' DAY
  AND    NOT EXISTS (SELECT 1
                     FROM   Inspections i
                     WHERE  i.CoreId = c.Id)
ORDER BY c.ReceivedDate, c.Id;
```

| id | partnumber | dealerid | receiveddate | status |
|---|---|---|---|---|
| 24 | PN-7002 | 7 | 2026-09-29 | RECEIVED |
| 25 | PN-7002 | 7 | 2026-09-29 | RECEIVED |
| 28 | PN-7005 | 7 | 2026-10-13 | RECEIVED |
| 23 | PN-7001 | 7 | 2026-10-14 | RECEIVED |
| 18 | PN-4003 | 4 | 2026-10-15 | RECEIVED |
| 5 | PN-1004 | 1 | 2026-10-28 | RECEIVED |
| 13 | PN-3002 | 3 | 2026-10-29 | RECEIVED |

Core 11 (received exactly 14 days ago, never inspected) is correctly **not** listed, and core 15 (inspected twice) is not listed either.

**Why `NOT EXISTS`:** it says what we mean, it can stop at the first matching inspection, it never multiplies rows, and unlike
`NOT IN (SELECT CoreId ...)` it is not broken by a `NULL` in the subquery (`NOT IN` then returns nothing).

**LEFT JOIN alternative (b2)**, same rows (confirmed in the run):

```sql
SELECT   c.Id, c.PartNumber, c.DealerId, c.ReceivedDate, c.Status
FROM     Cores c
LEFT JOIN Inspections i ON i.CoreId = c.Id
WHERE    c.ReceivedDate < CURRENT_DATE - INTERVAL '14' DAY
  AND    i.Id IS NULL
ORDER BY c.ReceivedDate, c.Id;
```

Many optimisers produce the same "anti join" plan for both. `NOT EXISTS` is easier to read and harder to get wrong.

### (c) Part numbers that appear more than once for the same dealer on the same received date

```sql
SELECT   DealerId,
         ReceivedDate,
         PartNumber,
         COUNT(*) AS Occurrences
FROM     Cores
GROUP BY DealerId, ReceivedDate, PartNumber
HAVING   COUNT(*) > 1
ORDER BY DealerId, ReceivedDate, PartNumber;
```

| dealerid | receiveddate | partnumber | occurrences |
|---|---|---|---|
| 1 | 2026-11-11 | PN-1001 | 2 |
| 2 | 2026-11-03 | PN-2002 | 3 |
| 7 | 2026-09-29 | PN-7002 | 2 |

`WHERE` filters rows before grouping; `HAVING` filters the groups after. The same part for a different dealer, or for the same
dealer on a different date, is not a duplicate (cores 19/20 and 22 in the sample data prove it).

## Recommended indexes

Primary keys are already indexed. See `04_indexes.sql`.

| Index | Serves | Why (one line) |
|---|---|---|
| `Cores (ReceivedDate, DealerId)` | (a), date filter of (b) | The date range is a seek on the leading column, and `DealerId` is in the index, so the per-dealer count can come from the index alone |
| `Cores (DealerId, ReceivedDate, PartNumber)` | (c) | `GROUP BY` reads entries already in group order, with no sort and no table access (covering) |
| `Inspections (CoreId)` | (b) | "Does this core have an inspection?" becomes one index lookup per core instead of a scan of `Inspections` (neither PostgreSQL nor SQL Server indexes a foreign key automatically) |

## How indexes improve execution

- **Seek vs scan.** Without a suitable index the database must read every row (a *full table scan*, or *sequential scan*) and test
  the filter on each. A B-tree index is sorted, so the database can *seek*: jump straight to the first matching entry and read only
  the range it needs (`ReceivedDate >= X` touches the last 30 days, not the whole table). The cost drops from "all rows" to
  "matching rows plus a few page reads".
- **Covering index.** If every column a query needs is inside the index (`DealerId` and `ReceivedDate` for query (a)), the engine
  never visits the table: an *index-only scan* in PostgreSQL, no *key lookup* in SQL Server. With a non-covering index it finds the
  entries in the index and then makes one extra table lookup per row, which is slow when many rows match. Extra columns can be added
  with `INCLUDE (...)` (see the optional covering index in `04_indexes.sql`).
- **Column order matters.** An index on `(A, B)` can seek on `A`, or on `A` and `B`, but not on `B` alone. Equality columns usually go first and
  the range column after them; here (a) ranges on its first column, and (c) uses the three grouping columns in order.

## Performance considerations

- **Write sargable date predicates.** Compare the bare column with a value computed once: `ReceivedDate >= CURRENT_DATE - INTERVAL '30' DAY`.
- **Never wrap an indexed column in a function.** `WHERE DATE_PART('day', CURRENT_DATE - ReceivedDate) <= 30`,
  `WHERE YEAR(ReceivedDate) = 2026` or T-SQL `WHERE DATEDIFF(DAY, ReceivedDate, GETDATE()) <= 30` force the function to run on every row, so the index
  cannot be used for a seek. Move the arithmetic to the other side of the comparison. (If a function on the column is unavoidable, an expression index can help.)
- **Extra indexes are not free.** Every `INSERT`, `UPDATE` and `DELETE` must maintain every index, and indexes use disk and cache.
  Add an index for a query that needs it, not "just in case". Three targeted indexes are enough here.
- **A date filter is not always selective.** In (b), "older than 14 days" matches most of an ageing table, so the planner may still
  choose a scan on `Cores`. What makes (b) fast is the anti join into `Inspections` through `Inspections (CoreId)`. Do not force an index; look at the plan.
- **Read the plan with `EXPLAIN`.** PostgreSQL: `EXPLAIN (ANALYZE, BUFFERS) <query>`. Check for `Seq Scan` vs `Index Scan` / `Index Only Scan`, estimated vs actual row
  counts (stale statistics: run `ANALYZE`), `Hash Anti Join` for (b), and sort or hash steps for the aggregates. `ANALYZE` actually runs the
  statement, so wrap `INSERT`/`UPDATE` in a transaction you roll back. SQL Server: `SET STATISTICS IO, TIME ON` and the actual execution plan, where
  you want an *Index Seek* rather than an *Index/Clustered Index Scan*, and no *Key Lookup* on a large number of rows. Tiny tables often use a scan even when an
  index exists, so judge plans on realistic data volumes.

## T-SQL differences used in the comments

| Topic | PostgreSQL | T-SQL |
|---|---|---|
| Date arithmetic | `CURRENT_DATE - INTERVAL '30' DAY` | `DATEADD(DAY, -30, CAST(GETDATE() AS DATE))` |
| First N rows | `LIMIT 5` | `SELECT TOP (5) ...` |
| First N with ties | `FETCH FIRST 5 ROWS WITH TIES` (13+) | `SELECT TOP (5) WITH TIES ... ORDER BY ...` |
| Auto-number column | `GENERATED BY DEFAULT AS IDENTITY` | `IDENTITY(1,1)`; `SET IDENTITY_INSERT <table> ON` to insert explicit ids |
| Identifier case | unquoted names are folded to lower case | case is kept (default collation is case-insensitive) |
