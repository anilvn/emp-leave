---
title: 'Story 3.2: Approve a Request with an Optional Comment'
type: 'feature'
created: '2026-10-25'
status: 'in-review'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'da44c6dc4a1d3415d9293543b3c4192577578a91'
context:
  - '{project-root}/backend/AGENTS.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Managers need to decide assigned pending requests and optionally comment, so employees know the outcome and their balance reflects approved leave.

**Approach:** Add an approvals API operation delegating request state to `leave`, which coordinates balance, audit, and notification effects.

## Boundaries & Constraints

**Always:** Approve only a pending request assigned to the caller. Persist decision, approver, time, and optional comment. Convert reserved days to deducted days by decreasing `reserved_days` and `total_days` by the working-day count, preserving available days. Write one request audit event and one `DEDUCT` ledger entry in the decision transaction. Pass the approver ID explicitly to `leave.applyDecision(requestId, decision, comment, approverId)`. Notify the employee after commit; notification failure must not undo the decision.

**Never:** Let `approvals` write requests or balances; decide for another approver; mutate data on an invalid or repeated transition; implement rejection, routing changes, discussion comments, team leave, or a frontend dashboard.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Invalid transition | Request is not `PENDING` | No decision or balance changes | `409 INVALID_TRANSITION` |
| Wrong approver | Authenticated caller is not assigned | No request or balance changes | `403 ACCESS_DENIED`; log denied access |
| Unauthenticated / unknown | No user, or request ID does not exist | No state changes | Standard `401` / `404` error |

</frozen-after-approval>

## Code Map

- `backend/src/main/java/com/company/leave/approvals/ApprovalController.java`, `ApprovalService.java` — expose the operation, delegate decisions, retain queue behavior and `AccessPolicy`.
- `backend/src/main/java/com/company/leave/leave/LeaveRequestService.java`, `LeaveRequestRepository.java`, `LeaveRequest.java` — lock and transition requests; reuse audit/history and transaction conventions.
- `backend/src/main/java/com/company/leave/balances/BalanceService.java`, `LeaveBalance.java`, `LedgerKind.java` — keep balance/ledger writes in `balances`; deduct atomically.
- `backend/src/main/java/com/company/leave/notifications/NotificationService.java` — reuse best-effort `sendNotification()` after commit; no SMTP.
- `backend/src/test/java/com/company/leave/approvals/ApprovalControllerIntegrationTest.java` — extend queue integration tests for decisions and effects.
- `backend/src/test/java/com/company/leave/common/OpenApiDocsIntegrationTest.java`, `backend/src/main/resources/openapi.yaml` — align API docs and contract.

## Tasks & Acceptance

**Execution:**
- [x] `backend/src/main/java/com/company/leave/leave/LeaveRequestService.java`, `LeaveRequestRepository.java`, `LeaveRequest.java` — add locked pending-only transition and audit.
- [x] `backend/src/main/java/com/company/leave/balances/BalanceService.java`, `LeaveBalance.java` — convert reservation to deduction and write one ledger row atomically.
- [x] `backend/src/main/java/com/company/leave/approvals/ApprovalController.java`, `ApprovalService.java`, decision DTO — expose optional-comment approval and pass actor ID.
- [x] `backend/src/main/java/com/company/leave/notifications/NotificationService.java` — notify after commit without rolling back on failure.
- [x] `backend/src/test/java/com/company/leave/approvals/ApprovalControllerIntegrationTest.java`, `backend/src/test/java/com/company/leave/common/OpenApiDocsIntegrationTest.java` — test success, auth, invalid requests, side effects, and docs.

