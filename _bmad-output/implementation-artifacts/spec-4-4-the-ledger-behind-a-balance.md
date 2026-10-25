---
title: 'Story 4.4: The Ledger Behind a Balance'
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

**Problem:** HR can set and adjust balances (Stories 4.2–4.3) but has no API to read the full ledger trail — they cannot explain how a balance reached its current figure without querying the database directly.

**Approach:** Add `GET /api/balances/{userId}/{leaveTypeId}/ledger` to `BalanceController`, backed by a new `getLedger(userId, leaveTypeId)` method on `BalanceService`. Add a read query to `BalanceLedgerRepository` ordered by `created_at ASC`. Create a `BalanceLedgerEntryResponse` DTO matching the `BalanceLedgerEntry` OpenAPI schema. Enforce `HR_ADMIN` role via `AccessPolicy.requireRole(HR_ADMIN)`.

</frozen-after-approval>

## Implementation Notes

- Created `BalanceLedgerEntryResponse` DTO (`@Value @Builder`, `@Schema(name="BalanceLedgerEntry")`) matching the openapi schema exactly; `requestId` is `Long` (nullable), `createdAt` is `Instant`.
- Added `findByUserIdAndLeaveTypeIdOrderByCreatedAtAsc` to `BalanceLedgerRepository` (Spring Data derived query).
- Added `getLedger(long userId, long leaveTypeId)` to `BalanceService` (`@Transactional(readOnly=true)`) — streams entity → DTO; returns empty list for unknown user/type pairs (consistent with other balance endpoints).
- Added `GET /{userId}/{leaveTypeId}/ledger` to `BalanceController` with `accessPolicy.requireRole(HR_ADMIN)`.
- Added `delete from balance_ledger` to `@BeforeEach` in `BalanceControllerIntegrationTest` to ensure clean ledger state; safe because no existing test relies on pre-existing ledger rows.
- Added 5 integration tests: happy path (SET+ADJUST ordering, changeDays, null requestId), empty array, EMPLOYEE 403, MANAGER 403, unauthenticated 401.
- Review patches: updated BalanceLedgerRepository Javadoc, added `example` to `kind` @Schema, added 401 @ApiResponse, added `$[0].changeDays` and `requestId` null assertions to happy-path test.

## Spec Change Log

## Review Triage Log

- **low/patch**: BalanceLedgerRepository Javadoc still read "Insert-only" after a read query was added. Fixed: updated to "insert only and ledger reads by user/leave type."
- **low/patch**: Missing `@ApiResponse(401)` on ledger endpoint. Fixed: added alongside the existing 403 annotation.
- **false**: 200 @ApiResponse no content/schema. Not a defect — `getAllBalances`, `setBalance`, and `adjustBalance` all follow the same pattern of omitting schema annotation on the 200 response in this controller.
- **low/patch**: `hrCanGetLedgerForUserAndLeaveType` missing `$[0].changeDays` assertion. Fixed: added `.andExpect(jsonPath("$[0].changeDays").value(5))`.
- **defer**: No test for non-existent userId/leaveTypeId returns empty. Consistent with all other balance read endpoints. Added to deferred-work.md.
- **low/patch**: `@Schema` on `kind` field lacked `example`. Fixed: added `example = "ADJUST"`.
- **low/patch**: No `requestId` null assertion in happy-path test. Fixed: added `.andExpect(jsonPath("$[0].requestId").value(Matchers.nullValue()))` for both entries.
- **low/reject**: curl doc missing 401 example. Story 4.2 curl guide and other guides omit it too; the unauthenticated case is fully covered by the integration test.
- **defer**: No paging/limit on `getLedger`. Not in openapi spec or FR30 requirements. Added to deferred-work.md.
