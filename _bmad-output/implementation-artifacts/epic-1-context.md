# Epic 1 Context: Secure Access and Account Administration

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

HR can onboard the organisation and every user can get in safely. By the end of this epic, an HR admin creates accounts with a role and a manager, hands out temporary passwords and resets forgotten ones; users log in over HTTPS, are forced to replace a temporary password, get locked out after five wrong attempts, and are dropped after thirty idle minutes; and every authorization decision — allowed or denied — is recorded in an insert-only log nobody can edit. This epic also installs the three mechanisms every later epic depends on: the Flyway-managed schema, the single `AccessPolicy` permission component, and the audit write path with its DB-trigger immutability. It extends the existing committed scaffold rather than starting a new project, and closes two blocking dependency corrections left open in `pom.xml`.

## Stories

- Story 1.1: Corrected Dependencies and an HTTPS-Fronted Stack
- Story 1.2: An Immutable Audit Trail and Access Log
- Story 1.3: Password Login with an Idle-Expiring Session
- Story 1.4: Account Lockout After Five Failed Attempts
- Story 1.5: One Permission Layer for Every Endpoint
- Story 1.6: HR Creates Accounts with a Role and a Manager
- Story 1.7: Forced Password Change and HR Password Reset

## Requirements & Constraints

- Three roles: EMPLOYEE, MANAGER, HR_ADMIN. Employees see only their own data, managers see only their direct reports, HR sees everything with no scope filter. Unauthorized access returns 403 and is logged.
- Every sensitive-data access and every authorization decision (allowed or denied) is recorded; the access log has no read API/UI in this POC — it's checked directly against the DB in tests.
- Password rule: minimum 8 characters, letters and numbers. 5 failed logins locks the account for 15 minutes. Sessions expire after 30 minutes of inactivity.
- Cookies are HttpOnly, Secure, SameSite=Strict — only enforceable because the whole stack sits behind HTTPS from this epic onward.
- HR creates accounts (username, role, manager, a temporary password HR types in, never logged in plaintext) and can reset any user's password; both paths land as `password_hash` + `must_change_password=true`. A forced password change gate applies on first login after creation or reset.
- Config (DB credentials, JWT signing secret) comes entirely from environment variables; no secret is committed or baked into an image.
- Non-2xx responses are RFC 7807 `ProblemDetail` with a `code` from one canonical error enum shared with the OpenAPI contract (`ACCESS_DENIED`, `VALIDATION_FAILED`, `INVALID_TRANSITION`, `INSUFFICIENT_BALANCE`, `NOT_FOUND`, `INVALID_CREDENTIALS`, `LEAVE_TYPE_CHANGE_NOT_ALLOWED`, `COMMENT_REQUIRED`, `ACCOUNT_LOCKED`, `PASSWORD_CHANGE_REQUIRED`) — exact match, no extras, none missing.
- This epic creates no domain tables itself beyond what auth/users/audit/security need; `leave_types`/`leave_balances` seed with Epic 2.
- JVM default timezone must be UTC at runtime (already set in `CompanyLeaveApplication.main`, confirm not duplicated elsewhere).

## Technical Decisions

