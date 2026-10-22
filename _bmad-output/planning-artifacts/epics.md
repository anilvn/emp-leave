# Epic Breakdown: Backend API (v3)

**Status:** draft for re-review
**Scope:** Spring Boot API only. The Angular SPA is planned separately.
**Source:** POC PRD (2026-10-15 and 2026-10-22), Architecture Spine (AD-1 to AD-13)

## Changes from v2

| # | Fix | Where |
|---|---|---|
| 1 | Added user update and deactivation story (manager change, role, active flag) | Story 1.8 |
| 2 | Action catalog reconciled with every action used in stories; AD-7 count needs updating (see note) | Story 1.2 |
| 3 | Bootstrap vs demo seed conflict resolved: demo seed owns the demo HR admin; bootstrap is off in `demo` | Stories 1.3, 1.10 |
| 4 | HR self-approved request sends the "approved" email to the requester; "submitted" is skipped | Story 5.2 |
| 5 | Decision comment appears in History once, via the decision audit row | Stories 4.3, 4.4, 3.6 |
| 6 | Access-log resource mapping lists every resource used in the stories | Story 1.9 |
| 7 | Back-dating and overlap decided (proposed, needs sign-off); overlap error code added | Story 3.1, Decisions |
| 8 | Approved-leave cancellation stated as out of scope in README limits | Story 6.3 |
| 9 | Edit to zero working days covered | Story 3.3 |
| 10 | Decision comment capped at 2000 characters, same as comments | Stories 4.3, 4.4 |
| 11 | Team view has an access-log AC and a 366-day range cap | Story 4.6 |
| 12 | Negative-availability test on HR set/adjust with reservations in flight | Story 2.2 |
| 13 | Access-log rule written into AD-8 and applied to all list and single reads | Stories 3.2, 4.1, 4.6, 5.3 |
| 14 | Lock ordering (request before balance) written into AD-4 with a test | Story 3.4, Story 4.3 |
| 15 | No-notes-in-email rule applies to all three emails | Stories 5.1, 5.2 |

**Note on the action count:** AD-7 lists 12 actions. The stories below use 13 (`USER_UPDATED` is new, from Story 1.8). The spine must be updated to 13, or `USER_UPDATED` must be folded into an existing action. This is the only change that touches the spine.

**Note on the overlap code:** `OVERLAPPING_REQUEST` is a new error code. It must be added to the catalog in `common`.

## Requirements Inventory

### Functional Requirements

FR numbers match the source PRD. Phase 2 FRs (FR32–FR41, FR48, FR49) are out of scope.

**Leave requests**
- FR1: Submit a leave request with a type (Vacation, Sick, Parental, Sabbatical). Whole days only.
- FR2: Start and end dates. End on or after start. Days are working days (Mon–Fri, inclusive, holidays ignored).
- FR3: Optional free-text notes.
- FR4: View status of own requests (PENDING / APPROVED / REJECTED / CANCELLED).
- FR5: Filterable history of own requests.
- FR6: Balance per leave type: total, reserved, available.
- FR7: Modify a request only while PENDING. Reservation recalculated and logged.
- FR8: Cancel a PENDING request. Status CANCELLED, days released, never deleted.
- FR9: Submitter and submission time recorded on the request and its audit event.

**Approvals**
- FR10: Pending-approvals list for the approver.
- FR11: Decision context: employee, dates, type, balance, recent history.
- FR12: Approve with optional comment. Reserved days become deducted.
- FR13: Reject with required non-blank comment. Reserved days released.
- FR14: Team approved leave by date.
- FR15: Comment thread per request. No notifications; users refresh.
- FR16: Decision, time and approver recorded on the request and its audit event.
- FR17: Only the assigned approver decides.

**Users and access**
- FR18: HR creates accounts.
- FR19: HR assigns role and manager. Top-level managers report to HR.
- FR20: HR resets a password to a temporary one; user must change it at next login.
- FR21: RBAC with EMPLOYEE, MANAGER, HR_ADMIN on every endpoint.
- FR22: Employees see only their own data.
- FR23: Managers see their direct reports' data (plus their own).
- FR24: HR sees all data.
- FR25: Unauthorized access returns 403 and writes an access-log entry.
- FR26: Access to sensitive data is logged.

**Balances**
- FR27: HR views all balances.
- FR28: HR sets a balance with a reason.
- FR29: HR adjusts a balance by +/- days with a reason.
- FR30: Every balance change recorded in a ledger (time, amount, reason, actor).
- FR31: No negative balance. Request days ≤ available. HR set/adjust cannot drop a balance below reserved days.

**Audit**
- FR42: Every transaction produces an audit event.
- FR43: Every audit event records time and actor.
- FR44: Audit records are immutable (insert-only, DB trigger).
- FR45: Per-request History timeline.
- FR46: Daily pg_dump backup script (Partial).
- FR47: Audit logs kept 3 years. No purge path. Retention policy documented.

**Notifications**
- FR50: Requester emailed on approval.
- FR51: Requester emailed on rejection, with reason.
- FR52: Approver emailed on new submission.
- FR53: Notification history per user.
- FR54: Emails include type, dates, days, status and a link.

**Security and operations**
- FR55: HTTPS via reverse proxy (Partial).
- FR56: Username/password login, BCrypt.
- FR57: Lockout 15 minutes after 5 consecutive failures.
- FR58: Session times out after 30 minutes of inactivity.
- FR59: Access logs (same as FR26).
- FR60: Daily automated backups (Partial, same script as FR46).
- FR61: Backup and recovery documented, not tested (Deferred).

### Non-Functional Requirements

- NFR1: PostgreSQL 16.
- NFR2: Role checks on every endpoint. UI hiding is not a security control.
- NFR3: Audit, access-log and ledger tables insert-only, enforced by trigger.
- NFR4: BCrypt passwords, minimum 8 characters, letters and numbers.
- NFR5: Auth cookie HttpOnly, Secure, SameSite=Strict. No tokens in JavaScript storage.
- NFR6: 30-minute sliding inactivity timeout, capped at 8 hours from login.
- NFR7: Flyway only; Hibernate `ddl-auto=validate`.
- NFR8: All config and secrets from environment variables.
- NFR9: Tests cover RBAC, approval, balance and audit rules, against Postgres in Testcontainers (never H2).
- NFR10: Logs never contain passwords, tokens or request notes.
- NFR11: Committed actions never fail because of email. SMTP timeout 5 s; errors logged.
- NFR12: Concurrent submit, decide and cancel are race-safe (row locks; loser gets 409).
- NFR13: Local stack starts with `docker compose up`.
- NFR14: Structured for production: module boundaries, `NotificationSender`, audit `details` with room for before/after.

Production-only targets (performance, scaling, encryption at rest, DR, APM, CI/CD, OWASP, WCAG) are deferred. Frontend NFRs belong to the SPA plan.

### Additional Requirements

**Starter:** move the Initializr scaffold into `backend/`, bump to Spring Boot 4.1.1, add Spring Modulith BOM 2.1.1 (core and test), Security, OAuth2 resource server, Validation, Web, Lombok and Testcontainers.

**Modules (AD-1, AD-2):** `common`, `auth`, `users`, `leave`, `approvals`, `balances`, `audit`, `notifications`. Root package is the public API; `internal/` and `web/` are private. `ApplicationModules.verify()` enforces the graph. Each table has one owning module. `UserSummary` is the only shared user DTO. `UsersService.setPassword` is the only hashing path.

**Transactions (AD-3):** `@Transactional` only on public service methods. `open-in-view=false`. Exceptions: `AuthService.login` is non-transactional; `access_log` and `notifications` writes use `REQUIRES_NEW`.

