---
title: 'Story 3.3: Reject a Request with a Required Comment'
type: 'feature'
created: '2026-10-25'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'c1dc4b62b87060ff580b9ce5bdd043db5bd6fc8a'
context:
  - '{project-root}/backend/AGENTS.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** An assigned approver needs to reject a pending leave request with a reason employees can see, while releasing the reserved leave days.

**Approach:** Add the rejection operation alongside approval, requiring a non-blank comment. Record the decision, release the reservation, add the rejection to request history, and attempt the existing rejection notification after commit.

## Boundaries & Constraints

**Always:** Only the request's assigned approver may decide it, and only while it is `PENDING`. Missing, null, empty, or whitespace-only rejection comments return `400 COMMENT_REQUIRED` and make no changes. Persist the supplied comment, approver, and decision time; write one `LEAVE_REQUEST_REJECTED` audit event and one `RELEASE` ledger entry in the decision transaction. Release reserved days without changing total entitlement. Attempt a `REJECTED` notification after commit; notification failure must not undo the decision.

**Never:** Make approval comments required or change approval behavior; allow cross-approver decisions or repeated transitions; let `approvals` write request or balance state; add a database migration, email delivery, routing changes, or frontend dashboard work.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Successful rejection | Assigned approver rejects pending request with non-blank comment | `200`; request is `REJECTED`, reason is stored, reservation released, history/audit and one `RELEASE` entry recorded, notification attempted after commit | N/A |
| Missing or blank reason | Missing body, null, empty, or whitespace-only comment | No request, balance, ledger, audit, or notification change | `400 COMMENT_REQUIRED` |
| Invalid decision | Request is not `PENDING` | No additional effects | `409 INVALID_TRANSITION` |
| Unauthorized or unknown | Wrong approver, unauthenticated caller, or unknown request | No request or balance changes | `403 ACCESS_DENIED` with denied-access logging; `401`; or `404` |

</frozen-after-approval>

## Code Map

- `backend/src/main/java/com/company/leave/approvals/ApprovalController.java`, `ApprovalService.java`, `ApprovalDecisionRequest.java`, `RejectDecisionRequest.java` -- add the reject endpoint and assigned-approver orchestration. Keep approval's comment optional; validate rejection independently.
- `backend/src/main/java/com/company/leave/leave/LeaveRequestService.java`, `LeaveRequest.java`, `LeaveRequestDecision.java`, `RequestStatus.java` -- reuse the locked transactional decision path; add rejected state and audit/history behavior.
- `backend/src/main/java/com/company/leave/balances/BalanceService.java`, `LedgerKind.java` -- release the reservation through the owning balances service; preserve entitlement and use an accurate rejection ledger reason.
- `backend/src/main/java/com/company/leave/notifications/NotificationService.java`, `NotificationKind.java` -- reuse best-effort `REJECTED` delivery after commit.
- `backend/src/main/resources/openapi.yaml`, `backend/src/test/java/com/company/leave/common/OpenApiDocsIntegrationTest.java` -- align static and generated reject contracts without changing optional approval semantics.
- `backend/src/test/java/com/company/leave/approvals/ApprovalControllerIntegrationTest.java` -- cover decision effects, validation, authorization, transitions, and notification failure.
- `backend/curl/story-3.2-approve-a-request-with-an-optional-comment.md` -- follow its runnable curl conventions for the new Story 3.3 guide.

## Tasks & Acceptance

**Execution:**
- [x] `backend/src/main/java/com/company/leave/approvals/ApprovalController.java`, `ApprovalService.java`, `RejectDecisionRequest.java` -- expose rejection for the assigned approver and return `COMMENT_REQUIRED` for an absent or blank reason without affecting approval.
- [x] `backend/src/main/java/com/company/leave/leave/LeaveRequestService.java`, `LeaveRequest.java`, `LeaveRequestDecision.java` -- implement the pending-only rejected transition, persisted comment, audit event, and history entry in the existing transaction.
- [x] `backend/src/main/java/com/company/leave/balances/BalanceService.java` -- release reserved days atomically without changing entitlement; record a rejection-accurate `RELEASE` ledger entry.
- [x] `backend/src/main/java/com/company/leave/leave/LeaveRequestService.java`, `backend/src/main/java/com/company/leave/notifications/NotificationService.java` -- attempt the existing `REJECTED` notification after commit and preserve committed rejection if delivery fails.
- [x] `backend/src/main/resources/openapi.yaml`, `backend/src/test/java/com/company/leave/common/OpenApiDocsIntegrationTest.java` -- document and assert required non-blank rejection comment and endpoint responses, while approval remains optional.
- [x] `backend/src/test/java/com/company/leave/approvals/ApprovalControllerIntegrationTest.java`, `backend/curl/story-3.3-reject-a-request-with-a-required-comment.md` -- verify success, invalid inputs, authorization, transitions, side effects, and runnable usage examples.

