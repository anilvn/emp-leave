CREATE TABLE active_sessions (
    token_id      VARCHAR(36) PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users (id),
    last_seen_at  TIMESTAMPTZ NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX active_sessions_user_id_idx ON active_sessions (user_id);
