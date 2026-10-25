---
title: 'Story 4.5: The HR Audit Report, One Entry per Request'
type: 'feature'
created: '2026-10-25'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
baseline_commit: ''
context:
  - '{project-root}/backend/AGENTS.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** HR has no API to view a complete audit trail per leave request. After Stories 4.2–4.4, HR can manage and read balances, but seeing the full picture of a request — who submitted it, its decision history, and its discussion thread — requires querying multiple endpoints or the database directly.

**Approach:** Add `GET /api/audit` to `LeaveRequestController` in the `leave` module. Add `getAuditReport()` to `LeaveRequestService`: enforce `HR_ADMIN` role via `accessPolicy.requireRole(HR_ADMIN)`, fetch all leave requests via `leaveRequestRepository.findAllByStatus(null)` (returns all statuses, newest submission first), batch-fetch employee usernames via `UserService`, and for each request assemble the comments list and history timeline using private helper methods that reuse `requestCommentRepository` and `auditService.findLeaveRequestEvents` from existing patterns (no redundant access re-check since `HR_ADMIN` is verified once at the top). Create a new `AuditRequestReport` DTO (`@Value @Builder`) in `com.company.leave.leave` matching the OpenAPI `AuditRequestReport` schema exactly. The endpoint takes no query parameters, is tagged `Audit`, and returns 200 array or 403 for non-HR callers.

</frozen-after-approval>

## Implementation Notes

- Created `AuditRequestReport.java` DTO (`@Value @Builder`) in `com.company.leave.leave`, matching the OpenAPI schema exactly. Uses `String status` (consistent with `TeamLeaveEntry` pattern).
- Created `AuditController.java` in `com.company.leave.leave` at `@RequestMapping("/audit")`, tag `Audit`. Delegates entirely to `leaveRequestService.getAuditReport()`.
- Added `getAuditReport()` to `LeaveRequestService`: enforces `HR_ADMIN` role, batches employee username lookup via `UserService`, calls per-request private helpers `assembleComments` and `assembleHistory` to avoid redundant `findById` + access-check re-evaluation on each iteration.
- Added `assembleComments(long requestId)` and `assembleHistory(long requestId)` private helpers that extract the core assembly logic from `listComments` and `history` without the redundant DB load and access check (since HR_ADMIN is verified once at the top).
- Added `import com.company.leave.users.Role` to `LeaveRequestService`.
- `leaveTypeService.findNameById` is called per-request inline (consistent with `applyDecision` pattern); leave types are few (4) and stable so N+1 impact is negligible.
- Created `AuditControllerIntegrationTest.java` (6 tests): all-statuses-newest-first (PENDING/APPROVED/REJECTED/CANCELLED), nested comments+history, empty array, EMPLOYEE 403, MANAGER 403, unauthenticated 401.
- Created `backend/curl/story-4.5-the-hr-audit-report-one-entry-per-request.md`.
- All 193 tests pass after implementation.

## Spec Change Log

## Review Triage Log

- **low/patch**: No CANCELLED status tested. AC names all four statuses. Fixed: added fourth request with status CANCELLED to `hrGetsAuditReportWithAllStatusesNewestFirst`.
- **low/patch**: Curl guide used invented passwords that won't work against seeded users. Fixed: replaced specific passwords with `<password-from-seed>` and added note about the forced-password-change flow.
- **false**: `employeeUsername` silently null. Users cannot be deleted in this POC; `getUsernamesByIds` always returns entries for all provided IDs. Same no-null-guard pattern is used in `TeamLeaveService.getTeamLeave` without issue.
- **low/reject**: N+1 leave-type name lookup. Only 4 leave types in the POC; same inconsistency exists in `applyDecision` and `submit`. Not worth fixing in this story.
- **low/defer**: `assembleComments`/`assembleHistory` duplicate bodies of public `listComments`/`history`. Real structural concern — refactoring public methods to delegate to private helpers would be cleaner. Added to deferred-work.md.