**State machine and lock order (AD-4):**
- Transitions: submit → PENDING; PENDING → APPROVED or REJECTED (approver); PENDING → CANCELLED (owner).
- Edits only while PENDING, only by owner. Leave type cannot change (400 `LEAVE_TYPE_CHANGE_NOT_ALLOWED`).
- Each transition or edit takes `PESSIMISTIC_WRITE` on the request, re-checks status, and loser gets 409 `INVALID_TRANSITION`. All transitions live in one `leave.internal` component.
- **Lock order:** request row first, then balance row. Submit locks only the balance (no request row exists yet). No flow may lock a balance and then a request. A test asserts this with concurrent approve/edit/cancel.
- Decision comment is stored only in `leave_requests.decision_comment` and the audit row, never in `request_comments`.

**Approver snapshot (AD-5):** `approver_id` set at submit: the caller's own id for HR_ADMIN, otherwise `manager_id`. Decisions check `approver_id`. Team visibility uses the live `manager_id`. Self-approval writes `selfApproved: true`.

**Balances (AD-6):** one row per (user, type); missing row means 0. Each operation locks the row and writes exactly one ledger row using the signed `change_days` table. Refusals return 409 `INSUFFICIENT_BALANCE`. DB CHECK is the backstop.

**Audit (AD-7):** one audit row per use case per aggregate, via `AuditService.record(...)` inside the business transaction. Action catalog below. History merges audit and comments unpaged. Trigger blocks UPDATE and DELETE on `audit_events`, `access_log` and `balance_ledger`.

**Authorization and access logging (AD-8):**
- Every controller method has `@PreAuthorize`. Record checks only in `@leavePermissions`, `@balancePermissions`, `@userPermissions`.
- `canView` = owner, approver, owner's live manager, or HR. `canEdit` and `canCancel` = owner. `canDecide` = approver. `canComment` = `canView`.
- Missing record → 403.
- **Access-log rule:** every read of leave, balance, user, audit or notification data is logged with `allowed=true`, whether it is a list or a single record. Every 403 is logged with `allowed=false`. The only writer is the access-log filter in `audit`, which runs right after bearer-token auth. Note: CSRF rejections are raised before bearer auth, so the filter also handles them through the entry-point and access-denied handlers, which call the same logger.

**Authentication (AD-9):** HS256 JWT from `JWT_SECRET` (≥ 32 bytes), claims `sub`, `role`, `must_change_password`, `auth_time`. Carried in an HttpOnly, Secure, SameSite=Strict cookie via a custom `BearerTokenResolver`. Single `AuthCookieWriter`. Sliding 30-minute re-issue, capped at 8 hours.

**CORS and CSRF (AD-10):** explicit allow-list from `CORS_ALLOWED_ORIGINS`; no wildcard. `CookieCsrfTokenRepository`; token via `GET /api/auth/csrf`; `X-XSRF-TOKEN` on every state change, including login.

**Email (AD-11):** `leave` publishes id-only events. `notifications` handles them `AFTER_COMMIT`, sends through `NotificationSender`, writes its row in `REQUIRES_NEW`, and catches and logs all errors. No notes in any email.

**Working days (AD-12):** one `common` function, server-side only, Mon–Fri inclusive. Zero days or end before start → 400.

**Migrations and seed (AD-13):** `V<yyyyMMddHHmm>__<module>_<description>.sql`. Applied migrations are never edited. Leave types come from a versioned migration. Bootstrap and demo seed follow the rule in Story 1.3.

**Errors:** `ApiException(HttpStatus, code)` plus one `@RestControllerAdvice` and the security handlers, all returning RFC 9457 `ProblemDetail` with `code`. Error catalog:

`VALIDATION_FAILED`, `COMMENT_REQUIRED`, `LEAVE_TYPE_CHANGE_NOT_ALLOWED`, `UNAUTHENTICATED`, `INVALID_CREDENTIALS`, `ACCESS_DENIED`, `CSRF_INVALID`, `PASSWORD_CHANGE_REQUIRED`, `NOT_FOUND`, `INSUFFICIENT_BALANCE`, `INVALID_TRANSITION`, `OVERLAPPING_REQUEST` (new).

**Audit action catalog (13 actions):**

| Action | Entity | Details keys |
|---|---|---|
| USER_CREATED | USER | role, managerId |
| USER_UPDATED | USER | managerId, role, active (changed fields) |
| USER_PASSWORD_CHANGED | USER | none |
| USER_PASSWORD_RESET | USER | none |
| USER_LOCKED | USER | lockedUntil |
| LEAVE_REQUEST_SUBMITTED | LEAVE_REQUEST | leaveTypeId, startDate, endDate, workingDays, selfApproved |
| LEAVE_REQUEST_EDITED | LEAVE_REQUEST | startDate, endDate, workingDays, notesChanged |
| LEAVE_REQUEST_CANCELLED | LEAVE_REQUEST | none |
| LEAVE_REQUEST_APPROVED | LEAVE_REQUEST | comment, selfApproved |
| LEAVE_REQUEST_REJECTED | LEAVE_REQUEST | comment, selfApproved |
| LEAVE_BALANCE_SET | BALANCE | leaveTypeId, totalDays, reason |
| LEAVE_BALANCE_ADJUSTED | BALANCE | leaveTypeId, changeDays, reason |
| REQUEST_COMMENT_ADDED | REQUEST_COMMENT | requestId |

**Config:** `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `MAIL_HOST`, `MAIL_PORT`, `APP_BASE_URL`, `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`. Profiles: `dev` (default), `demo`, `test`.

**Endpoints beyond the PRD:** `GET /api/auth/csrf`, `GET /api/auth/me`, `GET /api/leave-types`, `GET /api/balances/{userId}/ledger`, `PATCH /api/users/{id}`.

## FR Coverage Map

| FR | Epic and story |
|---|---|
| FR1, FR2, FR3, FR9 | 3.1 |
| FR4, FR5, FR22 | 3.2 |
| FR6, FR22 | 2.1 |
| FR7 | 3.3 |
| FR8 | 3.4 |
| FR15 | 3.5 |
| FR31 (submit) | 3.1 |
| FR31 (HR side) | 2.2, 2.3 |
| FR45 | 3.6 |
| FR10 | 4.1 |
| FR11 | 4.2 |
| FR12, FR16 | 4.3 |
| FR13, FR16 | 4.4 |
| FR14, FR23 | 4.6 |
| FR17, FR19, FR23 | 4.5 |
| FR18, FR19 | 1.7 |
| FR19 (updates) | 1.8 |
| FR20 | 1.7 |
| FR21, FR25, FR26, FR59 | 1.4, 1.9 |
| FR22, FR23, FR24 | 1.9 (pattern), applied in Epics 2–4 |
| FR27, FR28, FR29, FR30 | 2.2, 2.3 |
| FR42, FR43, FR44, FR47 | 1.2 |
| FR46, FR60 | 6.2 |
| FR50, FR51 | 5.2 |
| FR52 | 5.1 |
| FR53 | 5.3 |
| FR54 | 5.1, 5.2 |
| FR55 | 6.1 |
| FR56, FR57, FR58 | 1.4, 1.5 |
| FR61 | 6.2 |

## Epic List

Flow: Epic 1 → 2 → 3 → 4 → 5, then Epic 6.

1. **Secure Sign-In & User Administration** (Stories 1.1–1.10)
2. **Leave Balance Management** (2.1–2.5)
3. **Request & Track Leave** (3.1–3.6)
4. **Approve, Reject & Oversee Team Leave** (4.1–4.6)
5. **Email Notifications & History** (5.1–5.3)
6. **Demo-Ready Stack & Data Protection** (6.1–6.4)

---

## Epic 1: Secure Sign-In & User Administration

### Story 1.1: Modular backend foundation

As a developer, I want the scaffold restructured into the modular layout with a local Postgres, Mailpit and a shared error contract, so that boundaries are enforced from the start.

**Acceptance Criteria:**

**Given** the scaffold at the repo root
**When** the story is complete
**Then** it lives under `backend/` on Spring Boot 4.1.1 with the Modulith BOM 2.1.1 imported
**And** `./mvnw verify` passes from `backend/`.

**Given** the module packages under `com.company.leave`
**When** they are created
**Then** each of the eight modules has a `package-info.java` with `@ApplicationModule(allowedDependencies = …)` matching the spine graph
**And** `ModularityTests` runs `ApplicationModules.verify()` and passes.

**Given** `docker-compose.yml`
**When** a developer runs `docker compose up postgres mailpit`
**Then** `postgres:16` (with a `pg_isready` healthcheck) and `axllent/mailpit:v1.31` start
**And** `./mvnw spring-boot:run` on the `dev` profile connects using `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

