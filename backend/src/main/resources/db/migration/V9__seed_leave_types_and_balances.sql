INSERT INTO leave_types (name)
VALUES ('Vacation'), ('Sick'), ('Parental'), ('Sabbatical');

INSERT INTO leave_balances (user_id, leave_type_id, total_days, reserved_days)
SELECT users.id,
       leave_types.id,
       CASE leave_types.name
           WHEN 'Vacation' THEN 20
           WHEN 'Sick' THEN 10
           WHEN 'Parental' THEN 10
           WHEN 'Sabbatical' THEN 5
       END,
       0
FROM users
CROSS JOIN leave_types;
