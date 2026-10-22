---
title: 'Story 1.2: An Immutable Audit Trail and Access Log'
type: 'feature'
created: '2026-10-22'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'd7f2e4a3d74460e8ea012a9adbac363a6cde6cea'
context: ['{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Nothing in the codebase can yet record an action or an authorization decision in a form nobody — including someone with raw DB access — can alter, so no later story (login, lockout, RBAC, leave actions) has a trustworthy place to write to.

**Approach:** Add the `audit_events`/`access_log` schema via Flyway with a DB-level `BEFORE UPDATE OR DELETE` trigger, and a leaf `audit.AuditService` that exposes only insert-only `recordEvent`/`recordAccess` methods, taking the actor id as an explicit parameter — never derived from `SecurityContext`. Nothing calls it yet; that starts in Story 1.3+.

## Boundaries & Constraints

**Always:**
- `audit`'s `@Entity`/`@Repository` classes are package-private (or otherwise non-public) — only `AuditService` is callable from outside the package (AD-2).
- `AuditService.recordEvent`/`recordAccess` take the actor id as an explicit `Long`/`long` parameter; `audit` never imports `SecurityContextHolder` (AD-4).
- The immutability trigger is created by a Flyway migration, never applied by hand (AD-6).
- `audit` imports only `common` (and, if ever needed, `security`) — never another domain module (AD-2 leaf status).

**Never:**
- No `update`/`delete` method on `AuditService` or on either repository interface — don't extend `CrudRepository`/`JpaRepository` (which pulls in `delete*`); extend plain `Repository<T, ID>` and declare only a `save` method.
- Don't wire any caller into `AuditService` yet (no login, lockout, or RBAC exists until later stories) — this story only builds the write path and proves it.
- Don't give `access_log.actor_id` or `access_log.resource_id` a `NOT NULL` constraint — an unauthenticated attempt, or a collection-level check, has no actor/resource id to record.

</frozen-after-approval>

## Code Map

- `backend/src/main/resources/db/migration/` -- empty except `.gitkeep` (Story 1.1 left an empty baseline); this story adds the first real migrations, `V1__`/`V2__`.
- `backend/src/main/resources/application.yml` -- `spring.flyway.locations: classpath:db/migration` already set; dev profile has `flyway.out-of-order: true`; nothing to change here.
- `backend/src/main/java/com/company/leave/audit/package-info.java` -- already exists, names the module's responsibility; leave as-is.
- `backend/src/main/java/com/company/leave/common/` -- `ErrorCode`/`ApiException`/`GlobalExceptionHandler` pattern to follow for style (Javadoc on the "why", not the "what"); this story raises no `ApiException` since nothing calls `AuditService` from a controller yet.
- `backend/src/test/java/com/company/leave/CompanyLeaveApplicationTests.java` -- asserts `tables.containsExactly("flyway_schema_history")`; this story adds two domain tables, so this assertion must be updated to `containsExactlyInAnyOrder("flyway_schema_history", "audit_events", "access_log")` or it starts failing.
- Hibernate (Spring Boot 4.1.1 ships Hibernate 7) maps JSON natively via `@JdbcTypeCode(SqlTypes.JSON)` on a `String` field — no extra dependency needed for `details_json`.

## Tasks & Acceptance

**Execution:**
- [x] `backend/src/main/resources/db/migration/V1__create_audit_tables.sql` -- add -- creates `audit_events (id BIGINT GENERATED ALWAYS AS IDENTITY, entity VARCHAR(100) NOT NULL, entity_id BIGINT, action VARCHAR(100) NOT NULL, actor_id BIGINT NOT NULL, details_json jsonb, created_at timestamptz NOT NULL)` and `access_log (id BIGINT GENERATED ALWAYS AS IDENTITY, actor_id BIGINT, resource VARCHAR(100) NOT NULL, resource_id BIGINT, allowed BOOLEAN NOT NULL, created_at timestamptz NOT NULL)`
- [x] `backend/src/main/resources/db/migration/V2__add_audit_immutability_trigger.sql` -- add -- one `plpgsql` function that `RAISE EXCEPTION`s, reused by a `BEFORE UPDATE OR DELETE` trigger on each table (FR44, AD-6)
- [x] `backend/src/main/java/com/company/leave/audit/AuditEvent.java`, `AccessLogEntry.java` -- add -- package-private `@Entity` classes mapping the two tables (AD-2)
- [x] `backend/src/main/java/com/company/leave/audit/AuditEventRepository.java`, `AccessLogRepository.java` -- add -- package-private, extend `org.springframework.data.repository.Repository<T, Long>`, declare only `save(...)` — no inherited `delete*` (AD-6)
- [x] `backend/src/main/java/com/company/leave/audit/AuditService.java` -- add -- public `@Service`; `recordEvent(String entity, Long entityId, String action, long actorId, String detailsJson)` and `recordAccess(Long actorId, String resource, Long resourceId, boolean allowed)`, both insert-only, both setting `created_at` from `Instant.now()`
- [x] `backend/src/test/java/com/company/leave/audit/AuditServiceImmutabilityTest.java` -- add -- `@SpringBootTest` + Testcontainers Postgres: `recordEvent`/`recordAccess` succeed; a raw `JdbcTemplate` `UPDATE` and `DELETE` against each of `audit_events`/`access_log` each throw (NFR10)
- [x] `backend/src/test/java/com/company/leave/CompanyLeaveApplicationTests.java` -- update -- table list now includes `audit_events`, `access_log`

**Acceptance Criteria:**
- Given the Flyway migrations, when applied, then `audit_events` and `access_log` exist with the exact columns/nullability above, satisfying FR43/NFR12
- Given an existing row in either table, when an `UPDATE` or `DELETE` is attempted (including via raw JDBC/psql), then the trigger raises and the statement fails, satisfying FR44 and demo scenario 13
- Given `audit`'s public surface, when inspected, then only `AuditService` is referenced outside the package, and neither it nor the repositories declare an update/delete method (AD-2, AD-6)
- Given a caller recording an event, when it calls `AuditService`, then the actor id is an explicit parameter and `audit` never reads `SecurityContext` (AD-4)

## Implementation Notes

## Spec Change Log

## Review Triage Log

- **high** — `V2__add_audit_immutability_trigger.sql` defines `BEFORE UPDATE OR DELETE ... FOR EACH ROW` triggers only; PostgreSQL never fires row-level triggers for `TRUNCATE`, so `TRUNCATE audit_events`/`access_log` via raw SQL erases all rows unblocked. Verified true (Postgres documented trigger semantics; no `FOR EACH STATEMENT`/`TRUNCATE` trigger exists in the migration). Routed **defer**, not patch: the frozen Intent's Approach and AD-6 (architecture spine, `[ADOPTED]`) both specify the mechanism as exactly "a `BEFORE UPDATE OR DELETE` trigger" — FR44 itself reads "DB trigger blocks update/delete" — so TRUNCATE-hardening is outside what this story's intent committed to, not an omission introduced by sloppy spec-writing. (blind-hunter #1, edge-case-hunter, verification-gap)
- **medium** — The same trigger-only design gives the table-owning DB role (the app's own role, which created the tables via Flyway) the power to `ALTER TABLE ... DISABLE TRIGGER` or `DROP TRIGGER` and then mutate freely; no `REVOKE`/role-separation backs up the "even with raw DB access" framing. Verified true (table owner always has DDL rights over its own tables by default). Routed **defer**: AD-6 explicitly scopes the control to the trigger as "belt-and-braces", stating no role-separation requirement — a pre-existing architecture-level scope decision, not a defect in this story's execution. (blind-hunter #2)
- **false** — Claimed risk: `AuditEvent.entity`/`action` and `AccessLogEntry.resource` map `@Column(nullable = false)` with no `length`, defaulting to Hibernate's 255, against `VARCHAR(100)` DDL, risking a `ddl-auto: validate` startup failure. Refuted: the full test suite (including `CompanyLeaveApplicationTests`, which boots the JPA context with `ddl-auto: validate` against a real Testcontainers Postgres 18 with these exact migrations applied) ran green — `Tests run: 5, Failures: 0, Errors: 0, BUILD SUCCESS` — immediately after this diff landed. Hibernate's schema validator did not fail on the length mismatch. (blind-hunter #3)
- **medium** — The AC "`audit_events`/`access_log` exist with the exact columns/nullability above" is only tested at the table-name level (`containsExactlyInAnyOrder`); no test checks column nullability (e.g. that `audit_events.actor_id` rejects `NULL` while `access_log.actor_id`/`resource_id` accept it — the exact scenario the spec's Boundaries call out). Verified true by reading `CompanyLeaveApplicationTests.java` and `AuditServiceImmutabilityTest.java` in full — neither test exercises a null `actorId`/`resourceId` or a nullability violation. Routed **patch**: trivial test-only addition in already-touched files. (blind-hunter #4, #5)
- **false** — Claimed gap: AD-2 ("only `AuditService` referenced outside the package") and AD-4 ("`audit` never imports `SecurityContextHolder`") have no automated enforcement (e.g. ArchUnit), so a later story could silently violate either. Refuted for AD-2: package-private classes are compiler-enforced — no later module can reach `AuditEvent`/`AccessLogEntry`/the repositories regardless of convention. For AD-4: the architecture spine explicitly states tool-enforced boundary verification instead of the AD-2 code-review convention "is a paradigm change, not an amendment" — i.e. deliberately rejected, not a gap this story left open. (blind-hunter #6)
- **low** — No index beyond the primary key on either table, despite being append-only logs future stories will filter by actor/entity/time. Verified true by reading `V1__create_audit_tables.sql`. Routed **defer**: no query path exists yet to index for — the architecture spine explicitly defers `access_log` querying/reads to "Phase 2"; adding a speculative index now with no known access pattern is premature. (blind-hunter #7)
- **false** — Claimed gap: `AuditService.recordEvent`/`recordAccess` perform no validation of null/overlong/malformed input, so a future caller would get a raw unwrapped `DataIntegrityViolationException` instead of a clear error. Refuted: no caller of `AuditService` exists anywhere in this diff or the codebase (the spec's Boundaries explicitly forbid wiring one in yet) — the described bad outcome has no code path that currently reaches it. A situation the program is never shown is not a defect. (blind-hunter #8; edge-case-hunter's five null/length/JSON-validation findings on `AuditService.java`)
- **low** — `CompanyLeaveApplicationTests`'s test method is still named `contextLoadsAndFlywayCreatesOnlyItsOwnHistoryTable`, but its Javadoc and assertion were updated to say Flyway now also creates `audit_events`/`access_log` — the name no longer matches what it checks. Verified true by reading the file. Routed **patch**: fix is a direct rename, so it survives the low-severity rejection filter. (verification-gap)
- **medium** — `AuditServiceImmutabilityTest` inserts a non-trivial `detailsJson` payload (`{"note":"ok"}`) via `recordEvent` but never reads back or asserts its content, so a `@JdbcTypeCode(SqlTypes.JSON)` mapping bug (double-encoding, truncation) would pass silently. Verified true by reading the test file in full. Routed **patch**: trivial assertion addition to an already-touched test. (verification-gap)

## Verification

**Commands:**
- `cd backend && ./mvnw -DskipTests=false test` -- expected: all tests pass, including the new `AuditServiceImmutabilityTest` and the updated `CompanyLeaveApplicationTests`
- `cd backend && ./mvnw -DskipTests compile` -- expected: compiles clean