**Given** `application.yml`
**When** inspected
**Then** it holds non-secret defaults only, with `ddl-auto=validate`, `open-in-view=false`, Flyway at `classpath:db/migration`, and profiles `dev` (`flyway.out-of-order=true`), `demo` and `test`.

**Given** `common`'s error contract
**When** a module throws `ApiException` or a request fails Bean Validation
**Then** one `@RestControllerAdvice` returns RFC 9457 `ProblemDetail` with `code` (`VALIDATION_FAILED` for validation errors)
**And** `common` exposes an injectable `Clock`, `PasswordPolicy` (≥ 8 characters, letters and numbers) and a BCrypt `PasswordEncoder`, each unit tested.

**Given** integration tests
**When** they run
**Then** they share one Testcontainers Postgres 16 base. H2 is not used.

### Story 1.2: Immutable audit trail

As an HR admin, I want every audited action stored with actor and time in a log nobody can change, so that the record can be trusted.

**Acceptance Criteria:**

**Given** migration `V<ts>__audit_create_audit_events.sql`
**When** Flyway runs
**Then** `audit_events` exists (id identity, entity, entity_id, action, actor_id, details_json jsonb, created_at timestamptz; all NOT NULL except details_json) (FR43)
**And** trigger function `audit_reject_mutation` raises on UPDATE or DELETE (FR44).

**Given** an open business transaction
**When** a module calls `AuditService.record(entity, entityId, action, actorId, details)`
**Then** one row is inserted in the same transaction, `created_at` comes from `Clock`, and `details` uses camelCase keys (FR42, FR43)
**And** a rollback removes the audit row too.

**Given** the action catalog
**When** the `audit` public API is inspected
**Then** actions and entities are enums in `audit`'s root package (the 13 in the catalog above). Callers cannot pass free-text actions.

**Given** scenario 13
**When** a test runs SQL UPDATE or DELETE on `audit_events`
**Then** the statement fails and the row is unchanged.

**Given** the codebase
**When** searched for purge or delete on audit tables
**Then** none exists, and the 3-year retention policy is stated in `audit`'s `package-info.java` (FR47).

### Story 1.3: Bootstrap the first HR admin

As an operator, I want an HR admin to exist on first start, so that someone can sign in and set up everyone else.

**Acceptance Criteria:**

**Given** migration `V<ts>__users_create_users.sql`
**When** Flyway runs
**Then** `users` exists with id, username (unique), email, full_name, password_hash, role (`EMPLOYEE` | `MANAGER` | `HR_ADMIN`), manager_id (nullable FK to users), must_change_password, failed_logins, locked_until, active, created_at.

**Given** the `dev` or `test` profile, no HR_ADMIN exists, and `BOOTSTRAP_ADMIN_*` is set
**When** the application starts
**Then** one HR_ADMIN is created with `must_change_password=true` and no manager
**And** one `USER_CREATED` audit row is written with actor = the new admin.

**Given** the `demo` profile
**When** the application starts
**Then** the bootstrap is skipped. The demo HR admin comes from the demo seed (Story 1.10). This is the single rule that prevents two admins.

**Given** an HR_ADMIN already exists
**When** the application starts
**Then** no user is created.

**Given** the bootstrap password fails `PasswordPolicy`
**When** the application starts
**Then** startup fails with a message that does not echo the password.

**Given** other modules
**When** they need user data
**Then** `UsersService` exposes `UserSummary(id, fullName, email, role, managerId)` lookups and `setPassword(userId, raw, mustChange)`, the only hashing path. Entities stay in `users.internal`.

### Story 1.4: Login, logout and current user

As a user, I want to sign in securely without my token being readable by scripts.

**Acceptance Criteria:**

**Given** the auth chain
**When** the application starts
**Then** HS256 encoder and decoder are built from `JWT_SECRET`, and startup fails if it is shorter than 32 bytes
**And** CORS allows only `CORS_ALLOWED_ORIGINS`, with credentials and no wildcard.

**Given** a SPA client
**When** it calls `GET /api/auth/csrf`
**Then** the token is returned in the body and as the `XSRF-TOKEN` cookie.

**Given** an active, unlocked user with the correct password and a valid `X-XSRF-TOKEN`
**When** they `POST /api/auth/login`
**Then** the password is checked against its BCrypt hash, and the response sets the auth cookie (HttpOnly, Secure, SameSite=Strict) via `AuthCookieWriter` (FR56)
**And** the JWT has claims `sub`, `role`, `must_change_password`, `auth_time`, `exp = now + 30 min`
**And** the body returns `UserSummary` and `mustChangePassword`
**And** `failed_logins` resets.

**Given** an unknown user, wrong password, locked account or inactive account
**When** login is attempted
**Then** the response is 401 `INVALID_CREDENTIALS` with an identical body in every case.

**Given** a valid cookie
**When** `GET /api/auth/me` is called
**Then** it returns the caller's `UserSummary` and `mustChangePassword`, resolved through `CurrentUser`.

**Given** no token or an invalid token on a protected route
**When** it is processed
**Then** the response is 401 `UNAUTHENTICATED`.

**Given** the chain's entry point and access-denied handler
**When** they reject a request
**Then** they return the same ProblemDetail shape: 401 `UNAUTHENTICATED`, 403 `ACCESS_DENIED`, 403 `CSRF_INVALID` (FR21, FR25).

**Given** a valid cookie but no `X-XSRF-TOKEN` on a POST or PUT
**When** it is sent
**Then** the response is 403 `CSRF_INVALID`. A test asserts this.

**Given** a signed-in user
**When** they `POST /api/auth/logout`
**Then** the cookie is cleared. The JWT remains technically valid until `exp` (accepted risk, see Story 6.3).

### Story 1.5: Lockout and session timeout

As an HR admin, I want repeated wrong passwords to lock an account and idle sessions to expire.

**Acceptance Criteria:**

**Given** a user with 4 consecutive failures
**When** a 5th wrong password is submitted (scenario 14)
**Then** `locked_until = now + 15 min` is committed even though login fails (`recordLoginFailure` commits on its own) (FR57)
**And** one `USER_LOCKED` audit row is written with `lockedUntil`.

**Given** a locked account
**When** a correct password is submitted before `locked_until`
**Then** the response is 401 `INVALID_CREDENTIALS`.
**Note:** attempts during a lock do not extend `locked_until`.

