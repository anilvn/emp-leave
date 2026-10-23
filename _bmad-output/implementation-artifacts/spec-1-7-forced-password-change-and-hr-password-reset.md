---
title: 'Story 1.7: Forced Password Change and HR Password Reset'
type: 'feature'
created: '2026-10-23'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: '5e5af4cf065fc15c190a65e48b5f68ecd712de56'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A user holding a temporary password can use every endpoint without ever replacing it, and there is no way for HR to reset a forgotten/locked-out password or for any user to change their own.

**Approach:** Add `POST /auth/change-password` (self-service) and `POST /users/{userId}/reset-password` (HR-only), backed by new `UserService` methods, plus a new security filter that blocks all other endpoints while `mustChangePassword` is true. The OpenAPI contract already fully specifies both endpoints, the `PasswordChangeRequired` response, and the `PASSWORD_CHANGE_REQUIRED` error code — only the Java implementation is missing.

## Boundaries & Constraints

**Always:** Gate reset-password with `accessPolicy.requireRole(Role.HR_ADMIN)` like `UserController`'s other endpoints. `changePassword`: verify `currentPassword` via `passwordEncoder.matches`, require `newPassword` >=8 chars with a letter and a digit, reject (hash untouched) on either failure, else set `mustChangePassword=false` and write one `audit_events` row (`entity="USER"`, `action="CHANGE_PASSWORD"`, actor=the caller, entity id=the caller). `resetPassword`: generate a random temp password (`SecureRandom`, never logged/audited in plaintext), BCrypt it into `password_hash`, set `mustChangePassword=true`, call existing `sessionRegistry.revokeAllForUser(userId)`, write one `audit_events` row (`entity="USER"`, `action="RESET_PASSWORD"`, actor=HR caller, entity id=target). Give `User` a package-visible mutator for `passwordHash`/`mustChangePassword` (no setters exist today) in the style of `recordFailedLogin`. `PasswordChangeRequiredFilter` loads `User` fresh per request (no cache exists), short-circuits with the `PasswordChangeRequired` body from `openapi.yaml:1596-1612` unless the path is `/auth/login`, `/auth/logout`, `/auth/change-password`, `/actuator/health`, or has no `Authentication`. Register via `.addFilterBefore(passwordChangeRequiredFilter, AccessLogFilter.class)`.

**Never:** Add a new `ErrorCode` (`PASSWORD_CHANGE_REQUIRED` already exists). Call `revokeAllForUser` from self-service `changePassword`. Reopen `/users` POST/GET/GET-managers (Story 1.6, frozen). Enforce complexity on the HR-generated temp password.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Valid self-change | correct `currentPassword`, `newPassword="Passw0rd1"` | 204; hash replaced; `mustChangePassword=false` | N/A |
| Weak `newPassword` | too short / no letters / no digits | n/a | 400 `VALIDATION_FAILED`; hash unchanged |
| Wrong `currentPassword` | incorrect value | n/a | 401 `INVALID_CREDENTIALS`; hash unchanged |
| Blocked while pending | `mustChangePassword=true`, calls any `/api` endpoint other than change-password/logout | n/a | 403 `PASSWORD_CHANGE_REQUIRED` |
| Escape routes stay open | same caller calls `/auth/change-password` or `/auth/logout` | processes normally | N/A |
| HR resets existing user | `HR_ADMIN` caller, valid target `userId` | 200 with generated `temporaryPassword`; hash replaced; `mustChangePassword=true`; sessions revoked; audit row written | N/A |
| HR resets unknown user | `userId` does not exist | n/a | 404 `NOT_FOUND` |
| Non-HR reset attempt | caller role `EMPLOYEE`/`MANAGER` | n/a | 403 `ACCESS_DENIED`; `access_log` row `allowed=false` |

</frozen-after-approval>

## Code Map

Main paths relative to `backend/src/main/java/com/company/leave/`, test paths to `backend/src/test/java/com/company/leave/`, both already-created by earlier stories unless marked **new**.

