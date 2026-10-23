---
title: 'Story 2.2: Submit a Leave Request Against a Real Balance'
type: 'feature'
created: '2026-10-23'
status: 'done'
route: 'dispatch'
review_loop_iteration: 1
context: ['{project-root}/backend/AGENTS.md']
baseline_commit: '5f2e805'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** No leave request table or submission endpoint exists yet. An employee cannot submit a leave request, and later stories (2.3–2.7) depend on `leave_requests`, `balance_ledger`, and the working-day calculator this story provides. Without the reservation mechanism, balances are static numbers disconnected from actual requests.

**Approach:** Add `leave_requests` and `balance_ledger` tables via Flyway. Build a stateless working-day calculator in `common` (AD-3). Add `POST /api/requests` in the `leave` module that validates dates, computes working days, checks balance availability, inserts the request with status PENDING, reserves days on `leave_balances` via `BalanceService` (AD-5), writes a `balance_ledger` RESERVE row, and records an audit event — all in one transaction. Add a public `UserService.getManagerId(userId)` read seam so `leave` can set `approverId` to the caller's manager without accessing `User` directly (AD-2). Notification is not in scope (story 2.3).

## Boundaries & Constraints

**Always:** `BalanceService` is the sole writer of `leave_balances` and `balance_ledger` (AD-5). `LeaveRequest` entity and repository stay package-private in `leave`; `BalanceLedger` entity and repository stay package-private in `balances` (AD-2). The working-day calculator is a stateless utility in `common` with no entity, repository, or business-rule service (AD-3). The `leave` module writes its own audit row passing the actor id explicitly (AD-4); `balances` writes its own ledger row. Balance check compares requested working days against `totalDays - reservedDays`. The CHECK constraint on `leave_balances` is defence in depth. All dates are `DATE`; all timestamps are `timestamptz`; keys are `BIGINT GENERATED ALWAYS AS IDENTITY` (NFR12). Schema and seed migrations are separate (NFR7). `leave_requests.approver_id` is set to the caller's `manager_id` from the `users` table via `UserService`; HR users with NULL `manager_id` get `approver_id` set to their own id (self-approval path for Epic 3).

**Never:** Expose GET, PUT, cancel, or history endpoints (stories 2.4–2.7). Send notifications (story 2.3). Let `leave` write `leave_balances` or `balance_ledger` directly. Let `balances` write `leave_requests`. Let any module derive the actor from `SecurityContext` inside a called service (AD-4). Add business-rule services or entities to `common` (AD-3).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Happy path — Thu to Tue | Employee with 20 available Vacation days, `startDate=2026-10-29`, `endDate=2026-11-03` | 201, `LeaveRequest` with `workingDays=4`, `status=PENDING`, `reservedDays` increases by 4, RESERVE ledger row, audit event | N/A |
| Zero working days | `startDate=2026-10-31` (Sat), `endDate=2026-11-01` (Sun) | N/A | 400 `VALIDATION_FAILED` "Range yields zero working days" |
| End before start | `endDate` before `startDate` | N/A | 400 `VALIDATION_FAILED` "endDate must be on or after startDate" |
| Insufficient balance | Employee with 2 available days requests 4 working days | N/A | 409 `INSUFFICIENT_BALANCE` detail naming requested, available, total, reserved |
| Existing pending reservation | Employee with 20 total, 10 already reserved (from other pending requests), requests 15 days | N/A | 409 `INSUFFICIENT_BALANCE` (available = 10, not 20) |
| Optional notes omitted | `notes` absent or null | 201, `notes` is null in response | N/A |
| Invalid leave type | `leaveTypeId` references non-existent type | N/A | 400 `VALIDATION_FAILED` |
| Unauthenticated | No `access_token` cookie | N/A | 401 (existing JWT filter) |
| Must-change-password | `mustChangePassword = true` | N/A | 403 `PASSWORD_CHANGE_REQUIRED` (existing filter) |

</frozen-after-approval>

## Code Map