**Given** `locked_until` has passed (advanced via test `Clock`)
**When** the correct password is submitted
**Then** login succeeds and `failed_logins` is 0.

**Given** `failed_logins` is non-zero and `locked_until` has passed
**When** the next wrong password arrives
**Then** the counter restarts from 1 after a successful login only. A wrong attempt after the lock expires increments from its existing count, so one more failure re-locks the account. This is intentional.

**Given** an authenticated request on any route except login, logout and change-password
**When** it is processed
**Then** the cookie is re-issued with `exp = now + 30 min`, never beyond `auth_time + 8 h`.

**Given** a token past `exp` (scenario 15, 30 minutes idle)
**When** the next request is sent
**Then** the response is 401 `UNAUTHENTICATED` (FR58).

**Given** a token past `auth_time + 8 h`
**When** a request is sent
**Then** it is not re-issued, and the user must log in again.

### Story 1.6: Forced and voluntary password change

As a user with a temporary password, I want to set my own password before doing anything else.

**Acceptance Criteria:**

**Given** a token with `must_change_password=true`
**When** any endpoint other than change-password, logout or me is called
**Then** the response is 403 `PASSWORD_CHANGE_REQUIRED`.

**Given** a signed-in user
**When** they `POST /api/auth/change-password` with the correct current password and a valid new password
**Then** `UsersService.setPassword(userId, raw, false)` stores it, `must_change_password` becomes false, the cookie is re-issued, and one `USER_PASSWORD_CHANGED` audit row is written.

**Given** a wrong current password
**When** change-password is called
**Then** the response is 400 `VALIDATION_FAILED` and nothing changes.

**Given** a new password under 8 characters or lacking letters or numbers
**When** change-password is called
**Then** the response is 400 `VALIDATION_FAILED`.

### Story 1.7: HR creates users and resets passwords

As an HR admin, I want to create accounts with a role and manager, and reset forgotten passwords.

**Acceptance Criteria:**

**Given** an HR admin
**When** they `POST /api/users` with username, email, fullName, role, managerId and a temporary password
**Then** the user is created with `must_change_password=true` through `setPassword`, and one `USER_CREATED` row is written with `role` and `managerId` (FR18, FR19)
**And** the response is the `UserSummary`.

**Given** the manager rules (AD-5)
**When** a user is created or updated
**Then** `EMPLOYEE` and `MANAGER` need a `managerId` pointing to an active `MANAGER` or `HR_ADMIN`
**And** `HR_ADMIN` has no manager
**And** a violation, duplicate username or weak password returns 400 `VALIDATION_FAILED`.

**Given** an HR admin
**When** they `GET /api/users`
**Then** all users are listed with role and manager. The read is logged (Story 1.9).

**Given** an HR admin
**When** they `POST /api/users/{id}/reset-password` with a temporary password (scenario 11)
**Then** the password is replaced, `must_change_password=true`, `failed_logins` resets, `locked_until` clears, and one `USER_PASSWORD_RESET` row is written (FR20).

**Given** an `EMPLOYEE` or `MANAGER`
**When** they call any `/api/users/**` endpoint
**Then** the response is 403 `ACCESS_DENIED` and is logged.

### Story 1.8: HR updates users (manager, role, active)

As an HR admin, I want to move someone to a new manager, change their role, or deactivate them, so that approval routing stays correct as people change jobs.

**Acceptance Criteria:**

**Given** an HR admin
**When** they `PATCH /api/users/{id}` with any of `managerId`, `role`, `active`
**Then** the change is applied, validated by the same manager rules as Story 1.7, and one `USER_UPDATED` row is written listing the changed fields.

**Given** a manager change
**When** it is applied
**Then** pending requests keep their original `approver_id` (AD-5). Team visibility and approvals of future requests use the new manager. Story 4.5 covers this.

**Given** a proposed `managerId` that would create a cycle (the user would become their own ancestor)
**When** the update is submitted
**Then** the response is 400 `VALIDATION_FAILED`.

**Given** a user is set to `active=false`
**When** they try to log in
**Then** the response is 401 `INVALID_CREDENTIALS`.
**And** an already-issued token stays valid until its `exp` (at most 30 minutes). This is an accepted POC limit, listed in Story 6.3.
**And** their pending requests stay PENDING with their existing approver. HR decides them or the approver does, as normal.

**Given** an `EMPLOYEE` or `MANAGER`
**When** they call `PATCH /api/users/{id}`
**Then** the response is 403 `ACCESS_DENIED` and is logged.

### Story 1.9: Access logging and the audit viewer

As an HR admin, I want every denied access and every sensitive read logged, and I want to browse the audit trail.

**Acceptance Criteria:**

**Given** migration `V<ts>__audit_create_access_log.sql`
**When** Flyway runs
**Then** `access_log` exists (id, actor_id nullable, resource, resource_id nullable, allowed, created_at) and `audit_reject_mutation` is attached to it, so UPDATE and DELETE fail (FR44).

**Given** the access-log filter, placed right after bearer-token auth
**When** any request ends in 403
**Then** one row is written with `allowed=false`, actor if authenticated, `resource`, `resource_id`, in its own `REQUIRES_NEW` transaction (FR25, FR26).
**And** CSRF and password-change rejections are logged through the same logger, called from the entry-point and access-denied handlers.

**Given** the resource mapping
**When** a sensitive read succeeds
**Then** one row is written with `allowed=true`. The mapping covers exactly these resources: `USER`, `AUDIT`, `BALANCE`, `LEAVE_REQUEST`, `NOTIFICATION`. Every list and single-record read of that data is logged (AD-8).

**Given** an HR admin
**When** they `GET /api/audit` with optional `entity`, `entityId`, `actorId` filters and paging
**Then** rows come back newest first as a `PagedModel` (FR24, FR42)
**And** non-HR callers get 403, which is logged.

**Given** every `@RestController` handler method
**When** an architecture test scans them
**Then** each has `@PreAuthorize`, or the build fails (FR21).

### Story 1.10: Demo HR admin and team structure

As a demo presenter, I want the demo users loaded so that I can show every role without setup.

**Acceptance Criteria:**

**Given** the `demo` profile
**When** the application starts
**Then** Flyway runs `db/seed/R__demo_seed.sql` after the versioned migrations. The seed location is not active in `dev` or `test`.

**Given** the seed has run
**Then** it contains:
- 1 HR_ADMIN (`hr.admin`), the only HR admin in the demo
- 1 top-level MANAGER reporting to `hr.admin`
- 2 team MANAGERs reporting to the top-level manager
- 8 EMPLOYEEs (4 per team manager)

**And** each has a documented demo password stored as a BCrypt hash, with `must_change_password=false`.

**Given** the seed runs again (for example, after a checksum change)
**When** it executes
**Then** it upserts by username and creates no duplicates.

**Given** each demo role logs in
**When** `GET /api/auth/me` is called
**Then** the role and manager match the seed.

---

## Epic 2: Leave Balance Management

### Story 2.1: Leave types and my balances

As an employee, I want to see total, held and available days per leave type.

**Acceptance Criteria:**

**Given** migration `V<ts>__balances_create_leave_types_and_balances.sql`
**When** Flyway runs
**Then** `leave_types` holds Vacation, Sick, Parental, Sabbatical (id, name unique)
**And** `leave_balances` has PK (user_id, leave_type_id), FKs, `total_days` and `reserved_days` (default 0), and `CHECK (reserved_days >= 0 AND reserved_days <= total_days)`.

**Given** any signed-in user
**When** they `GET /api/leave-types`
**Then** the four types are returned.

