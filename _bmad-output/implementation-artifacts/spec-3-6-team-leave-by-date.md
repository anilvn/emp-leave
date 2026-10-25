---
title: 'Story 3.6: Team Leave by Date'
type: 'feature'
created: '2026-10-25'
status: 'in-review'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '14d61b5'
context:
  - '{project-root}/backend/AGENTS.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-company-leave-2026-10-22/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Managers have no endpoint to view their team's approved leave across a date range, making it impossible to spot scheduling clashes or answer questions about a team member's past leave without querying requests individually.

**Approach:** Add `GET /api/team/leave?from=&to=` in a new `team` module, scoped to the MANAGER caller's direct reports' `APPROVED` requests overlapping the given date range, with each entry's comments and history nested inline so no second call is needed.

## Boundaries & Constraints

**Always:** Gate with `accessPolicy.requireRole(MANAGER)` (only MANAGER; HR_ADMIN is not in scope per the contract's 403 text); filter by `approverId = caller_id` for direct-report scoping; return only `APPROVED` status requests; date overlap filter (`endDate >= from AND startDate <= to`, each independently nullable); assemble `TeamLeaveEntry` using `leaveRequestService.history(id)` and `leaveRequestService.listComments(id)` per request; batch-resolve `employeeUsername` via `userService.getUsernamesByIds`; return 200 empty array when no matches; write `access_log` row on denied access.

**Never:** No new Flyway migration; no audit event for this read; no direct repository access from `team` to `leave_requests` — query is in `LeaveRequestRepository` via `LeaveRequestService`; do not include non-APPROVED requests.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Manager with approved reports | MANAGER auth, direct reports have APPROVED requests | 200 `TeamLeaveEntry[]` each with `employeeUsername`, `comments` oldest-first, `history` in order | N/A |
| Date range filter | MANAGER, `?from=2026-08-01&to=2026-08-31` | Only APPROVED requests where `startDate <= 2026-08-31 AND endDate >= 2026-08-01` | N/A |
| Omitted date params | MANAGER, no `from`/`to` | All APPROVED requests for direct reports, no date restriction | N/A |
| No approved reports | MANAGER, no APPROVED requests for their reports | 200 empty array | N/A |
| EMPLOYEE caller | EMPLOYEE auth | 403 `ACCESS_DENIED`; `access_log` row `allowed=false` | N/A |
| HR_ADMIN caller | HR_ADMIN auth | 403 `ACCESS_DENIED`; `access_log` row `allowed=false` | N/A |

</frozen-after-approval>

## Code Map

- `backend/src/main/java/com/company/leave/leave/LeaveRequestRepository.java` -- add `findApprovedTeamLeaveInRange(@Param("managerId") long managerId, @Param("from") LocalDate from, @Param("to") LocalDate to)` JPQL: `status=APPROVED AND approverId=:managerId AND (:from IS NULL OR endDate >= :from) AND (:to IS NULL OR startDate <= :to) ORDER BY startDate ASC, id ASC`
- `backend/src/main/java/com/company/leave/leave/LeaveRequestService.java` -- expose `getApprovedTeamLeaveInRange(long managerId, LocalDate from, LocalDate to)` delegating to the new repository method
- `backend/src/main/java/com/company/leave/team/TeamLeaveEntry.java` -- new response DTO; `@Value @Builder`; fields: `long requestId`, `long employeeId`, `String employeeUsername`, `long leaveTypeId`, `LocalDate startDate`, `LocalDate endDate`, `int workingDays`, `String status`, `List<Comment> comments`, `List<HistoryEntry> history`; `@Schema(name = "TeamLeaveEntry")`
- `backend/src/main/java/com/company/leave/team/TeamLeaveService.java` -- new; injects `AccessPolicy`, `LeaveRequestService`, `UserService`; method `getTeamLeave(LocalDate from, LocalDate to)`: `requireRole(MANAGER)` → `currentUserId()` → `getApprovedTeamLeaveInRange` → batch usernames → per-request `listComments` + `history` → return `List<TeamLeaveEntry>`
- `backend/src/main/java/com/company/leave/team/TeamLeaveController.java` -- new `@RestController @RequestMapping("/team")`; `GET /leave` with optional `@RequestParam @DateTimeFormat(iso=DATE) LocalDate from, to`; returns `List<TeamLeaveEntry>`
- `backend/src/test/java/com/company/leave/team/TeamLeaveControllerIntegrationTest.java` -- new Testcontainers integration test

## Tasks & Acceptance

**Execution:**
- [ ] `backend/src/main/java/com/company/leave/leave/LeaveRequestRepository.java` -- add `findApprovedTeamLeaveInRange` JPQL query method
- [ ] `backend/src/main/java/com/company/leave/leave/LeaveRequestService.java` -- add `getApprovedTeamLeaveInRange(long managerId, LocalDate from, LocalDate to)` delegating to the repository method
- [ ] `backend/src/main/java/com/company/leave/team/TeamLeaveEntry.java` -- create `@Value @Builder` DTO with all fields per openapi.yaml `TeamLeaveEntry` schema
- [ ] `backend/src/main/java/com/company/leave/team/TeamLeaveService.java` -- create service: requireRole(MANAGER), currentUserId() → managerId, assemble TeamLeaveEntry per request using listComments + history
- [ ] `backend/src/main/java/com/company/leave/team/TeamLeaveController.java` -- create controller at `/team`; GET `/leave` returning `List<TeamLeaveEntry>`
- [ ] `backend/src/test/java/com/company/leave/team/TeamLeaveControllerIntegrationTest.java` -- integration test: manager happy path, date filter, empty result, EMPLOYEE 403, HR_ADMIN 403

**Acceptance Criteria:**
- Given an authenticated MANAGER, when `GET /api/team/leave` is called, then 200 with `TeamLeaveEntry[]` containing only their direct reports' APPROVED requests, each entry carrying `employeeUsername`, `comments` oldest-first, and `history` in chronological order (FR14, FR23).
- Given `?from=2026-08-01&to=2026-08-31`, when called by a MANAGER, then only APPROVED requests overlapping that range are returned; a request ending before August or starting after August is excluded.
- Given no `from`/`to` params, when called, then all APPROVED requests for the caller's direct reports are returned with no date restriction.
- Given an approved request, when its `TeamLeaveEntry` is inspected, then it includes the full `comments` thread oldest-first and full `history` timeline, needing no second call (FR14, FR15, FR45).
- Given an authenticated EMPLOYEE or HR_ADMIN, when `GET /api/team/leave` is called, then 403 `ACCESS_DENIED` and an `access_log` row is written with `allowed=false` (FR21, FR25).
- Given the `leave` module holds `getApprovedTeamLeaveInRange` and `team` calls it, when reviewed, then `team` never reaches `leave`'s repository or entity directly (AD-2).

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Design Notes

`findApprovedTeamLeaveInRange` uses `approverId = managerId` as the direct-report scope proxy: at submission time every request sets `approverId` from the employee's `manager_id`, so this correctly returns only the manager's direct reports' approved leave.

Date overlap condition: a request overlaps [from, to] when `startDate <= to AND endDate >= from`. Both bounds are independently nullable; a null `from` means no lower bound, a null `to` means no upper bound.

The `team` package depends on `leave` (for service calls and DTOs) but never on `leave`'s repositories or entities directly, satisfying AD-2's cross-module access rule.

## Verification

**Commands:**
- `cd backend; .\mvnw.cmd -Dtest=TeamLeaveControllerIntegrationTest test` -- expected: all integration test cases pass
- `cd backend; .\mvnw.cmd -Dtest=LeaveRequestControllerIntegrationTest,ApprovalControllerIntegrationTest test` -- expected: existing test suites pass unaffected
