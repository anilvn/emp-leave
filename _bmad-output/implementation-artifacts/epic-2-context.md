# Epic 2 Context: Employee Leave Requests and Balances

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

An employee can run their own leave end to end without chasing anyone. They submit a request against a real balance, see it counted in working days, watch its status, edit or cancel it while pending, read a History timeline, and see their notification log — while their manager gets a notification row the moment it lands. Submissions that exceed available days are refused, and reserved days move correctly on every edit and cancellation.

## Stories

- Story 2.1: Leave Catalogue and My Balances
- Story 2.2: Submit a Leave Request Against a Real Balance
- Story 2.3: Notification on Submission and My Notification History
- Story 2.4: See My Requests and Their Status
- Story 2.5: Edit a Pending Request
- Story 2.6: Cancel a Pending Request
- Story 2.7: The History Timeline on a Request

## Requirements & Constraints

**Functional (FR1–FR9, FR31, FR45, FR52–FR54):**
- Submit a request choosing from Vacation, Sick, Parental, Sabbatical; whole days only.
- Date range validated: end ≥ start; days counted as working days Mon–Fri, public holidays ignored.
- Optional free-text notes on create and update.
- Status lifecycle: Pending → Approved/Rejected/Cancelled. Cancelled requests stay visible, never deleted.
- Employee views only their own requests; filterable by status.
- Balance per type shows total, reserved (pending) and available days; available = total − reserved, never negative.
- Edit allowed only while Pending; re-reservation recalculated. Leave-type change refused (400 `LEAVE_TYPE_CHANGE_NOT_ALLOWED`).
- Submission/edit refused with 409 `INSUFFICIENT_BALANCE` when working days exceed available.
- On edit, re-reservation excludes the request's own currently-held days.
- Every request records employee id, submission timestamp, and an audit event.
- History timeline per request: chronological entries derived from audit_events.
- On submit, `sendNotification()` writes a `notifications` row to the manager and prints a log line (no mail server).
- Notification history scoped to the caller's own rows.
- Email template renders leave type, dates, day count, status and a link; reused by Epic 3.

**Non-functional:**
- Schema managed by Flyway; schema and seed migrations are separate files (seed data added incrementally per epic).
- Integration tests run against real PostgreSQL via Testcontainers.
- All timestamps `timestamptz`, JVM pinned to UTC; `start_date`/`end_date` are `DATE`; leave amounts are whole-day integers.
- Surrogate keys `BIGINT GENERATED ALWAYS AS IDENTITY`.
- All errors are RFC 9457 `ProblemDetail` from one `@RestControllerAdvice` with `common.ErrorCode`.

**Demo scenarios this epic must close:** 1 (submit Thu–Tue = 4 days), 2 (edit then cancel), 9 (employee opens another's request → 403 + logged), 10 (exceeds available days).

## Technical Decisions

- **Table ownership (AD-2):** `leave_types` → `leave`; `leave_requests`, `request_comments` → `leave`; `leave_balances`, `balance_ledger` → `balances`; `notifications` → `notifications`. Each module's entities/repositories are package-private.
- **Balance invariant (AD-5):** `BalanceService` is the sole writer of `leave_balances`/`balance_ledger`. A `CHECK (total_days - reserved_days >= 0)` on `leave_balances` as defence in depth.
- **Audit writes (AD-4):** The module owning the mutated table writes its own audit/ledger row inside its `@Transactional` method. Actor id passed explicitly, never re-derived from `SecurityContext`. Neither module duplicates the other's row.
- **Working-day calculator (AD-3):** Stateless utility in `common`; no entity, repository or business-rule service beside it.
- **Access control (AD-10):** All role/ownership checks go through `security.AccessPolicy` — `requireOwnerManagerOrHr(...)`, `currentScope()` returning `UserScope`, and `requireRole(...)`. Repository methods take `UserScope` for scoped queries.
- **Notifications (AD-12):** `NotificationService.sendNotification()` catches all exceptions internally, logs at ERROR, returns normally. Called last, after audit/balance writes. Synchronous, in-process (AD-1).
- **Dependency edges:** `leave` → `balances`, `audit`, `notifications`, `common`. `notifications` is a leaf (depends only on `common`/`security`).

## Cross-Story Dependencies

- Epic 1 provides: Flyway infrastructure, `users` table with seeded demo cast (12 users), `audit_events`/`access_log` tables with immutability triggers, `AuditService`, `AccessPolicy`/`UserScope`, `SecurityConfig`/`JwtAuthenticationFilter`, `active_sessions`, `ErrorCode` enum, `ProblemDetail` advice.
- Story 2.1 (tables + seed) is a prerequisite for all later stories in this epic.
- Story 2.2 (leave_requests, balance_ledger, working-day calc) is needed before 2.3–2.7.
- Story 2.3 (notifications table) stands alone after 2.2 but its template is reused by Epic 3.
- Story 2.7's `HistoryEntryType` enum includes `APPROVED`, `REJECTED`, `COMMENT` — populated by Epic 3.
