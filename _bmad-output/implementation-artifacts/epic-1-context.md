# Epic 1 Context: Secure Access and Account Administration

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

HR can onboard the organisation and every user can get in safely. An HR admin creates accounts with a role and a manager, hands out temporary passwords and resets forgotten ones; users log in over HTTPS, are forced to replace a temporary password, get locked out after five wrong attempts and dropped after thirty idle minutes; and every authorization decision — allowed or denied — is recorded in an insert-only log nobody can edit. This epic also installs the three mechanisms every later epic depends on: the Flyway-managed schema, the single `AccessPolicy`, and the audit write path with its immutability trigger. It extends and corrects the existing backend scaffold rather than starting a new project.

## Stories

- Story 1.1: Corrected Dependencies and an HTTPS-Fronted Stack
- Story 1.2: An Immutable Audit Trail and Access Log
- Story 1.3: Password Login with an Idle-Expiring Session
- Story 1.4: Account Lockout After Five Failed Attempts
- Story 1.5: One Permission Layer for Every Endpoint
- Story 1.6: HR Creates Accounts with a Role and a Manager
- Story 1.7: Forced Password Change and HR Password Reset

## Requirements & Constraints

- HR account creation requires a role and (for EMPLOYEE/MANAGER) a manager whose own role is MANAGER or HR_ADMIN; a typed temporary password is hashed immediately and never persisted or logged in plaintext; the new account must change it at first login. HR can also force-generate a reset password for an existing user.
- Access control covers exactly three roles (EMPLOYEE, MANAGER, HR_ADMIN) on every endpoint, with three scoping shapes: employees see only their own data, managers only their direct reports' data, HR sees everything unfiltered. Every refusal returns HTTP 403 and is written to an insert-only access log; the POC exposes no read API/UI for that log — it's verified by direct DB query.
- Every state-changing action writes an immutable audit event recording the acting user and timestamp; audit and access-log tables must reject UPDATE/DELETE even from a direct DB session.
- Login uses username/BCrypt password; new-password rule is minimum 8 characters with letters and numbers. Five consecutive failed logins lock an account for 15 minutes. A session goes stale after 30 minutes of inactivity and must be re-authenticated.
- The app must be reachable over HTTPS (local reverse proxy, self-signed cert in the POC) because the auth cookie's `Secure` flag cannot be honestly verified over plain HTTP.
- All non-2xx responses share one error envelope with a closed set of error codes; this epic is responsible for nine of the ten canonical codes plus the tenth (`PASSWORD_CHANGE_REQUIRED`), which it introduces.
- Success/demo gate for this epic: HR creates a user, assigns a manager, resets a password, and that user logs in and is forced to change the temporary password; an audit row proves immutable against direct tampering; a 5th bad login locks the account; an idle session is rejected after 30 minutes.
- Demo seed data for this epic is the 12-user cast only (1 HR admin, 1 top-level manager, 2 team managers, 8 employees); leave types/balances seed with their own tables in a later epic — never bundle seed data for tables this epic doesn't own.
- Config (DB credentials, JWT signing secret) comes only from environment variables; nothing secret is committed or baked into an image.

## Technical Decisions

- Modules communicate only through direct, synchronous, in-process `@Service` calls — no async, no events, no message broker. A module's `@Entity`/`@Repository` stay private to that module; every cross-module read/write goes through the owning module's service. This epic owns: `users` (users table), `security` (`active_sessions`), `audit` (`audit_events`, `access_log`). `common` holds only stateless utilities, cross-cutting config, exceptions and the one error advice — no entities, repositories or business logic there.
- `pom.xml` must drop the Spring Modulith starters/BOM entirely, and must keep the already-made swap from the OAuth2 resource-server starter to hand-rolled JWT (`jjwt` 0.13.0, impl/jackson at runtime scope) — verify and commit rather than redo.
- `docker-compose.yml` must pin Postgres 18, add `backend`, `frontend`, and a reverse-proxy service terminating HTTPS, drop `mailpit` (deferred until real SMTP), and expose only the proxy outside the compose network; backend config uses in-network hostnames.
- Auth is hand-rolled JWT (`security.JwtService`, HMAC secret from env, 15-minute token, claims `sub`/`role`/`token_id`) validated by a custom `OncePerRequestFilter` registered via `addFilterBefore`. The filter — and only the filter — populates `SecurityContextHolder`.
- `active_sessions` (token_id, user_id, last_seen_at, expires_at) is owned exclusively by `security`, exposed as `SessionRegistry`: `auth` calls `createSession` at login only; the filter calls `touchOrReject` (401 on missing/stale row, else bump `last_seen_at`); logout, password reset, and any new lockout call `revokeAllForUser`.
- Lockout counters (`failed_logins`, `locked_until`) live only on `users` and are checked at the login boundary before a token is ever issued — never in a JWT claim, never in the filter.
- `security.AccessPolicy` is the single place for role/ownership logic, with exactly three shapes: `requireOwnerManagerOrHr(ownerId, managerId)`, `currentScope()` (sealed `UserScope`: `EmployeeOwn | ManagerTeam | HrAll`), and `requireRole(Role...)`. No controller or service may write its own role/ownership comparison; list-returning repository methods take a `UserScope` and apply the filter themselves.
- Every event-recording call passes the acting user's id explicitly as a parameter; the owning module never re-derives it from `SecurityContext` and never duplicates another module's audit/ledger row.
- Immutability on `audit_events`/`access_log` is enforced by a Flyway-created `BEFORE UPDATE OR DELETE` trigger, backed by the service layer exposing no update/delete method on those tables.
- One RFC 9457 `ProblemDetail` envelope from a single `@RestControllerAdvice` in `common`; its `code` is a `common.ErrorCode` value mirroring the contract's closed enum exactly (10 values, `PASSWORD_CHANGE_REQUIRED` being the one this epic adds). Every endpoint except login/logout/change-password must return 403 `PASSWORD_CHANGE_REQUIRED` while the caller's forced-change flag is set.
- Data conventions: `BIGINT GENERATED ALWAYS AS IDENTITY` surrogate keys, `timestamptz` columns with the JVM pinned to UTC, `DATE` for date-only fields. Naming: lower-case singular packages, PascalCase singular entities, DTOs suffixed `Request`/`Response`, `snake_case` plural tables.
- Tests: unit/API coverage for RBAC, lockout and audit-write paths is part of each story's acceptance criteria (no separate test epic); integration tests run against real Postgres via Testcontainers so triggers/constraints are actually exercised.

## Cross-Story Dependencies

- Story 1.1's HTTPS-fronted stack and corrected dependencies must land first — later stories' `Secure` cookies and error envelope can't be honestly verified without it.
- Story 1.2's audit/access-log tables and trigger must exist before Stories 1.3–1.7, since each of them writes an audit event and Story 1.5's request filter writes an access-log row on every call.
- Story 1.3 (users table, login, JWT, `SessionRegistry`) is a prerequisite for Story 1.4 (lockout reuses `users.failed_logins`/`locked_until` and revokes sessions through `SessionRegistry`) and for Stories 1.5–1.7, which act on the `users` table and the current session.
- Story 1.5 installs `AccessPolicy` and the three `UserScope` shapes that Stories 1.6 and 1.7 gate their endpoints through — and that every later epic (2–4) extends rather than reimplements.
- Stories 1.6 and 1.7 together are required for the epic's demo scenario (HR creates a user, assigns a manager, resets a password; the user logs in and must change the temporary password).
- This epic's three installed mechanisms (Flyway schema, `AccessPolicy`, the audit write path with its immutability trigger) are load-bearing for Epics 2–4, which extend rather than rebuild them.
