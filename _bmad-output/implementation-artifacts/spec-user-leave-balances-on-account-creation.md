---
title: 'Create Leave Balances When Users Are Inserted'
type: 'feature'
created: '2026-10-27'
status: 'in-progress'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: 'b01c30ded24a633907a1a0a83e0d5eef5b6ee082'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `V9__seed_leave_types_and_balances.sql` initializes balances only for accounts present when that migration runs. Accounts created later have no `leave_balances` rows and cannot reserve leave.

**Approach:** Add a PostgreSQL trigger that inserts one balance row for every leave type whenever any user row is inserted, using the requested defaults: Vacation 20, Sick 10, Parental 10, Sabbatical 5 days. In the same migration, add missing user/type pairs for existing accounts without changing balances already present.

## Boundaries & Constraints

**Always:** Apply to users of every role. Set `reserved_days` to zero. Use `ON CONFLICT DO NOTHING` so existing entitlement and reservation values are preserved. Keep the existing `V9` file unchanged; add a new Flyway migration after `V13`.

**Never:** Change existing balances, modify application user-creation logic to duplicate this database behavior, or add balance ledger entries for initial provisioning.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Insert a user directly in SQL | Valid new `users` row; all four seeded leave types exist | The insert trigger creates four `leave_balances` rows with totals 20, 10, 10, 5 and zero reserved days | The user insert fails transactionally if balance initialization fails |
| Apply migration to an existing database | Some users lack some or all leave-type balances | Missing pairs are inserted with the requested defaults; existing pairs and values remain unchanged | Migration fails visibly if schema/data constraints are violated |

</frozen-after-approval>

## Code Map

- `backend/src/main/resources/db/migration/V9__seed_leave_types_and_balances.sql` -- current default values and leave-type names; preserve this user-edited file and use its current requested values in the new migration.
- `backend/src/main/resources/db/migration/V13__create_request_comments_table.sql` -- latest migration; add the next migration version after it.
- `backend/src/main/resources/db/migration/V3__create_users_table.sql` -- PostgreSQL users table and identity details used by the trigger.
- `backend/src/main/resources/db/migration/V8__create_leave_balances_table.sql` -- composite `(user_id, leave_type_id)` primary key and `reserved_days` default/check constraints.
- `backend/src/main/resources/db/migration/V2__add_audit_immutability_trigger.sql` -- existing PostgreSQL PL/pgSQL trigger style.
- `backend/src/test/java/com/company/leave/CompanyLeaveApplicationTests.java` -- Spring Boot/Flyway/Testcontainers PostgreSQL integration-test pattern; use an isolated integration test for a direct SQL insert to avoid relying on mutable controller-test fixtures.

## Tasks & Acceptance

**Execution:**
- [x] `backend/src/main/resources/db/migration/V14__create_user_leave_balances_trigger.sql` -- create a PostgreSQL trigger function and `AFTER INSERT` trigger that provisions all four default balance rows; backfill missing pairs for existing users without overwriting existing rows.
- [x] `backend/src/test/java/com/company/leave/balances/UserLeaveBalanceTriggerIntegrationTest.java` -- verify a direct SQL insert for a new user creates exactly one row per leave type with the specified totals and zero reserved days.

**Acceptance Criteria:**
- Given the Flyway migration has run, when any role of user is inserted directly into `users`, then four corresponding balance rows are committed with Vacation 20, Sick 10, Parental 10, Sabbatical 5 and zero reserved days.
- Given users already exist when the migration runs, when a balance pair already exists, then its total and reserved days are unchanged; when a pair is missing, then only that pair is initialized with the requested default.

## Implementation Notes

## Spec Change Log

## Review Triage Log
