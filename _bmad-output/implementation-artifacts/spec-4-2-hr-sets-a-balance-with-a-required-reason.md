---
title: 'Story 4.2: HR Sets a Balance with a Required Reason'
type: 'feature'
created: '2026-10-25'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
baseline_commit: '17733f5'
context:
  - '{project-root}/backend/AGENTS.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** HR can view all balances (Story 4.1) but cannot change them. FR28 requires an HR admin to be able to set a user's `total_days` for any leave type with a mandatory reason recorded in the ledger.

**Approach:** Add `PUT /api/balances/{userId}/{leaveTypeId}` to the existing `BalanceController`. Add a `SetBalanceRequest` DTO with `totalDays` (min 0, required) and `reason` (not blank, required). Add `setBalance(userId, leaveTypeId, totalDays, reason, actorId)` to `BalanceService` — upsert the balance row with pessimistic lock, enforce the service-level constraint, write a `balance_ledger` row of kind `SET` and an `audit_events` row in the same transaction. Inject `AuditService` into `BalanceService` (new `balances → audit` edge, intentional per epic-4-context AD-4/AD-5).

</frozen-after-approval>

## Implementation Notes

- Added package-private constructor `LeaveBalance(LeaveBalanceId id, int totalDays)` and `void setTotalDays(int newTotal)` method to `LeaveBalance`.
- Injected `AuditService` into `BalanceService`; added `@Transactional setBalance(...)` method (owns its own transaction — controller initiates).
- Added `@PutMapping("/{userId}/{leaveTypeId}")` to `BalanceController` guarded by `requireRole(HR_ADMIN)`.
- Added 5 test cases to `BalanceControllerIntegrationTest`.
