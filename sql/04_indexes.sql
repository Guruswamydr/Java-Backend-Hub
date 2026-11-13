-- =====================================================================================
-- Exercise 2 - SQL | File 4 of 4: recommended indexes
--
-- The syntax is the same in PostgreSQL and SQL Server.
-- Primary keys (Cores.Id and Inspections.Id) are already indexed. One line of reasoning per index below;
-- the longer explanation (seek vs scan, covering, write cost, EXPLAIN) is in README.md.
-- Run this after 01_schema.sql and 02_sample_data.sql.
-- =====================================================================================

-- 1. Serves (a), and the date filter of (b).
--    The date range is a seek on the leading column, and DealerId is stored in the index,
--    so the per-dealer count can be done from the index alone (covering).
CREATE INDEX IX_Cores_ReceivedDate_DealerId
    ON Cores (ReceivedDate, DealerId);

-- 2. Serves (c).
--    GROUP BY DealerId, ReceivedDate, PartNumber can read the entries already in group order:
--    no sort, and no table access because all three columns are in the index (covering).
CREATE INDEX IX_Cores_DealerId_ReceivedDate_PartNumber
    ON Cores (DealerId, ReceivedDate, PartNumber);

-- 3. Serves (b).
--    "Does this core have an inspection?" becomes one index lookup per core instead of a scan of Inspections.
--    (Neither PostgreSQL nor SQL Server indexes a foreign-key column automatically.)
CREATE INDEX IX_Inspections_CoreId
    ON Inspections (CoreId);

-- Optional, not created here: make (b) fully covering so it never has to visit the Cores table.
--   CREATE INDEX IX_Cores_ReceivedDate_Covering ON Cores (ReceivedDate) INCLUDE (PartNumber, DealerId, Status);
--   (PostgreSQL 11+ and SQL Server both support INCLUDE. SQL Server adds the clustered key Id to every
--    nonclustered index by itself; in PostgreSQL add Id to the INCLUDE list if the query returns it.)
-- Every extra index must be kept up to date on every INSERT, UPDATE and DELETE, so add one only for a query
-- that really needs it.
