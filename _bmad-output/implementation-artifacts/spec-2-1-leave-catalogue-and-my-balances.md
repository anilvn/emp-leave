---
title: 'Story 2.1: Leave Catalogue and My Balances'
type: 'feature'
created: '2026-10-23'
status: 'done'
route: 'dispatch'
review_loop_iteration: 1
context: ['{project-root}/backend/AGENTS.md']
baseline_commit: '932a036011015521de95ac418354291a7f6c5270'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** No leave types or balances exist yet. An employee cannot see how many days they have before requesting leave, and later stories (2.2–2.7) cannot reserve, release or deduct days without the tables and services this story provides.

**Approach:** Add `leave_types` and `leave_balances` tables via Flyway, seed the four contractual leave types and a starting balance row per user per type, build `leave.LeaveTypeService` (public read API for reference data) and `balances.BalanceService` (the sole writer of `leave_balances`, AD-5), and expose `GET /api/balances/me` returning `Balance[]` with computed `availableDays`. A user missing a balance row for any type sees zero days rather than nothing.

## Boundaries & Constraints

**Always:** `BalanceService` is the only writer of `leave_balances` (AD-5). `LeaveType` entity/repository stay package-private in `leave`; `LeaveBalance` entity/repository stay package-private in `balances` (AD-2). `LeaveTypeService` is the public read seam for leave types, callable by any module needing the type name. The `CHECK (total_days - reserved_days >= 0)` constraint on `leave_balances` serves as defence in depth. Schema and seed migrations are separate files (NFR7). Surrogate keys `BIGINT GENERATED ALWAYS AS IDENTITY` on `leave_types`; `leave_balances` uses a natural composite PK `(user_id, leave_type_id)` since the API identifies balances by that pair. All `GET /api/balances/me` callers are gated as authenticated via the existing `SecurityFilterChain`. Every seeded user starts with Vacation 20, Sick 10, Parental 30, and Sabbatical 0 days.

**Never:** Give `balances` a write path for `leave_types`. Expose a `balance_ledger` table or write path (Story 2.2). Add HR-only endpoints (`GET /balances`, `PUT`, `POST adjust`, `GET ledger`) — those are Epic 4. Let `BalanceService` derive the actor from `SecurityContext` (AD-4 — pass explicitly when writing, though this story has no write path beyond seed).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Happy path — employee | Authenticated EMPLOYEE, balance rows exist for all 4 types | 200, `Balance[]` with 4 entries: `userId`, `leaveTypeId`, `totalDays`, `reservedDays`, `availableDays = totalDays - reservedDays` | N/A |
| Happy path — manager | Authenticated MANAGER | 200, same shape; `/balances/me` is role-agnostic | N/A |
| Happy path — HR | Authenticated HR_ADMIN | 200, same shape | N/A |
| Missing balance row | User has no `leave_balances` row for a leave type | That type still appears with `totalDays=0`, `reservedDays=0`, `availableDays=0` | N/A |
| Unauthenticated | No `access_token` cookie | n/a | 401 (existing JWT filter) |
| Must-change-password | `mustChangePassword = true` | n/a | 403 `PASSWORD_CHANGE_REQUIRED` (existing filter) |

</frozen-after-approval>



## Code Map

- `backend/src/main/resources/db/migration/` contains V1–V6; `V5__seed_users.sql` seeds 12 users (HR 1, managers 2–4, employees 5–12). `application.yml` sets Hibernate `ddl-auto: validate`; append V7 types, V8 balances, V9 seeds.
- `backend/src/main/java/com/company/leave/leave/` and `balances/` have package docs only. AD-2 assigns `leave_types` to `leave`, `leave_balances` to `balances`; entities/repos stay package-private. Public `LeaveTypeService` returns a public DTO.
- `balances/BalanceService` alone uses `LeaveBalanceRepository` and writes balances. It combines caller rows with all types and fills missing pairs with zero; pass scalar IDs across modules.
- Reuse `security/AccessPolicy.requireRole(...)`, `SecurityConfig`, `PasswordChangeRequiredFilter`; all protected API routes authenticate. Permit EMPLOYEE, MANAGER, HR_ADMIN and use the numeric principal.
- Follow `users/UserController.java`, `UserResponse.java`, `common/GlobalExceptionHandler.java`, `ErrorCode.java`; `openapi.yaml` already documents `/balances/me` and `Balance`.
- `users/UserControllerIntegrationTest.java` shows PostgreSQL 18 Testcontainers, MockMvc, JdbcTemplate and auth helpers. Test callers need `must_change_password=false`. `audit/AuditEventRepository.java` shows minimal repository methods; don't inherit broad deletes.

## Tasks & Acceptance

