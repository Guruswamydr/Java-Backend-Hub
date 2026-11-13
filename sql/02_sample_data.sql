-- =====================================================================================
-- Exercise 2 - SQL | File 2 of 4: sample data
--
-- ILLUSTRATIVE DATA ONLY: invented part numbers, dealer ids and results, used to try the queries.
-- Dates are given as "days ago" and turned into real dates relative to today, so the data is
-- always "fresh" whenever the script is run.
--
-- What the data is built to show (with 30 cores):
--   (a) 30-day window : dealer 7 has 8 cores in total but only 1 inside the last 30 days (exactly 30 days ago is
--                       included, 31 days ago is not); dealers 5 and 6 tie for 5th place with 2 cores each.
--   (b) no inspection : a core received exactly 14 days ago is NOT listed ("more than 14 days"), 15 days ago is;
--                       core 15 was inspected twice, so it must be absent from the result (and an INNER JOIN used
--                       to find cores WITH inspections would list it twice, while EXISTS needs no DISTINCT).
--   (c) duplicates    : 3 groups of repeated (dealer, date, part): core ids 1+2, 8+9+10 and 24+25.
--                       Cores 19/20 (same dealer and part, different date) and 22 (same part and date as 1+2,
--                       different dealer) are deliberately NOT duplicates.
--
-- The ids are given explicitly so inspections can refer to them.
-- T-SQL: wrap each INSERT in  SET IDENTITY_INSERT <table> ON; ... SET IDENTITY_INSERT <table> OFF;
--        and replace  CURRENT_DATE - v.DaysAgo * INTERVAL '1' DAY  with
--        DATEADD(DAY, -v.DaysAgo, CAST(GETDATE() AS DATE))
-- =====================================================================================

INSERT INTO Cores (Id, PartNumber, DealerId, ReceivedDate, Status)
SELECT v.Id,
       v.PartNumber,
       v.DealerId,
       CURRENT_DATE - v.DaysAgo * INTERVAL '1' DAY,
       v.Status
FROM (VALUES
    -- Id, PartNumber, DealerId, DaysAgo, Status
    ( 1, 'PN-1001', 1,   2, 'RECEIVED'),
    ( 2, 'PN-1001', 1,   2, 'RECEIVED'),        -- duplicate of 1 (same dealer, date, part)
    ( 3, 'PN-1002', 1,   5, 'INSPECTED'),
    ( 4, 'PN-1003', 1,   9, 'DISASSEMBLED'),
    ( 5, 'PN-1004', 1,  16, 'RECEIVED'),        -- older than 14 days, never inspected
    ( 6, 'PN-1005', 1,  25, 'REMANUFACTURED'),

    ( 7, 'PN-2001', 2,   3, 'RECEIVED'),
    ( 8, 'PN-2002', 2,  10, 'RECEIVED'),
    ( 9, 'PN-2002', 2,  10, 'RECEIVED'),        -- 8, 9, 10 are the same dealer, date and part
    (10, 'PN-2002', 2,  10, 'RECEIVED'),
    (11, 'PN-2003', 2,  14, 'RECEIVED'),        -- exactly 14 days ago, never inspected: NOT "more than 14 days"

    (12, 'PN-3001', 3,   1, 'RECEIVED'),
    (13, 'PN-3002', 3,  15, 'RECEIVED'),        -- 15 days ago, never inspected
    (14, 'PN-3003', 3,  20, 'INSPECTED'),
    (15, 'PN-3004', 3,  28, 'SCRAPPED'),        -- two inspections

    (16, 'PN-4001', 4,   4, 'RECEIVED'),
    (17, 'PN-4002', 4,  12, 'INSPECTED'),
    (18, 'PN-4003', 4,  29, 'RECEIVED'),        -- never inspected

    (19, 'PN-5001', 5,   6, 'RECEIVED'),
    (20, 'PN-5001', 5,   8, 'RECEIVED'),        -- same dealer and part as 19 but a different date

    (21, 'PN-6001', 6,   7, 'RECEIVED'),
    (22, 'PN-1001', 6,   2, 'RECEIVED'),        -- same part and date as 1 and 2 but a different dealer

    (23, 'PN-7001', 7,  30, 'RECEIVED'),        -- exactly 30 days ago: inside the 30-day window; never inspected
    (24, 'PN-7002', 7,  45, 'RECEIVED'),        -- never inspected
    (25, 'PN-7002', 7,  45, 'RECEIVED'),        -- duplicate of 24
    (26, 'PN-7003', 7,  60, 'REMANUFACTURED'),
    (27, 'PN-7004', 7,  75, 'SCRAPPED'),
    (28, 'PN-7005', 7,  31, 'RECEIVED'),        -- 31 days ago: outside the 30-day window; never inspected
    (29, 'PN-7006', 7, 100, 'REMANUFACTURED'),
    (30, 'PN-7007', 7, 110, 'REMANUFACTURED')
) AS v (Id, PartNumber, DealerId, DaysAgo, Status);

INSERT INTO Inspections (Id, CoreId, InspectedDate, Result)
SELECT v.Id,
       v.CoreId,
       CURRENT_DATE - v.DaysAgo * INTERVAL '1' DAY,
       v.Result
FROM (VALUES
    -- Id, CoreId, DaysAgo, Result
    ( 1,  3,  4, 'PASS'),
    ( 2,  4,  7, 'PASS'),
    ( 3,  6, 24, 'PASS'),
    ( 4, 14, 19, 'PASS'),
    ( 5, 15, 27, 'FAIL'),                       -- core 15 was inspected twice
    ( 6, 15, 25, 'PASS'),
    ( 7, 17, 11, 'PASS'),
    ( 8, 26, 58, 'PASS'),
    ( 9, 27, 73, 'FAIL'),
    (10, 29, 98, 'PASS'),
    (11, 30, 108, 'PASS')
) AS v (Id, CoreId, DaysAgo, Result);

-- PostgreSQL only, needed only if you INSERT more rows afterwards: the rows above used explicit ids, so move
-- the identity counters past them. (T-SQL does this by itself.)
-- SELECT setval(pg_get_serial_sequence('cores', 'id'), (SELECT MAX(Id) FROM Cores));
-- SELECT setval(pg_get_serial_sequence('inspections', 'id'), (SELECT MAX(Id) FROM Inspections));
