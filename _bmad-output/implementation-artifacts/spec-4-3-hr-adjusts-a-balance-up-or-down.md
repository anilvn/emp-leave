---
title: 'Story 4.3: HR Adjusts a Balance Up or Down'
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

**Problem:** HR can set a balance to an absolute value (Story 4.2) but cannot add or remove days relative to the current total — FR29 requires a signed delta endpoint with a mandatory reason that is recorded in the immutable ledger.

**Approach:** Add `POST /api/balances/{userId}/{leaveTypeId}/adjust` to `BalanceController`. Add an `AdjustBalanceRequest` DTO (`changeDays` non-null, `reason` not-blank). Add `adjustBalance(userId, leaveTypeId, changeDays, reason, actorId)` to `BalanceService` — reject zero `changeDays` with `VALIDATION_FAILED`, upsert the balance row, enforce `total_days - reserved_days >= 0` and `total_days >= 0` with `INSUFFICIENT_BALANCE`, write an `ADJUST` ledger row and a `LEAVE_BALANCE_ADJUSTED` audit event in the same transaction, return the updated `BalanceResponse`.

</frozen-after-approval>

## Implementation Notes

- Added `AdjustBalanceRequest` DTO (new file) with `@NotNull changeDays` and `@NotBlank reason`, schema name `"BalanceAdjust"` matching the existing OpenAPI definition.
- Added `adjustBalance(...)` to `BalanceService` — rejects `changeDays == 0` with `VALIDATION_FAILED`, uses `orElse(new LeaveBalance(balanceId, 0))` like `setBalance`, checks `newTotal < 0 || newTotal < reservedDays` → 409, writes `ADJUST` ledger row and `LEAVE_BALANCE_ADJUSTED` audit event.
- Added `POST /{userId}/{leaveTypeId}/adjust` to `BalanceController` with `@PostMapping` import.
- Added 8 integration tests (6 core + 2 from review patches) to `BalanceControllerIntegrationTest`.
- Both ledger-verification tests filter by `change_days` value to avoid `queryForMap` ambiguity when multiple ADJUST rows accumulate across tests.
- OpenAPI `openapi.yaml` and `LedgerKind.java` required no changes — both already had `ADJUST`.

## Review Triage Log

- **medium/patch**: up-test ledger query lacked `change_days = 5` filter — order-dependent test failure if down-test ran first. Fixed: added `change_days = 5` to the SQL predicate.
- **low/patch**: down-test missing `userId`, `leaveTypeId`, `reservedDays` assertions. Fixed: added all three.
- **low/patch**: no 401 test for unauthenticated POST `/adjust`. Fixed: added `unauthenticatedCallerGets401ForAdjustBalance`.
- **low/patch**: no test for null/missing `changeDays` → 400. Fixed: added `adjustBalanceWithNullChangeDaysGets400`.
- **maybe-false/defer**: `adjustBalance` on a user with no balance row is untested — `orElse(new LeaveBalance(balanceId, 0))` creates one. Same gap exists in `setBalance` (story 4.2), not caused by this change. Deferred to `deferred-work.md`.