- `auth/AuthController.java` -- add `POST /auth/change-password`; mirror `login`/`logout`'s principal cast and `auditService.recordEvent(...)` shape.
- **new** `auth/ChangePasswordRequest.java` -- `@Value` DTO (`currentPassword`, `newPassword`, `@NotBlank`), maps `openapi.yaml:1680-1693`.
- `users/UserService.java` -- add `changePassword(userId, currentPassword, newPassword)` and `resetPassword(targetUserId, actorId) -> String`; reuse `passwordEncoder.matches`/`.encode` (lines 43, 57, 108) and `auditService.recordEvent` (line 123) patterns, new `@Transactional` methods.
- `users/User.java` -- add a package-visible password mutator (no setters exist); style of `recordFailedLogin` (line 64).
- `users/UserController.java` -- add `POST /users/{userId}/reset-password`, same `requireRole(Role.HR_ADMIN)` pattern as the other three endpoints (lines 31, 39, 45).
- **new** `users/TemporaryPasswordResponse.java` -- `@Value @Builder` DTO (`temporaryPassword`), maps `openapi.yaml:1800-1806`.
- `security/SecurityConfig.java` -- `.addFilterBefore(passwordChangeRequiredFilter, AccessLogFilter.class)` into the existing chain (lines 32-47).
- **new** `security/PasswordChangeRequiredFilter.java` -- loads `User` via `UserRepository` when `Authentication` is present, short-circuits with the `PasswordChangeRequired` body unless on an escape route.
- `security/SessionRegistry.java` -- reuse existing `revokeAllForUser(long userId)` (line 48); no change.
- `resources/openapi.yaml` -- no change; both endpoints, `ChangePasswordRequest`, `TemporaryPassword`, `PasswordChangeRequired` already fully specified (lines noted above).
- `auth/AuthControllerIntegrationTest.java` -- add change-password tests; reuse `prepareDatabase`/`auditCount`/`HR_ADMIN_ID` etc. conventions.
- `users/UserControllerIntegrationTest.java` -- add reset-password tests covering the I/O matrix (lines 1-56, 243-245 conventions).
- **new** `security/PasswordChangeRequiredFilterIntegrationTest.java` -- `AccessLogFilterIntegrationTest.java` conventions, covers the blocked/escape-route rows.

## Tasks & Acceptance

**Execution (in dependency order, see Code Map for roles):**
- [x] `auth/ChangePasswordRequest.java`, `users/TemporaryPasswordResponse.java` -- new DTOs.
- [x] `users/User.java` -- password mutator.
- [x] `users/UserService.java` -- `changePassword`, `resetPassword`.
- [x] `auth/AuthController.java` -- `POST /auth/change-password`.
- [x] `users/UserController.java` -- `POST /users/{userId}/reset-password`.
- [x] `security/PasswordChangeRequiredFilter.java` + `security/SecurityConfig.java` registration.
- [x] `auth/AuthControllerIntegrationTest.java` -- covers matrix rows 1-3.
- [x] `users/UserControllerIntegrationTest.java` -- covers matrix rows 6-8.
- [x] `security/PasswordChangeRequiredFilterIntegrationTest.java` -- covers matrix rows 4-5.

**Acceptance Criteria:**
- Given a password reset and a later self-service password change, when each completes, then each writes its own `audit_events` row with actor and timestamp — together with Story 1.6 this closes demo scenario 11.
- Given the generated temporary password, when `resetPassword` runs, then that value never appears in any log statement, exception message, or audit `detailsJson`.

## Implementation Notes

Implemented exactly per the Code Map. One addition not explicitly named there but required by the package boundary: `UserService` is the only public seam into the `users` package (`UserRepository` and `User` are package-private), so `PasswordChangeRequiredFilter` cannot load `User` via `UserRepository` directly as the Code Map's prose suggested. Added `UserService.mustChangePassword(long userId)` (a plain `findById(...).map(User::isMustChangePassword)` read) and had the filter depend on `UserService` instead — consistent with the existing `AuthController`/`UserController` pattern of depending on `UserService`, and with the pre-existing bidirectional `security`<->`users` package coupling (`UserService` already depends on `security.SessionRegistry`; `security.AccessPolicy` already depends on `users.Role`).

Also note: this repo is on Spring Boot 4 / Spring Framework 7, which ships Jackson 3 under the `tools.jackson.*` package (group id `tools.jackson.core`), not the classic `com.fasterxml.jackson.*`. `PasswordChangeRequiredFilter` and `SecurityConfig` use `tools.jackson.databind.ObjectMapper` accordingly to write the `PasswordChangeRequired` body outside the DispatcherServlet (filters run before `GlobalExceptionHandler` can apply).

## Spec Change Log

## Review Triage Log

