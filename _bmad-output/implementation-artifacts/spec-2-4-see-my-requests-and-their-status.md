---
title: 'Story 2.4: See My Requests and Their Status'
type: 'feature'
created: '2026-10-23'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: ['{project-root}/backend/AGENTS.md']
baseline_commit: '61ec86ff875ac1371086d991f43750acc5589cc5'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Employees have no API to retrieve their leave requests or see their current status, while managers and HR need permitted request visibility.

**Approach:** Add scoped, optionally filtered request-list and authorized detail reads using the existing model and shared access policy. Keep the API aligned with OpenAPI and add integration coverage and runnable curl examples.

## Boundaries & Constraints

**Always:** Keep request entities/repositories inside `leave`. Apply collection scope through `AccessPolicy.currentScope()` and repository-side `findByScope(UserScope)`. A manager's unfiltered list includes their own requests and direct reports; `employeeId` narrows it to a validated direct report. Use `AccessPolicy.requireOwnerManagerOrHr(...)` for resource and requested-employee access; do not write role/ownership comparisons in controllers. Preserve filters, ProblemDetail errors, denial logging, and all `LeaveRequest` response fields. No schema change.

**Never:** Modify migrations, weaken row-level access, expose other employees' data to an employee, add non-contractual fields/statuses, or change submission, balance, notification, or audit writes. Backend/API only; do not add frontend UI.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Employee list | Employee supplies optional status | 200, only caller's matching requests (including cancelled) | N/A |
| Manager default | Manager omits `employeeId` | 200, own requests plus direct reports' requests | N/A |
| Manager filter | Manager supplies a direct-report `employeeId` | 200, that report's matching requests | N/A |
| Disallowed employee | Manager names a non-report, or employee reads another's detail | No request data returned | 403 ACCESS_DENIED and access-log denial |
| Detail | Owner, direct manager, or HR reads existing request | 200 with complete request | N/A |
| Unknown detail | Authenticated caller requests unknown id | No request data returned | 404 NOT_FOUND |

</frozen-after-approval>

## Code Map

- `backend/src/main/java/com/company/leave/leave/LeaveRequestController.java` — POST only; add GET routes.
- `LeaveRequestService.java` / `LeaveRequestRepository.java` in the same package — reuse response mapping; add scoped reads and lookup, keeping JPA types internal.
- `backend/src/main/java/com/company/leave/security/AccessPolicy.java` and `UserScope.java` — existing scope and owner/manager/HR policy.
- `backend/src/main/java/com/company/leave/users/UserService.java` — `getManagerId(...)`; do not access users persistence directly from `leave`.
- `backend/src/main/resources/openapi.yaml` — list/detail operations and response/error contract already documented.
- `backend/src/test/java/com/company/leave/leave/LeaveRequestControllerIntegrationTest.java` — PostgreSQL Testcontainers + MockMvc coverage to extend.
- `backend/curl/story-2.2-submit-leave-request.md` — cookie-authenticated curl-guide pattern.
- `_bmad-output/planning-artifacts/epics.md` — authoritative Story 2.4 criteria.

## Tasks & Acceptance

**Execution:**
- [x] `backend/src/main/java/com/company/leave/leave/LeaveRequestRepository.java` — add `findByScope(UserScope)` with repository-owned filters and lookup.
- [x] `backend/src/main/java/com/company/leave/leave/LeaveRequestService.java` — add read-only list/detail, policy checks, and DTO mapping.
- [x] `backend/src/main/java/com/company/leave/leave/LeaveRequestController.java` — expose list/detail GET routes and contract query parameters.
- [x] `backend/src/main/resources/openapi.yaml` — verify runtime matches existing operations and error contract.
- [x] `backend/src/test/java/com/company/leave/leave/LeaveRequestControllerIntegrationTest.java` — cover roles/scopes, filters, detail, 404, and denial logging.
- [x] `backend/curl/story-2.4-see-my-requests-and-their-status.md` — add runnable success/denial examples.

