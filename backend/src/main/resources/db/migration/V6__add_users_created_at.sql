-- Story 1.3 lists users.created_at among the table's columns; V3 omitted it. Added as its own
-- versioned migration rather than editing V3, so already-applied databases keep a valid Flyway
-- checksum. DEFAULT now() backfills the seeded demo cast (inserted in V5) and populates future
-- inserts, which never set the column explicitly (NFR12: timestamptz).

ALTER TABLE users ADD COLUMN created_at timestamptz NOT NULL DEFAULT now();
