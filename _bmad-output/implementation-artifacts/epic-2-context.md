# Epic 2 Context: Employee Leave Requests and Balances

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

This epic delivers the employee-facing leave flow end to end: employees can see valid balances, submit whole-day leave requests against those balances, track status and history, edit or cancel only while pending, and review their own notifications. It matters because it proves the core workflow of the POC without waiting for approval or HR features: a request is submitted against a real balance, reserved correctly, audited, and surfaced to the employee and manager with the necessary access controls. Planning note: the planning artifacts include the PRD and backend architecture spine, but no separate UX/design or product brief artifact exists, so this context stays focused on the backend contract and operational rules for the backend-only scope.

## Stories

- Story 2.1: Leave Catalogue and My Balances
- Story 2.2: Submit a Leave Request Against a Real Balance
- Story 2.3: Notification on Submission and My Notification History
- Story 2.4: See My Requests and Their Status
- Story 2.5: Edit a Pending Request
- Story 2.6: Cancel a Pending Request
- Story 2.7: The History Timeline on a Request

## Requirements & Constraints

- Employees can request leave by type from the seeded catalogue (Vacation, Sick, Parental, Sabbatical), limited to whole-day ranges.
- Start and end dates must be valid date-only values, endDate >= startDate, and the request must produce at least one working day when counted Monday through Friday only; public holidays are ignored as part of the shared working-day utility.
- Each request records the employee, approver (their manager), leave type, optional notes, status, and submission time; the request never disappears on cancel and remains visible in history and list views.
- Leave balances are tracked per user and leave type as totalDays, reservedDays, and availableDays = totalDays - reservedDays. Requests reserve the working-day amount on submit, and edits and cancellations release or re-reserve the correct amount within the same transaction.
- Submission is rejected with INSUFFICIENT_BALANCE when requested working days exceed currently available days. This check is service-layer enforced and aligned with the rule that reserved days already held by pending requests count against the entitlement.
- Employees can view only their own requests and notification history; managers can see their direct reports' requests when the scope permits; HR admins can access any user data without scope filtering. In all unauthorized cases, the access check fails with ACCESS_DENIED and an access-log entry is written.
- GET /api/requests supports an optional status filter (PENDING, APPROVED, REJECTED, CANCELLED); GET /api/requests/{id}/history returns a timeline of user-visible events (submission, edits, comments, cancellation, and later decision events from Epic 3). The timeline is a core audit/read view, not just raw DB rows.
- A notification is created when a report submits a leave request; it is addressed to the approver with the leave type, date range, working-day count, status, and request link. The user can read only their own notifications, and the same template logic is later reused by approval/rejection notifications.
- Every state-changing transaction must write an audit event with the acting user and timestamp; request/balance changes must remain in a consistent transaction and must never be silently missing a ledger or audit record.
- The implementation follows backend-only, package-by-feature conventions: direct synchronous calls between service layers, no event bus, and the domain ownership of tables remains explicit (leave owns requests/types, balances owns balances/ledger, audit owns audit/access logs, notifications owns notifications, security owns active sessions).

## Technical Decisions

- Architecture follows the backend architecture spine: layered modular monolith with package-by-feature boundaries and direct in-process service calls. Domain modules stay separate in source tree and review discipline, but no Spring Modulith enforcement is required.
- common remains inert and contains stateless utilities such as the working-day calculator and shared error handling; it does not hold business rules or entity repositories.
- leave_types and leave_balances are introduced in the epic, with leave_balances carrying a defensive DB CHECK (total_days - reserved_days >= 0) while the service layer remains the actual enforcement point.
- leave_requests and balance_ledger are written in the same transaction. BalanceService is the single writer of balance/ledger state and records the reservation and release path; the leave module writes its own request and audit row, with explicit actor IDs passed in rather than re-reading security context.
- Access control is centralized through the access-policy scope model. Requested employee data is resolved by owner, manager, or HR scope using the same authorization flow used across the backend, and no controller-level ad hoc checks are relied on for the core access rules.
- Notification delivery is intentionally in-process and tolerant: the service logs and returns normally if template rendering or DB writes fail, so a failed notification does not roll back the request or reservation it is reporting.
- There is no separate UX design contract in the planning artifacts. The relevant interaction contract is backend/API behavior and the PRD's desktop-first accessibility expectations, which remain deferred for frontend planning rather than implemented here.

## UX & Interaction Patterns

- Employee flows: see the current leave balance for each type, submit a request with start/end dates and notes, review a filterable list of own requests, and edit or cancel while still pending.
- Manager flow: receives a notification when a direct report submits a request; the request is still resolved in the API as an approver-owned object, with scope checks requiring manager-of-report access.
- History flow: a request detail exposes a readable timeline of state changes and comments in chronological order so an employee can understand what happened without using raw database records.
- Product constraints from the PRD still apply: desktop-first, semantic HTML, labelled fields, and keyboard-accessible primary flows, but these are deferred to frontend design work because no UX contract exists in the planning artifacts.

## Cross-Story Dependencies

- Story 2.1 is the foundation: it creates the leave-type and balance tables and seeds the starting entitlement data, which all later stories read.
- Story 2.2 depends on the balance model and the shared working-day logic; it introduces the actual reservation flow and the request/ledger/audit write path that Stories 2.3–2.7 consume.
- Story 2.3 relies on a successful submission to create the approver notification and on the request model for a request-specific notification link and owned-user filtering.
- Stories 2.4–2.7 depend on the request, access, and history model created in 2.2, and they all rely on the Epic 1 access and security foundation (login, RBAC, audit log, and session rules).
- Epic 3 extends this epic's history and notification patterns for approval/rejection and comments, but Epic 2 remains functionally standalone once Epic 1 is present: employees can submit, edit, cancel, and track leave without needing later epics.