- **Paradigm:** layered modular monolith, package-by-feature (`auth`, `users`, `leave`, `approvals`, `balances`, `audit`, `notifications`, plus `security` and `common`), each `controller → service → repository`. Modules call each other's service layer directly/synchronously — no event bus, no Spring Modulith verification (remove `spring-modulith-*` dependencies from `pom.xml`).
- **Auth (AD-7):** hand-rolled JWT (not OAuth2 Resource Server) via `security.JwtService` (jjwt 0.13.0), 15-min expiry, HMAC secret from env var, claims include `sub`, `role`, `token_id`. Returned as an HttpOnly/Secure/SameSite=Strict cookie. `security.JwtAuthenticationFilter` (plain `OncePerRequestFilter`) validates it on every request. Remove `spring-boot-starter-security-oauth2-resource-server`; keep `spring-boot-starter-security`; add `jjwt-api`/`jjwt-impl`/`jjwt-jackson` 0.13.0 (`-impl`/`-jackson` at runtime scope).
- **Idle timeout/revocation (AD-8):** a `security`-owned `active_sessions(token_id, user_id, last_seen_at, expires_at)` table backs every JWT via `security.SessionRegistry`. The filter checks signature then calls `touchOrReject(tokenId)` (401 if missing or idle > 30 min). Logout, HR password reset, and new lockouts each call `revokeAllForUser(userId)`.
- **Lockout (AD-9):** stays on `users` (`failed_logins`, `locked_until`), checked at `/api/auth/login` before any JWT issues — independent of the token model.
- **RBAC (AD-10):** one `security.AccessPolicy` component, exactly two check shapes used everywhere — `requireOwnerManagerOrHr(resourceOwnerId, resourceManagerId)` for single-resource checks, and `currentScope()` returning a sealed `UserScope` (`EmployeeOwn`/`ManagerTeam`/`HrAll`) for collection queries — plus `requireRole(Role...)` for role-only gates. No controller/service writes its own role/ownership `if` check.
- **Error envelope (AD-11):** one `@RestControllerAdvice` in `common` produces `ProblemDetail`; `common.ErrorCode` enum mirrors `openapi.yaml`'s `ProblemDetail.code` exactly.
- **Audit immutability (AD-6):** a Flyway-managed Postgres `BEFORE UPDATE OR DELETE` trigger on `audit_events` and `access_log` raises an exception; the service layer exposes no update/delete method for either table.
- **`common` stays inert (AD-3):** no `@Entity`, `@Repository`, or business-rule `@Service` in `common` — only stateless utilities, cross-cutting config, exceptions, and the error advice.
- **Deployment (AD-13):** `docker-compose.yml` must define `postgres` (pinned to PostgreSQL 18, not exposed to host), `backend`, `frontend`, and a reverse-proxy service terminating HTTPS with a local self-signed cert — the proxy is the only service exposed outside the compose network. Remove the `mailpit` service (deferred until real SMTP dispatch is integrated). Backend reads `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`/JWT secret from compose env vars using in-network hostnames (`postgres`, never `localhost`); `application.yml` `localhost` defaults remain only for running outside compose.
- **Stack versions:** Java 21, Spring Boot 4.1.1, PostgreSQL 18, Flyway (`flyway-database-postgresql`), Maven (wrapper already committed).
- **Naming conventions:** packages lower-case singular; JPA entities PascalCase singular; DTOs suffixed `Request`/`Response`; tables `snake_case` plural; surrogate keys `BIGINT GENERATED ALWAYS AS IDENTITY`; timestamps `timestamptz` with JVM pinned to UTC; `start_date`/`end_date` as `DATE`.

## Cross-Story Dependencies

- Story 1.1 must land first: it corrects `pom.xml` (removes Spring Modulith, finalizes the jjwt swap) and stands up the HTTPS-fronted Docker Compose stack, the package skeleton, and the common error envelope — every later story in this epic (and every later epic) builds on top of it.
- Story 1.2 (audit trail) must exist before Stories 1.3–1.7 can log login attempts, lockouts, and access decisions, since AD-4/AD-6 require the audit write path and its immutability trigger to already be in place.
- Story 1.5 (`AccessPolicy`) is a prerequisite for every controller added in this epic and in Epics 2–4 — no module should invent its own role/ownership check ahead of or instead of it.
- Stories 1.6 and 1.7 share the account-creation/password-reset code path (both end in `password_hash` + `must_change_password=true`) and both depend on Story 1.3's session/login machinery already existing.
- This epic's `security.SessionRegistry`/`active_sessions` table (Story 1.3) is later called by `users` (password reset) and by the lockout path (Story 1.4) to revoke sessions — those stories depend on Story 1.3 landing first.
