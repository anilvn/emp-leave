# Epic 2 Context: Employee Leave Requests and Balances

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Give employees a complete self-service leave flow: understand their available entitlements, submit and track requests, make permitted changes, review what happened, and see their notifications. Requests must be counted consistently and held against real balances so pending leave cannot be overbooked.

## Stories

- Story 2.1: Leave Catalogue and My Balances
- Story 2.2: Submit a Leave Request Against a Real Balance
- Story 2.3: Notification on Submission and My Notification History
- Story 2.4: See My Requests and Their Status
- Story 2.5: Edit a Pending Request
- Story 2.6: Cancel a Pending Request
- Story 2.7: The History Timeline on a Request

## Requirements & Constraints

- Requests use the Vacation, Sick, Parental, and Sabbatical leave types, whole days, a start/end date, and optional notes. Reject reversed date ranges and ranges with no working days. Count Monday–Friday inclusively; public holidays are not excluded.
- Employees can view their own requests and statuses, filter their request list, and read each request's chronological history. Only pending requests can be edited or cancelled. A cancelled request remains visible.
- Request details and history are visible to the employee, their direct manager, or HR; enforce this shared access policy and log denied access.
- Show each leave type's total, reserved, and available days. Available days are total minus reserved and must never be negative; a missing balance is shown as zero. Refuse submissions or edits that exceed available days, including days held by other pending requests.
- Reserve days on submission, recalculate the reservation on edit, and release it on cancellation. Record the action, actor, and time in the request history.
- On submission, create a notification row for the request's approver and support each user's own notification history. Render notification content with leave type, dates, working-day count, status, and request link. This POC logs notifications to the database and application log; it does not send email. Notification failure must not undo a committed request or reservation.
- Use Flyway-managed PostgreSQL schema migrations and separate, incremental seed migrations. Keep date ranges as `DATE` values and leave-day amounts as whole-day integers. Exercise database-backed behavior with PostgreSQL integration tests.

## Technical Decisions

- Keep domain code in the layered modular monolith. `leave` owns `leave_types`, `leave_requests`, and request comments; `balances` owns `leave_balances` and `balance_ledger`; `notifications` owns notification rows. Entities and repositories stay within their owning module; cross-module access goes through public service methods.
- `BalanceService` is the only writer of balances and ledger entries. Enforce the non-negative balance invariant in the service and with a database check constraint. A reservation/release and its ledger entry commit in the same transaction as the triggering request change.
- Put the stateless working-day calculator in `common`; keep business-rule services and persistence out of that package.
- The module owning the changed data writes its audit entry within its transaction. Pass the actor id explicitly across module boundaries; do not have a caller duplicate another module's audit or ledger write.
- Call domain services synchronously in-process; do not introduce an event bus or asynchronous flow. `NotificationService` is a leaf and handles its own failures.
- Apply ownership and role checks through the shared `AccessPolicy`; keep reads scoped to the authorized user's data. Use the shared RFC 9457 `ProblemDetail` error shape and machine-readable error codes.
- For the employee UI, use the employee role shell and requests feature area. Follow the frontend architecture's standalone-component and signals-based state conventions; use `httpResource()` for reads and `HttpClient` for mutations.

## UX & Interaction Patterns

- Present balances as per-type totals, reserved days, and available days; offer date pickers, leave-type selection, and optional notes in the request flow.
- Make request status and pending-only actions clear in the employee's filterable request list. Show history oldest-first as a readable timeline. Present domain-specific errors inline where useful, with a global toast fallback for unhandled errors.
- Keep forms labelled and keyboard accessible, with a responsive, desktop-first layout.

## Cross-Story Dependencies

- Epic 1 supplies the shared schema, seeded users, audit service, access policy, and error handling that this epic builds on.
- The leave catalogue and balance records must exist before requests can be checked and reserved. Request submission and its audit/balance writes underpin request notifications, listing, editing, cancellation, and history.
- The notification template/content established here is reused for approval and rejection notifications in Epic 3. The history model also leaves approval, rejection, and comment event types available for Epic 3.