- **medium — patch, Blind Hunter + Edge Case Hunter: `PasswordChangeRequiredFilter`'s 403 denials never reach `access_log`.** Confirmed: it's registered via `addFilterBefore(passwordChangeRequiredFilter, AccessLogFilter.class)`, so a short-circuited request never calls `filterChain.doFilter`, and `AccessLogFilter` (registered after it) never runs. This breaks the epic's invariant that every authorization decision, allowed or denied, is recorded. Fixable without touching the frozen filter-ordering clause: the filter can call the existing `AuditService.recordAccess(actorId, resource, resourceId, allowed)` itself before writing its body, mirroring `AccessLogFilter`'s own resource-string convention.
- **low — patch, Blind Hunter: `generateTemporaryPassword()` has only ~13 bits of entropy (`secureRandom.nextInt(10_000)`, fixed `"Temp#"` prefix).** Confirmed by reading the code. Mitigated in practice by Story 1.4's login lockout (5 attempts/15 min), but still a trivial, direct fix (widen the random value).
- **low — reject, Blind Hunter: no rate limit/lockout on `/auth/change-password` lets a caller guess `currentPassword` indefinitely.** Real, but exploiting it requires an already-valid, already-authenticated session for that user — at which point the attacker already has full account access without needing to guess anything. Low likelihood in everyday use, and the fix (a new per-endpoint lockout mechanism) is far more than a direct correction.
- **false, Blind Hunter: failed `changePassword` attempts write no audit/access-log row.** Refuted: this matches the codebase's existing, established convention — failed login attempts in `AuthController`/`UserService.authenticate` also write no `audit_events` row (only the `failed_logins` counter changes); only successes are audited anywhere in this codebase.
- **false, Blind Hunter: `accessPolicy.requireRole(Role.EMPLOYEE, Role.MANAGER, Role.HR_ADMIN)` in `changePassword` is a "no-op."** Refuted: this is the exact pre-existing call shape already used by `AuthController.logout` (confirmed via investigation), not something introduced by this story; it asserts the principal carries a valid `Role` grant rather than restricting by role, which is correct for an endpoint every authenticated role must reach.
- **false, Blind Hunter: the filter hand-builds its JSON body instead of reusing `ProblemDetail`/`GlobalExceptionHandler`.** Refuted: `GlobalExceptionHandler` is a `@RestControllerAdvice`, reachable only from `DispatcherServlet`-handled exceptions; a `Filter` that short-circuits before the dispatcher cannot use it. The hand-built body matches the OpenAPI-documented example exactly, including the absence of an `instance` field.
- **false, Blind Hunter: escape-route matching is unverified behind the `X-Forwarded-Prefix` reverse-proxy convention.** Refuted: confirmed by reading `AccessLogFilter` and `SecurityConfig` — internal Spring MVC routing and `SecurityConfig`'s own `permitAll` matchers use the bare, unprefixed path (e.g. `"/auth/login"`) with no dependency on `X-Forwarded-Prefix`; that header only affects how `AccessLogFilter` *formats and gates* its logged resource string, a separate, unrelated concern from this filter's routing.
- **false, Blind Hunter: a duplicate password-complexity regex risks drifting from an earlier story's rule.** Refuted: Story 1.6's own spec explicitly deferred all complexity validation to Story 1.7 ("Story 1.7 owns that rule for change-password"); no earlier copy of this rule exists anywhere in the codebase.
- **low — patch, Blind Hunter: `AuthControllerIntegrationTest` adds a near-duplicate `initialUserAuditCounts`/`userAuditCount`/`currentUserAuditCount` trio, differing from the pre-existing trio only by a hard-coded `entity = 'USER'`.** Confirmed by reading the diff; developers will meet this every time they touch the file, and parameterizing the existing helper is a direct, trivial fix.
- **low — patch, Blind Hunter: no test exercises a blank `currentPassword`/`newPassword` to confirm `@Valid` rejects it.** Confirmed gap; the underlying `@NotBlank` + `@Valid` mechanism is already proven elsewhere (`LoginRequest`), so risk is minimal, but adding one test is a trivial, direct addition.
- **false, Blind Hunter: principal-extraction style is inconsistent (`(Long)` hard cast vs. defensive `instanceof Number`).** Refuted: `JwtAuthenticationFilter` always populates the principal as a boxed `Long`; this identical question was already raised and refuted twice in this codebase's history (Story 1.5 and Story 1.6 triage logs) for the same cast pattern.
- **low — reject, Edge Case Hunter: HR resetting their own password via `reset-password` self-revokes their own session.** Real but narrow: HR has a dedicated self-service `/auth/change-password` endpoint for their own account, so hitting the admin reset endpoint against their own id is an unlikely usage pattern, and the outcome is a mild, fully-recoverable inconvenience (the response still returns the new temporary password to log back in with) rather than a lockout. Resolving it would require a new product decision (block self-target or not) beyond a direct, demonstrated correction.
- **medium — patch, Edge Case Hunter: `changePassword` accepts a `newPassword` identical to `currentPassword`.** Confirmed: `NEW_PASSWORD_PATTERN` only checks shape, and nothing compares `newPassword` against the stored hash. This lets a user "satisfy" the forced-change gate while keeping the exact same, HR-known temporary password — directly undermining the story's own "temporary credentials never linger" goal. Direct fix: reject when `passwordEncoder.matches(newPassword, user.getPasswordHash())`.
- **low — patch, Edge Case Hunter: `newPassword` over 72 bytes is silently truncated by BCrypt.** Confirmed by reading `spring-security-crypto:7.1.1` source (`AbstractValidatingPasswordEncoder`/`BCryptPasswordEncoder`) — no length validation exists in this version; the underlying bcrypt algorithm itself truncates beyond 72 bytes. Unlikely with realistic passwords, but a direct max-length addition closes it.
- **maybe-false (medium if true), Edge Case Hunter: a deactivated (`active=false`) user may still change/reset a password through a stale, not-yet-expired session.** Neither `JwtAuthenticationFilter`/`SessionRegistry.touchOrReject` nor either new endpoint checks `active` post-login in the diff. What would settle it: whether any existing filter/service already enforces `active` after initial login — if not, this is a pre-existing gap affecting every endpoint, not something introduced by this story. Not this story's problem either way.
- **false, Edge Case Hunter: escape-route matching isn't robust to a trailing slash or case variation.** Refuted: this is the same exact-path, case-sensitive matching convention `SecurityConfig`'s own `permitAll` matchers and Spring MVC's `@PostMapping` routing already use throughout the codebase; a request in that shape wouldn't reach the intended controller method either, so there's no new asymmetry.
- **low — reject, Edge Case Hunter: no optimistic locking on `User` allows a lost update between two concurrent password mutations.** Real but narrow (requires two concurrent requests against the same user's password within the same transaction window), and the fix (`@Version` column) requires a schema migration — more than a direct correction.
- **low — patch, Verification Gap: self-service `changePassword`'s "no session revocation" rule is unverified by any test.** Filed pre-verified by the reviewer: the one success test for self-change never reuses the original session afterward, so a regression that added a revocation call there would ship silently. Fix: extend that test to make one more authenticated call with the original cookie and assert it still succeeds.

## Design Notes

A filter (not a per-controller `AccessPolicy` check) enforces the gate: `mustChangePassword` is in neither the JWT claims nor the principal, so a DB read is needed regardless of where the check lives, and a filter catches every endpoint — present and future — in one place. In `changePassword`, check `newPassword` format before `currentPassword` (no DB hit needed for the former); the contract tests each failure independently, so order isn't observable. No temp-password generator exists yet (Story 1.6's HR-create flow takes the value from the caller instead); generate one with `SecureRandom`, shaped like the contract's own `Temp#8841` example.

## Verification

**Commands:**
- `backend\mvnw.cmd -f backend\pom.xml test` -- expected: `BUILD SUCCESS`, all tests including the new ones pass.
  - Ran (pre-patch): `BUILD SUCCESS`, 53 tests, 0 failures, 0 errors (up from 44 before this story).
  - Re-ran after the review patches (7 findings, see Review Triage Log): `BUILD SUCCESS`, 54 tests, 0 failures, 0 errors.

**Manual checks:**
- Search the diff for any logging, exception-message, or audit `detailsJson` call that could carry `currentPassword`, `newPassword`, or the generated `temporaryPassword` in plaintext; confirm none exists.
  - Done: the main codebase has zero logging statements anywhere; every `auditService.recordEvent(...)` call added for this story passes `null` for `detailsJson`; every `ApiException` message is a static string with no password value interpolated. Grepped for all three field names across `backend/src/main/java` to confirm each use is a parameter/field declaration or a password-encoder call, never a log/exception/audit payload.
