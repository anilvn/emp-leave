CREATE FUNCTION initialize_user_leave_balances() RETURNS trigger AS $$
BEGIN
    INSERT INTO leave_balances (user_id, leave_type_id, total_days, reserved_days)
    SELECT NEW.id,
           leave_types.id,
           CASE leave_types.name
               WHEN 'Vacation' THEN 20
               WHEN 'Sick' THEN 10
               WHEN 'Parental' THEN 10
               WHEN 'Sabbatical' THEN 5
           END,
           0
    FROM leave_types
    ON CONFLICT (user_id, leave_type_id) DO NOTHING;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_create_leave_balances
    AFTER INSERT ON users
    FOR EACH ROW EXECUTE FUNCTION initialize_user_leave_balances();

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
CROSS JOIN leave_types
ON CONFLICT (user_id, leave_type_id) DO NOTHING;
