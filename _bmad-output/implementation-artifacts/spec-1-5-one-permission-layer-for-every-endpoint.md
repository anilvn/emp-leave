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

**Results:**
- `backend\\mvnw.cmd -f backend\\pom.xml test` -- `BUILD SUCCESS`; 32 tests, zero failures, errors, or skips.
- `backend\\mvnw.cmd -f backend\\pom.xml "-Dtest=AccessPolicyTest,AccessLogFilterIntegrationTest" test` -- `BUILD SUCCESS`; 9 focused tests passed after review fixes.
- The backend source search found policy decisions in `AccessPolicy`; `JwtAuthenticationFilter` establishes role authorities and `GlobalExceptionHandler` maps denials. `AuthController.logout` now delegates supported-role authorization to `AccessPolicy`.
- Proxy-path integration tests verify that requests forwarded with `/api` are audited under their external path, while unauthenticated and public login requests are omitted. An automated configuration assertion verifies that Nginx forwards the prefix from its `/api/` location.
- `git diff --check` -- passed; backend source search found no role comparisons or `hasRole(...)` checks outside `AccessPolicy`.

## Spec Change Log

## Review Triage Log
- **medium — patch (fixed), Blind Hunter: policy methods had no production caller.** Source search confirmed no callers; the only existing protected controller action is logout, so it now delegates the supported-role check to `AccessPolicy.requireRole(...)`.
- **false, Blind Hunter: non-403 responses are logged as allowed.** The configured authorization denial maps to 403; other response failures occur after authentication/authorization and do not represent denied access.
- **maybe-false — defer, Blind Hunter: multiple numeric path segments.** The filter selects the last numeric segment, but a single `resourceId` cannot be inferred reliably for composite/nested routes without route-specific semantics. See the deferred-work entry.
- **false, Blind Hunter: audit-write failure can replace request failure.** The filter does not silently serve an authenticated request when the mandatory audit write fails; this fail-closed behavior preserves the audit requirement.
- **false, Blind Hunter: authenticated public API requests are audited.** The filter now excludes public `POST /api/auth/login`, with an integration assertion that no access-log row is created.
- **false, Blind Hunter: manager ID may be untrusted.** The policy takes owner and manager IDs as its specified arguments; no caller supplies request-controlled IDs, and the policy does not source resource data itself.
- **false, Blind Hunter: arbitrary `Number` principal conversion.** Production authentication constructs a `Long` principal in `JwtAuthenticationFilter`; the broader `Number` check does not truncate the configured principal.
- **false, Blind Hunter: integration denial bypasses `AccessPolicy`.** Policy decisions are covered in unit tests; the integration test verifies the shared `AccessDeniedException` mapping and audit behavior, and no resource-authorization endpoint exists yet.
- **false, Blind Hunter: public-route test only used health.** The updated integration test also exercises authenticated public login through the forwarded `/api` prefix and verifies omission.
- **medium — patch (fixed), Blind Hunter: manual verification result was not recorded.** Added the backend search result and test outcome above.
- **false, Blind Hunter: empty Implementation Notes heading.** An empty optional notes heading does not change behavior or leave an acceptance criterion unmet.
- **maybe-false — defer, Edge Case Hunter: multiple numeric path segments.** Same unresolved route-to-resource-ID semantics as the Blind Hunter finding; resolving route-specific IDs will settle it.
- **false, Edge Case Hunter: non-403 responses are logged as allowed.** As above, only the configured 403 outcome denotes access denial; later handler errors do not undo authorization.
- **medium — patch (fixed), implementation verification: reverse proxy strips `/api/`.** `proxy_pass` rewrites the path before the backend sees it, so the filter previously skipped all proxied API traffic. Nginx now forwards the prefix and the filter reconstructs the external path; integration tests cover the rewritten route and public login exclusion.
- **false, Blind Hunter: a non-authorization 403 is recorded as a denied authorization.** The backend source search found only the `AccessDeniedException` handler producing 403; there is no current non-authorization 403 path, and the planned `PASSWORD_CHANGE_REQUIRED` response is also an authorization gate.
- **maybe-false — defer, Blind Hunter: multiple numeric path segments (carried).** Carried: the prior triage entry and deferred-work record already capture that selecting the correct resource ID for nested routes requires route-specific semantics; no new implementation change is warranted here.
- **false, Blind Hunter: request-controlled owner/manager IDs can authorize an unrelated caller.** There are no production callers of `requireOwnerManagerOrHr`; the signature requires resource owner/manager IDs, and no current request path supplies those arguments from user input.
- **false, Blind Hunter: `ManagerTeam` membership and active-user semantics are undefined.** Epic 1 context defines manager visibility as direct reports, and this story only introduces the scope value; no collection query or active-user rule is implemented here.
- **maybe-false — defer, Blind Hunter: audit persistence failure after a state-changing request.** The filter writes in `finally` after endpoint execution, but current tests do not establish the client response or committed state when audit persistence fails. A fault-injection test on a state-changing request is needed to settle whether the client can see an error after the mutation commits.
- **medium — patch (fixed), Blind Hunter: the null `resourceId` case had no test.** Added an authenticated collection-route test asserting exactly one row with a null resource ID and the expected actor/outcome; focused tests pass.
- **maybe-false — defer, Edge Case Hunter: multiple numeric path segments (carried).** Carried: this is the same route-to-resource-ID ambiguity already recorded and deferred above.
- **medium — patch (fixed), Verification Gap Reviewer: the proxy prefix was only simulated in MockMvc.** Added an automated assertion that Nginx sets `X-Forwarded-Prefix: /api` inside its `/api/` location; the proxy setting and request-path reconstruction are now both checked.
