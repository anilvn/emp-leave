---
title: 'Story 3.4: The Approval Chain at Every Level'
type: 'feature'
created: '2026-10-25'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'd6188d0e798e993140744ba207d08cc6f49bfb67'
context:
  - '{project-root}/backend/AGENTS.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-company-leave-2026-10-22/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Managers and HR need a complete approval path for their own leave, and managers must not view or decide requests outside their approval scope. HR with no manager is the terminal self-approval case.

**Approach:** Verify and cover the existing manager-based routing end to end at each seeded level, explicitly distinguish HR self-approval in the immutable decision audit details, and ensure out-of-scope decision and request-read attempts are denied and access-logged.

## Boundaries & Constraints

**Always:** Route a request to its user's configured manager; the seeded chain is employee → team manager → top-level manager → HR, with HR whose `manager_id` is null assigned as its own approver. Only the assigned approver may approve or reject. Preserve request and balance state on denied access. For HR self-approval, record the JSON boolean `"selfApproval": true` in the decision's `audit_events.details_json` while retaining the existing `detail` field and history behavior. Verify refusals through the `access_log` database table.

**Never:** Add or alter manager relationships, permit cross-team access or decisions, change approval/rejection or balance semantics, add a migration, or introduce UI, notification, or SMTP work.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Manager-chain submission | Employee, team manager, or top-level manager submits | `201`; `approverId` is respectively direct manager, top-level manager, or HR | N/A |
| HR self-approval | HR with null manager submits and approves own pending request | Request is approved; audit details explicitly identify self-approval | N/A |
| Cross-team decision | Manager approves or rejects another team's request | `403 ACCESS_DENIED`; no decision effects; denied access logged | N/A |
| Cross-team detail/history | Manager requests another team's request or history | `403 ACCESS_DENIED`; no request data returned; denied access logged | N/A |

</frozen-after-approval>

## Code Map

- `backend/src/main/java/com/company/leave/leave/LeaveRequestService.java` -- `submit` already resolves `approverId` from `UserService.getManagerId`, falling back to the submitting user when no manager exists. `applyDecision` owns the transaction and audit write; preserve the existing `detail` JSON used by `history` and add the boolean `selfApproval: true` for an approved request whose employee and approver IDs match. Do not move persistence ownership into `approvals`.
- `backend/src/main/java/com/company/leave/users/UserService.java` and `backend/src/main/resources/db/migration/V5__seed_users.sql` -- manager lookup and seeded four-level chain; reuse them without changing user relationships or schema.
- `backend/src/main/java/com/company/leave/security/AccessPolicy.java` and `backend/src/main/java/com/company/leave/security/AccessLogFilter.java` -- request reads require owner, assigned manager, or HR; forbidden API responses are recorded in `access_log`. Reuse this policy/filter rather than adding controller filters.
- `backend/src/main/java/com/company/leave/approvals/ApprovalService.java` -- approve/reject require the request's assigned approver and delegate to `LeaveRequestService`; preserve this orchestration.
- `backend/src/test/java/com/company/leave/approvals/ApprovalControllerIntegrationTest.java` -- Testcontainers API suite and seeded cast; extend it to verify all four submission/approval levels, self-approval audit details, and cross-team decision denial.
- `backend/src/test/java/com/company/leave/leave/LeaveRequestControllerIntegrationTest.java` -- Testcontainers detail/history authorization and access-log coverage; ensure cross-team manager reads remain denied.
- `backend/curl/story-3.4-the-approval-chain-at-every-level.md` -- add runnable examples for chain routing, HR self-approval, and important authorization failures, following existing story curl guides.

## Tasks & Acceptance

**Execution:**
- [x] `backend/src/main/java/com/company/leave/leave/LeaveRequestService.java` -- write `selfApproval: true` alongside the existing decision detail when an approved request is decided by its owner; preserve history decoding and normal decision audit payloads.
- [x] `backend/src/test/java/com/company/leave/approvals/ApprovalControllerIntegrationTest.java` -- exercise submission, pending queue, and approval through the employee-to-team-manager, team-manager-to-top-level-manager, top-level-manager-to-HR, and HR-self paths; assert the audit marker and denied cross-team approve/reject attempts with no state changes.
- [x] `backend/src/test/java/com/company/leave/leave/LeaveRequestControllerIntegrationTest.java` -- assert out-of-scope manager reads of request detail and history return `403 ACCESS_DENIED` and create denied access-log rows.
- [x] `backend/curl/story-3.4-the-approval-chain-at-every-level.md` -- document runnable success and authorization cases and their expected responses.

**Acceptance Criteria:**
- Given each seeded manager-chain user, when they submit their own leave request, then the stored `approverId` is their configured manager and that approver can retrieve it from the pending queue and decide it.
- Given HR with `manager_id` null, when HR submits and approves their own request, then the approval succeeds and its audit details contain the JSON boolean `"selfApproval": true` without breaking request history.
- Given a manager outside a request's approval scope, when they attempt to approve or reject it, then they receive `403 ACCESS_DENIED`, an `access_log` row records `allowed = false`, and the request, balance, ledger, audit, and notification state is unchanged.
- Given a manager outside the owner's team, when they request the request detail or history, then they receive `403 ACCESS_DENIED`, no protected request data, and an `access_log` row records `allowed = false`.
- Given the approval-chain integration tests, when they run against Testcontainers PostgreSQL, then they verify all four levels as API behavior, including HR self-approval.

## Implementation Notes

Use the layered modular-monolith pattern (AD-1/AD-2): `approvals` authorizes and delegates, `leave` owns request state/history and writes the decision audit, and `security` supplies shared policy and access logging. Keep the immutable audit payload's existing `detail` key so `LeaveRequestService.history` continues to render the decision. No migration is expected.

## Spec Change Log

## Review Triage Log

- **patch** — HR self-rejection lacked an assertion that its audit details remain the ordinary detail-only payload, so removing the approval-only condition could go undetected. Added a Testcontainers integration case verifying the rejection audit JSON contains `detail` and no `selfApproval`; the full targeted suites pass with this assertion.
- **false** — The blind review said HR self-approval history was not asserted. `seededApprovalChainRoutesAndDecidesRequestsAtEveryLevel` fetches HR's history and asserts both the `APPROVED` type and unchanged `detail`.
- **false** — The blind review requested a same-team, unassigned approver test. `ApprovalService` rejects every caller whose ID differs from `approverId`; existing wrong-approver API coverage exercises the same authorization guard, and this story's required rejection scenario is cross-team.
- **false** — The blind review requested same-team, unassigned request-read tests. `AccessPolicy.requireOwnerManagerOrHr` limits access to owner, assigned approver, or HR; the required out-of-team detail/history denial is exercised through the same policy.
- **false** — The blind review claimed access-log assertions were not correlated. Decision tests query by actor, request resource ID, denial flag, and operation; detail/history tests query by actor, exact resource path, and denial flag.
- **false** — The blind review asked whether self-approval is HR-only. The approved decision is self-approval exactly when employee and approver IDs match; HR with no manager is the configured self-approval case, and the identity check accurately marks any self-assigned approval.
- **false** — The blind review said HR routing and queue visibility were not asserted. The chain API test checks HR's submission response has HR as employee and approver, retrieves it from HR's pending queue, approves it, and verifies its audit marker.

## Verification

**Commands:**
- `cd backend; .\mvnw.cmd -Dtest=ApprovalControllerIntegrationTest,LeaveRequestControllerIntegrationTest test` -- expected: both Testcontainers suites pass, including the four-level API flow and denied-access assertions.