**Acceptance Criteria:**
- Given a pending request assigned to the authenticated caller, including an HR user's own request when HR is the assigned approver, when approval is submitted with or without a comment, then the `200` response and persisted request record `APPROVED`, the approver, the decision time, and the supplied comment when present.
- Given an approved request, when the transaction commits, then `reserved_days` and `total_days` decrease by its working days, `availableDays` is unchanged, and one `DEDUCT` row records change, actor, request, and time.
- Given an approval, when module ownership is checked, then `approvals` calls `leave.applyDecision(requestId, decision, comment, approverId)`; `leave` writes one audit event and `balances` one ledger entry.
- Given a non-pending request, when approval is submitted, then the response is `409 INVALID_TRANSITION` and no state or balance changes occur.
- Given an authenticated caller who is not the assigned approver, when approval is submitted, then the response is `403 ACCESS_DENIED` and an access-log row records `allowed = false`.
- Given an unauthenticated caller, when approval is submitted, then the response is `401` and no request or balance changes occur.
- Given a successful approval, when the transaction commits, then `LEAVE_REQUEST_APPROVED` is audited and one `APPROVED` notification is attempted after audit/balance writes; failure cannot roll back the decision or contact SMTP.
- Given the endpoint, when generated and static API docs are checked, then both describe the optional comment request and success response consistently.

## Implementation Notes

Use the layered modular-monolith pattern: `approvals` delegates synchronously to `leave`; the owning `leave` and `balances` services write their own state/audit/ledger in the triggering transaction (AD-2, AD-4, AD-5). Register notification delivery after commit and retain `NotificationService` best-effort error logging (AD-12). Add a backend curl guide per the backend context.

Applicable architecture pattern: layered modular monolith (AD-1/AD-2), with `approvals` delegating request state to `leave` and balance/ledger writes to `balances`. Lock the request and balance rows, pass the actor id explicitly, and use post-commit notification delivery (AD-4/AD-12).

## Spec Change Log

## Review Triage Log

- **false — rejected:** The reviewer proposed an additional manager/HR role check. The captured intent authorizes approval by assigned approver ID, and request routing supplies manager/HR IDs; the approval path already requires an authenticated caller and checks the assignment.
- **low — patch applied:** Approval request JSON can fail deserialization with `400`, but the endpoint contract omitted that response. Added the response to generated and static OpenAPI and asserted it in the contract test.
- **low — defer:** The pending-detail status schema allowed non-pending values, although the queue query returns only pending requests. This is a pre-existing Story 3.1 contract mismatch, not introduced by the decision endpoint.
- **low — defer:** The static comment schema omitted required response fields. This is a pre-existing Story 3.1 contract gap.
- **low — defer:** The static history schema did not state oldest-first ordering. The implementation already orders by event time and ID; this is a pre-existing Story 3.1 documentation gap.
- **low — defer:** The legacy audit-comment fallback exposes “Event details were not recorded.” as comment text. This behavior and its test predate Story 3.2; it can misrepresent incomplete historical data as user-authored text.
- **low — patch applied:** The new curl guide described notification recording as guaranteed. Clarified that notification is attempted after commit and may fail without undoing approval.
- **false — rejected:** The guide's no-comment example points back to the earlier runnable submission command and says to submit another request; a second copy of the command is not required.
- **low — defer:** The pending-detail history documentation mentioned decision events even though the endpoint returns pending requests. This wording predates Story 3.2.
- **low — defer:** The user-owned Postman history request still has a generated submission description/body despite its Story 3.1 GET route update; this unrelated collection issue predates Story 3.2.
- **false — rejected:** The edge-case review returned only a generic “input empty or undecodable” record. The diff was non-empty and readable; the response identified no code location or reachable bad outcome.

## Design Notes

`leave` owns decision state, `balances` owns ledger state, and `approvals` owns authorization. Keep request and balance writes atomic; deliver the best-effort notification after commit.

## Verification

**Commands:**
- `cd backend; .\mvnw.cmd -Dtest=ApprovalControllerIntegrationTest,OpenApiDocsIntegrationTest test` — expected: focused integration and OpenAPI tests pass (Docker-backed tests may require Docker availability).
