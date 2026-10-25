# Epic 4 Context: HR Entitlement Control and Audit Oversight

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

HR can govern leave entitlements and answer any question about what happened. After this epic, HR views every user's balance for every leave type (with optional per-user filtering), sets or adjusts any balance with a mandatory reason that is recorded in an immutable ledger, and pulls a complete audit report per leave request — any status — with the request's full comment thread and History timeline nested in one call. The epic closes with a readiness story that proves all 15 PRD acceptance scenarios pass on the seeded demo cast.

## Stories

- Story 4.1: HR Views Every Balance
- Story 4.2: HR Sets a Balance with a Required Reason
- Story 4.3: HR Adjusts a Balance Up or Down
- Story 4.4: The Ledger Behind a Balance
- Story 4.5: The HR Audit Report, One Entry per Request
- Story 4.6: POC Demo Readiness

## Requirements & Constraints

**Functional:**
- FR27: `GET /api/balances` (optional `?userId=` filter) returns all balances with no scope restriction for HR.
- FR28: `PUT /api/balances/{userId}/{leaveTypeId}` sets `total_days`; `reason` is mandatory.
- FR29: `POST /api/balances/{userId}/{leaveTypeId}/adjust` adds/removes days with a mandatory `reason`; `changeDays=0` is refused.
- FR30: Every balance change writes a `balance_ledger` row (kind `SET`, `ADJUST`, `RESERVE`, `RELEASE`, or `DEDUCT`); ledger is readable via `GET /api/balances/{userId}/{leaveTypeId}/ledger` oldest-first.
- FR31 (re-verification): A set or adjustment that would make `total_days - reserved_days < 0` is refused with 409 `INSUFFICIENT_BALANCE`.
- FR42–FR45 (re-verification from HR vantage): `GET /api/audit` returns one `AuditRequestReport` per leave request, any status, newest submission first, with full `comments` thread and `history` timeline nested; HR-only.

**Non-functional:**
- All endpoints enforce HR_ADMIN role via `AccessPolicy.requireRole(HR_ADMIN)`; EMPLOYEE and MANAGER callers receive 403 `ACCESS_DENIED` with an `access_log` row at `allowed=false`.
- Every balance mutation writes both a `balance_ledger` row and an `audit_events` row in the same transaction, written by `BalanceService` alone.
- `GET /api/audit` is hosted in the `leave` module (not `audit`) to avoid making `audit` a non-leaf; the endpoint stays at `/api/audit` with the swagger `Audit` tag and `AuditRequestReport` shape unchanged.

## Technical Decisions

**`BalanceService` is the sole writer of `leave_balances` and `balance_ledger`** (AD-5). No controller or other service writes these tables directly. The `CHECK (total_days - reserved_days >= 0)` constraint is defence-in-depth behind the service-layer check.

**Actor id is passed explicitly** (AD-4). `BalanceService` receives the acting HR user's id as a parameter; it never reads `SecurityContext` internally to derive it.

**`AccessPolicy` shapes** (AD-10): Epic 4 uses only `requireRole(HR_ADMIN)` — the scope shapes (`EmployeeOwn`, `ManagerTeam`, `HrAll`) are not needed for write operations that are HR-only with no per-user scope.

**`audit` stays a leaf** (AD-2). `GET /api/audit` lives in `leave`, which already has a permitted `leave → audit` edge for reading `audit_events` through `AuditService`. `audit` never reads `leave_requests` or `request_comments`.

**Error codes** (AD-11): No new `ErrorCode` values are needed for this epic. All refusals use existing codes: `VALIDATION_FAILED` (missing/blank reason, zero changeDays, negative totalDays), `INSUFFICIENT_BALANCE` (set/adjust below reserved), `ACCESS_DENIED` (non-HR caller), `NOT_FOUND` (unknown userId or leaveTypeId).

**Ledger kinds**: `LedgerKind` enum already carries `RESERVE`, `RELEASE`, `DEDUCT` from earlier epics. Epic 4 adds `SET` and `ADJUST` as new values (verify `openapi.yaml` enum and `LedgerKind` Java enum are extended together).

**`GET /api/balances` for HR re-uses the existing `BalanceResponse` shape** — same DTO as `/api/balances/me`. No new response type.

## Cross-Story Dependencies

- Stories 4.2, 4.3, and 4.4 all depend on `balance_ledger` existing (created by Story 2.2). No migration is added in Epic 4 for that table.
- Story 4.4 (ledger read) is naturally tested alongside 4.2 and 4.3; they share the same Testcontainers test class.
- Story 4.5 (`GET /api/audit`) depends on all earlier epics having populated `audit_events`, `request_comments`, and `leave_requests` — only meaningful with a seeded dataset, so its integration test uses the demo cast.
- Story 4.6 depends on all other stories in epics 1–4 being done; it verifies the full suite and all 15 PRD acceptance scenarios.
