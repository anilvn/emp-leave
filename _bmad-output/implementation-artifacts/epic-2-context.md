# Epic 2 Context: Employee Leave Requests and Balances

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Epic 2 gives employees a complete, self-serve leave journey: they can submit requests against real balances, see their own history and notifications, adjust pending requests, and cancel them without leaving the system in an inconsistent state. The epic matters because it proves the core employee workflow end to end: requests count working days correctly, balances reserve and release days as status changes, and the approval path still has a trustworthy trail of what happened.

## Stories

- Story 2.1: Leave Catalogue and My Balances
- Story 2.2: Submit a Leave Request Against a Real Balance
- Story 2.3: Notification on Submission and My Notification History
- Story 2.4: See My Requests and Their Status
- Story 2.5: Edit a Pending Request
- Story 2.6: Cancel a Pending Request
- Story 2.7: The History Timeline on a Request

## Requirements & Constraints

- Employees can request the seeded leave types Vacation, Sick, Parental, and Sabbatical, using whole-day date ranges. Start and end dates are required; the end cannot precede the start, and a range with zero working days is invalid. Count Monday–Friday inclusive and ignore public holidays.
- Requests store optional notes and one of `PENDING`, `APPROVED`, `REJECTED`, or `CANCELLED`. A cancelled request is retained and remains visible.
- `availableDays = totalDays - reservedDays`; missing balance rows behave as zero, not unlimited entitlement. Pending requests reserve days at submission, and successful edits recalculate the hold while excluding that request's existing reservation from the availability check. Cancellation releases exactly its held days.
- Only the owner may edit or cancel, and only while the request is pending. The leave type cannot be changed by editing. Invalid date input returns `VALIDATION_FAILED`; an insufficient balance returns `INSUFFICIENT_BALANCE`; an illegal state change returns `INVALID_TRANSITION`. A rejected mutation leaves the request and balance unchanged.
- Apply one `AccessPolicy` to every endpoint: employees access their own requests and notifications; managers may view direct reports' requests when authorized; HR may access all applicable records. Editing and cancellation remain owner-only. Denials return `ACCESS_DENIED` and are recorded in the access log.
- Every request state change records its actor and timestamp. Request history is derived from audit events and ordered oldest first; Epic 2 contributes submission, edit, and cancellation entries, while approval, rejection, and comment entries are added by Epic 3.
- On submission, the assigned approver receives a DB-backed notification row and log entry. Notifications are best effort: failures are logged and never roll back the request or balance change. No mail server is contacted in this epic.
- Keep the backend-only layered modular monolith: no event bus, no cross-module repository access, and no business logic in `common`.

## Technical Decisions

- Backend work belongs to `leave`, `balances`, and `notifications`. `leave` owns `leave_types`, `leave_requests`, and `request_comments`; `balances` owns `leave_balances` and `balance_ledger`; `notifications` owns `notifications` and remains a leaf module. Modules communicate only through public service APIs.
- `BalanceService` is the sole writer of balances and ledger entries. It owns reservation, release, and related ledger writes in the same transaction as the request mutation. Enforce `total_days - reserved_days >= 0` in the service and with a database check constraint.
- `leave_requests` stores employee, approver, leave type, date range, working-day count, notes, status, submission/decision timestamps, and decision comment. `balance_ledger` records each change with its actor, request context, and timestamp; read ledger/history entries oldest first. Keep request ranges as SQL `DATE`, working-day counts as whole-day integers, and timestamps as `timestamptz`.
- Keep the working-day calculator as a stateless utility in `common`, reusable by request validation and later reporting without adding business services or persistence there.
- History is assembled by `leave` using audit data read through `AuditService`; `audit` must not query leave tables. The owning module writes the audit event for its state change and passes actor IDs explicitly across module calls. Audit and access-log records are insert-only.
- Use RFC 9457 `ProblemDetail` responses with canonical error codes, including `ACCESS_DENIED`, `INSUFFICIENT_BALANCE`, and `INVALID_TRANSITION`. Authentication and authorization reuse Epic 1's user/role policy and `AccessPolicy`.
- For the Angular frontend, keep requests as a shell-agnostic feature mounted in employee, manager, and HR shells. Feature API state belongs in route-scoped services using signals and `httpResource`; request mutations invalidate both request and balance data. Use Signal Forms, server-calculated working-day and availability values, and the generated API types rather than duplicating schemas.

## UX & Interaction Patterns

- The requests feature provides the employee's request list, status filtering, request details/history, submission, pending edit/cancel, and balance views across the role shells. Request details use the same canonical route for owner, direct manager, and HR where authorized.
- Show server-provided `workingDays` and `availableDays`; do not calculate them in the UI. Use native date inputs and send `yyyy-MM-dd` unchanged, trim optional text and send blank values as `null`. Reuse the submit form for edits with leave type read-only.
- Surface documented validation and balance/transition errors inline where appropriate; render access-denied/read errors once at page level. Label all form fields and preserve keyboard-accessible main flows.
- Present request history chronologically, oldest first, with readable event details and actor/time context.

## Cross-Story Dependencies

- Epic 2 depends on Epic 1 for the user model, role/manager relationships, JWT/session security, immutable audit and access-log foundations, and the shared `AccessPolicy`.
- Epic 2 establishes the request lifecycle required by Epic 3's approval decisions, comments, and decision notifications. Its history and comment model also support later manager/HR history and audit-reporting work.