**Given** any signed-in user of any role
**When** they `GET /api/balances/me`
**Then** one entry per type is returned with `leaveTypeId`, `leaveTypeName`, `totalDays`, `reservedDays`, `availableDays` (= total − reserved), for the caller only (FR6, FR22)
**And** a type with no row returns 0 / 0 / 0
**And** one `access_log` row is written with `allowed=true`, `resource=BALANCE`.

### Story 2.2: HR views balances and sets a balance

As an HR admin, I want to see everyone's balances and set an entitlement with a reason.

**Acceptance Criteria:**

**Given** migration `V<ts>__balances_create_balance_ledger.sql`
**When** Flyway runs
**Then** `balance_ledger` exists (id, user_id, leave_type_id, change_days, kind `set`|`adjust`|`reserve`|`release`|`deduct`, reason, actor_id, request_id nullable, created_at)
**And** `balances_reject_ledger_mutation` blocks UPDATE and DELETE.

**Given** an HR admin
**When** they `GET /api/balances` (optional `userId` filter)
**Then** every user × type is returned with totals (FR27, FR24)
**And** the read is logged with `resource=BALANCE`.

**Given** an HR admin and a balance with `reserved_days = r`
**When** they `PUT /api/balances/{userId}/{leaveTypeId}` with `totalDays = x ≥ r` and a non-blank reason
**Then** the row is locked `PESSIMISTIC_WRITE` (created if missing), `total_days := x`, one `set` ledger row is written with `change_days = x − old total`, and one `LEAVE_BALANCE_SET` row is written, all in one transaction (FR28, FR30).

**Given** `totalDays < reserved_days` or negative
**When** the set is attempted
**Then** the response is 409 `INSUFFICIENT_BALANCE`, and no balance, ledger or audit row changes (FR31).

**Given** a set attempted while requests are in flight (reservations exist)
**When** `totalDays` is lowered but still ≥ `reserved_days`
**Then** the set succeeds, available days drop accordingly, and a test asserts the pending requests keep their reservations unchanged.

**Given** a blank or missing reason, unknown user or unknown type
**When** the set is attempted
**Then** the response is 400 `VALIDATION_FAILED`.

**Given** an `EMPLOYEE` or `MANAGER`
**When** they call `GET /api/balances` or `PUT /api/balances/**`
**Then** the response is 403 `ACCESS_DENIED` and is logged.

### Story 2.3: HR adjusts a balance and reviews the ledger

As an HR admin, I want to add or remove days with a reason and see every change.

**Acceptance Criteria:**

**Given** an HR admin
**When** they `POST /api/balances/{userId}/{leaveTypeId}/adjust` with a non-zero integer `changeDays` and a non-blank reason, and `total + changeDays ≥ reserved`
**Then** the row is locked, `total_days` changes, one `adjust` ledger row is written, and one `LEAVE_BALANCE_ADJUSTED` row is written (FR29, FR30).

**Given** an adjustment that leaves `total < reserved` or negative (scenario 12)
**When** it is attempted
**Then** the response is 409 `INSUFFICIENT_BALANCE` and nothing changes (FR31).

**Given** `changeDays = 0`, blank reason, unknown user or unknown type
**When** an adjustment is attempted
**Then** the response is 400 `VALIDATION_FAILED`.

**Given** an HR admin
**When** they `GET /api/balances/{userId}/ledger` (optional `leaveTypeId`)
**Then** rows come back newest first with type, kind, changeDays, reason, actor id and name, requestId, createdAt
**And** the read is logged with `resource=BALANCE`.

**Given** any non-HR caller
**When** they call the ledger endpoint
**Then** the response is 403 and is logged.

**Given** a test that issues UPDATE or DELETE on `balance_ledger`
**Then** the statement fails.

### Story 2.4: Reserve, release and deduct

As an employee, I want days held when I request them and released or deducted on decision, so that I can never overbook.

**Acceptance Criteria:**

**Given** `BalancesService`
**When** it is inspected
**Then** it exposes `reserve`, `release`, `deduct` (userId, leaveTypeId, days, requestId, actorId). Each joins the caller's transaction and writes no audit row (ledger only, AD-7).

**Given** `n ≤ available`
**When** `reserve(n)` is called
**Then** the row is locked, `reserved += n`, and a `reserve` ledger row `+n` with `request_id` is written.

**Given** `n > available`, including no row (0 available)
**When** `reserve(n)` is called
**Then** `InsufficientBalanceException` (409) is thrown and nothing is written (FR31).

**Given** `release(n)` after a reservation of n
**Then** `reserved −= n` and a `release` row `−n` is written.

**Given** `deduct(n)` after a reservation of n
**Then** `total −= n`, `reserved −= n`, and a `deduct` row `−n` is written.

**Given** two concurrent reserves whose sum exceeds available (Testcontainers)
**Then** exactly one succeeds, the other gets 409, and `reserved ≤ total` holds.

**Given** the AD-6 table
**When** service tests run
**Then** every row, including refusals, asserts both balance columns and the signed `change_days`.

### Story 2.5: Demo balances

As a demo presenter, I want every demo user to start with realistic balances.

**Acceptance Criteria:**

**Given** the demo seed runs
**Then** every demo user has a row per type (Vacation 20, Sick 10, Parental 0, Sabbatical 0), each with exactly one `set` ledger row, reason `Demo seed`, actor `hr.admin`.

**Given** the seed runs again
**Then** no balance or ledger row is duplicated or changed.

**Note:** these `set` rows have no audit row. This is a documented exception to AD-7's one-audit-per-use-case rule, because they are seeded data, not a business action.

---

## Epic 3: Request & Track Leave

### Story 3.1: Submit a leave request

As an employee, I want to request leave by type and dates and have my days held immediately.

**Acceptance Criteria:**

**Given** migration `V<ts>__leave_create_leave_requests.sql`
**When** Flyway runs
**Then** `leave_requests` exists (id, employee_id, approver_id, leave_type_id, start_date, end_date, working_days, notes, status, submitted_at, decided_at, decision_comment) with FKs
**And** a later migration `V<ts>__balances_add_ledger_request_fk.sql` adds `balance_ledger.request_id → leave_requests.id`.

**Given** `common`'s working-day function
**When** unit tested
**Then** it counts Mon–Fri in `[start, end]` inclusive. Thu → Tue = 4. It is the only implementation (FR2).

**Given** any signed-in user with enough available days
**When** they `POST /api/requests` with `leaveTypeId`, `startDate`, `endDate`, optional `notes` (scenario 1)
**Then** a PENDING request is created with `working_days` computed on the server, `employee_id` = caller, `submitted_at` from `Clock` (FR1–FR3, FR9)
**And** `approver_id` = caller if HR_ADMIN, else `manager_id` (AD-5)
**And** `reserve` holds the days, and one `LEAVE_REQUEST_SUBMITTED` row is written (`leaveTypeId`, `startDate`, `endDate`, `workingDays`, `selfApproved`), all in one transaction
**And** `LeaveRequestSubmitted` is published (id only).

**Given** working days exceed available days, counting other pending reservations (scenario 10)
**When** the request is submitted
**Then** the response is 409 `INSUFFICIENT_BALANCE` with the available count in the message, and no request, ledger or audit row is written (FR31).

**Given** a request that overlaps another PENDING or APPROVED request of the same employee (any leave type)
**When** it is submitted
**Then** the response is 409 `OVERLAPPING_REQUEST` and nothing is written.
**Note:** this rule is proposed (see Decisions). It applies to the same employee only, and to dates that share at least one day.