- `backend/src/main/resources/db/migration/` — V1–V9 exist. Append V10 for `leave_requests`, V11 for `balance_ledger`. No seed migration needed for these tables.
- `backend/src/main/java/com/company/leave/leave/` — `LeaveType`, `LeaveTypeRepository`, `LeaveTypeResponse`, `LeaveTypeService` exist (package-private entity/repo, public service/DTO). Add `LeaveRequest` entity, `LeaveRequestRepository`, `RequestStatus` enum, `LeaveRequestCreateRequest` DTO, `LeaveRequestResponse` DTO, `LeaveRequestService`, `LeaveRequestController` following the same pattern.
- `backend/src/main/java/com/company/leave/balances/` — `LeaveBalance` (package-private, `@EmbeddedId LeaveBalanceId`, getters only, no setters), `LeaveBalanceRepository` (extends `Repository`, only `findForUser`), `BalanceService` (read-only `getBalancesForUser`), `BalanceResponse`, `BalanceController`. Add `BalanceLedger` entity, `BalanceLedgerRepository`, `LedgerKind` enum. Extend `LeaveBalance` with a `reserveDays(int delta)` mutator. Extend `LeaveBalanceRepository` with `save()` and `findById()`. Extend `BalanceService` with `reserve(userId, leaveTypeId, days, actorId, requestId)` and (for later stories) `release(...)`.
- `backend/src/main/java/com/company/leave/common/` — `ErrorCode`, `ApiException`, `GlobalExceptionHandler`, `OpenApiConfig`. Add `WorkingDayCalculator` as a static utility class.
- `backend/src/main/java/com/company/leave/users/UserService.java` — public service. Add `Long getManagerId(long userId)` returning the user's `managerId` (or null for top-level HR). This is the read seam `leave` calls to set `approverId`.
- `backend/src/main/java/com/company/leave/audit/AuditService.java` — `recordEvent(entity, entityId, action, actorId, detailsJson)`. Called by `LeaveRequestService` with entity `"LEAVE_REQUEST"`, action `"LEAVE_REQUEST_SUBMITTED"`.
- `backend/src/main/java/com/company/leave/security/AccessPolicy.java` — `requireRole(Role...)` used by the controller to gate submission as any authenticated user.
- `backend/src/test/java/com/company/leave/` — follow `UserControllerIntegrationTest` or `BalanceControllerIntegrationTest` pattern: `@SpringBootTest`, `@AutoConfigureMockMvc`, `@Testcontainers`, `PostgreSQLContainer("postgres:18")` with `@ServiceConnection`, `MockMvc` + `JdbcTemplate`, auth via `SecurityMockMvcRequestPostProcessors.authentication()` with `TestingAuthenticationToken`.

## Tasks & Acceptance

**Execution:**
- [x] `backend/src/main/resources/db/migration/V10__create_leave_requests_table.sql` — create `leave_requests` table with all columns per openapi.yaml `LeaveRequest` schema, FKs to `users` and `leave_types`, status defaulting to `PENDING`.
- [x] `backend/src/main/resources/db/migration/V11__create_balance_ledger_table.sql` — create `balance_ledger` table with `id`, `user_id`, `leave_type_id`, `change_days`, `kind`, `reason`, `actor_id`, `request_id`, `created_at`, FKs to `users`, `leave_types`, `leave_requests`.
- [x] `backend/src/main/java/com/company/leave/common/WorkingDayCalculator.java` — stateless utility: `static int countWorkingDays(LocalDate start, LocalDate end)` returning Mon–Fri count, inclusive, holidays ignored. No Spring bean.
- [x] `backend/src/main/java/com/company/leave/users/UserService.java` — add `public Long getManagerId(long userId)` that returns the user's managerId or throws NOT_FOUND.
- [x] `backend/src/main/java/com/company/leave/balances/` — add `LedgerKind` enum, `BalanceLedger` entity, `BalanceLedgerRepository`; add `reserveDays(int delta)` mutator to `LeaveBalance`; add `save(LeaveBalance)` and `Optional<LeaveBalance> findById(LeaveBalanceId)` to `LeaveBalanceRepository`; add `reserve(long userId, long leaveTypeId, int workingDays, long actorId, long requestId)` to `BalanceService` that checks availability, updates `reserved_days`, and writes a RESERVE ledger row.
- [x] `backend/src/main/java/com/company/leave/leave/` — add `RequestStatus` enum, `LeaveRequest` entity, `LeaveRequestRepository` (package-private), `LeaveRequestCreateRequest` DTO, `LeaveRequestResponse` DTO, `LeaveRequestService` (orchestrates validation, working-day calc, balance reservation, audit), `LeaveRequestController` (POST /api/requests).
- [x] `backend/src/test/java/com/company/leave/leave/LeaveRequestControllerIntegrationTest.java` — test happy path, insufficient balance, end-before-start, zero working days, invalid leave type, audit event written, ledger row written, reserved_days updated; follow existing Testcontainers pattern.
- [x] `backend/curl/story-2.2-submit-leave-request.md` — runnable curl examples for success and each error case.

**Acceptance Criteria:**
- Given migrations are applied, when schema is inspected, then `leave_requests` and `balance_ledger` tables exist with the correct columns, types, FKs, and defaults.
- Given an authenticated employee with sufficient balance, when `POST /api/requests` is called with valid dates, then a 201 response returns a `LeaveRequest` with `workingDays` correctly computed (Thu 2026-10-29 to Tue 2026-11-03 = 4), `status=PENDING`, `approverId` set to the caller's manager, `reserved_days` increased by `workingDays`, a RESERVE `balance_ledger` row written, and an `audit_events` row with action `LEAVE_REQUEST_SUBMITTED`.
- Given insufficient available days, when `POST /api/requests` is called, then 409 `INSUFFICIENT_BALANCE` is returned with detail naming requested, available, total, and reserved days, and no request or ledger rows are written.
- Given `endDate` before `startDate` or a range yielding zero working days, when `POST /api/requests` is called, then 400 `VALIDATION_FAILED` is returned.
- Given the working-day calculator, when called with a weekend-only range, then it returns 0; the calculator is stateless in `common` with no entity or service (AD-3).

