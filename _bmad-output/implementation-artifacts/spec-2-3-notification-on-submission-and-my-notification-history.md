---
title: 'Story 2.3: Notification on Submission and My Notification History'
type: 'feature'
created: '2026-10-23'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context: ['{project-root}/backend/AGENTS.md']
baseline_commit: '53d2f083c7d706f84598195c302ff0468f8c59ec'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Approvers may miss new leave requests, and users cannot review their notifications.

**Approach:** Render reusable notification templates, record submission notifications for the approver, and expose caller-scoped history. Dispatch after request commit so notification failure cannot undo a request or reservation.

## Boundaries & Constraints

**Always:** `notifications` owns its schema and depends only on `common` and `security`. Use `AccessPolicy` for caller scope; never accept a requested user id. Notify the request's `approverId`. Keep request, reservation, ledger, and audit writes atomic. Catch and ERROR-log notification failures; persist separately after request commit. Do not contact a mail server. This story is backend/API only; leave the placeholder frontend unchanged.

**Never:** Modify existing migrations, use SMTP, async dispatch, or an event bus; expose other users' rows; or make `notifications` depend on `leave`/`balances`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Submission | Valid request with approver | Core writes commit; one `SUBMITTED` row is recorded; rendered content has type, dates, day count, status, and link | No mail transport |
| Notification failure | Render or write fails after commit | Request and reservation stay committed | ERROR log; return normally |
| History | Authenticated caller requests `GET /api/notifications` | 200 with only caller rows and documented fields | Shared authentication handling |

</frozen-after-approval>

## Code Map

- `backend/src/main/resources/db/migration/` — append a notifications migration after V11; retain existing schema history.
- `backend/src/main/java/com/company/leave/notifications/package-info.java` — module marker; add package-private persistence and public service/API types without domain dependencies.
- `backend/src/main/java/com/company/leave/leave/LeaveRequestService.java` — `submit(...)` atomically writes request, reservation, and audit and knows the approver. Dispatch synchronously after commit, passing values rather than entities.
- `backend/src/main/java/com/company/leave/leave/LeaveRequest.java` and `LeaveTypeService.java` — use request data and public leave-type lookup for rendered content; do not expose entities.
- `backend/src/main/java/com/company/leave/security/AccessPolicy.java` and `backend/src/main/java/com/company/leave/common/GlobalExceptionHandler.java` — reuse caller scope and the common API error contract.
- `backend/src/main/resources/openapi.yaml` — existing history operation and `Notification` schema; keep the response aligned.
- `backend/src/test/java/com/company/leave/leave/LeaveRequestControllerIntegrationTest.java` — reuse PostgreSQL Testcontainers/MockMvc/JDBC patterns; cover approver delivery, isolation, and failure behavior.
- `_bmad-output/implementation-artifacts/spec-2-2-submit-leave-request-against-real-balance.md` — completed predecessor: preserve atomic core writes and its integration-test conventions.
- `_bmad-output/planning-artifacts/architecture/architecture-frontend-company-leave-2026-10-23/.memlog.md` — frontend notes specify standalone components, lazy routes, role shells, signals, `httpResource()` reads, and `HttpClient` mutations. `frontend/index.html` is a placeholder; no routes or services exist.

## Tasks & Acceptance

**Execution:**
- [x] Add the notifications migration and package-private entity/repository; leave prior migrations unchanged.
- [x] Add reusable subject/body templates and a public notification service that records `SUBMITTED` and ERROR-logs failures.
- [x] Dispatch synchronously after commit so notification persistence cannot affect the request transaction.
- [x] Add caller-scoped `GET /api/notifications`; align OpenAPI and add curl examples.
- [x] Add PostgreSQL integration tests for content, approver delivery, isolation, and failure preserving committed core writes.
- [x] Leave the frontend unchanged; UI is out of scope for this story.

**Acceptance Criteria:**
- Given the migration is applied, when the schema is inspected, then `notifications` has the required columns, types, identity key, and foreign keys.
- Given submission commits, when dispatch runs, then exactly one `SUBMITTED` notification targets `approverId` and its template has all required details; no SMTP is contacted.
- Given rendering or notification persistence fails, when submission completes, then request/reservation remain committed and the failure is ERROR-logged without failing submission.
- Given an authenticated caller, when they request history, then only their rows with the documented fields are returned.
- Given module dependencies are inspected, when dispatch is reviewed, then notifications depends only on `common`/`security` and uses no async/event publisher.

## Implementation Notes

Preserve Story 2.2's atomic request/reservation/ledger/audit transaction. A caught PostgreSQL write failure inside that transaction is not enough; dispatch only after commit.

Architecture pattern: layered modular monolith with synchronous public service calls (AD-1/AD-2), `notifications` as a leaf, and best-effort delivery (AD-12). Run notification persistence in a separate `REQUIRES_NEW` transaction after commit; wrap transaction execution itself in the notification service's catch so commit-time failures are also contained.

Implemented the V12 schema, notification service/API, template renderer, request after-commit hook, caller scoping, OpenAPI auth responses, and curl guide. Added renderer and PostgreSQL integration coverage, including a failing notification write that proves the request still commits.

## Spec Change Log

| Date | Change |
|------|--------|
| 2026-10-23 | Recorded final review triage and implementation commit. |

## Review Triage Log

| # | Source | Claim | Verdict | Evidence | Route |
|---|---|---|---|---|---|
| 1 | verification-gap | Successful dispatch did not assert rendered body appears in logs | low | The service logs the rendered body; integration coverage now captures output and asserts the working-day count and request link. | patch |
| 2 | verification-gap | Notification-write failure did not assert ERROR logging | low | The failure path logs at ERROR; the integration test now captures and asserts the error message and injected database failure. | patch |
| 3 | edge-case-hunter | Rendered-body logging lacked a direct assertion | duplicate | Covered by row 1; integration coverage now captures output and asserts the working-day count and request link. | no change |

## Verification

**Commands:**
- `backend\mvnw.cmd -f backend\pom.xml test` — all 84 tests pass.

**Commit:** `61ec86ff875ac1371086d991f43750acc5589cc5` (`feat(notifications): add submission alerts and history`).
