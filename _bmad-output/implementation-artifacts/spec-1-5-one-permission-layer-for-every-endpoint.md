---
title: 'Story 1.5: One Permission Layer for Every Endpoint'
type: 'feature'
created: '2026-10-23'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: []
baseline_commit: 'b3cca8fb8ecf1d3456520b1e5d7d7bf8676c824c'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Authorization rules are not yet centralized, and refused access must be recorded consistently so modules cannot diverge in role or ownership behavior.

**Approach:** Add the `security.AccessPolicy` API with the three Epic 1 AD-10 checks and sealed `UserScope` variants. Record one audit row for each authenticated API authorization attempt. Follow the existing Lombok conventions without adding broad setters or unrelated boilerplate.

## Boundaries & Constraints

**Always:** Expose exactly `requireOwnerManagerOrHr(long, long)`, `currentScope()`, and `requireRole(Role...)`; provide only `EmployeeOwn(long userId)`, `ManagerTeam(long managerId)`, and `HrAll` as scope variants. Reuse the existing access-log write path, exception mapping, role model, and authentication principal. Keep authorization decisions in `AccessPolicy`.

**Never:** Add an access-log read API or UI; implement unrelated role/ownership checks in controllers or services; add the change-password endpoint in this story (Story 1.7 owns it); pull HR account creation, password reset, or unrelated authorization work from later stories into this story.

**Decisions:** Access logging covers authenticated `/api/**` requests only; omit public and unauthenticated requests. Record `resource` as HTTP method plus request path, `resourceId` from a numeric path ID when present (otherwise null), and `allowed` as the authorization outcome. Defer Story 1.5's live change-password endpoint integration to Story 1.7, accepting that this story does not meet that live-endpoint criterion.

</frozen-after-approval>

## Code Map

- `backend/src/main/java/com/company/leave/security/JwtAuthenticationFilter.java` -- authenticates requests with numeric user ID principal and `ROLE_<role>` authority; reuse its established identity model.
- `backend/src/main/java/com/company/leave/security/SecurityConfig.java` -- existing stateless filter chain; login and health are public, other routes require authentication.
- `backend/src/main/java/com/company/leave/security/package-info.java` -- planned security package boundary; no `AccessPolicy` or `UserScope` exists yet.
- `backend/src/main/java/com/company/leave/users/Role.java` and `User.java` -- role enum and manager relationship; existing entity uses Lombok `@Getter`, protected no-args constructor, and builder without broad setters.
- `backend/src/main/java/com/company/leave/audit/AuditService.java`, `AccessLogRepository.java`, and `AccessLogEntry.java` -- reuse `recordAccess(actorId, resource, resourceId, allowed)` and insert-only persistence.
- `backend/src/main/resources/db/migration/V1__create_audit_tables.sql` -- `access_log` has nullable actor/resource ID, resource, allowed, and timestamp; it has no HTTP action column.
- `backend/src/main/java/com/company/leave/common/GlobalExceptionHandler.java` -- maps `AccessDeniedException` to 403 `ACCESS_DENIED`.
- `backend/src/main/java/com/company/leave/auth/AuthController.java` -- currently has login/logout only; there is no change-password endpoint yet. Story 1.7 defines its password-change behavior.
- `backend/src/test/java/com/company/leave/audit/AuditServiceImmutabilityTest.java`, `backend/src/test/java/com/company/leave/auth/AuthControllerIntegrationTest.java`, and `backend/src/test/java/com/company/leave/common/GlobalExceptionHandlerTest.java` -- existing integration-test patterns for audit persistence, auth routes, and 403 error mapping. No policy or protected-route authorization tests exist.
- `_bmad-output/implementation-artifacts/epic-1-context.md` -- primary Epic 1 decisions; use all three policy shapes now, with `requireRole` as the live policy integration in this epic until scoped repository queries are introduced.

## Tasks & Acceptance

**Execution:**
- [x] `backend/src/main/java/com/company/leave/security/UserScope.java` -- define the sealed scope and exactly its three required variants.
- [x] `backend/src/main/java/com/company/leave/security/AccessPolicy.java` -- implement the three public checks using the authenticated principal and `Role`, with consistent `AccessDeniedException` failures.
- [x] `backend/src/main/java/com/company/leave/security/AccessLogFilter.java` -- record exactly one access-log row for each authenticated `/api/**` request via the existing audit service; do not log independently from `AccessPolicy`.
- [x] `backend/src/main/java/com/company/leave/security/SecurityConfig.java` -- order the request-audit filter after authentication so the actor is known and public/unauthenticated requests are omitted.
- [x] `backend/src/test/java/com/company/leave/security/AccessPolicyTest.java` and relevant integration tests -- cover roles, ownership, scope mapping, denials, and exactly-one-row audit behavior. Do not add a change-password endpoint in this story.

**Acceptance Criteria:**
- Given an authenticated user with role `EMPLOYEE`, `MANAGER`, or `HR_ADMIN`, when `currentScope()` is called, then it returns respectively `EmployeeOwn(userId)`, `ManagerTeam(userId)`, or `HrAll`; when the caller has no supported authenticated role, then it denies access.
- Given the resource owner, the owner's direct manager, or an HR_ADMIN caller, when `requireOwnerManagerOrHr` is called, then it returns normally; given any other caller, when that check is called, then it throws `AccessDeniedException` and the API response is 403 `ACCESS_DENIED`.
- Given an authenticated caller, when `requireRole` is called with a role matching that caller, then it returns normally; when called with no matching allowed role, then it throws `AccessDeniedException` and the API response is 403 `ACCESS_DENIED`.
- Given an authenticated `/api/**` request, when it is handled, then exactly one `access_log` row records the actor, HTTP method plus request path, numeric path ID when present, and authorization outcome; public and unauthenticated requests are omitted.
- Given backend source, when controller and service authorization is inspected, then no role or ownership policy is reimplemented outside `AccessPolicy`; the existing filter-chain authentication configuration remains the authentication boundary.
- Given Story 1.5 is implemented, when its scope is checked, then it does not add the change-password endpoint; Story 1.7 owns that endpoint and the live-endpoint criterion is explicitly deferred.

## Implementation Notes

## Design Notes

Existing `access_log` records have no action column, so HTTP method is included in `resource` alongside the request path. Collection-scope support is prepared in `AccessPolicy`; Epic 1 has no collection repository to retrofit, and the Epic context records `findByScope(UserScope)` as a later consumer. The user explicitly accepted deferring the change-password live-endpoint criterion to Story 1.7.

## Verification

**Commands:**
- `backend\\mvnw.cmd -f backend\\pom.xml test` -- expected: backend tests pass, including the new policy and request-audit cases.

**Manual checks:**
- Search backend controllers and services for role comparisons and `hasRole(...)`; policy decisions must remain centralized, with only filter-chain configuration exempt.

## Spec Change Log

## Review Triage Log
