---
status: final
stepsCompleted: [1, 2, 3, 4]
inputDocuments:
  - _bmad-output/planning-artifacts/prds/poc-prd-emp-leave-tracker-2026-10-22/prd.md
  - _bmad-output/planning-artifacts/architecture/architecture-company-leave-2026-10-22/ARCHITECTURE-SPINE.md
  - backend/src/main/resources/openapi.yaml
scope: backend-only
---

# company-leave - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for company-leave (Employee Leave Tracking System POC), decomposing the requirements from the POC PRD, the backend Architecture Spine, and the OpenAPI/Swagger contract (`backend/src/main/resources/openapi.yaml`) into implementable stories.

**Scope decision (confirmed with anil_vn, 2026-10-22):** stories cover the **Java/Spring Boot backend only**, matching the Architecture Spine's stated backend-only scope. The Angular front end is not storied here; it becomes separate planning work once a UX design contract exists. Acceptance criteria are therefore written at the API, service, and database level, verifiable with API tests, Testcontainers integration tests, and direct DB checks.

**No UX design contract exists** (`planning-artifacts/ux-designs/` not present), so there are no UX-DRs. The PRD's UI constraints (responsive Chrome/Edge desktop-first, semantic HTML, labelled fields, keyboard navigation) are recorded under Additional Requirements as deferred frontend scope.

## Requirements Inventory

### Functional Requirements

The POC covers 45 in-scope FRs: FR1-FR31, FR42-FR45, FR50-FR59. FR numbers match the source PRD (Employee Leave Tracking System PRD, Anil, 2026-10-15).

**Leave requests (FR1-FR9)**

FR1: An employee can submit a leave request choosing a leave type from Vacation, Sick, Parental or Sabbatical; whole days only.
FR2: A leave request carries a start date and an end date, with end date on or after start date; leave days are counted as working days Monday-Friday, inclusive, public holidays ignored.
FR3: An employee can attach optional free-text notes to a leave request.
FR4: An employee can view the status of each of their requests as one of Pending, Approved, Rejected or Cancelled.
FR5: An employee can view a filterable history of their own leave requests.
FR6: An employee can view their leave balance per leave type, showing total days, reserved (pending) days and available days.
FR7: An employee can modify their own leave request only while its status is Pending; the balance reservation is recalculated and the change is logged.
FR8: An employee can cancel their own Pending leave request; status becomes Cancelled, reserved days are released, and the request is never deleted.
FR9: Every leave request records the submitting user and the submission timestamp, on the request and on an audit event.

**Approvals (FR10-FR17)**

FR10: A manager can see a Pending Approvals dashboard listing the pending requests of their direct reports.
FR11: A manager can see rich context for a pending request: the employee, dates, leave type, their balance, and recent request history.
FR12: A manager can approve a pending request with an optional comment; approval converts reserved days into deducted days.
FR13: A manager can reject a pending request only with a non-blank comment; rejection releases the reserved days.
FR14: A manager can view their team's approved leave by date, each entry carrying its comment thread (FR15) and its History timeline (FR45).
FR15: Any authorized viewer of a request (owner, their manager, HR) can add and read discussion comments on it; comments trigger no email and no in-app alert and appear on page refresh.
FR16: Every decision records the decision, the decision timestamp and the deciding approver, on the request and on an audit event.
FR17: Only the requester's own manager (`manager_id`) may decide a request; HR users decide their own requests (recorded as self-approval); any other actor is refused.

**Users and access (FR18-FR26)**

FR18: An HR admin can create user accounts, typing a temporary password that is hashed immediately and never stored or logged in plaintext.
FR19: An HR admin assigns a role and a manager at account creation; the manager must be a MANAGER or HR_ADMIN user, and top-level managers report to an HR user.
FR20: An HR admin can reset a user's password; the system generates a temporary password and the user must change it at next login.
FR21: The system enforces role-based access control for exactly three roles - EMPLOYEE, MANAGER, HR_ADMIN - on every endpoint.
FR22: Employees can read only their own data; queries are scoped by their user id.
FR23: Managers can read only their direct reports' data; queries are scoped by `manager_id`.
FR24: HR admins can read all data, with no scope filter applied.
FR25: Unauthorized access attempts are refused with HTTP 403 and recorded as an access-denied log entry.
FR26: Access to sensitive data is written to an insert-only access log by a request filter; the POC exposes no read API or UI for it.

**Balances (FR27-FR31)**

FR27: An HR admin can view all users' leave balances.
FR28: An HR admin can set a user's balance for a leave type, with a required reason.
FR29: An HR admin can adjust a user's balance for a leave type by a positive or negative amount, with a required reason.
FR30: Every balance change is recorded in a balance ledger with timestamp, change amount, kind, reason and acting user, readable per user and leave type, oldest first.
FR31: A balance can never go negative: a submission is refused when its working days exceed available days (total - reserved), and an HR adjustment that would take the balance below zero is refused.

**Audit (FR42-FR45)**

FR42: Every state-changing transaction writes an audit event.
FR43: Every audit event records the acting user and the timestamp as required columns.
FR44: Audit records are immutable: the table is insert-only and a database trigger blocks UPDATE and DELETE.
FR45: Each request exposes a History timeline of its own events in order - submission, edits, comments, cancellation and the decision.

**Notifications (FR50-FR54)**

FR50: Approving a request triggers `sendNotification()`, which writes a `notifications` row and prints a log line; no mail server is called.
FR51: Rejecting a request triggers `sendNotification()` the same way, carrying the rejection reason.
FR52: Submitting a request triggers `sendNotification()` the same way, addressed to the approving manager.
FR53: A user can view their own in-app notification history, read from the `notifications` rows.
FR54: Notification subject and body are built from an email template containing the leave type, dates, day count, status and a link to the request.

**Security (FR55-FR59)**

FR55: The application is served over HTTPS; in the POC this is a local reverse proxy with a self-signed certificate (Partial - real certificates are out of POC scope).
FR56: Users authenticate with username and password, passwords stored as BCrypt hashes.
FR57: Five consecutive failed logins lock the account for 15 minutes.
FR58: A session expires after 30 minutes of inactivity; the next action requires login again.
FR59: Access to sensitive resources is written to the access log (same mechanism as FR26).

**Explicitly out of scope** - do not generate stories for these: FR32-FR41, FR48, FR49 (Phase 2: balance history view, carryover, reports, exports, audit export, before/after audit values) and FR46, FR47, FR60, FR61 (backups, 3-year retention, restore testing - not planned for this POC).

### NonFunctional Requirements

Derived from the PRD's "Technical requirements" and the Architecture Spine's Consistency Conventions and Stack, since the PRD carries no numbered NFR list.

