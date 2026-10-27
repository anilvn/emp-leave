CREATE TABLE leave_balances (
    user_id       BIGINT NOT NULL REFERENCES users (id),
    leave_type_id BIGINT NOT NULL REFERENCES leave_types (id),
    total_days    INTEGER NOT NULL,
    reserved_days INTEGER NOT NULL DEFAULT 0,
    available_days INTEGER ,

    PRIMARY KEY (user_id, leave_type_id),

    CONSTRAINT leave_balances_available_days_check CHECK (
        total_days >= 0
        AND reserved_days >= 0
        AND total_days - reserved_days >= 0
    )
);