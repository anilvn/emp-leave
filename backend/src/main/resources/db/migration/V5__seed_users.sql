-- INSERT INTO users (id, username, password_hash, role, manager_id, must_change_password, active)
-- OVERRIDING SYSTEM VALUE
-- VALUES
--     (1, 'hr.admin',   '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'HR_ADMIN', NULL, FALSE, TRUE),
--     (2, 'mgr.rossi',  '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'MANAGER',  1,    FALSE, TRUE),
--     (3, 'mgr.chen',   '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'MANAGER',  2,    FALSE, TRUE),
--     (4, 'mgr.ortiz',  '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'MANAGER',  2,    FALSE, TRUE),
--     (5, 'emp.jsmith', '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'EMPLOYEE', 3,    FALSE, TRUE),
--     (6, 'emp.alee',   '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'EMPLOYEE', 3,    FALSE, TRUE),
--     (7, 'emp.cwong',  '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'EMPLOYEE', 3,    FALSE, TRUE),
--     (8, 'emp.dpatel', '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'EMPLOYEE', 3,    FALSE, TRUE),
--     (9, 'emp.emurphy','$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'EMPLOYEE', 4,    FALSE, TRUE),
--     (10,'emp.fkim',   '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'EMPLOYEE', 4,    FALSE, TRUE),
--     (11,'emp.gsingh', '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'EMPLOYEE', 4,    FALSE, TRUE),
--     (12,'emp.hnguyen','$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'EMPLOYEE', 4,    FALSE, TRUE);

-- SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT MAX(id) FROM users));



INSERT INTO users (id, username, password_hash, role, manager_id, must_change_password, active)
OVERRIDING SYSTEM VALUE
VALUES
    (1, 'hr.admin',   '$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri', 'HR_ADMIN', NULL, FALSE, TRUE);
SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT MAX(id) FROM users));