NFR1: Persistence is a relational, ACID database - PostgreSQL 18.
NFR2: Every REST endpoint carries a role check; no endpoint is reachable without authorization evaluation.
NFR3: `audit_events` and `access_log` are insert-only, protected by a Flyway-managed Postgres `BEFORE UPDATE OR DELETE` trigger that raises an exception (AD-6).
NFR4: Passwords are BCrypt-hashed; the password rule is minimum 8 characters containing letters and numbers.
NFR5: The auth token is delivered in an `HttpOnly; Secure; SameSite=Strict` cookie, never exposed to JavaScript (AD-7).
NFR6: Account lockout is 5 failed logins for 15 minutes (AD-9); session idle timeout is 30 minutes, enforced against an `active_sessions` registry rather than token expiry alone (AD-8).
NFR7: Schema is managed by Flyway from day one; schema migrations and seed-data migrations are separate, versioned files, and seed data is added incrementally as each epic's entities land - never all in one story.
NFR8: All configuration, including the database URL/credentials and the JWT signing secret, comes from environment variables; no secret is committed to the repository or baked into an image (AD-13).
NFR9: Unit and API tests cover RBAC, approval rules and audit writes; these are the riskiest paths.
NFR10: Integration tests run against a real PostgreSQL via Testcontainers, not an in-memory substitute, so triggers and constraints are exercised.
NFR11: Runtime is Java 21 with Spring Boot 4.1.1 (parent already pinned in `pom.xml`, superseding the PRD's stale "Spring Boot 3"), built with the committed Maven wrapper.
NFR12: Surrogate keys are `BIGINT GENERATED ALWAYS AS IDENTITY`; all timestamps are `timestamptz` with the JVM pinned to UTC; `start_date`/`end_date` are `DATE`; leave amounts are whole-day integers.
NFR13: Every non-2xx response is an RFC 9457 `ProblemDetail` produced by a single `@RestControllerAdvice` in `common`, carrying a `code` drawn from one canonical `common.ErrorCode` enum (AD-11).
NFR14: The whole stack starts with one `docker compose up`; the reverse proxy is the only service exposed outside the compose network, and backend config uses in-network service hostnames (AD-13).
NFR15: Logging is SLF4J/Logback to stdout; no external APM.
NFR16: Naming follows the spine: packages lower-case singular per domain, JPA entities PascalCase singular, DTOs suffixed `Request`/`Response`, tables `snake_case` plural matching the PRD's core-tables list.
NFR17 (deferred - frontend): responsive desktop-first layout for Chrome and Edge, semantic HTML, labelled form fields and keyboard navigation on main flows. Out of backend-only story scope; carried forward to frontend planning.

### Additional Requirements

**Story implementation workflow (binding for every story):**

- Before implementing a story, explicitly load `backend/AGENTS.md`, read its acceptance criteria and detailed implementation spec when present, and follow the binding Architecture Spine. Resolve open design decisions before coding.
- Use Lombok for boilerplate in every Java model/POJO, with annotations appropriate to the type; avoid `@Data` on JPA entities unless explicitly justified.
- Record applicable design patterns and non-obvious coding practices in the story's implementation notes and project context before coding. After completing a story, add its runnable curl examples and expected outcomes to `backend/curl/story-<epic>.<story>-<slug>.md`, following the existing guides, and run them against the local stack when available.

**Starter template:** none. There is no greenfield starter/template to scaffold from - the backend scaffold already exists in the repository (commit `076b1cd`, "Story 1.1 modular backend foundation"), currently just `CompanyLeaveApplication.java`, `application.yml`, an empty `db/migration/` directory, and `openapi.yaml`. Epic 1's first story therefore **extends and corrects the existing scaffold**, it does not initialize a new project.

**Architecture-driven requirements (ADs are binding, not advisory):**

- AD-1: Modules communicate by direct, synchronous, in-process `@Service` bean calls only - no `@Async`, no `ApplicationEventPublisher`, no message broker between domain modules.
- AD-1 **open item - still open as of 2026-10-22 (blocking, must be closed by the first story touching `pom.xml`):** remove `spring-modulith-starter-core`, `spring-modulith-starter-test` and the `spring-modulith-bom` import (plus the `spring-modulith.version` property), which imply boundary verification this design deliberately does not run. Verified present in `backend/pom.xml` at lines 31, 37-39, 48-49 and 144-145.
- AD-7 **open item - already closed in the working tree, not yet committed:** `spring-boot-starter-security-oauth2-resource-server` has been removed and `io.jsonwebtoken:jjwt-api`/`-impl`/`-jackson` 0.13.0 added (`jjwt.version` property, `-impl` and `-jackson` at `runtime` scope). This is the uncommitted modification to `backend/pom.xml` shown in git status. Story 1.1 verifies and commits it rather than performing it.
- AD-2: A module's `@Repository` and `@Entity` classes stay package-private or module-internal; all cross-module reads and writes go through the owning module's `@Service`. Table ownership is fixed: `users`-> `users`; `leave_types`, `leave_requests`, `request_comments`-> `leave`; `leave_balances`, `balance_ledger`-> `balances`; `audit_events`, `access_log`-> `audit`; `notifications`-> `notifications`; `active_sessions`-> `security`.
- AD-2: Dependency edges are fixed and non-reversible - `auth`-> `users`/`audit`/`security`; `users`-> `audit`/`security`; `leave`-> `balances`/`audit`/`notifications`; `approvals`-> `leave`/`balances`/`audit`/`notifications`; `audit` and `notifications` are leaves among domain modules.
- AD-3: `common` holds only stateless utilities (working-day calculator), cross-cutting config, exception types and the single error advice. No `@Entity`, `@Repository` or business-rule `@Service` there.
- AD-4: The module owning the mutated table is the only one that writes its audit/ledger row, inside its own `@Transactional` method, before returning. A calling module passes the acting user's id as an explicit parameter and never re-derives it from `SecurityContext` inside the owning module, and never writes a duplicate audit row for the same state change. `approvals` calls `leave.applyDecision(requestId, decision, comment, approverId)`; `approvals` never writes `leave_requests` itself.
- AD-5: `BalancesService` is the only writer of `leave_balances`/`balance_ledger`, plus a `CHECK (total_days - reserved_days >= 0)` constraint as defense-in-depth.
- AD-6: Immutability is a DB trigger, with the service layer additionally exposing no update/delete method for those tables.
- AD-7: Hand-rolled JWT via `security.JwtService` (jjwt), HMAC secret from an env var, 15-minute token, claims `sub`, `role`, `token_id`; a custom `security.JwtAuthenticationFilter` (`OncePerRequestFilter`, registered with `addFilterBefore`) reads the cookie, validates, performs the AD-8 check, and populates `SecurityContextHolder`. No OAuth2 resource server, no external IdP.
- AD-8: `active_sessions (token_id, user_id, last_seen_at, expires_at)` is owned by `security` and exposed as `security.SessionRegistry`. `auth` calls `createSession(userId)` at login and never touches the table. The filter calls `touchOrReject(tokenId)`, which 401s on a missing row or `last_seen_at` older than 30 minutes, else bumps it. Logout, HR password reset (FR20) and a new lockout each call `revokeAllForUser(userId)`.
- AD-9: Lockout stays on `users.failed_logins`/`users.locked_until`, checked at the `/api/auth/login` boundary before any token is issued.
- AD-10: `security.AccessPolicy` is the only place role and ownership checks are written, with exactly three shapes: `requireOwnerManagerOrHr(resourceOwnerId, resourceManagerId)`, `currentScope()` returning the sealed `UserScope` (`EmployeeOwn | ManagerTeam | HrAll`) consumed by `findByScope(UserScope)` repository methods, and `requireRole(Role...)`. No controller or service writes its own role comparison.
- AD-11: One RFC 9457 `ProblemDetail` envelope with a canonical `common.ErrorCode` enum. **Confirmed by anil_vn on 2026-10-22** - the spine's `[ASSUMPTION]` marker is resolved; this envelope is binding. Where the spine's prose and the swagger disagree on a code's spelling, **the swagger wins**: it is `INSUFFICIENT_BALANCE` (not `BALANCE_INSUFFICIENT`) and `ACCESS_DENIED` (not `RBAC_DENIED`). The complete set of codes, now declared as an `enum` on `ProblemDetail.code` in `openapi.yaml`, which `common.ErrorCode` must mirror exactly, is these ten: `ACCESS_DENIED`, `VALIDATION_FAILED`, `INVALID_TRANSITION`, `INSUFFICIENT_BALANCE`, `NOT_FOUND`, `INVALID_CREDENTIALS`, `LEAVE_TYPE_CHANGE_NOT_ALLOWED`, `COMMENT_REQUIRED`, `ACCOUNT_LOCKED`, `PASSWORD_CHANGE_REQUIRED`. Any genuinely new error class adds a value to that one enum.
- AD-12: `NotificationService.sendNotification(...)` catches every exception internally, logs at ERROR and returns normally; it never throws to its caller. `leave`/`approvals` call it last, after their own audit/balance writes.
- AD-13: `docker-compose.yml` defines `postgres`, `backend`, `frontend` and a reverse proxy terminating HTTPS with a local self-signed certificate; the proxy is the only externally exposed service.

**API contract requirements (from `openapi.yaml`, 24 operations):** the swagger file is the binding request/response contract. Stories must match its paths, DTO shapes, enums (`Role`, `RequestStatus`, `LedgerKind`, `NotificationKind`, `HistoryEntryType`), query parameters and documented error codes. Notable contract details beyond the PRD's summary table:

- `GET /api/requests` accepts `status` and `employeeId` query params, the latter honoured only for a manager's direct report or an HR caller.
- `PUT /api/requests/{id}` accepts `leaveTypeId` for form parity but refuses an actual leave-type change with 400 `LEAVE_TYPE_CHANGE_NOT_ALLOWED`, and refuses an edit that would exceed the balance with 409 `INSUFFICIENT_BALANCE`. Re-reservation compares the new working-day count against `availableDays + thisRequest.workingDays`, so lengthening a 4-day request to 5 days needs one more free day, not five.
- Every operation except login, logout and change-password returns 403 `PASSWORD_CHANGE_REQUIRED` while the caller's `mustChangePassword` is true (shared `PasswordChangeRequired` response component) - this is how FR20's forced change is enforced.
- `GET /api/team/leave` accepts `from`/`to` date params and returns `TeamLeaveEntry` objects with comments and history nested.
- `GET /api/users/managers` returns `ManagerOption` rows (every MANAGER plus every HR_ADMIN) to populate the Manager dropdown - supports FR19, not its own FR.
- `POST /api/users` requires `username`, `role`, `temporaryPassword`; `managerId` is required unless the role is HR_ADMIN, and must reference a MANAGER or HR_ADMIN user.
- `POST /api/users/{id}/reset-password` returns a server-generated `temporaryPassword` - intentionally asymmetric with account creation, where HR types one.
- `GET /api/balances` accepts an optional `userId` filter; `GET /api/balances/{user}/{type}/ledger` returns ledger rows oldest first.
- `GET /api/audit` takes no filters and returns one `AuditRequestReport` per leave request of any status, with that request's full comment thread and full History timeline nested, newest submission first - not a flat action log.
- `access_log` has no read API or UI in the POC; acceptance scenarios 8 and 9 verify it by direct database check.
- Server base URL is `https://localhost/api`, behind the HTTPS reverse proxy.

**Seed-data requirement:** the demo cast is 12 users - 1 HR admin, 1 top-level manager, 2 team managers, 8 employees - plus the 4 leave types. Seeded as separate, incremental Flyway migrations as each epic's entities land (NFR7), never in one story.

**Acceptance-scenario requirement:** the PRD's 15 demo scenarios are the POC's acceptance gate. Every scenario must be traceable to at least one story's acceptance criteria.

### UX Design Requirements

Not applicable. No UX design contract exists in `planning-artifacts`, and the confirmed scope for this breakdown is backend-only. The PRD's UI constraints are recorded as NFR17 (deferred) and carried forward to frontend planning once a `DESIGN.md`/`EXPERIENCE.md` pair exists.

### FR Coverage Map

Every one of the 45 in-scope FRs maps to exactly one **primary** epic — the epic that must fully deliver and verify it. Cross-cutting FRs (FR21-FR26, FR42-FR44) have their mechanism built once in Epic 1 and are then **extended** by every later epic that writes state or exposes a scoped query; those extensions are listed so no later epic can treat them as someone else's job.

| FR | Primary epic | Delivered as |
| --- | --- | --- |
| FR1 | Epic 2 | Submit with leave type from the 4 seeded `leave_types`, whole days |
| FR2 | Epic 2 | `startDate`/`endDate` validation plus the Mon-Fri working-day calculator in `common` |
| FR3 | Epic 2 | Optional `notes` on create and update |
| FR4 | Epic 2 | `status` on `LeaveRequest`; `GET /api/requests?status=` filter |
| FR5 | Epic 2 | `GET /api/requests` scoped to the caller's own requests |
| FR6 | Epic 2 | `GET /api/balances/me` returning total, reserved, available |
| FR7 | Epic 2 | `PUT /api/requests/{id}`, PENDING only, re-reservation + audit |
| FR8 | Epic 2 | `POST /api/requests/{id}/cancel`, release, never deleted |
| FR9 | Epic 2 | `employeeId`/`submittedAt` on the request plus the audit event |
| FR10 | Epic 3 | `GET /api/approvals/pending` scoped by `manager_id` |
| FR11 | Epic 3 | `ApprovalDetail` carrying employee, dates, type, balance, recent history |
| FR12 | Epic 3 | `POST /api/approvals/{id}/approve`, optional comment, reserved -> deducted |
| FR13 | Epic 3 | `POST /api/approvals/{id}/reject`, non-blank comment required, release |
| FR14 | Epic 3 | `GET /api/team/leave?from=&to=` with comments and history nested |
| FR15 | Epic 3 | `GET`/`POST /api/requests/{id}/comments`, no push, oldest first |
| FR16 | Epic 3 | `approverId`/`decidedAt`/`decisionComment` plus the audit event |
| FR17 | Epic 3 | Approver-only decision check, HR self-approval marked in audit |
| FR18 | Epic 1 | `POST /api/users` with an HR-typed temporary password, hashed on save |
| FR19 | Epic 1 | Role + manager at creation; `GET /api/users/managers` dropdown source |
| FR20 | Epic 1 | `POST /api/users/{id}/reset-password` generating a temporary password |
| FR21 | Epic 1 | `security.AccessPolicy` with the three AD-10 shapes; extended by Epics 2-4 |
| FR22 | Epic 1 | `UserScope.EmployeeOwn` + `findByScope`; extended by Epics 2-4 |
| FR23 | Epic 1 | `UserScope.ManagerTeam`; extended by Epics 3-4 |
| FR24 | Epic 1 | `UserScope.HrAll`; extended by Epics 2-4 |
| FR25 | Epic 1 | 403 `ACCESS_DENIED` `ProblemDetail` + `access_log` denial row; extended by Epics 2-4 |
| FR26 | Epic 1 | Request filter writing `access_log`; no read API; extended by Epics 2-4 |
| FR27 | Epic 4 | `GET /api/balances?userId=` with no scope filter for HR |
| FR28 | Epic 4 | `PUT /api/balances/{user}/{type}`, reason required |
| FR29 | Epic 4 | `POST /api/balances/{user}/{type}/adjust`, +/- days, reason required |
| FR30 | Epic 4 | `balance_ledger` rows + `GET /api/balances/{user}/{type}/ledger`, oldest first |
| FR31 | Epic 2 | Submission/edit refused with 409 `INSUFFICIENT_BALANCE`; the HR-adjustment-below-zero half is re-verified in Epic 4 |
| FR42 | Epic 1 | `audit_events` table + `AuditService` write path; extended by Epics 2-4 |
| FR43 | Epic 1 | `actor_id`/`created_at` NOT NULL columns; extended by Epics 2-4 |
| FR44 | Epic 1 | Flyway `BEFORE UPDATE OR DELETE` trigger; verified again in Epic 4 |
| FR45 | Epic 2 | `GET /api/requests/{id}/history` timeline; extended by Epic 3's decision and comment entries |
| FR50 | Epic 3 | `sendNotification()` on approve - DB row + log line |
| FR51 | Epic 3 | `sendNotification()` on reject, carrying the reason |
| FR52 | Epic 2 | `sendNotification()` on submit, addressed to the approver |
| FR53 | Epic 2 | `GET /api/notifications` scoped to the caller |
| FR54 | Epic 2 | Email template rendering type, dates, days, status, link; reused by Epic 3 |
| FR55 | Epic 1 | HTTPS reverse proxy with a self-signed certificate in `docker-compose.yml` |
| FR56 | Epic 1 | `POST /api/auth/login`, BCrypt, JWT in an HttpOnly cookie |
| FR57 | Epic 1 | `failed_logins`/`locked_until`, 5 attempts, 15 minutes |
| FR58 | Epic 1 | `active_sessions` idle check at 30 minutes in the JWT filter |
| FR59 | Epic 1 | Same `access_log` mechanism as FR26 |

**Acceptance-scenario coverage** (the PRD's 15 demo scenarios, the POC's acceptance gate):

| Scenario | Epic |
| --- | --- |
| 11 (HR creates user, assigns manager, resets password), 13 (audit row immutable), 14 (lockout), 15 (idle timeout) | Epic 1 |
| 1 (submit Thu-Tue = 4 days), 2 (edit then cancel), 9 (employee opens another's request -> 403 + logged), 10 (exceeds available days) | Epic 2 |
| 3 (approve with comment), 4 (reject blocked without comment), 5 (discuss, dates change, approve), 6 (team manager submits), 7 (top-level manager submits), 8 (manager acts on another team -> 403 + logged) | Epic 3 |
| 12 (HR sets and adjusts a balance; below zero refused) | Epic 4 |

## Epic List

Four epics. Each is standalone: it delivers complete, demonstrable functionality for its domain and does not require any later epic to function. Later epics build on earlier ones, never the reverse.

### Epic 1: Secure Access and Account Administration

HR can onboard the organisation and every user can get in safely. After this epic an HR admin creates accounts with a role and a manager, hands out temporary passwords and resets forgotten ones; users log in over HTTPS, are forced to replace a temporary password, get locked out after five wrong attempts and dropped after thirty idle minutes; and every authorization decision - allowed or denied - is recorded in an insert-only log that nobody can edit. This epic also installs the three mechanisms every later epic depends on: the Flyway schema, the single `AccessPolicy`, and the audit write path with its immutability trigger.

**FRs covered:** FR18, FR19, FR20, FR21, FR22, FR23, FR24, FR25, FR26, FR42, FR43, FR44, FR55, FR56, FR57, FR58, FR59

**Implementation notes:** Extends the existing scaffold, not a new project. Closes the two blocking `pom.xml` open items first (remove Spring Modulith per AD-1, remove OAuth2 resource server and add jjwt 0.13.0 per AD-7). Builds `common`, `security`, `audit`, `auth` and `users`. Seed migration here covers the 12-user demo cast only; `leave_types` and `leave_balances` seed with their tables in Epic 2 (NFR7). Demo scenarios 11, 13, 14, 15.

### Epic 2: Employee Leave Requests and Balances

An employee can run their own leave end to end without chasing anyone. After this epic they submit a request against a real balance, see it counted in working days, watch its status, edit or cancel it while it is still pending, read a full History timeline of what happened to it, and see their own notification log - while their manager gets a notification row the moment it lands. Submissions that exceed available days are refused, and reserved days move correctly on every edit and cancellation.

**FRs covered:** FR1, FR2, FR3, FR4, FR5, FR6, FR7, FR8, FR9, FR31, FR45, FR52, FR53, FR54

**Implementation notes:** Builds `leave`, `balances` (reservation side only) and `notifications`. The working-day calculator goes in `common` per AD-3. Reservation, release and the matching ledger rows are written by `BalanceService` alone (AD-5), inside the triggering transaction (AD-4). Seed migration adds `leave_balances` rows for the demo cast. Demo scenarios 1, 2, 9, 10.

### Epic 3: Manager Approvals, Team Visibility and Discussion

A manager can clear their queue in a few clicks and the approval chain works at every level. After this epic a manager sees their direct reports' pending requests with full context, approves with an optional comment or rejects with a required one, discusses a request with the employee in a comment thread, and reviews their team's approved leave by date. Managers submit their own leave to their own manager, top-level managers to HR, and HR approves its own requests with the self-approval recorded. Any attempt to decide another team's request is refused and logged.

**FRs covered:** FR10, FR11, FR12, FR13, FR14, FR15, FR16, FR17, FR50, FR51

**Implementation notes:** Builds `approvals` and extends `leave` with comments and `applyDecision`. `approvals` never writes `leave_requests` itself - it calls `leave.applyDecision(requestId, decision, comment, approverId)` and passes the actor id explicitly (AD-4). Decision notifications reuse Epic 2's template and `sendNotification()`. Demo scenarios 3, 4, 5, 6, 7, 8.

### Epic 4: HR Entitlement Control and Audit Oversight

HR can govern entitlements and answer any question about what happened. After this epic HR views every user's balance, sets or adjusts any balance with a mandatory reason, reads the full ledger behind each balance showing amount, reason, actor and time, and pulls a complete audit report per leave request - any status - with that request's whole comment thread and History timeline nested inside, in one call.

**FRs covered:** FR27, FR28, FR29, FR30 (plus re-verification of FR24, FR31, FR42-FR45 from the HR vantage point)

Epic 4 also carries the POC's **closing readiness story** (confirmed by anil_vn, 2026-10-22): README, demo script, and a recorded run-through of all 15 PRD acceptance scenarios on the seeded demo cast. This is the PRD's week-4 acceptance gate, tracked as a story rather than left to an untracked hardening phase.

**Implementation notes:** Extends `balances` with the HR admin write paths and the ledger read. Adjustments below zero are refused by `BalanceService` with the `CHECK` constraint behind it (AD-5). `GET /api/audit` composes `leave_requests`, `request_comments` and `audit_events` - see the open design question below, which must be settled before its story is implemented. Demo scenario 12.

### Resolved design question

**Which module owns `GET /api/audit`?** The swagger tags it `Audit` and the spine's capability map puts "History tab data" in `audit`. But the endpoint's report joins `leave_requests` and `request_comments`, both owned by `leave` (AD-2 table ownership), while AD-2's graph declares `audit` a leaf that "never calls another domain module" - so `audit` can neither read those tables nor call `LeaveRequestService`.

**Decision (anil_vn, 2026-10-22): host the composition in `leave`.** The controller and assembling service live in the `leave` module, which already has a permitted `leave -> audit` edge for fetching `audit_events` through `AuditService`. The endpoint stays at the contracted path `/api/audit` with the swagger's `Audit` tag and `AuditRequestReport` shape unchanged - only the owning package differs from what the tag implies. No new AD, no new dependency edge, `audit` keeps its leaf status. Binding on Epic 4's audit story.

### Deliberate deviations from the PRD's implementation plan

1. **FR55 (HTTPS) moves from week 4 to Epic 1.** AD-13 makes HTTPS a precondition, not hardening: the `Secure` cookie flag AD-7 requires is dropped by browsers over plain HTTP, so the auth story cannot be honestly verified until the proxy exists. Deferring it to the end would mean Epic 1's own acceptance criteria are untestable as written.
2. **No separate testing or hardening epic.** Testcontainers and API tests for RBAC, approval rules and audit writes (NFR9, NFR10) are acceptance criteria inside each story, not a trailing epic - a test epic at the end would leave three epics unverified until week 4. The PRD's remaining week-4 items (demo script, README, full scenario run-through) are tracked as the closing story on Epic 4.

---

## Epic 1: Secure Access and Account Administration

HR can onboard the organisation and every user can get in safely. An HR admin creates accounts with a role and a manager, hands out temporary passwords and resets forgotten ones; users log in over HTTPS, are forced to replace a temporary password, get locked out after five wrong attempts and dropped after thirty idle minutes; and every authorization decision is recorded in an insert-only log nobody can edit. This epic also installs the three mechanisms every later epic depends on: the Flyway-managed schema, the single `AccessPolicy`, and the audit write path with its immutability trigger.

**FRs covered:** FR18, FR19, FR20, FR21, FR22, FR23, FR24, FR25, FR26, FR42, FR43, FR44, FR55, FR56, FR57, FR58, FR59

### Story 1.1: Corrected Dependencies and an HTTPS-Fronted Stack

As a backend developer,
I want the committed scaffold's dependencies corrected and the whole stack reachable over HTTPS with one command,
So that every later story rests on the architecture's actual decisions and can rely on Secure cookies and a single error shape.

**Acceptance Criteria:**

**Given** `backend/pom.xml` as committed, which still declares `spring-modulith-starter-core`, `spring-modulith-starter-test` and the `spring-modulith-bom` import
**When** this story is complete
**Then** none of those three remain in `pom.xml`
**And** the project compiles and the Spring context starts without them, closing AD-1's named open item.

**Given** an uncommitted working-tree change to `pom.xml` has already removed `spring-boot-starter-security-oauth2-resource-server` and added `io.jsonwebtoken:jjwt-api`, `jjwt-impl` and `jjwt-jackson` 0.13.0
**When** this story is complete
**Then** that change is verified and committed rather than redone
**And** `spring-boot-starter-security` is still present, `-impl` and `-jackson` remain at `runtime` scope, and no OAuth2 resource-server bean exists in the application context, confirming AD-7's open item closed.

**Given** `docker-compose.yml` as committed, which defines only `postgres` and `mailpit`, pins `postgres:16`, publishes port 5432 to the host, and has no backend, frontend or proxy service
**When** this story is complete
**Then** `postgres` is pinned to **PostgreSQL 18**, matching the PRD and the spine's Stack table rather than contradicting both
**And** `backend`, `frontend` and a reverse-proxy service are added, so the compose file matches AD-13's declared envelope
**And** the `mailpit` service is removed, because the PRD and the spine both defer Mailpit until real SMTP dispatch is integrated and AD-13's service list omits it - `sendNotification()` writes a DB row and prints a log line, so nothing needs a mail catcher yet.

**Given** the corrected `docker-compose.yml`
**When** `docker compose up` is run from a clean checkout
**Then** `postgres`, `backend` and the reverse proxy all reach a healthy state
**And** the proxy is the only service publishing a port outside the compose network - `postgres` no longer exposes 5432 to the host
**And** `https://localhost/api/...` is reachable through the proxy using a local self-signed certificate, satisfying FR55 and AD-13.

**Given** the backend container
**When** it starts
**Then** `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and the JWT signing secret are all read from environment variables declared in `docker-compose.yml`
**And** those values use in-network service hostnames (`postgres`, never `localhost`)
**And** no secret value appears in any committed file or baked image layer (NFR8, AD-13)
**And** `application.yml`'s `localhost` defaults remain only for running the backend outside compose.

**Given** any request that fails for any reason
**When** the response is produced
**Then** it is `application/problem+json` conforming to the `ProblemDetail` schema, emitted by exactly one `@RestControllerAdvice` living in `common`
**And** its `code` is a value of the single `common.ErrorCode` enum
**And** that enum mirrors the `ProblemDetail.code` enum in `openapi.yaml` exactly - `ACCESS_DENIED`, `VALIDATION_FAILED`, `INVALID_TRANSITION`, `INSUFFICIENT_BALANCE`, `NOT_FOUND`, `INVALID_CREDENTIALS`, `LEAVE_TYPE_CHANGE_NOT_ALLOWED`, `COMMENT_REQUIRED`, `ACCOUNT_LOCKED`, `PASSWORD_CHANGE_REQUIRED` - with no extra values and none missing (NFR13, AD-11).

**Given** the source tree under `com.company.leave`
**When** this story is complete
**Then** the packages `auth`, `users`, `leave`, `approvals`, `balances`, `audit`, `notifications`, `security` and `common` all exist as laid out in the Structural Seed
**And** `common` holds no `@Entity`, no `@Repository` and no business-rule `@Service`, satisfying AD-3.

**Given** an empty database
**When** the backend starts
**Then** Flyway runs and creates its `flyway_schema_history` table
**And** this story creates no domain tables, since each table arrives with the story that needs it (NFR7).

**Given** `CompanyLeaveApplication.main` already calls `TimeZone.setDefault(TimeZone.getTimeZone("UTC"))` before `SpringApplication.run`
**When** this story is complete
**Then** that line is confirmed still present and is not duplicated into configuration elsewhere
**And** the JVM default timezone is UTC at runtime (NFR12).

### Story 1.2: An Immutable Audit Trail and Access Log

As a compliance owner,
I want every action and every access decision written to records nobody can alter or delete,
So that the organisation's leave history can be trusted even against someone with database access.

**Acceptance Criteria:**

**Given** the Flyway migration added by this story
**When** it is applied
**Then** `audit_events (id, entity, entity_id, action, actor_id, details_json, created_at)` and `access_log (id, actor_id, resource, resource_id, allowed, created_at)` both exist
**And** each `id` is `BIGINT GENERATED ALWAYS AS IDENTITY`
**And** `created_at` is `timestamptz NOT NULL` on both and `actor_id` is `NOT NULL` on `audit_events`, satisfying FR43 and NFR12
**And** `access_log.actor_id` is nullable, so an unauthenticated attempt can still be recorded.

**Given** an existing row in `audit_events` or `access_log`
**When** an `UPDATE` or a `DELETE` is attempted against it from any client, including a direct `psql` session
**Then** a `BEFORE UPDATE OR DELETE` trigger raises an exception and the statement fails, satisfying FR44 and demo scenario 13
**And** the trigger is created by a Flyway migration, not applied by hand (AD-6).

**Given** the `audit` module's public surface
**When** it is inspected
**Then** `AuditService` exposes insert-only operations and no update or delete method exists on either the service or its repositories (AD-6)
**And** `audit`'s `@Entity` and `@Repository` classes are not referenced from any other package, satisfying AD-2.

**Given** a caller in another module recording an event
**When** it calls `AuditService`
**Then** it passes the acting user's id as an explicit parameter
**And** `AuditService` never reads `SecurityContext` to derive the actor, satisfying AD-4.

**Given** the `audit` module's imports
**When** they are reviewed
**Then** it depends only on `common` and `security`, never on another domain module, keeping its AD-2 leaf status.

**Given** a Testcontainers PostgreSQL instance
**When** the integration test for this story runs
**Then** it proves an insert succeeds and both an update and a delete fail, against each of the two tables (NFR10).

### Story 1.3: Password Login with an Idle-Expiring Session

As a user,
I want to log in with my username and password and stay logged in only while I am active,
So that my account is not left open on an idle machine.

**Acceptance Criteria:**

**Given** the Flyway migrations added by this story
**When** they are applied
**Then** `users (id, username, password_hash, role, manager_id, must_change_password, failed_logins, locked_until, active, created_at)` exists with `username` UNIQUE, `manager_id` a self-referencing foreign key, and `role` constrained to `EMPLOYEE`, `MANAGER` or `HR_ADMIN`
**And** `active_sessions (token_id, user_id, last_seen_at, expires_at)` exists
**And** a seed migration kept separate from the schema migration inserts the 12-user demo cast - 1 HR_ADMIN with `manager_id` NULL, 1 top-level manager reporting to that HR user, 2 team managers reporting to the top-level manager, 8 employees split across the 2 team managers - with every `password_hash` BCrypt-hashed (NFR4, NFR7).

**Given** a seeded user and their correct password
**When** `POST /api/auth/login` is called
**Then** the response is 200 with a `CurrentUser` body carrying `id`, `username`, `role` and `mustChangePassword`
**And** a `Set-Cookie: access_token=...; HttpOnly; Secure; SameSite=Strict` header is returned
**And** the cookie value is a jjwt-signed JWT carrying `sub`, `role` and a `token_id` claim, expiring in 15 minutes, signed with the HMAC secret from the environment (FR56, AD-7, NFR5).

**Given** a seeded user and a wrong password
**When** `POST /api/auth/login` is called
**Then** the response is 401 `application/problem+json` with `code: INVALID_CREDENTIALS`
**And** no `access_token` cookie is set.

**Given** a successful login
**When** the session is created
**Then** `auth` obtained the `token_id` by calling `security.SessionRegistry.createSession(userId)`
**And** `auth` never reads or writes `active_sessions` itself, satisfying AD-8
**And** `active_sessions` is owned exclusively by the `security` module.

**Given** a request carrying a valid `access_token` cookie
**When** it reaches `security.JwtAuthenticationFilter`
**Then** that filter is a plain `OncePerRequestFilter` registered via `addFilterBefore` in the `SecurityFilterChain`
**And** it validates signature and expiry through `security.JwtService.parseAndValidate(token)`, calls `SessionRegistry.touchOrReject(tokenId)`, then populates `SecurityContextHolder` with an authority built from the `role` claim
**And** no module parses the token by any other path (AD-7).

**Given** a logged-in user whose last request was more than 30 minutes ago
**When** they make their next request
**Then** `touchOrReject` rejects it and the response is 401
**And** a user who acts within 30 minutes has `last_seen_at` bumped and is not interrupted, satisfying FR58 and demo scenario 15.

**Given** a logged-in user
**When** `POST /api/auth/logout` is called
**Then** the response is 204, that user's `active_sessions` rows are deleted and the cookie is cleared
**And** a later request presenting the old token is refused with 401 even though the JWT itself has not expired.

**Given** a successful login or a logout
**When** it completes
**Then** an `audit_events` row is written carrying the acting user's id and the event timestamp (FR42, FR43).

### Story 1.4: Account Lockout After Five Failed Attempts

As a security owner,
I want an account locked for fifteen minutes after five consecutive wrong passwords,
So that guessing a password is not practical.

**Acceptance Criteria:**

**Given** a seeded user with `failed_logins` at 0
**When** `POST /api/auth/login` is called with a wrong password five consecutive times
**Then** `users.failed_logins` reaches 5 and `users.locked_until` is set to 15 minutes after the fifth failure
**And** the fifth response, and every attempt while locked, return 423 `application/problem+json` with `code: ACCOUNT_LOCKED`, satisfying FR57 and demo scenario 14.

**Given** a locked account still inside its 15-minute window
**When** `POST /api/auth/login` is called with the correct password
**Then** the response is still 423 `ACCOUNT_LOCKED`
**And** no JWT is issued and no `active_sessions` row is created, because lockout is evaluated at the `/api/auth/login` boundary before any token exists (AD-9).

**Given** an account with 1 to 4 recorded failures
**When** a login with the correct password succeeds
**Then** `failed_logins` resets to 0 and `locked_until` is cleared.

**Given** a locked account whose `locked_until` has passed
**When** a login with the correct password is attempted
**Then** it succeeds and `failed_logins` resets to 0.

**Given** a user with one or more live sessions
**When** a new lockout is triggered for that user
**Then** `SessionRegistry.revokeAllForUser(userId)` is called and their `active_sessions` rows are deleted
**And** an already-issued token for that user is refused on its next use, so a lockout cannot be ridden out on an existing session (AD-8).

**Given** the lockout implementation
**When** it is reviewed
**Then** the attempt counter and lock window live only on `users.failed_logins` and `users.locked_until`
**And** neither is carried in a JWT claim nor evaluated inside the security filter, keeping lockout independent of the token model (AD-9).

### Story 1.5: One Permission Layer for Every Endpoint

As a security owner,
I want every role and ownership check written in exactly one component and every refusal recorded,
So that no module invents its own rule and no denied attempt goes unlogged.

**Acceptance Criteria:**

**Given** the `security` module
**When** this story is complete
**Then** `security.AccessPolicy` exists exposing exactly the three AD-10 shapes - `void requireOwnerManagerOrHr(long resourceOwnerId, long resourceManagerId)`, `UserScope currentScope()` and `void requireRole(Role... allowed)`
**And** `UserScope` is a sealed type with exactly the variants `EmployeeOwn(long userId)`, `ManagerTeam(long managerId)` and `HrAll`.

**Given** `requireOwnerManagerOrHr` is called
**When** the current user is the resource owner, or is the owner's direct manager, or holds `HR_ADMIN`
**Then** it returns normally
**And** in every other case it throws `AccessDeniedException`, surfacing as 403 with `code: ACCESS_DENIED`, satisfying FR22, FR23, FR24 and FR25.

**Given** a repository method that lists a collection
**When** it is written
**Then** it takes a `UserScope` parameter and applies the matching `WHERE` clause itself, in the `findByScope(UserScope)` shape
**And** an `EmployeeOwn` scope restricts rows to that user's own, a `ManagerTeam` scope to that manager's direct reports, and `HrAll` applies no filter at all (FR22, FR23, FR24).

**Given** the whole backend source tree
**When** it is searched for role comparisons
**Then** no controller and no service contains its own check such as `if (user.getRole() == ...)`, nor a `hasRole(...)` expression outside the filter-chain configuration
**And** every role or ownership decision routes through `AccessPolicy`, satisfying FR21 and AD-10.

**Given** any request that reaches the API
**When** it is handled
**Then** the request filter writes exactly one `access_log` row recording the actor, the resource, the resource id and whether access was `allowed`, satisfying FR26 and FR59
**And** a refused request is recorded with `allowed = false` alongside its 403 response (FR25)
**And** no read API and no UI is built for `access_log` in this POC, so rows are verified by direct database query.

**Given** the three roles and the three `AccessPolicy` shapes
**When** the unit and API tests for this story run
**Then** each shape is tested against `EMPLOYEE`, `MANAGER` and `HR_ADMIN`, for both the allowed and the denied outcome (NFR9)
**And** `POST /api/auth/change-password` is gated as authenticated-only through `AccessPolicy`, demonstrating the layer on a live endpoint.

### Story 1.6: HR Creates Accounts with a Role and a Manager

As an HR admin,
I want to create an account choosing its role, its manager and a temporary password,
So that a new joiner can log in on day one and their requests route to the right approver.

**Acceptance Criteria:**

**Given** an authenticated HR_ADMIN caller
**When** `POST /api/users` is called with `username`, `role`, `managerId` and `temporaryPassword`
**Then** the account is created and returned as a `User`
**And** `temporaryPassword` is BCrypt-hashed into `password_hash` on save, never persisted or written to a log in plaintext
**And** `must_change_password` is set true so the user must replace it at first login, satisfying FR18.

**Given** a create-account request whose `role` is `EMPLOYEE` or `MANAGER`
**When** `managerId` is absent
**Then** the response is 400 `VALIDATION_FAILED`
**And** when `role` is `HR_ADMIN`, `managerId` may be omitted, matching the contract's rule that a top-level user needs no manager.

**Given** a create-account request naming a `managerId`
**When** that user's role is neither `MANAGER` nor `HR_ADMIN`
**Then** the response is 400 `VALIDATION_FAILED`, so a manager link can only point at a real approver and a top-level manager can only report to HR, satisfying FR19.

**Given** a username already in use
**When** `POST /api/users` is called with it
**Then** the response is 400 `VALIDATION_FAILED` and no second account is created.

**Given** an authenticated HR_ADMIN caller
**When** `GET /api/users` is called
**Then** every account is returned with no scope filter applied, satisfying FR24
**And** when `GET /api/users/managers` is called, every `MANAGER` and every `HR_ADMIN` user is returned as `ManagerOption` rows carrying `id`, `username` and `role`, giving the create-account form real options instead of free text (FR19).

**Given** an authenticated caller whose role is `EMPLOYEE` or `MANAGER`
**When** any of `POST /api/users`, `GET /api/users` or `GET /api/users/managers` is called
**Then** the response is 403 with `code: ACCESS_DENIED`
**And** an `access_log` row is written with `allowed = false`, satisfying FR21, FR25 and FR26.

**Given** an account is created
**When** the transaction commits
**Then** an `audit_events` row records the action, the acting HR user's id and the timestamp, written by the `users` module inside its own transaction and handed the actor id explicitly (FR42, FR43, AD-4).

### Story 1.7: Forced Password Change and HR Password Reset

As a user holding a temporary password,
I want to be made to replace it before I can do anything else, and as an HR admin I want to reset a forgotten password,
So that temporary credentials never linger and a locked-out colleague can get back to work.

**Acceptance Criteria:**

**Given** an authenticated user
**When** `POST /api/auth/change-password` is called with a correct `currentPassword` and a `newPassword` of at least 8 characters containing both letters and numbers
**Then** the response is 204, `password_hash` is replaced with the BCrypt hash of the new password and `must_change_password` is set false (FR56, NFR4).

**Given** an authenticated user
**When** `POST /api/auth/change-password` is called with a `newPassword` shorter than 8 characters, or lacking letters, or lacking numbers
**Then** the response is 400 `VALIDATION_FAILED` and the stored hash is unchanged.

**Given** an authenticated user
**When** `POST /api/auth/change-password` is called with an incorrect `currentPassword`
**Then** the response is 401 `INVALID_CREDENTIALS` and the stored hash is unchanged.

**Given** a user whose `must_change_password` is true
**When** they call any `/api` endpoint other than `POST /api/auth/change-password` or `POST /api/auth/logout`
**Then** the response is 403 with `code: PASSWORD_CHANGE_REQUIRED`, matching the shared `PasswordChangeRequired` response component in `openapi.yaml`
**And** `POST /api/auth/change-password` and `POST /api/auth/logout` remain reachable, so the user can comply or leave
**And** that one code is the only addition to `common.ErrorCode` beyond the nine the contract already used, as AD-11 requires for a genuinely new error class.

**Given** an authenticated HR_ADMIN caller and an existing target user
**When** `POST /api/users/{userId}/reset-password` is called
**Then** the response is 200 carrying a server-generated `temporaryPassword`
**And** that password is BCrypt-hashed into the target's `password_hash` and `must_change_password` is set true, satisfying FR20
**And** the generated value is never written to a log.

**Given** the target user has live sessions
**When** HR resets their password
**Then** `SessionRegistry.revokeAllForUser(userId)` is called, so an already-issued token cannot outlive the reset (AD-8).

**Given** an HR_ADMIN caller and a `userId` that does not exist
**When** `POST /api/users/{userId}/reset-password` is called
**Then** the response is 404 with `code: NOT_FOUND`.

**Given** a non-HR caller
**When** `POST /api/users/{userId}/reset-password` is called
**Then** the response is 403 `ACCESS_DENIED` and an `access_log` row is written with `allowed = false`.

**Given** a password reset and a later password change
**When** each completes
**Then** each writes its own `audit_events` row with actor and timestamp
**And** together with Story 1.6 this closes demo scenario 11 - HR creates a user, assigns a manager and resets a password, and the user logs in and must change the temporary password.

---

## Epic 2: Employee Leave Requests and Balances

An employee can run their own leave end to end without chasing anyone. They submit a request against a real balance, see it counted in working days, watch its status, edit or cancel it while it is still pending, read a full History timeline of what happened to it, and see their own notification log - while their manager gets a notification row the moment it lands. Submissions that exceed available days are refused, and reserved days move correctly on every edit and cancellation.

**FRs covered:** FR1, FR2, FR3, FR4, FR5, FR6, FR7, FR8, FR9, FR31, FR45, FR52, FR53, FR54

### Story 2.1: Leave Catalogue and My Balances

As an employee,
I want to see how many days of each leave type I have left before I ask for any,
So that I plan around what I actually have rather than guessing.

**Acceptance Criteria:**

**Given** the Flyway migrations added by this story
**When** they are applied
**Then** `leave_types (id, name)` and `leave_balances (user_id, leave_type_id, total_days, reserved_days)` exist, both owned by their AD-2 modules - `leave_types` by `leave`, `leave_balances` by `balances`
**And** `leave_balances` carries a `CHECK (total_days - reserved_days >= 0)` constraint as defence in depth behind the service-layer rule (AD-5)
**And** a seed migration, separate from the schema migration, inserts the four leave types Vacation, Sick, Parental and Sabbatical plus a starting balance row per seeded user per type (NFR7).

**Given** an authenticated user of any role
**When** `GET /api/balances/me` is called
**Then** the response is 200 with a `Balance` array covering each leave type, each entry carrying `userId`, `leaveTypeId`, `totalDays`, `reservedDays` and `availableDays`
**And** `availableDays` equals `totalDays - reservedDays` and is never negative, satisfying FR6.

**Given** a user with no balance row for a leave type
**When** `GET /api/balances/me` is called
**Then** that type is still represented with zero days rather than omitted, so the caller cannot mistake a missing row for an unlimited entitlement.

**Given** the `balances` module
**When** it is reviewed
**Then** `BalanceService` is the only writer of `leave_balances`, and its `@Entity`/`@Repository` classes are referenced nowhere outside the module (AD-2, AD-5)
**And** `leave_types` is read through `leave.LeaveTypeService` by any other module that needs the type name.

### Story 2.2: Submit a Leave Request Against a Real Balance

As an employee,
I want to submit a leave request for a date range and have it counted in working days and held against my balance,
So that my manager sees a correct request and I cannot accidentally book days I do not have.

**Acceptance Criteria:**

**Given** the Flyway migrations added by this story
**When** they are applied
**Then** `leave_requests (id, employee_id, approver_id, leave_type_id, start_date, end_date, working_days, notes, status, submitted_at, decided_at, decision_comment)` and `balance_ledger (id, user_id, leave_type_id, change_days, kind, reason, actor_id, request_id, created_at)` exist
**And** `start_date` and `end_date` are `DATE`, not timestamps, and `working_days` is a whole-day integer (NFR12).

**Given** the working-day calculator in `common`
**When** it is given Thursday 2026-10-29 to Tuesday 2026-11-03
**Then** it returns 4, counting Monday to Friday inclusive and ignoring public holidays, matching the PRD's worked example and demo scenario 1
**And** a range falling entirely on a weekend returns 0
**And** the calculator is a stateless utility with no entity, repository or business-rule service beside it (AD-3).

**Given** an authenticated employee with sufficient available days
**When** `POST /api/requests` is called with `leaveTypeId`, `startDate`, `endDate` and optional `notes`
**Then** the response is 201 with a `LeaveRequest` whose `status` is `PENDING`, `workingDays` is the calculated count, `employeeId` is the caller and `submittedAt` is set, satisfying FR1, FR2, FR3 and FR9
**And** `approverId` is set to the caller's own `manager_id`, so the request routes to the right approver without the caller choosing one.

**Given** a submission request
**When** `endDate` is before `startDate`, or the range yields zero working days
**Then** the response is 400 `VALIDATION_FAILED` and no request row is written.

**Given** an employee whose available days for that leave type are fewer than the request's working days
**When** `POST /api/requests` is called
**Then** the response is 409 `INSUFFICIENT_BALANCE` with a detail naming the requested days, the available days, the total and the already-reserved days
**And** no `leave_requests` row and no ledger row are written, satisfying FR31 and demo scenario 10
**And** the check compares against `total_days - reserved_days`, so days held by other pending requests are honoured.

**Given** a successful submission
**When** the transaction commits
**Then** `reserved_days` for that user and leave type has increased by the request's working days
**And** a `balance_ledger` row of kind `RESERVE` is written carrying the change, the actor, the `request_id` and the timestamp
**And** both the balance update and the ledger row happen inside the same transaction as the request insert, written by `BalanceService` alone (AD-4, AD-5).

**Given** a successful submission
**When** the transaction commits
**Then** an `audit_events` row with action `LEAVE_REQUEST_SUBMITTED` records the acting employee and the timestamp (FR42, FR43)
**And** the `leave` module wrote it, passing the actor id explicitly, and `balances` wrote its own ledger row for the table it owns - neither writes the other's (AD-4).

### Story 2.3: Notification on Submission and My Notification History

As a manager,
I want a notification the moment one of my reports asks for leave, and as any user I want to see my own notification history,
So that nothing waits in a queue unnoticed and I can check what I was told.

**Acceptance Criteria:**

**Given** the Flyway migration added by this story
**When** it is applied
**Then** `notifications (id, user_id, request_id, kind, subject, sent_at)` exists, owned by the `notifications` module (AD-2).

**Given** an email template in `templates/email/`
**When** a notification is built
**Then** the subject and body are rendered from that template and contain the leave type, the start and end dates, the working-day count, the status and a link to the request, satisfying FR54
**And** the same template set is reused by Epic 3's approval and rejection notifications rather than duplicated.

**Given** an employee submits a request
**When** the submission completes
**Then** `NotificationService.sendNotification(...)` writes one `notifications` row addressed to the request's `approverId`, with `kind` `SUBMITTED` and `sentAt` set, and prints a log line
**And** no mail server is contacted and no SMTP connection is attempted, satisfying FR52 and completing demo scenario 1.

**Given** `sendNotification(...)` fails for any reason - template rendering or its own database write
**When** it is called from `leave`
**Then** it catches the exception internally, logs it at ERROR and returns normally, never throwing to its caller
**And** the leave request and its balance reservation remain committed, because the notification call happens last, after the audit and balance writes (AD-12).

**Given** an authenticated user
**When** `GET /api/notifications` is called
**Then** the response is 200 with a `Notification` array containing only that caller's own rows, each carrying `id`, `userId`, `requestId`, `kind`, `subject` and `sentAt`, satisfying FR53
**And** another user's notifications are never returned, enforced through `AccessPolicy` rather than a check written in the controller (AD-10).

**Given** the `notifications` module
**When** its imports are reviewed
**Then** it depends only on `common` and `security`, never on another domain module, keeping its AD-2 leaf status
**And** `leave` calls it synchronously and in-process, with no `@Async` and no event publisher (AD-1).

### Story 2.4: See My Requests and Their Status

As an employee,
I want a filterable list of my own requests and the detail of any one of them,
So that I can see where each stands without asking my manager.

**Acceptance Criteria:**

**Given** an authenticated employee
**When** `GET /api/requests` is called
**Then** the response is 200 with a `LeaveRequest` array containing only that employee's own requests, satisfying FR5
**And** the scoping comes from `AccessPolicy.currentScope()` returning `EmployeeOwn` and a `findByScope(UserScope)` repository method, not a filter written in the controller (AD-10).

**Given** an authenticated employee with requests in several states
**When** `GET /api/requests?status=PENDING` is called
**Then** only `PENDING` requests are returned, and the same holds for `APPROVED`, `REJECTED` and `CANCELLED`, satisfying FR4
**And** a cancelled request still appears in the list rather than vanishing, satisfying FR8's "never deleted" rule.

**Given** an authenticated manager
**When** `GET /api/requests?employeeId={id}` is called for one of their direct reports
**Then** that report's requests are returned
**And** when the same call names someone who is not their direct report, the response is 403 `ACCESS_DENIED` with an `access_log` row at `allowed = false`, satisfying FR23 and FR25.

**Given** an authenticated HR_ADMIN
**When** `GET /api/requests?employeeId={id}` is called for any user
**Then** that user's requests are returned with no scope restriction, satisfying FR24.

**Given** an authenticated user
**When** `GET /api/requests/{requestId}` is called for a request they own, or one belonging to their direct report, or any request if they are HR
**Then** the response is 200 with the full `LeaveRequest`, satisfying FR4 and FR11's underlying read.

**Given** an employee
**When** they call `GET /api/requests/{requestId}` for a request belonging to another employee, guessing the id in the URL
**Then** the response is 403 `ACCESS_DENIED`
**And** an `access_log` row is written with `allowed = false`, satisfying FR22, FR25, FR26 and demo scenario 9
**And** the check is `AccessPolicy.requireOwnerManagerOrHr(...)`, called after the service loads the request so the owner and manager ids are known (AD-10).

### Story 2.5: Edit a Pending Request

As an employee,
I want to change the dates or notes on a request while it is still pending,
So that a change of plan does not force me to cancel and start again.

**Acceptance Criteria:**

**Given** an employee's own request with status `PENDING`
**When** `PUT /api/requests/{requestId}` is called with `leaveTypeId`, `startDate`, `endDate` and optional `notes`
**Then** the response is 200 with the updated `LeaveRequest`, its `workingDays` recalculated from the new range, satisfying FR7.

**Given** a request whose status is `APPROVED`, `REJECTED` or `CANCELLED`
**When** `PUT /api/requests/{requestId}` is called
**Then** the response is 409 `INVALID_TRANSITION` and nothing is changed, so editing is possible only while pending.

**Given** a pending request
**When** `PUT /api/requests/{requestId}` is called with a `leaveTypeId` different from the request's current one
**Then** the response is 400 `LEAVE_TYPE_CHANGE_NOT_ALLOWED`
**And** sending the unchanged `leaveTypeId` is accepted, matching the contract's form-parity rule.

**Given** a pending request being edited to a longer range
**When** the new working-day count exceeds the employee's available days
**Then** the edit is refused with 409 `INSUFFICIENT_BALANCE` and the existing reservation is left untouched, satisfying FR31
**And** the response matches the `insufficientBalance` example now documented on this operation in `openapi.yaml`.

**Given** a successful edit that changes the working-day count
**When** the transaction commits
**Then** the old reservation is released and the new one taken, leaving `reserved_days` consistent with the edited request
**And** the matching `balance_ledger` rows are written by `BalanceService` in the same transaction, satisfying AD-4 and AD-5
**And** the comparison for the new reservation excludes this request's own currently-held days, so an edit from 4 days to 5 needs only one more day available, not five.

**Given** a request belonging to another employee
**When** `PUT /api/requests/{requestId}` is called
**Then** the response is 403 `ACCESS_DENIED` with an `access_log` row at `allowed = false`
**And** a manager may not edit their report's request either - editing is the owner's action alone.

**Given** a successful edit
**When** the transaction commits
**Then** an `audit_events` row records the edit, the acting employee and the timestamp, so the change is visible on the History timeline (FR7, FR42, FR43).

### Story 2.6: Cancel a Pending Request

As an employee,
I want to cancel a request I no longer need,
So that my held days go back to my balance and my manager's queue stays clean.

**Acceptance Criteria:**

**Given** an employee's own request with status `PENDING`
**When** `POST /api/requests/{requestId}/cancel` is called
**Then** the response is 200 with the request's `status` now `CANCELLED`, satisfying FR8
**And** the row is updated, never deleted, and the request remains visible in `GET /api/requests`.

**Given** a request whose status is `APPROVED`, `REJECTED` or already `CANCELLED`
**When** `POST /api/requests/{requestId}/cancel` is called
**Then** the response is 409 `INVALID_TRANSITION` and nothing is changed.

**Given** a successful cancellation
**When** the transaction commits
**Then** the request's reserved days are released and `reserved_days` for that user and leave type decreases by exactly the request's working days
**And** a `balance_ledger` row of kind `RELEASE` is written carrying the change, the actor, the `request_id` and the timestamp, in the same transaction (AD-4, AD-5)
**And** those days are immediately available to a new submission.

**Given** a request belonging to another employee
**When** `POST /api/requests/{requestId}/cancel` is called
**Then** the response is 403 `ACCESS_DENIED` and an `access_log` row is written with `allowed = false`.

**Given** a successful cancellation
**When** the transaction commits
**Then** an `audit_events` row records the cancellation, the acting employee and the timestamp (FR42, FR43)
**And** together with Story 2.5 this closes demo scenario 2 - the reservation updates on edit, is released on cancel, both steps appear in History, and the cancelled request stays visible.

### Story 2.7: The History Timeline on a Request

As an employee,
I want a timeline of everything that happened to a request, in order,
So that I can see what changed and when without asking anyone.

**Acceptance Criteria:**

**Given** an authenticated user and a request they are allowed to see
**When** `GET /api/requests/{requestId}/history` is called
**Then** the response is 200 with a `HistoryEntry` array, each entry carrying `type`, `actorId`, `occurredAt` and a free-text `detail`, satisfying FR45
**And** entries are returned in chronological order, oldest first.

**Given** a request that was submitted, edited twice and then cancelled
**When** its history is fetched
**Then** it contains one `SUBMITTED` entry, two `EDITED` entries and one `CANCELLED` entry, in the order the actions happened
**And** each entry's `detail` summarises the change in readable terms, for example the submitted type, range and working-day count.

**Given** the history is assembled
**When** the implementation is reviewed
**Then** entries are derived from the `audit_events` rows Stories 2.2, 2.5 and 2.6 already write, read through `audit.AuditService`
**And** the `leave` module performs the assembly, using its permitted `leave -> audit` edge; `audit` is not made to read `leave_requests` (AD-2).

**Given** the `HistoryEntryType` enum
**When** it is implemented
**Then** it contains exactly `SUBMITTED`, `EDITED`, `CANCELLED`, `APPROVED`, `REJECTED` and `COMMENT`
**And** the `APPROVED`, `REJECTED` and `COMMENT` branches are populated by Epic 3, which adds decisions and comments; this story leaves them reachable but unexercised rather than omitting them from the type.

**Given** a user who may not see the request
**When** `GET /api/requests/{requestId}/history` is called
**Then** the response is 403 `ACCESS_DENIED` with an `access_log` row at `allowed = false`
**And** the owner, the owner's direct manager and HR may all read it, enforced by `AccessPolicy.requireOwnerManagerOrHr(...)` (FR22, FR23, FR24).

---

## Epic 3: Manager Approvals, Team Visibility and Discussion

A manager can clear their queue in a few clicks and the approval chain works at every level. A manager sees their direct reports' pending requests with full context, approves with an optional comment or rejects with a required one, discusses a request with the employee in a comment thread, and reviews their team's approved leave by date. Managers submit their own leave to their own manager, top-level managers to HR, and HR approves its own requests with the self-approval recorded. Any attempt to decide another team's request is refused and logged.

**FRs covered:** FR10, FR11, FR12, FR13, FR14, FR15, FR16, FR17, FR50, FR51

### Story 3.1: The Pending Approvals Dashboard

As a manager,
I want one page listing every pending request from my direct reports with the full picture of each,
So that I can decide without opening four screens per request.

**Acceptance Criteria:**

**Given** an authenticated manager with pending requests from their direct reports
**When** `GET /api/approvals/pending` is called
**Then** the response is 200 with an `ApprovalDetail` array covering only requests whose `approverId` is the caller, satisfying FR10
**And** the scoping comes from `AccessPolicy.currentScope()` returning `ManagerTeam` and a `findByScope(UserScope)` repository method, not a filter written in the controller (AD-10).

**Given** a pending request in that list
**When** its `ApprovalDetail` is inspected
**Then** it carries every `LeaveRequest` field plus `employeeUsername`, `leaveTypeName`, the employee's current `balance` for that leave type, the full `comments` thread oldest first, and the `history` timeline up to this pending decision, satisfying FR11
**And** the approver therefore needs no follow-up call to `GET /api/requests/{id}`, `.../history` or `.../comments`.

**Given** the `balance` nested in an `ApprovalDetail`
**When** it is read
**Then** its `totalDays`, `reservedDays` and `availableDays` are the employee's values as of right now, read through `balances.BalanceService`, never by `approvals` querying `leave_balances` directly (AD-2).

**Given** an authenticated manager with no pending requests
**When** `GET /api/approvals/pending` is called
**Then** the response is 200 with an empty array, not a 404.

**Given** an authenticated employee with no direct reports
**When** `GET /api/approvals/pending` is called
**Then** the response is 403 `ACCESS_DENIED` and an `access_log` row is written with `allowed = false`, satisfying FR21 and FR25.

**Given** an authenticated HR_ADMIN
**When** `GET /api/approvals/pending` is called
**Then** the requests awaiting their decision are returned - those of the top-level managers who report to them, and their own, per the single-level chain.

### Story 3.2: Approve a Request with an Optional Comment

As a manager,
I want to approve a pending request, optionally saying something about it,
So that my report knows they can take the leave and their balance reflects it.

**Acceptance Criteria:**

**Given** a pending request whose `approverId` is the authenticated caller
**When** `POST /api/approvals/{requestId}/approve` is called with an optional `comment`
**Then** the response is 200 with the request's `status` now `APPROVED`, `decidedAt` set, `approverId` recorded and `decisionComment` holding the comment if one was given, satisfying FR12 and FR16
**And** omitting the comment entirely is accepted.

**Given** a successful approval
**When** the transaction commits
**Then** the request's reserved days become deducted days - `reserved_days` decreases by the working-day count and `total_days` decreases by the same amount, leaving `availableDays` unchanged from its pre-approval value
**And** a `balance_ledger` row of kind `DEDUCT` is written with the change, the actor, the `request_id` and the timestamp, in the same transaction (AD-4, AD-5).

**Given** the approval is processed
**When** the implementation is reviewed
**Then** `approvals` calls `leave.applyDecision(requestId, decision, comment, approverId)` and never writes `leave_requests` itself
**And** the acting approver's id is passed as an explicit parameter, not re-derived from `SecurityContext` inside `leave` (AD-4)
**And** `leave` writes exactly one audit row for the request it owns and `balances` exactly one ledger row for the table it owns - neither duplicates the other (AD-4).

**Given** a request whose status is not `PENDING`
**When** `POST /api/approvals/{requestId}/approve` is called
**Then** the response is 409 `INVALID_TRANSITION` and nothing is changed, so a request cannot be approved twice.

**Given** an authenticated user who is not the request's `approverId`
**When** `POST /api/approvals/{requestId}/approve` is called
**Then** the response is 403 `ACCESS_DENIED` and an `access_log` row is written with `allowed = false`, satisfying FR17 and FR25.

**Given** a successful approval
**When** the transaction commits
**Then** an `audit_events` row with action `LEAVE_REQUEST_APPROVED` records the approver and the timestamp (FR42, FR43)
**And** `sendNotification(...)` then writes one `notifications` row addressed to the employee with `kind` `APPROVED`, built from Epic 2's template, and prints a log line without contacting a mail server, satisfying FR50 and FR54
**And** the notification call happens last, after the audit and balance writes, and cannot roll them back if it fails (AD-12)
**And** this closes demo scenario 3.

### Story 3.3: Reject a Request with a Required Comment

As a manager,
I want rejection to force me to give a reason,
So that my report always learns why and their held days go straight back.

**Acceptance Criteria:**

**Given** a pending request whose `approverId` is the authenticated caller
**When** `POST /api/approvals/{requestId}/reject` is called with a non-blank `comment`
**Then** the response is 200 with the request's `status` now `REJECTED`, `decidedAt` set, `approverId` recorded and `decisionComment` holding the reason, satisfying FR13 and FR16.

**Given** a pending request
**When** `POST /api/approvals/{requestId}/reject` is called with `comment` absent, null, empty or whitespace only
**Then** the response is 400 `COMMENT_REQUIRED` and the request stays `PENDING`, satisfying FR13 and demo scenario 4
**And** the request body itself is required, so an empty body is refused the same way.

**Given** a successful rejection
**When** the transaction commits
**Then** the request's reserved days are released - `reserved_days` decreases by the working-day count while `total_days` is untouched, so `availableDays` rises
**And** a `balance_ledger` row of kind `RELEASE` is written with the change, the actor, the `request_id` and the timestamp, in the same transaction (AD-4, AD-5).

**Given** a request whose status is not `PENDING`
**When** `POST /api/approvals/{requestId}/reject` is called
**Then** the response is 409 `INVALID_TRANSITION` and nothing is changed.

**Given** an authenticated user who is not the request's `approverId`
**When** `POST /api/approvals/{requestId}/reject` is called
**Then** the response is 403 `ACCESS_DENIED` and an `access_log` row is written with `allowed = false` (FR17, FR25).

**Given** a successful rejection
**When** the transaction commits
**Then** an `audit_events` row with action `LEAVE_REQUEST_REJECTED` records the approver and the timestamp (FR42, FR43)
**And** `sendNotification(...)` writes one `notifications` row addressed to the employee with `kind` `REJECTED`, whose body carries the rejection reason, and prints a log line without contacting a mail server, satisfying FR51 and FR54
**And** the notification is called last and cannot roll back the decision if it fails (AD-12)
**And** this closes demo scenario 4.

### Story 3.4: The Approval Chain at Every Level

As a manager or an HR admin,
I want my own leave to route to whoever approves me, right up to the top of the chain,
So that nobody is left unable to book leave and nobody can decide outside their team.

**Acceptance Criteria:**

**Given** a team manager who reports to the top-level manager
**When** they submit their own leave request
**Then** its `approverId` is the top-level manager
**And** that top-level manager sees it on `GET /api/approvals/pending` and can approve it, satisfying FR17, FR19 and demo scenario 6.

**Given** the top-level manager, who reports to an HR user
**When** they submit their own leave request
**Then** its `approverId` is that HR user
**And** the HR user sees it on `GET /api/approvals/pending` and can approve it, satisfying FR17, FR19 and demo scenario 7.

**Given** an HR_ADMIN user whose `manager_id` is NULL
**When** they submit their own leave request and then approve it
**Then** the approval succeeds as a self-approval
**And** the `audit_events` row for the decision marks it explicitly as a self-approval, so the audit trail distinguishes it from a normal decision, per the PRD's confirmed decision on HR users' own leave.

**Given** a manager of one team
**When** they call `POST /api/approvals/{requestId}/approve` or `.../reject` for a request belonging to another team's employee
**Then** the response is 403 `ACCESS_DENIED`
**And** an `access_log` row is written with `allowed = false`, verified by direct database query since the POC exposes no access-log API
**And** this closes demo scenario 8, satisfying FR17, FR23, FR25 and FR26.

**Given** a manager of one team
**When** they call `GET /api/requests/{requestId}` or `.../history` for another team's request
**Then** the response is 403 `ACCESS_DENIED` with an `access_log` row at `allowed = false`, so the refusal is not limited to the decision endpoints.

**Given** the approval chain tests
**When** they run
**Then** they cover all four levels on the seeded cast - employee to team manager, team manager to top-level manager, top-level manager to HR, and HR self-approval - as API tests against a Testcontainers PostgreSQL (NFR9, NFR10)
**And** they are the tests the PRD's named RBAC risk calls for.

### Story 3.5: Discussion Comments on a Request

As a manager or an employee,
I want to discuss a request in a thread attached to it,
So that the conversation about leave lives with the leave rather than in a separate inbox.

**Acceptance Criteria:**

**Given** the Flyway migration added by this story
**When** it is applied
**Then** `request_comments (id, request_id, author_id, body, created_at)` exists, owned by the `leave` module (AD-2).

**Given** an authenticated user who may see a request - its owner, the owner's direct manager, or HR
**When** `POST /api/requests/{requestId}/comments` is called with a `body`
**Then** the response is 201 with the created `Comment` carrying `id`, `requestId`, `authorId`, `body` and `createdAt`, satisfying FR15.

**Given** a request with several comments
**When** `GET /api/requests/{requestId}/comments` is called
**Then** the response is 200 with the full thread oldest first.

**Given** a comment is added
**When** the transaction commits
**Then** no email is sent and no in-app notification row is written, so a comment produces no alert of any kind
**And** the new comment is visible to the other party only when they fetch the thread again, matching the PRD's confirmed "refresh to see" decision.

**Given** a comment is added
**When** the transaction commits
**Then** an `audit_events` row is written with entity `REQUEST_COMMENT`, action `REQUEST_COMMENT_ADDED` and a `details_json` containing the `requestId` and the comment body, so the comment text appears in the audit trail with its author and timestamp
**And** the comment therefore surfaces as a `COMMENT` entry on `GET /api/requests/{requestId}/history`, filling the branch Story 2.7 left reachable (FR45).

**Given** a comment may be added on a request in any status
**When** a comment is posted on a `PENDING`, `APPROVED`, `REJECTED` or `CANCELLED` request
**Then** it is accepted, since the contract places no status restriction on the discussion thread.

**Given** a user who may not see the request
**When** either comments endpoint is called
**Then** the response is 403 `ACCESS_DENIED` with an `access_log` row at `allowed = false`, enforced through `AccessPolicy.requireOwnerManagerOrHr(...)` (FR22, FR23, FR24, FR25).

**Given** a manager and an employee discussing a request, the employee then editing the dates, and the manager then approving it
**When** the request's history is fetched
**Then** the comments, the date change and the decision all appear in the order they happened, closing demo scenario 5 and the PRD's Journey 4.

### Story 3.6: Team Leave by Date

As a manager,
I want to see my team's approved leave across a date range, with each entry's discussion and history,
So that I can spot clashes and answer questions about past leave in one call.

**Acceptance Criteria:**

**Given** an authenticated manager
**When** `GET /api/team/leave?from=2026-08-01&to=2026-08-31` is called
**Then** the response is 200 with a `TeamLeaveEntry` array covering only that manager's direct reports, satisfying FR14 and FR23
**And** only requests with status `APPROVED` are included, since FR14 scopes the view to approved leave.

**Given** a `TeamLeaveEntry` in that response
**When** it is inspected
**Then** it carries `requestId`, `employeeId`, `leaveTypeId`, `startDate`, `endDate`, `workingDays` and `status`, plus the request's full `comments` thread oldest first and its full `history` timeline in order
**And** the manager therefore needs no second call per request, making the team view the one-call team overview the PRD describes (FR14, FR15, FR45).

**Given** the `from` and `to` query parameters
**When** they are supplied
**Then** entries are limited to approved leave overlapping that range
**And** when they are omitted, the endpoint returns the team's approved leave without a date restriction.

**Given** an authenticated employee with no direct reports
**When** `GET /api/team/leave` is called
**Then** the response is 403 `ACCESS_DENIED` and an `access_log` row is written with `allowed = false` (FR21, FR25).

**Given** the team view is assembled
**When** the implementation is reviewed
**Then** the `leave` module performs the assembly, reading `audit_events` through its permitted `leave -> audit` edge for the nested history
**And** no module reaches into another's repository or entity to build the response (AD-2).

---

## Epic 4: HR Entitlement Control and Audit Oversight

HR can govern entitlements and answer any question about what happened. HR views every user's balance, sets or adjusts any balance with a mandatory reason, reads the full ledger behind each balance showing amount, reason, actor and time, and pulls a complete audit report per leave request - any status - with that request's whole comment thread and History timeline nested inside, in one call. The epic closes with the POC's readiness gate.

**FRs covered:** FR27, FR28, FR29, FR30, plus re-verification of FR24, FR31 and FR42-FR45 from the HR vantage point

### Story 4.1: HR Views Every Balance

As an HR admin,
I want to see every user's balance for every leave type,
So that I can answer entitlement questions and spot problems before anyone complains.

**Acceptance Criteria:**

**Given** an authenticated HR_ADMIN caller
**When** `GET /api/balances` is called
**Then** the response is 200 with a `Balance` array covering every user and every leave type, with no scope filter applied, satisfying FR27 and FR24
**And** the scoping comes from `AccessPolicy.currentScope()` returning `HrAll`, which applies no `WHERE` clause (AD-10).

**Given** an authenticated HR_ADMIN caller
**When** `GET /api/balances?userId={id}` is called
**Then** only that user's balance rows are returned, across all leave types.

**Given** each returned `Balance`
**When** it is inspected
**Then** it carries `userId`, `leaveTypeId`, `totalDays`, `reservedDays` and `availableDays`
**And** `availableDays` equals `totalDays - reservedDays` and reflects days currently held by pending requests, so HR sees the same reserved figure the employee sees.

**Given** an authenticated caller whose role is `EMPLOYEE` or `MANAGER`
**When** `GET /api/balances` is called
**Then** the response is 403 `ACCESS_DENIED` and an `access_log` row is written with `allowed = false`, satisfying FR21 and FR25
**And** such a caller still reaches their own figures through `GET /api/balances/me` from Story 2.1.

### Story 4.2: HR Sets a Balance with a Required Reason

As an HR admin,
I want to set a user's entitlement for a leave type and be made to say why,
So that every entitlement figure in the system has a recorded justification.

**Acceptance Criteria:**

**Given** an authenticated HR_ADMIN caller
**When** `PUT /api/balances/{userId}/{leaveTypeId}` is called with `totalDays` and a `reason`
**Then** the response is 200 with the updated `Balance`
**And** `total_days` is set to the given value, satisfying FR28.

**Given** a set-balance request
**When** `reason` is absent, empty or whitespace only, or `totalDays` is negative
**Then** the response is 400 `VALIDATION_FAILED` and the balance is unchanged, so no entitlement change is ever recorded without a justification.

**Given** a user with days currently reserved by pending requests
**When** `PUT /api/balances/{userId}/{leaveTypeId}` is called with a `totalDays` lower than those reserved days
**Then** the response is 409 `INSUFFICIENT_BALANCE` and nothing is changed, because the result would make `total_days - reserved_days` negative
**And** the refusal comes from `BalanceService`, with the `CHECK (total_days - reserved_days >= 0)` constraint behind it as defence in depth, satisfying FR31 and AD-5.

**Given** a successful set
**When** the transaction commits
**Then** a `balance_ledger` row of kind `SET` is written carrying `changeDays`, the `reason`, the acting HR user as `actorId`, a null `requestId` and the timestamp, satisfying FR30
**And** the ledger row and the balance update happen in the same transaction, written by `BalanceService` alone (AD-4, AD-5)
**And** an `audit_events` row with action `LEAVE_BALANCE_SET` records the actor and the timestamp (FR42, FR43).

**Given** an authenticated caller whose role is `EMPLOYEE` or `MANAGER`
**When** `PUT /api/balances/{userId}/{leaveTypeId}` is called
**Then** the response is 403 `ACCESS_DENIED` with an `access_log` row at `allowed = false`, so no user can set their own entitlement.

### Story 4.3: HR Adjusts a Balance Up or Down

As an HR admin,
I want to add or remove days from a balance with a reason,
So that I can correct a figure without overwriting the history of how it got there.

**Acceptance Criteria:**

**Given** an authenticated HR_ADMIN caller
**When** `POST /api/balances/{userId}/{leaveTypeId}/adjust` is called with a positive `changeDays` and a `reason`
**Then** the response is 200 with the updated `Balance` and `total_days` increased by that amount, satisfying FR29.

**Given** an authenticated HR_ADMIN caller
**When** the same endpoint is called with a negative `changeDays` and a `reason`
**Then** `total_days` decreases by that amount, provided the result keeps `total_days - reserved_days` at zero or above.

**Given** an adjustment that would take the balance below zero, or below the days already reserved by pending requests
**When** it is submitted
**Then** the response is 409 `INSUFFICIENT_BALANCE` and nothing is changed, satisfying FR31 and the second half of demo scenario 12
**And** the refusal comes from `BalanceService`, not only from the database constraint (AD-5).

**Given** an adjustment request
**When** `reason` is absent, empty or whitespace only, or `changeDays` is zero
**Then** the response is 400 `VALIDATION_FAILED` and the balance is unchanged.

**Given** a successful adjustment
**When** the transaction commits
**Then** a `balance_ledger` row of kind `ADJUST` is written carrying the signed `changeDays`, the `reason`, the acting HR user as `actorId`, a null `requestId` and the timestamp, satisfying FR30
**And** an `audit_events` row with action `LEAVE_BALANCE_ADJUSTED` records the actor and the timestamp (FR42, FR43).

**Given** an authenticated caller whose role is `EMPLOYEE` or `MANAGER`
**When** the adjust endpoint is called
**Then** the response is 403 `ACCESS_DENIED` with an `access_log` row at `allowed = false`.

### Story 4.4: The Ledger Behind a Balance

As an HR admin,
I want to read every change ever made to a balance, with the amount, reason, who did it and when,
So that any entitlement figure can be explained from the record rather than from memory.

**Acceptance Criteria:**

**Given** an authenticated HR_ADMIN caller
**When** `GET /api/balances/{userId}/{leaveTypeId}/ledger` is called
**Then** the response is 200 with a `BalanceLedgerEntry` array for that user and leave type, oldest first, satisfying FR30.

**Given** each returned entry
**When** it is inspected
**Then** it carries `id`, `userId`, `leaveTypeId`, `changeDays`, `kind`, `reason`, `actorId`, a nullable `requestId` and `createdAt`
**And** `kind` is one of `SET`, `ADJUST`, `RESERVE`, `RELEASE` or `DEDUCT`, so HR sees HR's own actions and the system's automatic reservation movements in one timeline.

**Given** a user whose request was submitted, approved, and whose balance was then adjusted by HR
**When** the ledger is fetched
**Then** it shows the `RESERVE` row from submission, the `DEDUCT` row from approval and the `ADJUST` row from HR, each with its actor - the employee for the reservation, the approver for the deduction, the HR user for the adjustment
**And** rows carrying a `requestId` can be traced back to the request that caused them.

**Given** a user and leave type with no ledger history
**When** the endpoint is called
**Then** the response is 200 with an empty array, not a 404.

**Given** an authenticated caller whose role is `EMPLOYEE` or `MANAGER`
**When** the ledger endpoint is called
**Then** the response is 403 `ACCESS_DENIED` with an `access_log` row at `allowed = false`, since the ledger is HR-only in this POC.

**Given** Stories 4.2, 4.3 and this one together
**When** HR sets a balance, adjusts it, and reads the ledger
**Then** the ledger shows amount, reason, actor and time for each change and an adjustment below zero was refused, closing demo scenario 12 through an API path rather than a direct database check.

### Story 4.5: The HR Audit Report, One Entry per Request

As an HR admin,
I want one report per leave request carrying its whole discussion and its whole timeline,
So that I can answer any question about any request without assembling it myself.

**Acceptance Criteria:**

**Given** an authenticated HR_ADMIN caller
**When** `GET /api/audit` is called
**Then** the response is 200 with an `AuditRequestReport` array, one entry per leave request, newest submission first, satisfying FR42 and FR24
**And** the endpoint takes no filter parameters, so a single call returns the full trail.

**Given** the report array
**When** it is inspected
**Then** it includes requests of every status - `PENDING`, `APPROVED`, `REJECTED` and `CANCELLED` - for every employee, with no scope filter, unlike `GET /api/team/leave` which is approved-only and team-scoped.

**Given** one `AuditRequestReport`
**When** it is inspected
**Then** it carries `requestId`, `employeeId`, `employeeUsername`, `leaveTypeId`, `leaveTypeName`, `startDate`, `endDate`, `workingDays` and `status`
**And** it nests that request's full `comments` thread oldest first (FR15) and its full `history` timeline in order (FR45), so HR gets the complete picture per request in one call.

**Given** the endpoint's implementation
**When** it is reviewed
**Then** the controller and its assembling service live in the `leave` module, which reads `audit_events` through its permitted `leave -> audit` edge, per the resolved design question recorded above
**And** `audit` remains an AD-2 leaf: it is not made to read `leave_requests` or `request_comments`, and no new dependency edge is introduced
**And** the endpoint is still served at the contracted path `/api/audit` with the swagger's `Audit` tag and `AuditRequestReport` shape unchanged.

**Given** an authenticated caller whose role is `EMPLOYEE` or `MANAGER`
**When** `GET /api/audit` is called
**Then** the response is 403 `ACCESS_DENIED` with an `access_log` row at `allowed = false`, since the audit trail is HR view-only.

**Given** the audit trail is read-only
**When** the `leave` and `audit` modules are reviewed
**Then** no endpoint anywhere writes, updates or deletes an audit event through an API
**And** an attempted `UPDATE` or `DELETE` against `audit_events` still fails on the Story 1.2 trigger, re-confirming FR44 and demo scenario 13 with the full data set present.

### Story 4.6: POC Demo Readiness

As the product owner,
I want the POC runnable from a clean checkout with a scripted walkthrough and every acceptance scenario demonstrably passing,
So that the manager and client demo proves the system rather than a developer's memory of it.

**Acceptance Criteria:**

**Given** a clean checkout on a machine with Docker
**When** the README's instructions are followed
**Then** `docker compose up` brings up `postgres`, `backend` and the HTTPS reverse proxy, Flyway applies every schema and seed migration in order, and the API answers over `https://localhost/api`
**And** the README states the seeded demo cast and how to sign in as each role.

**Given** the demo script added by this story
**When** it is followed end to end
**Then** it walks all 15 of the PRD's acceptance scenarios in order, naming for each the role to use, the call to make and the result to expect
**And** it states plainly that notifications are a database row plus a log line, with no mail server involved, so the audience is not left expecting an email.

**Given** the full test suite
**When** `mvnw verify` is run
**Then** every unit and API test passes, including the RBAC, approval-rule and audit-write tests the PRD names as the riskiest paths (NFR9)
**And** integration tests run against a real PostgreSQL via Testcontainers, so the immutability trigger and the balance `CHECK` constraint are genuinely exercised (NFR10).

**Given** the 15 acceptance scenarios
**When** they are each executed against the running stack on seeded data
**Then** every one passes, and the run is recorded in the demo script with its outcome
**And** FR55 is recorded as Partial in that record, since the certificate is self-signed - the one documented gap the PRD accepts.

**Given** the architecture spine's open items
**When** this story completes
**Then** both are confirmed closed - no Spring Modulith dependency and no OAuth2 resource server remain in `pom.xml`
**And** no secret is committed to the repository or baked into an image (NFR8).

**Given** the PRD's out-of-scope lists
**When** the delivered POC is compared against them
**Then** nothing was built for FR32-FR41, FR48, FR49, FR46, FR47, FR60 or FR61
**And** any scope that did creep in is named explicitly in the readiness record rather than left for the retrospective to find.

---

## Validation Record

Run on 2026-10-22 against the 4 epics and 26 stories above.

### 1. FR coverage

**Pass.** All 45 in-scope FRs appear in at least one story's acceptance criteria, checked mechanically against the FR list in the Requirements Inventory - 0 missing. Every FR's criteria address the behaviour the PRD specifies for it, not merely a mention: the status codes, error codes, DTO fields, ledger kinds and audit actions are named explicitly so each criterion is independently testable.

All 24 operations in `openapi.yaml` are named by a story. The PRD's 15 acceptance scenarios each trace to at least one story, and the out-of-scope lists (FR32-FR41, FR48, FR49, FR46, FR47, FR60, FR61) are covered by a negative check in Story 4.6 rather than left implicit.

### 2. Architecture implementation

**Pass.**

**Starter template:** the Architecture Spine specifies none, so no "set up from starter template" story is required. Story 1.1 instead extends and corrects the scaffold already committed at `076b1cd`, and closes the spine's two named `pom.xml` open items (Spring Modulith removal per AD-1, OAuth2 resource-server removal plus jjwt addition per AD-7). This is the correct form of Epic 1 Story 1 for a repository that already has a scaffold.

**Table creation is just-in-time.** All 10 tables are created exactly once, each by the first story that needs it, and Story 1.1 explicitly creates none:

| Table | Created by |
| --- | --- |
| `audit_events`, `access_log` | Story 1.2 |
| `users`, `active_sessions` | Story 1.3 |
| `leave_types`, `leave_balances` | Story 2.1 |
| `leave_requests`, `balance_ledger` | Story 2.2 |
| `notifications` | Story 2.3 |
| `request_comments` | Story 3.5 |

Seed data follows the same rule (NFR7): the 12-user cast seeds with `users` in Story 1.3, the four leave types and starting balances with their tables in Story 2.1. No story seeds data for a table another story creates.

**AD compliance** is written into criteria rather than assumed: AD-1 (no async or event bus), AD-2 (table ownership and the leaf status of `audit`/`notifications`), AD-3 (`common` stays inert), AD-4 (owning module writes its own audit/ledger row, actor id passed explicitly), AD-5 (`BalanceService` sole writer plus the `CHECK` constraint), AD-6 (trigger-enforced immutability), AD-7/AD-8 (hand-rolled JWT, `SessionRegistry` idle check), AD-9 (lockout stays on `users`), AD-10 (one `AccessPolicy`, three shapes), AD-11 (one error envelope, ten-value enum), AD-12 (best-effort notifications), AD-13 (proxy-only ingress, env-var config).

### 3. Story quality

**Pass.** Each story is scoped to one capability with 5-9 Given/When/Then criteria, sized for a single dev session: the largest, Story 1.3, covers one endpoint pair plus the two tables it needs. Each names the FRs and ADs it implements, the exact endpoint paths, and the error codes from the contract. Error and edge cases are criteria, not afterthoughts - wrong password, locked account, non-pending transitions, blank comments, cross-team access, zero-working-day ranges, missing balance rows, and empty collections returning 200 rather than 404.

### 4. Epic structure

**Pass.** Epics are organised by user outcome and role journey - secure access and onboarding, the employee's own leave, the manager's queue, HR's control and oversight - not by technical layer. No epic is a database, API or test milestone. Testing is criteria inside each story (NFR9, NFR10) rather than a trailing epic.

**File churn check - overlap found, split justified.** Epics 2, 3 and 4 all touch the `leave` and `balances` modules: Epic 2 adds requests and the reserve/release paths, Epic 3 adds decisions and the deduct path, Epic 4 adds the HR set/adjust paths and the audit report. Consolidation was considered and rejected for three reasons:

1. Each epic is independently demoable to a different audience, which is what the PRD's milestone-per-week structure and its 15 role-specific scenarios require. Merging them would remove the demo gates.
2. A merged epic would run to 19 stories, past a sensible review and context boundary.
3. The overlap is additive, not revisiting: Epic 3 adds `applyDecision` and the `DEDUCT` ledger kind alongside Epic 2's work rather than reworking it, and Epic 4 adds `SET`/`ADJUST` alongside both. No epic rewrites what an earlier one built, so there is no feedback loop that would make the later split wasteful.

### 5. Dependency validation

**Pass.**

**Epic independence.** Epic 1 stands alone and installs what the rest build on. Epic 2 functions with only Epic 1 present - an employee submits, edits, cancels and tracks leave whether or not approvals exist. Epic 3 needs Epics 1 and 2 but nothing later. Epic 4 needs 1-3 but nothing later. No epic requires a later one.

**Within-epic ordering.** Each story builds only on previous ones, verified by scan: no story says "wait for", "once Story N", "depends on Story N" or "not yet implemented".

Two forward *references* exist and were checked individually - both are extension notes, not dependencies:

- Story 2.3 notes its email template is later reused by Epic 3's decision notifications. Story 2.3 is complete without that reuse.
- Story 2.7 notes the `APPROVED`, `REJECTED` and `COMMENT` branches of `HistoryEntryType` are populated by Epic 3. The story explicitly delivers the full enum with those branches reachable but unexercised, rather than shipping a partial type that Epic 3 would have to widen.

One case of deliberately shipping ahead of use is recorded rather than hidden: Story 1.5 delivers all three `AccessPolicy` shapes, but only `requireRole` gets a live endpoint inside Epic 1, with `currentScope()`/`findByScope` first consumed by Story 2.4. AD-10 makes the permission layer a single component; splitting it across epics would leave two modules writing their own scope checks in the interim, which is the exact risk the PRD names.

### 6. Contract gaps found and resolved

Three gaps surfaced while writing criteria. All three were closed in `openapi.yaml` on 2026-10-22, and the YAML was re-parsed to confirm it is still valid (22 paths, 29 schemas, 4 shared responses):

1. **`PUT /api/requests/{id}` had no documented code for an over-balance edit.** Its 409 description mentioned the case but carried only an `INVALID_TRANSITION` example, leaving FR31's "always blocked" rule unspecified on edit. The 409 now carries two named examples, `invalidTransition` and `insufficientBalance`.
2. **No error code existed for FR20's forced password change.** A shared `PasswordChangeRequired` response (403, `code: PASSWORD_CHANGE_REQUIRED`) was added, with a note in `info.description` that it applies to every operation except login, logout and change-password. `ProblemDetail.code` now declares all ten codes as an explicit `enum`, so `common.ErrorCode` has a single machine-readable source to mirror.
3. **Edit re-reservation semantics were unstated.** The `PUT` description now specifies that the new working-day count is compared against `availableDays + thisRequest.workingDays`, so lengthening a 4-day request to 5 needs one more free day, not five - the reading that would otherwise reject valid edits.

### Open items carried into implementation

Not validation failures - decisions already made that the first affected story must honour:

- `GET /api/audit` is hosted in the `leave` module, not `audit`, keeping `audit` an AD-2 leaf. Binding on Story 4.5; the swagger's `Audit` tag and `AuditRequestReport` shape are unchanged.
- FR55 (HTTPS) is delivered in Story 1.1 rather than the PRD's week 4, because AD-7's `Secure` cookie is unverifiable without it.
- FR55 remains Partial by the PRD's own accounting: the certificate is self-signed, and Story 4.6 records that as the one accepted gap.
- The PRD's "Spring Boot 3" line still disagrees with the `pom.xml` pin of 4.1.1 that the spine adopted. A one-line PRD correction would stop the two documents contradicting each other (C-6 in the Contradiction Register below).
- Five further contradictions between the committed code and the two documents were found after this validation ran and are recorded in the **Contradiction Register** below. Story 1.1's criteria were rewritten to name the real starting state, so that story now closes C-1 through C-5.
- NFR17 (responsive layout, semantic HTML, keyboard navigation) is out of this backend-only breakdown and needs frontend planning once a UX design contract exists.

---

## Contradiction Register

A cross-document audit run on 2026-10-22 over the PRD, the Architecture Spine, `openapi.yaml`, the committed code (`backend/pom.xml`, `docker-compose.yml`, `application.yml`, `CompanyLeaveApplication.java`) and this document. Seven contradictions found. Each is recorded with where it lives, which source wins, and who closes it - none is left for an implementer to discover and resolve by guesswork.

**`openapi.yaml` audited clean.** Every operation that can refuse a caller documents its 403, including `GET /api/requests`, whose `employeeId` parameter is restricted to a manager's direct report or an HR caller. The contract contradicts neither the PRD nor the spine on any endpoint, DTO field, enum value or status code.

### C-1 - `docker-compose.yml` pins PostgreSQL 16, both documents say 18

`docker-compose.yml` uses `postgres:16`. The PRD's stack table and the spine's Stack table both specify **PostgreSQL 18**. **The documents win** - 18 is a deliberate choice, 16 looks like a scaffold default nobody revisited. Closed by Story 1.1.

### C-2 - `docker-compose.yml` has no backend, frontend or proxy service

AD-13 requires `postgres`, `backend`, `frontend` and a reverse proxy terminating HTTPS, with the proxy as the only ingress. The committed file defines only `postgres` and `mailpit`. **AD-13 wins** - without the proxy, FR55 is unmet and AD-7's `Secure` cookie flag is unenforceable. Closed by Story 1.1.

### C-3 - `docker-compose.yml` publishes Postgres on 5432 to the host

AD-13 states the proxy is the only service exposed outside the compose network. The committed file maps `5432:5432`. **AD-13 wins.** Closed by Story 1.1, which removes the mapping; the backend reaches Postgres on the in-network hostname `postgres`.

### C-4 - Mailpit is already wired up, though both documents defer it

`docker-compose.yml` runs `axllent/mailpit:v1.31` on ports 1025 and 8025. The PRD says Mailpit "is not wired up yet" and is "a later integration"; the spine lists Spring Mail as "present but unused until real SMTP dispatch is integrated" and omits mailpit from AD-13's service list. **The documents win** - `sendNotification()` writes a DB row and prints a log line, so there is nothing for a mail catcher to catch, and leaving it running invites a demo audience to expect email that never arrives. Story 1.1 removes the service. Note `spring-boot-starter-mail` stays in `pom.xml`, which both documents sanction as present-but-unused.

### C-5 - AD-7's "open item" is already done; AD-1's is not

The spine records both as open. In fact the uncommitted working-tree change to `backend/pom.xml` has already removed `spring-boot-starter-security-oauth2-resource-server` and added jjwt 0.13.0. Spring Modulith, however, is still fully present - `spring-modulith.version`, the BOM import, `spring-modulith-starter-core` and `spring-modulith-starter-test`. **Reality wins on both counts.** Story 1.1 was corrected to verify-and-commit the jjwt change rather than perform it, and to remove Modulith plus its version property. Had this gone unchecked, an implementer would have hunted for a dependency that was no longer there and possibly left Modulith in place.

### C-6 - PRD says "Spring Boot 3", `pom.xml` pins 4.1.1

Already recorded in the spine's Deferred section, carried here because it still stands. The PRD's "Java 21, Spring Boot 3" line disagrees with the committed parent `4.1.1`, which the spine adopted as reality. **The `pom.xml` and the spine win.** This needs a one-line PRD correction; it is the user's document, so it is flagged rather than edited. No story is blocked by it, but anyone reading the PRD alone would pick the wrong starter names - Boot 4 renames them (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, `spring-boot-starter-data-jpa-test`), and `pom.xml` already uses the new ones.

### C-7 - `_bmad-output/understanding/db.md` disagrees with the contract in four ways

Excluded from this breakdown's inputs by choice, so it governs nothing here, but it is in the repository and will mislead anyone who reads it as schema truth:

1. `users.role` is documented as `EMPLOYEE, MANAGER, or HR`; the swagger's `Role` enum and the spine both use **`HR_ADMIN`**.
2. Every timestamp is `TIMESTAMP`; the spine requires **`timestamptz`** throughout (NFR12).
3. It describes **9 tables** and omits `active_sessions`, which AD-8 adds - the real count is 10.
4. `leave_balances` carries an `updated_at` column that appears in neither the PRD's core-tables list nor the spine, and `notifications.sent_at` is described as "when sent to mail catcher", contradicting C-4's deferral.

**The swagger and the spine win on all four.** Either refresh `db.md` from the implemented migrations once Epic 2 lands, or mark it superseded. Recommended: let the Flyway migrations be the schema's single source of truth and retire `db.md` as a planning sketch, rather than maintaining a fourth description of the same tables.

### Disposition summary

| ID | Lives in | Winner | Closed by |
| --- | --- | --- | --- |
| C-1 | `docker-compose.yml` | PRD + spine (Postgres 18) | Story 1.1 |
| C-2 | `docker-compose.yml` | AD-13 | Story 1.1 |
| C-3 | `docker-compose.yml` | AD-13 | Story 1.1 |
| C-4 | `docker-compose.yml` | PRD + spine (defer Mailpit) | Story 1.1 |
| C-5 | spine's open items vs `pom.xml` | Reality | Story 1.1 (corrected above) |
| C-6 | PRD stack line | `pom.xml` + spine | One-line PRD edit - user's call |
| C-7 | `understanding/db.md` | swagger + spine | Refresh or retire - user's call |

Five of the seven are closed inside Story 1.1, which is why that story's criteria now name the committed starting state explicitly instead of describing a clean slate. The two left open are both documentation drift in files this breakdown does not own.
