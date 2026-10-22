-- Story 1.2: audit_events and access_log are the only two tables this story creates.
-- Both are insert-only (see V2 for the immutability trigger) so nothing here needs an
-- updated_at column or an update-tracking mechanism.

CREATE TABLE audit_events (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity       VARCHAR(100) NOT NULL,
    entity_id    BIGINT,
    action       VARCHAR(100) NOT NULL,
    actor_id     BIGINT NOT NULL,
    details_json jsonb,
    created_at   timestamptz NOT NULL
);

-- actor_id/resource_id are intentionally nullable: an unauthenticated attempt, or a
-- collection-level check, has no actor or resource id to record (see Intent/Never).
CREATE TABLE access_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id    BIGINT,
    resource    VARCHAR(100) NOT NULL,
    resource_id BIGINT,
    allowed     BOOLEAN NOT NULL,
    created_at  timestamptz NOT NULL
);
