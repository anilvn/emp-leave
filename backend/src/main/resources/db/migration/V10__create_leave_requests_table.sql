CREATE TABLE leave_requests (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id      BIGINT NOT NULL REFERENCES users (id),
    approver_id      BIGINT NOT NULL REFERENCES users (id),
    leave_type_id    BIGINT NOT NULL REFERENCES leave_types (id),
    start_date       DATE NOT NULL,
    end_date         DATE NOT NULL,
    working_days     INTEGER NOT NULL CHECK (working_days > 0),
    notes            TEXT,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    submitted_at     TIMESTAMPTZ NOT NULL,
    decided_at       TIMESTAMPTZ,
    decision_comment TEXT,
    CONSTRAINT leave_requests_dates_check CHECK (end_date >= start_date)
);
