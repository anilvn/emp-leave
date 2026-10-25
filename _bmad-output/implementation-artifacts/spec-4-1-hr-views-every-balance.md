---
title: 'Story 4.1: HR Views Every Balance'
type: 'feature'
created: '2026-10-25'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
baseline_commit: '82d14ea'
context:
  - '{project-root}/backend/AGENTS.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** HR has no way to view all users' leave balances in a single call. The existing `GET /api/balances/me` is employee-scoped; HR needs an unrestricted view to answer entitlement questions.

**Approach:** Add `GET /api/balances` (HR_ADMIN only) to the existing `BalanceController`, accepting an optional `?userId=` filter. Extend `BalanceService` with `getAllBalances(Long userId)` and add a `findAll()` query to `LeaveBalanceRepository`. Reuse the existing `BalanceResponse` DTO.

</frozen-after-approval>

## Implementation Notes

- Added `findAll()` JPQL query to `LeaveBalanceRepository` (package-private, alongside `findForUser`).
- Added `getAllBalances(Long userId)` to `BalanceService` — both filtered and unfiltered paths use the same raw-row mapping (no zero-fill), keeping behaviour consistent. Filtered delegates to `findForUser`, unfiltered to `findAll`.
- Added `@GetMapping` (no path suffix) to `BalanceController` guarded by `requireRole(HR_ADMIN)`; existing `/me` handler unchanged.
- Added 5 test cases to the existing `BalanceControllerIntegrationTest`.
- Patched review finding: filtered path was previously delegating to `getBalancesForUser` which zero-fills missing types — inconsistent with the unfiltered path. Fixed to map raw rows directly in both paths.
- Also fixed pre-existing test failure in `CompanyLeaveApplicationTests` (missing `request_comments` from table list, left by Story 3.5).

## Review Triage Log

- **medium → patched** — `getAllBalances` filtered path called `getBalancesForUser` which zero-fills missing leave types, while unfiltered path returned only actual rows. Fixed: both paths now map raw rows without zero-fill.
- **low → rejected** — `hrAdminGetsAllBalancesForAllUsers` count assertion brittle. Correct for seeded data; not worth adding per-user type checking to a happy-path smoke test.
- **low → patched** — `@Tag` description was stale ("authenticated user's leave balances"). Updated.
- **low → patched** — `CompanyLeaveApplicationTests` Javadoc was stale (didn't mention Story 3.5 / `request_comments`). Updated.

