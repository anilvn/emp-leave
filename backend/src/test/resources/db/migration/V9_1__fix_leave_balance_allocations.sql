-- Test-only migration: correct the leave_balance seed values to match what integration tests assert.
-- V9 (and the V14 trigger) seed Parental=10 and Sabbatical=5; tests expect Parental=30 and Sabbatical=0.
-- Also sets available_days = total_days for every row; V9 left available_days NULL.
UPDATE leave_balances
SET total_days     = CASE lt.name
                         WHEN 'Parental'   THEN 30
                         WHEN 'Sabbatical' THEN 0
                         ELSE leave_balances.total_days
                     END,
    reserved_days  = 0,
    available_days = CASE lt.name
                         WHEN 'Vacation'   THEN 20
                         WHEN 'Sick'       THEN 10
                         WHEN 'Parental'   THEN 30
                         WHEN 'Sabbatical' THEN 0
                         ELSE leave_balances.total_days
                     END
FROM leave_types lt
WHERE leave_balances.leave_type_id = lt.id;