**Given** a start date in the past
**When** it is submitted
**Then** it is accepted (back-dating allowed). This is proposed (see Decisions).

**Given** `endDate < startDate`, a range with zero working days, or an unknown type
**When** submitted
**Then** the response is 400 `VALIDATION_FAILED`.

**Given** a request with notes
**When** it is submitted
**Then** the notes never appear in any log line (NFR10).

### Story 3.2: View my requests and request detail

As an employee, I want to see my requests and their status, and open one.

**Acceptance Criteria:**

**Given** a signed-in user
**When** they `GET /api/requests` with optional `status`, `leaveTypeId`, `from`, `to`
**Then** only the caller's own requests are returned, newest first, including cancelled and rejected (FR4, FR5, FR22)
**And** the list read is logged with `resource=LEAVE_REQUEST` (AD-8).

**Given** `@leavePermissions.canView(requestId)`
**When** evaluated
**Then** it is true for the owner, approver, owner's live manager, or HR, and false otherwise, including for a missing id.

**Given** a caller for whom `canView` is true
**When** they `GET /api/requests/{id}`
**Then** the full request is returned, including notes, approver id and name, decision comment and decided time
**And** one `access_log` row is written with `allowed=true`, `resource=LEAVE_REQUEST`, `resource_id={id}`.

**Given** an employee opening another employee's request (scenario 9)
**When** they `GET /api/requests/{id}`
**Then** the response is 403 `ACCESS_DENIED` and an `allowed=false` row is written (FR22, FR25, FR26). An RBAC test covers this.

**Given** a missing id
**When** any caller requests it
**Then** the response is 403, not 404 (AD-8).

### Story 3.3: Edit a pending request

As an employee, I want to change dates or notes of a request that is still pending.

**Acceptance Criteria:**

**Given** the owner of a PENDING request
**When** they `PUT /api/requests/{id}` with new dates and/or notes
**Then** the request row is locked (`PESSIMISTIC_WRITE`) and status re-checked, `working_days` is recomputed, and the balance changes by delta: `reserve(delta)` if positive, `release(−delta)` if negative, no call if zero (AD-6). Lock order: request first, then balance (AD-4).
**And** one `LEAVE_REQUEST_EDITED` row is written (`startDate`, `endDate`, `workingDays`, `notesChanged`) in the same transaction (FR7).
**And** `approver_id` does not change.

**Given** an edit that makes `workingDays = 0` (for example, dates changed to a weekend)
**When** submitted
**Then** the response is 400 `VALIDATION_FAILED` and nothing changes.

**Given** an edit that makes `endDate < startDate`
**When** submitted
**Then** the response is 400 `VALIDATION_FAILED`.

**Given** a positive delta that exceeds available days
**When** submitted
**Then** the response is 409 `INSUFFICIENT_BALANCE` and nothing changes.

**Given** an edit that would overlap another PENDING or APPROVED request of the same employee
**When** submitted
**Then** the response is 409 `OVERLAPPING_REQUEST`. The request's own row is excluded from the check.

**Given** a body whose `leaveTypeId` differs
**When** submitted
**Then** the response is 400 `LEAVE_TYPE_CHANGE_NOT_ALLOWED`.

**Given** an APPROVED, REJECTED or CANCELLED request
**When** the owner edits it
**Then** the response is 409 `INVALID_TRANSITION`.

**Given** anyone other than the owner (including approver and HR)
**When** they try to edit
**Then** the response is 403 (`canEdit`) and is logged.

### Story 3.4: Cancel a pending request

As an employee, I want to cancel a pending request so that its days are returned immediately.

**Acceptance Criteria:**

**Given** the owner of a PENDING request of n days
**When** they `POST /api/requests/{id}/cancel` (scenario 2)
**Then** the request is locked, status re-checked, set to CANCELLED, `release(n)` is called, and one `LEAVE_REQUEST_CANCELLED` row is written, all in one transaction (FR8). Lock order: request, then balance.
**And** the request is never deleted and still appears in the list.

**Given** a request that is not PENDING
**When** cancel is attempted
**Then** the response is 409 `INVALID_TRANSITION` and nothing changes.

**Given** anyone other than the owner
**When** they try to cancel
**Then** the response is 403 (`canCancel`) and is logged.

**Given** the `leave` code
**When** reviewed
**Then** every transition (submit, cancel, approve, reject) goes through one component in `leave.internal` that enforces AD-4.

**Scope note:** cancelling an APPROVED request is not supported in the POC (see Story 6.3).

### Story 3.5: Discuss a request in a comment thread

As an employee or manager, I want to comment on a request so that questions get settled on the record.

**Acceptance Criteria:**

**Given** migration `V<ts>__leave_create_request_comments.sql`
**When** Flyway runs
**Then** `request_comments` exists (id, request_id FK, author_id FK, body, created_at).

**Given** `canComment` (= `canView`) is true, in any status
**When** the caller `POST /api/requests/{id}/comments` with a non-blank body of at most 2000 characters
**Then** the comment is saved with author and `Clock` time, and one `REQUEST_COMMENT_ADDED` row is written with `requestId` (FR15)
**And** no event is published and no email or notification is created.

**Given** a caller who can view the request
**When** they `GET /api/requests/{id}/comments`
**Then** comments are returned oldest first with author id, name, body, `createdAt`
**And** the read is logged with `resource=LEAVE_REQUEST`.

**Given** a blank or over-2000-character body
**When** posted
**Then** the response is 400 `VALIDATION_FAILED`.

**Given** a caller who cannot view the request
**When** they read or post
**Then** the response is 403 and is logged.

### Story 3.6: Request History timeline

As an employee, manager or HR admin, I want one timeline of everything that happened to a request.

**Acceptance Criteria:**

**Given** `AuditService`
**When** `leave` needs history
**Then** it exposes a read method for an entity and id. `leave` does no SQL against `audit_events`.

**Given** a caller for whom `canView` is true
**When** they `GET /api/requests/{id}/history`
**Then** `LEAVE_REQUEST` audit rows and request comments are merged by `createdAt`, unpaged, each with type (`EVENT` or `COMMENT`), action, actor id and name, `createdAt`, and details or body (FR45, FR9)
**And** the read is logged with `resource=LEAVE_REQUEST`.

**Given** a decision with a comment
**When** history is fetched
**Then** the decision comment appears exactly once, inside the `LEAVE_REQUEST_APPROVED` or `LEAVE_REQUEST_REJECTED` entry's details. It is not repeated as a COMMENT entry (no double-count).

**Given** a request submitted, edited, commented on, then cancelled (scenarios 1 and 2)
**When** history is fetched
**Then** entries appear in order: `LEAVE_REQUEST_SUBMITTED`, `LEAVE_REQUEST_EDITED`, COMMENT, `LEAVE_REQUEST_CANCELLED`.

**Given** a caller who cannot view the request
**When** they fetch history
**Then** the response is 403 and is logged.

---

## Epic 4: Approve, Reject & Oversee Team Leave

### Story 4.1: Pending approvals list

As a manager, I want one list of requests waiting for my decision.

**Acceptance Criteria:**

**Given** the `approvals` module (no tables, no SQL)
**When** a MANAGER or HR_ADMIN calls `GET /api/approvals/pending`
**Then** PENDING requests with `approver_id` = caller are returned, oldest first, with id, employee id and name, leave type, dates, working days, `submittedAt` (FR10)
**And** data comes through `LeaveService` only
**And** the read is logged with `resource=LEAVE_REQUEST`.

**Given** requests pending for another approver, or already decided
**When** the list is fetched
**Then** they do not appear.

