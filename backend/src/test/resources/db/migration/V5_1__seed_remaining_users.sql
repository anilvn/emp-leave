-- Test-only migration: restore the full 12-user cast that V5 currently seeds in commented-out
-- form. All users share the bcrypt of "Passw0rd1" and are active with must_change_password=false.
-- Runs between V5 (insert hr.admin) and V6 (add created_at), before V14's trigger exists,
-- so no leave_balance rows are auto-created here; V9 seeds all 48 rows via its cross-join.
INSERT INTO users (id, username, password_hash, role, manager_id, must_change_password, active)
OVERRIDING SYSTEM VALUE
VALUES
    (2,  'mgr.rossi',  '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'MANAGER',  1,    FALSE, TRUE),
    (3,  'mgr.chen',   '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'MANAGER',  2,    FALSE, TRUE),
    (4,  'mgr.ortiz',  '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'MANAGER',  2,    FALSE, TRUE),
    (5,  'emp.jsmith', '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'EMPLOYEE', 3,    FALSE, TRUE),
    (6,  'emp.alee',   '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'EMPLOYEE', 3,    FALSE, TRUE),
    (7,  'emp.cwong',  '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'EMPLOYEE', 3,    FALSE, TRUE),
    (8,  'emp.dpatel', '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'EMPLOYEE', 3,    FALSE, TRUE),
    (9,  'emp.emurphy','$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'EMPLOYEE', 4,    FALSE, TRUE),
    (10, 'emp.fkim',   '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'EMPLOYEE', 4,    FALSE, TRUE),
    (11, 'emp.gsingh', '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'EMPLOYEE', 4,    FALSE, TRUE),
    (12, 'emp.hnguyen','$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'EMPLOYEE', 4,    FALSE, TRUE);

SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT MAX(id) FROM users));
