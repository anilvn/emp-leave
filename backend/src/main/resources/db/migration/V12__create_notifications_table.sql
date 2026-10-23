CREATE TABLE notifications (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users (id),
    request_id BIGINT NOT NULL REFERENCES leave_requests (id),
    kind       VARCHAR(20) NOT NULL CHECK (kind IN ('SUBMITTED', 'APPROVED', 'REJECTED')),
    subject    VARCHAR(500) NOT NULL,
    sent_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_notifications_user_sent_at
    ON notifications (user_id, sent_at DESC, id DESC);