**Given** an EMPLOYEE
**When** they call the endpoint
**Then** the response is 403 and is logged.

### Story 4.2: Decision context

As a manager, I want the employee's balance and recent leave next to the request.

**Acceptance Criteria:**

**Given** `@leavePermissions.canDecide(id)` (caller is `approver_id`; false for missing id)
**When** the approver calls `GET /api/approvals/{id}/context`
**Then** the response includes the request, the employee's `UserSummary`, their balance for that type (total, reserved, available), and their 5 most recent other requests (FR11)
**And** it is built by `LeaveService.getDecisionContext(id)`, which reads balances through `BalancesService`
**And** the read is logged with `resource=LEAVE_REQUEST`.

**Given** anyone other than the approver
**When** they request the context
**Then** the response is 403 and is logged.

### Story 4.3: Approve a request

As a manager, I want to approve a pending request with an optional comment.

**Acceptance Criteria:**

**Given** the approver of a PENDING request of n days
**When** they `POST /api/approvals/{id}/approve` with optional `comment` up to 2000 characters (scenario 3)
**Then** in one transaction: lock the request, re-check PENDING, set APPROVED, `decided_at` from `Clock`, `decision_comment`; call `deduct(n)`; write one `LEAVE_REQUEST_APPROVED` row (`comment`, `selfApproved`) (FR12, FR16). Lock order: request, then balance.
**And** `LeaveRequestApproved` is published (id only).
**And** the decision comment is not added to `request_comments`.

**Given** a request that is APPROVED, REJECTED or CANCELLED
**When** approve is called
**Then** the response is 409 `INVALID_TRANSITION` and the balance is untouched.

**Given** cancel and approve on the same request concurrently (Testcontainers)
**Then** exactly one wins, the other gets 409 `INVALID_TRANSITION`, and the balance reflects only the winner.

**Given** a caller who is not `approver_id`
**When** they call approve
**Then** the response is 403 and is logged.

**Given** a request that was commented on, edited, then approved (scenario 5)
**When** history is fetched
**Then** the comments, the EDITED entry and the APPROVED entry appear in time order, and no notification was created by the comments.

### Story 4.4: Reject a request with a reason

As a manager, I want to reject a request and state why.

**Acceptance Criteria:**

**Given** the approver of a PENDING request of n days
**When** they `POST /api/approvals/{id}/reject` with a non-blank `comment` up to 2000 characters (scenario 4)
**Then** in one transaction: lock, re-check, set REJECTED with `decided_at` and `decision_comment`; `release(n)`; one `LEAVE_REQUEST_REJECTED` row (`comment`, `selfApproved`) (FR13, FR16).
**And** `LeaveRequestRejected` is published (id only).

**Given** a missing or blank comment
**When** reject is called
**Then** the response is 400 `COMMENT_REQUIRED` and nothing changes.

**Given** a comment over 2000 characters
**When** reject is called
**Then** the response is 400 `VALIDATION_FAILED`.

**Given** a request that is not PENDING
**When** reject is called
**Then** the response is 409 `INVALID_TRANSITION`.

**Given** a caller who is not `approver_id`
**When** they call reject
**Then** the response is 403 and is logged.

### Story 4.5: Routing up the chain and manager changes

As a manager or HR admin, I want my own leave to go to the person above me, or be self-approved at HR level.

**Acceptance Criteria:**

**Given** a team manager submits leave (scenario 6)
**When** the top-level manager lists pending approvals
**Then** the request appears, and they can approve it (FR17, FR19).

**Given** the top-level manager submits leave (scenario 7)
**When** `hr.admin` lists pending approvals
**Then** the request appears, and they can approve it.

**Given** HR submits their own leave
**When** they approve it
**Then** the approval succeeds with `selfApproved: true` in the audit row, and the History shows it.
**And** the approved email goes to the requester (who is also the approver). Story 5.2 specifies this.

**Given** a manager outside the request's chain (scenario 8)
**When** they view, approve or reject it
**Then** the response is 403 and an `allowed=false` row is written (FR17, FR23, FR25, FR26).

**Given** HR changes an employee's manager after submission (Story 1.8)
**When** the old and new managers try to decide
**Then** only the original `approver_id` can decide (AD-5). The new manager can view the request as the owner's live manager.

**Given** the test suite
**Then** it includes one RBAC integration test each for scenarios 6, 7, 8 and 9.

### Story 4.6: Team leave view

As a manager, I want my team's approved leave by date so I can plan cover.

**Acceptance Criteria:**

**Given** a MANAGER
**When** they `GET /api/team/leave` with optional `from` and `to` (defaults: today through 60 days ahead)
**Then** APPROVED requests of their live direct reports (`manager_id` = caller) that overlap the range are returned, ordered by start date, with employee id and name, type, dates, working days (FR14, FR23)
**And** the read is logged with `resource=LEAVE_REQUEST`.

**Given** a range longer than 366 days
**When** requested
**Then** the response is 400 `VALIDATION_FAILED`.

**Given** `from` after `to`
**When** requested
**Then** the response is 400 `VALIDATION_FAILED`.

**Given** requests of other managers' teams, or non-APPROVED requests
**When** the view is fetched
**Then** they do not appear.

**Given** an EMPLOYEE or HR_ADMIN
**When** they call the endpoint
**Then** the response is 403 and is logged.

---

## Epic 5: Email Notifications & Notification History

### Story 5.1: Email the approver on submission

As a manager, I want an email when someone submits leave to me.

**Acceptance Criteria:**

**Given** migration `V<ts>__notifications_create_notifications.sql`
**When** Flyway runs
**Then** `notifications` exists (id, user_id FK, request_id FK, kind `REQUEST_SUBMITTED` | `REQUEST_APPROVED` | `REQUEST_REJECTED`, subject, sent_at).

**Given** the module
**When** inspected
**Then** it sends only through `NotificationSender` (SMTP via `JavaMailSender`, `MAIL_HOST`, `MAIL_PORT`, 5 s timeouts). No Spring Modulith event-publication starter is on the classpath.

**Given** a committed submission (scenario 1)
**When** the `AFTER_COMMIT` listener gets `LeaveRequestSubmitted`
**Then** it loads the request through `LeaveService` and people through `UsersService`, and emails `approver_id` (FR52)
**And** the email shows employee, leave type, start and end dates, working days, status and the link `APP_BASE_URL/requests/{id}` (FR54), from a template in `templates/email/`
**And** the email never includes request notes (applies to all three emails)
**And** one `REQUEST_SUBMITTED` notification row is written in `REQUIRES_NEW`.

**Given** a self-approved submission (approver = requester)
**When** it is submitted
**Then** no "submitted" email is sent and no row is written.

**Given** a submission that rolls back (for example, `INSUFFICIENT_BALANCE`)
**When** it fails
**Then** no email is sent and no row is written.

**Given** SMTP is unreachable or times out
**When** a submission commits
**Then** the API still succeeds, the listener logs the error (no tokens or notes), and no row is written (NFR11).

**Given** the dev stack
**Then** the email appears in Mailpit, verified by one integration test (Testcontainers SMTP or an equivalent test server).

### Story 5.2: Email the requester on decision

As an employee, I want an email when my request is approved or rejected.

**Acceptance Criteria:**

**Given** a committed approval (scenario 3)
**When** the listener gets `LeaveRequestApproved`
**Then** the requester is emailed with dates, days, status `APPROVED`, approver name, any comment, and the link (FR50, FR54)
**And** one `REQUEST_APPROVED` row is written in `REQUIRES_NEW`.