**Execution:**
- [x] Add `backend/src/main/resources/db/migration/V7__create_leave_types_table.sql`, `V8__create_leave_balances_table.sql`, and separate `V9__seed_leave_types_and_balances.sql`; use BIGINT keys/FKs, composite PK, and the available-days CHECK.
- [x] Add `backend/src/main/java/com/company/leave/leave/` files `LeaveType.java`, `LeaveTypeRepository.java`, `LeaveTypeResponse.java`, and `LeaveTypeService.java`; keep persistence types package-private and expose a public read DTO/service.
- [x] Add `backend/src/main/java/com/company/leave/balances/` files `LeaveBalance.java`, `LeaveBalanceId.java`, `LeaveBalanceRepository.java`, `BalanceResponse.java`, and `BalanceService.java`; keep repository use inside BalanceService and synthesize zeros.
- [x] Add `backend/src/main/java/com/company/leave/balances/BalanceController.java` for authenticated `GET /balances/me`, role-agnostic across the three roles.
- [x] Add `backend/src/test/java/com/company/leave/balances/BalanceControllerIntegrationTest.java` for roles, values, missing rows, auth, seeds, and CHECK; add `backend/curl/story-2.1-leave-catalogue-and-my-balances.md` with runnable success/auth examples.

**Acceptance Criteria:**
- Given migrations are applied, when schema and seeds are inspected, then both owned tables, the CHECK, four types and 48 seeded user/type balances exist, with each user allocated 20/10/30/0 days for Vacation/Sick/Parental/Sabbatical.
- Given an authenticated caller with any supported role, when `GET /api/balances/me` is called through the proxy, then it returns 200 with every type and `userId`, `leaveTypeId`, `totalDays`, `reservedDays`, `availableDays = totalDays - reservedDays`.
- Given a balance row is missing, when that user's balances are read, then the type appears with all day counts zero; unauthenticated and must-change-password callers receive 401 and 403 `PASSWORD_CHANGE_REQUIRED`.
- Given an update would make `total_days - reserved_days < 0`, when applied, then the CHECK rejects it; only `BalanceService` uses its repository and no other package references the balance entity/repository.

## Implementation Notes

Use the layered modular monolith with package-by-feature (AD-1/AD-2). Keep `LeaveType` and its repository package-private and expose type ids and names through the public `LeaveTypeService` DTO seam. Keep balance persistence inside `balances`; `BalanceService` is the only repository consumer and synthesizes zeros from all types. The migration owns initial allocations, and the available-days check backs the service boundary (AD-5).

## Spec Change Log

## Review Triage Log

| # | Source | Claim | Verdict | Evidence | Route |
|---|--------|-------|---------|----------|-------|
| 1 | blind-hunter | `BalanceResponse` omits `leaveTypeName` | false | Spec intent explicitly defines response as `userId, leaveTypeId, totalDays, reservedDays, availableDays`; code matches spec | rejected |
| 2 | blind-hunter | No REST endpoint for leave-type catalogue | false | Spec scopes to `GET /api/balances/me` only; `LeaveTypeService` is a Java-level seam, not REST | rejected |
| 3 | blind-hunter | V9 seed CASE has no ELSE clause | false | Flyway migrations are immutable; V9 cannot gain a fifth type | rejected |
| 4 | blind-hunter | No test for user with zero balance rows | low | Zero-fill logic handles each type independently; 1 vs 4 missing exercises same path | rejected (unlikely + adds complexity) |
| 5 | blind-hunter | @AfterEach hardcodes seed allocations | low | V9 is immutable so values can't drift; fix requires extracting constants | rejected (unlikely + complex fix) |
| 6 | blind-hunter | Postman request hardcodes localhost:8080, cryptic variable | low | Confirmed: cURL import artifact; direct correction | patch (Group A) |
| 7 | blind-hunter | Postman description has \r artifacts | low | Confirmed: literal \r from cURL import; direct deletion | patch (Group A) |
| 8 | blind-hunter | CHECK constraint name narrower than enforcement | low | Fix requires new migration; naming doesn't affect behavior | rejected (unlikely + migration needed) |
| 9 | blind-hunter | @SpringBootTest fully qualified instead of imported | low | Line 38 uses FQN; fix is add import + shorten | patch (Group B) |

## Design Notes

Use the package-by-feature layered modular monolith (AD-1/AD-2): controller calls services; `balances` reads reference data only through `LeaveTypeService`. Since `LeaveType` is package-private, the service returns a public DTO. Build balances from all types plus caller rows; missing rows become zero. Seed by type names, not assumed identity IDs. V7–V9 are additive, forward-only migrations; corrections after application require a later migration.

## Verification

**Commands:**
- `backend\mvnw.cmd -f backend\pom.xml test` — expected: `BUILD SUCCESS`, all existing and new tests pass.
- Cached Maven 3.10.0 with `-Dmaven.repo.local=C:\Users\1772\.m2\repository -f backend\pom.xml test` — `BUILD SUCCESS`; 9 tests, 0 failures, errors, or skips. The wrapper invocation failed in its PowerShell bootstrap (`icm : Cannot index into a null array`) before Maven started.
