CREATE TABLE users (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username              VARCHAR(100) NOT NULL UNIQUE,
    password_hash         VARCHAR(100) NOT NULL,
    role                  VARCHAR(20) NOT NULL CHECK (role IN ('EMPLOYEE', 'MANAGER', 'HR_ADMIN')),
    manager_id            BIGINT REFERENCES users (id),
    must_change_password  BOOLEAN NOT NULL DEFAULT TRUE,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    failed_logins         INTEGER NOT NULL DEFAULT 0 CHECK (failed_logins >= 0),
    locked_until          TIMESTAMPTZ
);
