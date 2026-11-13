-- =====================================================================================
-- Exercise 2 - SQL | File 3 of 4: the three queries
--
-- Dialect : PostgreSQL. SQL Server (T-SQL) variants are in comments that start with "T-SQL:".
-- Date math: "CURRENT_DATE - INTERVAL '30' DAY" is the standard-SQL interval literal (PostgreSQL also accepts
--            INTERVAL '30 days'). T-SQL has no INTERVAL type: it uses DATEADD(DAY, -30, CAST(GETDATE() AS DATE)).
-- Sargable : every date filter compares the bare column with a value calculated once ("column >= value").
--            No function is applied to the column, so an index on the column can be used (see 04_indexes.sql).
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- (a) The five dealers that returned the most cores in the last 30 days
--
-- "Last 30 days" = ReceivedDate from (today - 30 days) up to today, so a core received exactly 30 days ago
-- counts and one received 31 days ago does not. Every core counts, whatever its Status.
--
-- TIE HANDLING: the dealer id is the tie-breaker (ORDER BY ... DealerId), so the query always returns exactly
-- five rows and the same rows every time. If two dealers tie for 5th place, the lower DealerId wins.
-- If the business would rather see every dealer that ties for 5th place, use query (a2) below.
--
-- T-SQL: SELECT TOP (5) DealerId, COUNT(*) AS CoresReturned
--        FROM Cores
--        WHERE ReceivedDate >= DATEADD(DAY, -30, CAST(GETDATE() AS DATE))
--        GROUP BY DealerId
--        ORDER BY COUNT(*) DESC, DealerId ASC;
-- -------------------------------------------------------------------------------------
SELECT   DealerId,
         COUNT(*) AS CoresReturned
FROM     Cores
WHERE    ReceivedDate >= CURRENT_DATE - INTERVAL '30' DAY
GROUP BY DealerId
ORDER BY CoresReturned DESC,
         DealerId ASC
LIMIT    5;

-- (a2) Same question, but keep every dealer that ties with the 5th place (can return more than 5 rows).
--      FETCH FIRST ... WITH TIES needs PostgreSQL 13 or later. There must be NO tie-breaker in the ORDER BY.
-- T-SQL: SELECT TOP (5) WITH TIES DealerId, COUNT(*) AS CoresReturned
--        FROM Cores
--        WHERE ReceivedDate >= DATEADD(DAY, -30, CAST(GETDATE() AS DATE))
--        GROUP BY DealerId
--        ORDER BY COUNT(*) DESC;
SELECT   DealerId,
         COUNT(*) AS CoresReturned
FROM     Cores
WHERE    ReceivedDate >= CURRENT_DATE - INTERVAL '30' DAY
GROUP BY DealerId
ORDER BY CoresReturned DESC
FETCH FIRST 5 ROWS WITH TIES;


-- -------------------------------------------------------------------------------------
-- (b) Cores received more than 14 days ago that have no inspection record
--
-- "More than 14 days ago" = ReceivedDate earlier than (today - 14 days): a core received exactly 14 days ago is
-- not listed, one received 15 days ago is.
--
-- NOT EXISTS is preferred: it says what we mean ("no matching inspection"), stops at the first matching
-- inspection row, never multiplies rows, and (unlike NOT IN) is not broken by NULLs.
--
-- T-SQL: replace the date filter with  c.ReceivedDate < DATEADD(DAY, -14, CAST(GETDATE() AS DATE))
-- -------------------------------------------------------------------------------------
SELECT   c.Id, c.PartNumber, c.DealerId, c.ReceivedDate, c.Status
FROM     Cores c
WHERE    c.ReceivedDate < CURRENT_DATE - INTERVAL '14' DAY
  AND    NOT EXISTS (SELECT 1
                     FROM   Inspections i
                     WHERE  i.CoreId = c.Id)
ORDER BY c.ReceivedDate, c.Id;

-- (b2) LEFT JOIN alternative: join the inspections, keep the cores where no inspection was found.
--      Returns the same rows. Cores with one or more inspections are filtered out by "i.Id IS NULL"; a core
--      with no inspection matches nothing in the LEFT JOIN, so it appears exactly once (with NULLs on the
--      Inspections side). Many optimisers turn both forms into the same "anti join" plan; NOT EXISTS is
--      simply easier to read and harder to get wrong.
SELECT   c.Id, c.PartNumber, c.DealerId, c.ReceivedDate, c.Status
FROM     Cores c
LEFT JOIN Inspections i ON i.CoreId = c.Id
WHERE    c.ReceivedDate < CURRENT_DATE - INTERVAL '14' DAY
  AND    i.Id IS NULL
ORDER BY c.ReceivedDate, c.Id;


-- -------------------------------------------------------------------------------------
-- (c) Part numbers that appear more than once for the same dealer on the same received date
--
-- Group by the three columns that define "the same", keep only the groups with more than one row.
-- (HAVING filters groups after they are built; WHERE would filter rows before.)
-- Works unchanged in T-SQL.
-- -------------------------------------------------------------------------------------
SELECT   DealerId,
         ReceivedDate,
         PartNumber,
         COUNT(*) AS Occurrences
FROM     Cores
GROUP BY DealerId, ReceivedDate, PartNumber
HAVING   COUNT(*) > 1
ORDER BY DealerId, ReceivedDate, PartNumber;
