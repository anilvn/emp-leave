---
title: 'Story 1.6: HR Creates Accounts with a Role and a Manager'
type: 'feature'
created: '2026-10-23'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: 'bf9a20e7a0785d0ebed13c3a6f3b1706f3e08174'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** HR has no way to onboard a new joiner: there is no endpoint to create an account with a role, a manager and a temporary password, and no endpoint to list accounts or manager options for the create-account form.

**Approach:** Add a `users.UserController` exposing `POST /users`, `GET /users` and `GET /users/managers`, gated to `HR_ADMIN` via the existing `AccessPolicy`, backed by new `UserService` methods that validate the role/manager rule, BCrypt-hash the temporary password, and write one `audit_events` row per creation. Follow the existing Lombok conventions exactly.

## Boundaries & Constraints

**Always:** Gate all three endpoints with `accessPolicy.requireRole(Role.HR_ADMIN)` before any business logic, mirroring `AuthController.logout`. Rely on the existing `AccessLogFilter` for the FR21/FR25/FR26 access-log rows — do not log access decisions separately. BCrypt-hash `temporaryPassword` via the existing `PasswordEncoder` bean and never pass it to any logging or audit call. Set `mustChangePassword=true` and `active=true` on every created account. Write the `audit_events` row inside `UserService`'s own `@Transactional` method, passing the HR caller's id explicitly (mirror `AuthController.login`'s `auditService.recordEvent(...)` call), never derived from `SecurityContextHolder` inside `audit`. New DTOs follow existing Lombok conventions exactly: `@Value @Builder` for responses (like `UserAccount`), plain `@Value` with `jakarta.validation` annotations for the request (like `LoginRequest`).

**Never:** Add an access-log read API/UI. Enforce temporary-password complexity (length, letters+numbers) in this story — only non-blank is required; Story 1.7 owns that rule for `change-password`. Reimplement role or ownership checks outside `AccessPolicy`. Touch `/auth/change-password` or password-reset (Story 1.7 scope).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Create EMPLOYEE/MANAGER without `managerId` | `role=EMPLOYEE`, `managerId=null` | n/a | 400 `VALIDATION_FAILED` |
| Create HR_ADMIN without `managerId` | `role=HR_ADMIN`, `managerId=null` | 201, `managerId` null | N/A |
| `managerId` points to a non-manager | `role=EMPLOYEE`, `managerId=5` (an `EMPLOYEE`) | n/a | 400 `VALIDATION_FAILED` |
| `managerId` points to a nonexistent user | `managerId=9999` | n/a | 400 `VALIDATION_FAILED` |
| Duplicate username | username already in use | n/a | 400 `VALIDATION_FAILED`, no row created |
| Non-HR caller on any of the 3 endpoints | caller role `EMPLOYEE` or `MANAGER` | n/a | 403 `ACCESS_DENIED` |

</frozen-after-approval>

## Code Map

- `backend/src/main/java/com/company/leave/users/User.java` -- package-private entity; builder already supports `role`/`managerId`/`mustChangePassword`/`active`; reuse as-is.
- `backend/src/main/java/com/company/leave/users/UserAccount.java` -- login-only `@Value @Builder` DTO matching the contract's `CurrentUser` schema (no `managerId`/`active`); do not reuse for `/users` responses — add a new DTO instead.
- `backend/src/main/java/com/company/leave/users/UserRepository.java` -- minimal `Repository<User,Long>`; add `existsByUsername`, `findAll`, `findByRoleIn`.
- `backend/src/main/java/com/company/leave/users/UserService.java` -- add `createUser`/`listUsers`/`listManagerOptions`; reuse the constructor-injection and `PasswordEncoder` pattern from `authenticate`.
- `backend/src/main/java/com/company/leave/auth/AuthController.java` -- pattern to mirror: `accessPolicy.requireRole(...)` guard, `Authentication` principal cast to `Long`, `auditService.recordEvent(...)` call shape.
- `backend/src/main/java/com/company/leave/common/{ApiException,ErrorCode,GlobalExceptionHandler}.java` -- `VALIDATION_FAILED`/`NOT_FOUND` already exist; reuse `ApiException(status, code, message)`, add no new `ErrorCode`.
- `backend/src/main/java/com/company/leave/audit/AuditService.java` -- `recordEvent(entity, entityId, action, actorId, detailsJson)`; call with `entity="USER"`, `action="CREATE"`.
- `backend/src/main/resources/openapi.yaml:956-1105,1713-1799` -- contract for `/users`, `/users/managers`, and the `User`/`UserCreate`/`ManagerOption` schemas; field names and error-body shapes come from here verbatim.
- `backend/src/main/resources/db/migration/V3__create_users_table.sql`, `V5__seed_users.sql` -- `users` table already has every needed column (no migration); seeded demo users (ids 1-12, e.g. `hr.admin`=1 `HR_ADMIN`, `emp.jsmith`=5 `EMPLOYEE`) for test fixtures.
- `backend/src/test/java/com/company/leave/auth/AuthControllerIntegrationTest.java`, `security/AccessLogFilterIntegrationTest.java` -- test conventions: Testcontainers Postgres, MockMvc, `.with(authentication(new TestingAuthenticationToken(userId, null, new SimpleGrantedAuthority("ROLE_X"))))` for caller-role tests, `JdbcTemplate` assertions on `audit_events`/`access_log`.

## Tasks & Acceptance

**Execution:**
- [x] `backend/src/main/java/com/company/leave/users/UserCreateRequest.java` -- new `@Value` request DTO (`username`, `role`, nullable `managerId`, `temporaryPassword`) with `@NotBlank`/`@NotNull` where presence is required -- maps the `UserCreate` schema.
- [x] `backend/src/main/java/com/company/leave/users/UserResponse.java` -- new `@Value @Builder` response DTO (`id`, `username`, `role`, `managerId`, `mustChangePassword`, `active`) -- maps the `User` schema.
- [x] `backend/src/main/java/com/company/leave/users/ManagerOptionResponse.java` -- new `@Value @Builder` response DTO (`id`, `username`, `role`) -- maps the `ManagerOption` schema.
- [x] `backend/src/main/java/com/company/leave/users/UserRepository.java` -- add `boolean existsByUsername(String)`, `List<User> findAll()`, `List<User> findByRoleIn(Collection<Role>)`.
- [x] `backend/src/main/java/com/company/leave/users/UserService.java` -- add `createUser(UserCreateRequest, long actorId)` (validate the role/manager rule, check `existsByUsername`, hash the password, build and save the `User`, call `auditService.recordEvent`, map to `UserResponse`), `listUsers()`, `listManagerOptions()`.
- [x] `backend/src/main/java/com/company/leave/users/UserController.java` -- new `@RestController @RequestMapping("/users")`: `POST ""`, `GET ""`, `GET "/managers"`, each calling `accessPolicy.requireRole(Role.HR_ADMIN)` first.
- [x] `backend/src/test/java/com/company/leave/users/UserControllerIntegrationTest.java` -- cover every row of the I/O matrix plus the three endpoints' happy paths and the audit-event assertion.

**Acceptance Criteria:**
- Given an authenticated `HR_ADMIN`, when `POST /users` succeeds, then the response is 201 with the created `User` body and the plaintext `temporaryPassword` never appears in the response or in any log output.
- Given an authenticated `HR_ADMIN`, when `GET /users` is called, then every account is returned with no scope filter (FR24).
- Given an authenticated `HR_ADMIN`, when `GET /users/managers` is called, then every `MANAGER` and every `HR_ADMIN` user is returned as `ManagerOption` rows.
- Given an account is created, when the transaction commits, then exactly one `audit_events` row records the action, the acting HR user's id, and the timestamp.

## Implementation Notes

## Spec Change Log

## Review Triage Log
- **low — patch (fixed), Blind Hunter + Verification Gap: concurrent duplicate-username requests can both pass `existsByUsername` before either `save()`.** Confirmed: `createUser` checks-then-acts with no transaction isolation and `GlobalExceptionHandler` has no handler for `DataIntegrityViolationException`, so the losing request would get an unhandled error instead of 400 `VALIDATION_FAILED`. Low likelihood (requires a true race) but the fix is a direct, trivial correction, so it is not rejected.
- **medium — patch (fixed), Edge Case Hunter: `validateManager` returns immediately for `role=HR_ADMIN` without checking `managerId` at all.** Confirmed by reading `UserService.validateManager`: the `if (role == Role.HR_ADMIN) return;` branch skips validation entirely, so an HR_ADMIN creation request with a nonexistent or wrong-role `managerId` reaches `save()` and fails on the `manager_id` FK constraint with an unhandled `DataIntegrityViolationException` instead of 400 `VALIDATION_FAILED`. The Design Notes' general rule ("`managerId` referencing a missing or wrong-role user both resolve to `VALIDATION_FAILED`") was not applied to the HR_ADMIN branch.
- **low — patch (fixed), Blind Hunter + Edge Case Hunter: `UserCreateRequest.username` has no length bound matching the `users.username VARCHAR(100)` column.** An over-length value reaches `save()` and throws an unhandled `DataIntegrityViolationException` instead of 400 `VALIDATION_FAILED`. Low likelihood, trivial fix (one annotation), not rejected.
- **low — patch (fixed), Blind Hunter: `username` is never trimmed before the uniqueness check or persistence.** `"alice"` and `"alice "` are treated as distinct accounts; plausible data-entry mistake, trivial fix.
- **false, Blind Hunter: `listManagerOptions`/`validateManager` don't filter by `active`, so a deactivated manager/HR admin is still selectable and assignable.** Refuted: the `openapi.yaml` `/users/managers` description says "every MANAGER plus every HR_ADMIN" with no active-status qualifier, `ManagerOption` carries no `active` field at all, and `UserCreate.managerId`'s description requires only "the id of a user with role MANAGER or HR_ADMIN," not that they be active. The contract itself settles this as intended behavior, not a gap.
- **false, Blind Hunter: `auditService.recordEvent` passes `null` for `detailsJson`, omitting role/managerId/username context.** Refuted: the Story 1.6 AC's literal text requires the audit row to record only "the action, the acting HR user's id, and the timestamp," matching FR42/FR43 exactly; no requirement for a richer payload exists in this story's scope.
- **false, Blind Hunter: no password-complexity validation on `temporaryPassword`.** Refuted by the spec's own `Never` clause and by `epics.md`, where length/letters+numbers validation is explicitly scoped to Story 1.7's `change-password` endpoint, not Story 1.6's account creation.
- **false, Edge Case Hunter: `(Long) authentication.getPrincipal()` could throw `ClassCastException` for a non-`Long` principal.** Refuted: production authentication always constructs a `Long` principal in `JwtAuthenticationFilter` — the same conclusion already reached and logged in Story 1.5's own triage for the identical cast pattern, and `UserController` reuses the exact cast already shipped in `AuthController.logout`.
- **low — reject, Edge Case Hunter: `@Valid` request-body validation runs before `accessPolicy.requireRole()` in `createUser`, so a non-HR caller sending an invalid payload gets 400 instead of 403.** Real but low-severity: `UserCreate`'s exact schema is already fully public in `openapi.yaml`, so no information is disclosed by this ordering; it is standard Spring MVC argument-resolution behavior, and correcting it would require restructuring request binding against framework conventions rather than a direct correction. Rejected: unlikely to matter in everyday use and the fix is more than a direct correction.
- **defer, Blind Hunter: the documented `POST /users` 403 example body (`"Only HR can create user accounts."`) doesn't match the actual generic `"Access is denied"` from `GlobalExceptionHandler.handleAccessDenied`.** Pre-existing: this is the same centralized, single-message `AccessDeniedException` handling established in Story 1.5 and reused exactly as this spec instructed ("mirroring `AuthController.logout`"); not introduced by this story.
- **defer, Blind Hunter: `UserCreateRequest`'s Lombok `@Value` has no `toString` exclusion on `temporaryPassword`, so a future logging/debug statement on the request object would print the plaintext password.** Pre-existing: `LoginRequest` (password field, no `toString` exclusion) has had the identical characteristic since an earlier story; this spec's `Always` clause explicitly directed mirroring that exact convention.

## Design Notes

`UserAccount` only carries the login-response fields (`id`/`username`/`role`/`mustChangePassword`) matching the contract's `CurrentUser` schema. The contract's separate `User` schema used by `/users` adds `managerId`/`active`, so `/users` responses need the new `UserResponse` DTO rather than reusing `UserAccount`. Validate the role/manager rule before the uniqueness check, since it needs no DB round trip; `managerId` referencing a missing or wrong-role user both resolve to `VALIDATION_FAILED` — the contract reserves 404 for the Story 1.7 reset-password path, not for a bad `managerId` on create.

## Verification

**Commands:**
- `backend\mvnw.cmd -f backend\pom.xml test` -- expected: `BUILD SUCCESS`, all tests including the new `UserController` tests pass.

**Manual checks:**
- Search the diff for any logging or test-failure-message statement that could print `temporaryPassword` in plaintext; confirm none exists.

**Results:**
- `backend\mvnw.cmd -f backend\pom.xml test` -- `BUILD SUCCESS`; 41 tests, zero failures, errors, or skips, across all 9 test classes including the new `UserControllerIntegrationTest` (9/9 passing), independently re-run and confirmed via `target/surefire-reports/`.
- Diff search found no logging/audit call passing `temporaryPassword` or its raw value; the only use is the single `passwordEncoder.encode(...)` call. The response-body assertion in `hrCreatesEmployeeReturns201WithAuditEventAndNoPlaintextPasswordInResponse` confirms the plaintext and the field name are both absent from the HTTP response, and that the stored `password_hash` is a bcrypt hash, not plaintext.