**Given** an HR self-approved request
**When** it is approved
**Then** the requester (who is also the approver) still receives the approved email and a `REQUEST_APPROVED` row. This is the one case where the approver is emailed about their own decision.

**Given** a committed rejection (scenario 4)
**When** the listener gets `LeaveRequestRejected`
**Then** the requester is emailed with dates, days, status `REJECTED`, approver name and the rejection comment (FR51, FR54)
**And** one `REQUEST_REJECTED` row is written.

**Given** a comment posted on a request (FR15)
**When** saved
**Then** no email and no notification row is produced.

**Given** SMTP fails after a decision commits
**When** the response returns
**Then** it is unaffected and the error is logged.

### Story 5.3: Notification history

As a user, I want to see the notifications sent to me.

**Acceptance Criteria:**

**Given** a signed-in user of any role
**When** they `GET /api/notifications` with paging
**Then** only rows where `user_id` = caller are returned, newest first, as a `PagedModel`, each with id, kind, subject, `requestId`, `sentAt` (FR53)
**And** the read is logged with `resource=NOTIFICATION` (AD-8).

**Given** notifications for other users
**When** a user fetches their history
**Then** none appear, and no endpoint reads another user's notifications.

**Given** an unauthenticated caller
**When** they call the endpoint
**Then** the response is 401 `UNAUTHENTICATED`.

---

## Epic 6: Demo-Ready Stack & Data Protection

### Story 6.1: One-command demo stack over HTTPS

As a demo presenter, I want the whole backend to start with one command over HTTPS, with secure cookies working as in production.

**Acceptance Criteria:**

**Given** `backend/Dockerfile`
**When** built
**Then** it is multi-stage (JDK 21 build, Java 21 JRE runtime), runs as non-root, and reads all config from environment variables.

**Given** `docker-compose.yml` and a `.env` made from the committed `.env.example` (`.env` git-ignored)
**When** `docker compose up` runs
**Then** `postgres:16` (healthcheck), `mailpit:v1.31`, the `backend` (profile `demo`, waits for healthy postgres) and `caddy:2.11` start
**And** the demo seed loads and the bootstrap variables are set (bootstrap is off in `demo`, Story 1.3).

**Given** the `Caddyfile`
**When** the stack is up
**Then** Caddy terminates TLS with its internal CA and proxies to the backend (FR55, Partial)
**And** API and SPA origins share a registrable domain, and `CORS_ALLOWED_ORIGINS` lists the SPA origin.
**And** the compose file has a documented `frontend` slot for the SPA plan.

**Given** a demo user logs in over HTTPS
**When** the response returns
**Then** the cookie is `Secure; HttpOnly; SameSite=Strict`, and a following authenticated call succeeds through Caddy.

**Given** the backend cannot reach Postgres or `JWT_SECRET` is under 32 bytes
**When** it starts
**Then** it exits with a clear error and does not run half-configured.

### Story 6.2: Daily backup script and restore notes

As an operator, I want a dated dump of the database and documented restore steps.

**Acceptance Criteria:**

**Given** the compose stack is running
**When** `scripts/backup.sh` runs (scenario 16)
**Then** it runs `pg_dump` and writes `backups/leave-YYYY-MM-DD.sql.gz` with today's date (FR46, FR60)
**And** credentials come from the environment or `.env`, never from the script.

**Given** `pg_dump` fails or Postgres is down
**When** the script runs
**Then** it exits non-zero with a message and leaves no partial file.

**Given** today's file already exists
**When** the script runs again
**Then** it overwrites that day's file.

**Given** the repository
**Then** `backups/` is git-ignored, and the README documents daily scheduling (cron on Linux and macOS; Task Scheduler via Git Bash or WSL on Windows).

**Given** the README restore section
**Then** it gives the steps to restore into an empty database (`gunzip` piped to `psql`), marked as documented but not tested (FR61, Deferred).

### Story 6.3: Project README and operating notes

As a developer or the client, I want one README that explains running, demoing and the limits of the POC.

**Acceptance Criteria:**

**Given** the repo-root `README.md`
**Then** it covers prerequisites (JDK 21, Docker), the dev run, the demo run, the Mailpit URL, and a table of every environment variable with purpose and an example value (no real secrets).

**Given** the demo section
**Then** it lists every demo user with role, manager and demo password, and their starting balances.

**Given** the data-protection section
**Then** it states the retention policy (audit, access-log and ledger rows kept at least 3 years, insert-only, no purge path) (FR47), and links Story 6.2.

**Given** the limitations section
**Then** it lists:
- Deferred production items (from the PRD)
- Token revocation: a logged-out or deactivated user's token remains valid for up to 30 minutes
- Emails are lost if SMTP fails or the app crashes after commit
- Restore is documented but untested
- Approved-leave cancellation and edits are not supported; a manager or HR must handle it outside the system
- Database superusers can disable the immutability trigger (scenario 13); the trigger protects against application and ordinary-role writes only
- Phase 2 FRs are not built
- Back-dating and overlap rules as decided (see Decisions)

### Story 6.4: Acceptance suite and demo script

As the product owner, I want each PRD scenario proven by an automated test and a script to walk the client through.

**Acceptance Criteria:**

**Given** a Testcontainers Postgres loaded with the demo-equivalent fixtures
**When** `./mvnw verify` runs
**Then** there is one named API-level test per scenario 1–15, asserting its "Pass when" column (statuses, reserved, deducted and released days, audit and History entries, 403 plus access log, 409 refusals, lockout, session expiry via test `Clock`)
**And** email assertions for scenarios 1, 3 and 4 use a capturing `NotificationSender` double or the SMTP test server.

**Given** scenario 16
**Then** it is verified manually by running `scripts/backup.sh` against the demo stack, as a step in the demo script.

**Given** the required suite (Spine)
**Then** it includes and passes: Modulith `verify()`, RBAC tests for scenarios 6–9, the audit trigger test (13), the lockout test (14), the CSRF rejection test, the `@PreAuthorize` coverage test, and a lock-order test (request before balance under concurrent approve, edit and cancel).

**Given** `docs/demo-script.md`
**When** a presenter follows it
**Then** it walks through scenarios 1–16 in order, with the user to log in as, ready-to-run requests in a `.http` file, expected results, and where to see the emails in Mailpit.

---

## Decisions

Proposed in this version. Anil must confirm the two marked **(proposed)** before the stories are final.

| Topic | Decision |
|---|---|
| Back-dating | **(proposed)** Allowed. No rule in POC. |
| Overlapping requests | **(proposed)** Refused with `OVERLAPPING_REQUEST` for the same employee, any leave type, when dates share a day with a PENDING or APPROVED request. |
| Approved-leave cancellation | Out of scope for POC. Documented as a limitation. |
| Bootstrap vs demo seed | Demo seed owns `hr.admin`. Bootstrap runs only in `dev` and `test`. |
| HR self-approved request email | Requester still gets the approved email. |
| Token after logout or deactivation | Valid until `exp` (max 30 min). Accepted, documented. |
| Lockout extension | Attempts during a lock do not extend it. |
| Access-log scope | Every list and single read of leave, balance, user, audit and notification data is logged. |
| Lock order | Request row first, then balance row. |
| Decision comment length | 2000 characters, same as comments. |

## Open items before sprint

1. Confirm the two proposed decisions (back-dating, overlap).
2. Update AD-7 from 12 actions to 13 (`USER_UPDATED` added), and add `OVERLAPPING_REQUEST` to the error catalog in AD-13 / `common`.
3. Add the lock-order rule to AD-4 (already reflected here).
4. Add the access-log scope rule to AD-8 (already reflected here).