**Acceptance Criteria:**
- Given a pending request assigned to the authenticated caller, when the caller rejects it with a non-blank reason, then the response and stored request show `REJECTED`, the assigned approver, decision time, and supplied reason.
- Given a successful rejection, when the transaction commits, then reserved days decrease by the request's working days, total entitlement is unchanged, and available days increase by that amount.
- Given a successful rejection, when module ownership is enforced, then `leave` records one rejection audit/history event and `balances` records one `RELEASE` ledger entry; the `REJECTED` notification is attempted after commit and cannot roll back the decision.
- Given a missing, null, empty, or whitespace-only reason, when rejection is submitted, then the response is `400 COMMENT_REQUIRED` and no decision, balance, ledger, audit, or notification effect is recorded.
- Given a non-pending request, wrong approver, unauthenticated caller, or unknown request, when rejection is submitted, then the response is respectively `409 INVALID_TRANSITION`, `403 ACCESS_DENIED`, `401`, or `404`, with no decision side effects; denied access is logged.
- Given the reject endpoint and the existing approve endpoint, when generated and static API contracts are checked, then rejection requires a non-blank reason and approval continues to accept an omitted comment.

## Implementation Notes

Use the layered modular-monolith pattern: `approvals` authorizes and delegates, `leave` owns request state and audit/history, and `balances` owns balance and ledger writes. Pass the actor explicitly and keep request/balance changes atomic. Reuse the existing after-commit, best-effort notification path; do not contact SMTP.

Use a rejection-specific request schema so rejection requires a non-blank reason while approval remains optional. Validate the reason in the transactional decision path after request, approver, and pending-state checks so existing authorization and transition errors retain precedence. Supply the balance release reason explicitly to distinguish rejection from cancellation.

The focused approval and OpenAPI integration suites passed after a clean build. The curl guide is added but was not run against the local Compose stack because `JWT_SECRET` is unset.

## Spec Change Log

## Review Triage Log

- low -- patched: A null `LeaveRequestDecision` could fall through to rejection and persist a null reason. The decision service now rejects null/unsupported enum values before any mutation, and an integration test verifies request, balance, ledger, audit, and notification remain unchanged.
- low -- patched: The shared conflict detail had changed for existing approvals. Approval retains its prior `can no longer be approved` response, rejection now has rejection-specific wording, and tests assert both details.
- low -- patched: The generated reject operation documented an incomplete 401 response shape. The response now declares `ProblemDetail`, and the generated API test asserts its schema.
- low -- patched: The success test did not verify that the employee can read their rejection reason. It now fetches the request as its owner and asserts `REJECTED` and `decisionComment`.
- low -- patched: The curl guide did not show how to create the pending request used for validation, and reused that pending id for the invalid-transition example. It now creates a separate validation request and retries the already-rejected id.
- false -- rejected: The matrix's `Invalid decision` label is followed by the explicit `not PENDING` state and `409 INVALID_TRANSITION`; no unsupported decision input or behavioral ambiguity is asserted.
- false -- rejected: The authorization/unknown matrix row is not the sole contract; its acceptance criterion explicitly requires no decision side effects, and integration tests assert no ledger, audit, or notification rows.
- low -- patched: The edge-case review independently identified the null-decision fall-through. The same guard and no-mutation integration test address it.
- low -- patched: The edge-case review independently identified the approval conflict-detail regression. Existing approval wording is restored and tested.
- low -- patched: The verification-gap review identified the missing conflict-detail assertion. Tests now verify the rejection-specific detail and preservation of the existing approval detail.

## Verification

**Commands:**
- `cd backend; .\\mvnw.cmd clean -Dtest=ApprovalControllerIntegrationTest,OpenApiDocsIntegrationTest test` -- passed: 25 tests, 0 failures/errors.
