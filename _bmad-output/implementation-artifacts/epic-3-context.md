# Epic 3 Context: Manager Approvals, Team Visibility and Discussion

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Enable managers and HR to review requests in their approval scope, make accountable and auditable decisions, discuss requests in context, and see their team's approved leave. The approval chain must cover managers' own requests and HR self-approval while preventing cross-team decisions, so employees receive timely outcomes and teams can coordinate leave confidently.

## Stories

- Story 3.1: The Pending Approvals Dashboard
- Story 3.2: Approve a Request with an Optional Comment
- Story 3.3: Reject a Request with a Required Comment
- Story 3.4: The Approval Chain at Every Level
- Story 3.5: Discussion Comments on a Request
- Story 3.6: Team Leave by Date

## Requirements & Constraints

- Pending approvals include requests awaiting the caller's decision. Each item provides employee, dates, leave type, working days, notes, current balance, request history, and comment thread without requiring separate context calls. Empty results are a successful empty list.
- Approval is allowed only for a pending request's assigned approver. Approval comments are optional; rejection requires a non-blank reason. Record the decision, approver, time, and comment; repeated or otherwise invalid transitions must not alter state.
- Approval converts reserved days into deducted days; rejection releases the reservation without changing total entitlement. Preserve available balance on approval and increase it on rejection. The decision and corresponding balance ledger/audit records must be consistent.
- Route employee requests to their manager, manager requests to their own manager, and top-level manager requests to HR. HR may self-approve when no manager is assigned; record that explicitly. Unauthorized cross-team access or decisions are refused and logged.
- Request owners, their manager, and HR may read and add comments, including after a request is decided. Comments do not trigger email or in-app alerts; participants see new comments when they refresh. Keep comments and decision activity in request history.
- Team leave contains only direct reports' approved requests, optionally limited to requests overlapping the requested date range. Include each request's comments and full history in the team overview.

## Technical Decisions

- Keep the backend a layered modular monolith with synchronous service-to-service calls. `approvals` owns approval endpoints and team-scope decisions; `leave` owns `leave_requests` and `request_comments`; `balances` owns balances and ledger; `audit` owns audit events; `notifications` owns notification rows. Cross-module access is through public services, never another module's repository or entity.
- Resolve manager scope through the shared `AccessPolicy`/`UserScope` path, not controller-level filtering. Enforce authorization on the server; denied access produces `ACCESS_DENIED` and an access-log entry.
- `approvals` delegates request changes to `leave.applyDecision(requestId, decision, comment, approverId)` and supplies the actor explicitly. The owning modules write their audit/ledger entries within the triggering transaction; `BalanceService` is the sole balance/ledger writer.
- Decision notifications reuse the existing template and `sendNotification()`: create a notification row and log, without SMTP. Call notification delivery last and treat failures as best-effort so they cannot undo a committed decision.
- The `leave` module assembles team responses, including history obtained through `AuditService`; it must not bypass module ownership to compose the response.

## UX & Interaction Patterns

- Present pending requests with their decision context together. Keep Reject disabled until a non-blank comment is entered and show server validation inline. After a decision, remove the request from the pending list and show its resulting status.
- Provide a date-range team view with each approved leave item’s discussion and timeline. Show comment updates on page refresh or an explicit refresh action; do not add live push or comment alerts. Use accessible, labelled, keyboard-operable main flows and communicate status/errors without relying on color alone.

## Cross-Story Dependencies

- Approval queue and decisions rely on Epic 2's request submission, pending status, balance reservations, audit/history, and notification template/service.
- Approval routing and authorization rely on Epic 1's roles, manager relationships, `AccessPolicy`, and access/audit logging.
- Stories 3.2 and 3.3 act on requests surfaced by 3.1; 3.4 relies on the existing request-routing relationship; 3.6 composes the comment and history capabilities delivered by 3.5 and Epic 2.