**Acceptance Criteria:**
- Given any authenticated role, when the request list is called, then employees see only their own requests, managers see their own plus direct reports' requests unless filtered to one direct report, and HR may see any employee's requests; status narrows each result.
- Given a caller authorized for an existing request, when detail is fetched, then the complete `LeaveRequest` response is returned; otherwise a disallowed caller gets 403 `ACCESS_DENIED` with no data and an access-log denial.
- Given an unknown request id, when detail is fetched, then 404 `NOT_FOUND` is returned.
- Given any list/detail result, when the API responds, then it matches the OpenAPI schema and shared error envelope, and collection scope is enforced in the repository.

## Implementation Notes

Use controller → leave service → package-private repository. Repository predicates enforce `EmployeeOwn`, `ManagerTeam`, and `HrAll`; user/manager facts come through `UserService`, decisions through `AccessPolicy`. For the chosen manager default, combine the manager's own request query with `ManagerTeam` results without changing the shared meaning of `ManagerTeam` in other features. Check detail after loading owner/manager identity (AD-10). Verify existing 403 access logging end-to-end; do not duplicate it. No migration expected.

Implementation added scoped list/detail reads, query validation through the common ProblemDetail handler, documented the manager default, and added PostgreSQL-backed authorization/filter tests and curl examples. Review follow-up corrected the manager-default assertion to include every seeded direct report and changed the curl detail example to capture a real request id.

## Spec Change Log

## Review Triage Log

| # | Source | Claim | Verdict | Evidence | Route |
|---|---|---|---|---|---|
| 1 | blind review | HR-wide request results have no pagination and could grow without bound. | low | This is a plausible scale concern, but the current POC has no pagination contract and ordinary use is unlikely to reach a harmful result size; pagination would add API surface and complexity. | no change |
| 2 | blind review | A former manager may retain detail access after an employee is reassigned. | false | No manager-reassignment path exists in the current application, so the described transition cannot be reached through current application behavior. | no change |
| 3 | blind review | OpenAPI does not document the request-detail GET operation. | false | The baseline OpenAPI already documents `GET /requests/{requestId}`; the omission claim is disproved by the existing contract. | no change |
| 4 | review | Existing Postman requests use a raw token as the `Cookie` header instead of `access_token=<token>`. | low | The affected uncommitted Postman requests will not authenticate with that header. They predate this story and were not modified; the issue is recorded in `deferred-work.md`. | defer |
| 5 | review | The Postman request named `my-leave-req-by-id` targets `/balances/me` rather than request detail. | low | The request's path does not match its stated purpose. It is uncommitted user-owned Postman work and is recorded in `deferred-work.md`, not changed here. | defer |
| 6 | review | No Postman request exercises the new list endpoint. | false | This story's documented usage deliverable is the curl guide, and list behavior is also present in OpenAPI; a Postman collection is not an acceptance requirement. | no change |
| 7 | blind review | The curl detail example assumes a fixed request id that may not exist on a fresh stack. | low | The guide now creates a request, captures its returned id, and checks that capture before issuing the detail request. | patch |
| 8 | verification-gap | Manager status filtering lacked an integration assertion. | low | The test now filters manager-visible requests to `APPROVED` and asserts the matching direct report and status. | patch |
| 9 | verification-gap | The Postman detail request has the wrong endpoint. | low | This duplicates finding 5's same request and bad outcome; the deferred-work entry for that endpoint issue covers both reports. | defer (duplicate of 5) |

## Design Notes

Collection scope belongs in repository `UserScope` predicates; status and permitted employee filters only narrow scoped results. Detail uses the shared policy after loading owner/manager identity.

## Verification

**Commands:**
- `backend\mvnw.cmd -f backend\pom.xml -Dtest=LeaveRequestControllerIntegrationTest test` — 23 tests pass.
- `backend\mvnw.cmd -f backend\pom.xml -Dtest=OpenApiDocsIntegrationTest test` — 3 tests pass, including the new GET paths.
- `python -c "import yaml; yaml.safe_load(open(r'backend\src\main\resources\openapi.yaml', encoding='utf-8'))"` — OpenAPI YAML parses successfully.