## Implementation Notes

New dependency edge: `leave → users` (read-only, for manager lookup via `UserService.getManagerId`). This edge is not listed in the Architecture Spine's AD-2 graph but is required by the acceptance criteria (`approverId = caller's manager_id`). The edge is one-directional and read-only, consistent with AD-2's principle that cross-module reads go through the owning module's `@Service`.

## Spec Change Log

## Review Triage Log

| # | Source | Claim | Verdict | Evidence | Route |
|---|--------|-------|---------|----------|-------|
| 1 | blind-hunter, edge-case-hunter | No pessimistic lock on `BalanceService.reserve()` — concurrent submissions can over-reserve | high | `findById` at line 54 does a plain SELECT; two concurrent transactions read the same `reserved_days`, both pass the availability check, and both increment — DB CHECK prevents corruption but surfaces as 500 instead of 409. | patch (Group A) |
| 2 | blind-hunter | `@Transactional` on `reserve()` should use `Propagation.MANDATORY` | low | Javadoc says "must be called within existing transaction" but default REQUIRED silently starts a new one. Direct correction: one annotation parameter. | patch (Group A) |
| 3 | blind-hunter, verification-gap | HR self-approval path (`managerId=null → approverId=self`) is untested | medium | No test authenticates as HR admin (id=1, manager_id=NULL). The branch at line 71 of `LeaveRequestService` is exercised only in production. Pre-verified by verification-gap layer. | patch (Group B) |
| 4 | verification-gap | `BalanceService.reserve()` "no balance row" error path is untested | low | `orElseThrow` at line 55-57 of `BalanceService` is never triggered in any test. Pre-verified. Direct test addition. | patch (Group B) |
| 5 | blind-hunter | Postman request body `endDate` disagrees with curl examples | false | Both `description` and `body.content` in the Postman file use `"2026-11-03"`, matching the curl doc. Reviewer misread the diff. | rejected |
| 6 | blind-hunter, edge-case-hunter | No input-length constraint on `notes` field (`@Size`) | low | `notes` is `TEXT` with no max. Spec and openapi.yaml specify no length limit. Adding `@Size` introduces a new validation constraint not in the contract. | rejected (adds spec-undefined constraint) |
| 7 | blind-hunter | `WorkingDayCalculator` day-by-day iteration with no range cap (DoS) | low | Max range yields ~29K iterations (microseconds). No practical performance concern. Fix adds cap or algorithm rewrite. | rejected (unlikely + complexity) |
| 8 | blind-hunter | No test for Bean Validation failures (`@Valid`/`@NotNull` path) | low | `GlobalExceptionHandler` handles `MethodArgumentNotValidException` → 400 `VALIDATION_FAILED`. This framework path is tested by prior stories. | rejected (unlikely + complexity) |
| 9 | blind-hunter, edge-case-hunter | No overlapping-date-range check (double-booking) | false | Spec and openapi.yaml do not require overlap detection. The story's intent is balance reservation; overlap detection is a separate feature. Balance arithmetic is correct — overlapping ranges consume correct day counts. | rejected |
| 10 | blind-hunter | Audit event `detailsJson` is null (incomplete audit) | false | Spec requires only action, actor, and timestamp. `null` detailsJson matches `UserService.createUser` convention and is consistent with AD-4. | rejected |
| 11 | blind-hunter | `Instant.now()` called directly rather than through injectable `Clock` | low | Both `AuditService` and `SessionRegistry` use `Instant.now()` — established codebase convention. Fix requires adding `Clock` bean across multiple classes. | rejected (unlikely + complexity) |
| 12 | edge-case-hunter | `startDate` in the past not validated | false | Neither spec nor openapi.yaml forbids past dates. Retroactive leave (e.g. logging a sick day) is a legitimate use case. | rejected |

**Grouped survivors:**

- **Group A** (high): Race condition + transaction propagation — `LeaveBalanceRepository.findById` needs `@Lock(PESSIMISTIC_WRITE)`, `BalanceService.reserve()` needs `Propagation.MANDATORY`. → **patch**
- **Group B** (medium): Missing test coverage for HR self-approval path and no-balance-row error path. → **patch**

## Design Notes

`BalanceService.reserve()` is transactional. `LeaveRequestService.submit()` calls `BalanceService.reserve()` within its own `@Transactional` method, ensuring the request insert, balance update, ledger row, and audit event all commit or roll back together (AD-4). The balance check and the reservation happen inside `BalanceService` so the CHECK constraint acts as defence in depth, not primary enforcement.

For HR users with `manager_id = NULL`, set `approver_id` to the user's own id — the self-approval path is exercised by Epic 3 (story 3.4) but the routing must be correct from submission.

## Verification

**Commands:**
- `backend\mvnw.cmd -f backend\pom.xml test` — expected: BUILD SUCCESS, all existing and new tests pass.
