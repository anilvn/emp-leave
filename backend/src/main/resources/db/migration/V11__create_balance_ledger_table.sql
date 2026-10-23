CREATE TABLE balance_ledger (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES users (id),
    leave_type_id  BIGINT NOT NULL REFERENCES leave_types (id),
    change_days    INTEGER NOT NULL,
    kind           VARCHAR(20) NOT NULL CHECK (kind IN ('SET', 'ADJUST', 'RESERVE', 'RELEASE', 'DEDUCT')),
    reason         TEXT,
    actor_id       BIGINT NOT NULL REFERENCES users (id),
    request_id     BIGINT REFERENCES leave_requests (id),
    created_at     TIMESTAMPTZ NOT NULL
);
