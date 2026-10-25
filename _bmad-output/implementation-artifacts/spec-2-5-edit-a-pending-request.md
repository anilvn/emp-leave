---
title: 'Story 2.5: Edit a Pending Request'
type: 'feature'
created: '2026-10-23'
status: 'in-review'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '6e9ec4e9d314f7f53365a26c4a7aab5fe403a8ee'
context:
  - '{project-root}/backend/AGENTS.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-company-leave-2026-10-22/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Employees cannot change a leave request after submission, even while it is still pending.

**Approach:** Implement the documented owner-only `PUT /api/requests/{requestId}` operation. Recalculate working days, update the pending request, adjust its balance reservation, and record the edit atomically.

## Boundaries & Constraints

**Always:** Allow edits only by the request owner and only while status is `PENDING`. Require the submitted `leaveTypeId` to match the existing request. Validate the date range and calculate weekdays through `WorkingDayCalculator`. Compare the new reservation against available days plus this request's existing reservation. Commit request, balance, ledger, and audit changes in one transaction. Preserve request identity, employee, approver, status, submission time, and decision fields.

**Never:** Allow a manager to edit a report's request or HR to edit another user's request. Change leave type, edit a terminal request, delete a request, or change the database schema. Add frontend behavior or send a new notification for edits.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Valid owner edit | Own `PENDING` request; same leave type; valid dates and optional notes | 200 with the updated request and recalculated `workingDays` | N/A |
| Terminal request | `APPROVED`, `REJECTED`, or `CANCELLED` | No request or balance change | 409 `INVALID_TRANSITION` |
| Leave type change | Different `leaveTypeId` | No change | 400 `LEAVE_TYPE_CHANGE_NOT_ALLOWED`; unchanged type is accepted |
| Invalid date range | End before start, or no weekdays in range | No change | 400 `VALIDATION_FAILED` |
| Insufficient additional balance | New working-day count exceeds available days plus this request's held days | Existing request and reservation remain untouched | 409 `INSUFFICIENT_BALANCE` |
| Non-owner edit | Another employee, their manager, or HR edits the request | No request data is changed | 403 `ACCESS_DENIED` and denied access-log row |
| Changed reservation | Successful edit changes `workingDays` | Release old and reserve new amount; corresponding ledger rows and audit event commit with request | Transaction rolls back all changes on failure |

</frozen-after-approval>

## Code Map

- `backend/src/main/java/com/company/leave/leave/LeaveRequestController.java` — add the missing `PUT` route; existing GET/POST routes delegate to the service.
- `backend/src/main/java/com/company/leave/leave/LeaveRequestService.java` — add transactional edit orchestration; reuse `WorkingDayCalculator`, `toResponse`, and repository lookup.
- `backend/src/main/java/com/company/leave/leave/LeaveRequest.java` — entity currently has no mutation method; keep field changes within the leave package.
- `backend/src/main/java/com/company/leave/leave/LeaveRequestRepository.java` — reuse `findById` and `save`; no schema change is expected.
- `backend/src/main/java/com/company/leave/balances/BalanceService.java` — sole balance/ledger writer; `reserve` is mandatory-transaction and has no adjustment path yet.
- `backend/src/main/java/com/company/leave/security/AccessPolicy.java` — existing owner/manager/HR helper is broader than edit's owner-only rule; keep authorization centralized.
- `backend/src/main/java/com/company/leave/audit/AuditService.java` and `security/AccessLogFilter.java` — reuse event recording and automatic denial logging.
- `backend/src/main/resources/openapi.yaml` — PUT and `LeaveRequestUpdate` contract already exist; preserve/verify its response and error examples.
- `backend/src/test/java/com/company/leave/leave/LeaveRequestControllerIntegrationTest.java` — PostgreSQL-backed request, balance, ledger, audit, and access integration coverage; extend for edits.
- `backend/curl/story-2.5-edit-pending-request.md` — add runnable success and important failure examples, following the existing Story 2.4 guide.

## Tasks & Acceptance

**Execution:**
- [x] Add update input validation and the controller/service/entity edit path, enforcing owner-only access and `PENDING` status.
- [x] Add a transactional balance adjustment in `BalanceService`; check availability excluding the request's own held days and write matching ledger entries only when the reservation changes.
- [x] Record one audit event for every successful edit; rely on the shared access-log filter for denied requests.
- [x] Verify the existing OpenAPI operation matches runtime behavior and add integration tests for the I/O matrix, including rollback and balance/ledger/audit consistency.
- [x] Add a cookie-authenticated curl guide for successful edit, invalid transition, insufficient balance, and access denial.

**Acceptance Criteria:**
- Given an owner's pending request, when a valid same-type edit is submitted, then the API returns 200 with recalculated working days and updated notes/dates.
- Given a terminal request, changed leave type, invalid date range, or insufficient balance, when edited, then the documented error is returned and existing request/reservation state is unchanged.
- Given a request not owned by the caller, when edited by an employee, manager, or HR user, then the API returns 403 `ACCESS_DENIED` and records a denied access-log entry.
- Given an edit that changes working days, when the transaction commits, then reserved days equal the new request amount and matching ledger and audit records exist; a failure leaves all state unchanged.
- Given any successful edit, when its response is serialized, then it matches the existing `LeaveRequest` OpenAPI schema and retains fields not editable by the request payload.

## Implementation Notes

Architecture pattern: package-by-feature with a `controller → service → repository` flow. The leave service owns request mutation and calls the balance and audit service APIs within one transaction. Owner-only authorization stays centralized in `AccessPolicy`; edit locks the request and balance rows, and treats the request's existing reservation as available only to that request. `WorkingDayCalculator` counts in constant time and rejects counts outside the request's integer field range.

## Design Notes

Reservation validation must treat the request's current held days as available to that same request, not as free entitlement for another request. For example, an edit from four held days to five needs one additional day beyond the other currently available days. Keep balance and ledger writes inside the request's transaction.

## Verification

**Commands:**
- `backend\mvnw.cmd -f backend\pom.xml -Dtest=LeaveRequestControllerIntegrationTest test` — edit scenarios pass against PostgreSQL.
- `backend\mvnw.cmd -f backend\pom.xml -Dtest=OpenApiDocsIntegrationTest test` — documented PUT contract matches the application.
- `backend\mvnw.cmd -f backend\pom.xml -Dtest=WorkingDayCalculatorTest test` — date-boundary and overflow behavior.
- `python -c "import yaml; yaml.safe_load(open(r'backend\src\main\resources\openapi.yaml', encoding='utf-8'))"` — OpenAPI YAML parses.

**Results:** The request integration suite ran 36 tests, the OpenAPI suite ran 4 tests, and the weekday calculator suite ran 2 tests; all 42 passed with no failures or skips. The OpenAPI YAML parsed successfully, and `git diff --check` passed. The curl examples were not run against the Docker stack because Compose requires a configured `JWT_SECRET`; the guide documents the required local `.env` setup.

## Review Triage Log

| Finding | Verdict | Evidence and route |
|---|---|---|
| Concurrent edits can apply a stale reservation delta. | medium | Reproduced with two simultaneous PUT requests: request days were 6 while reserved days were 7. Added a pessimistic request-row lock and concurrency integration test; patch applied. |
| Static OpenAPI omitted PUT 404 and examples differed from generated runtime errors. | medium | Service returns `NOT_FOUND` for unknown IDs and shared exception advice supplies runtime details. Added the 404 reference and aligned error examples; patch applied. |
| No Postman collection request was added for PUT. | false | The missing optional Postman entry causes no broken endpoint behavior or failure of the requested curl-guide path; there is no separate user-facing Postman issue to fix. |
| Edit audit events do not include before/after metadata. | false | The spec requires one audit event per successful edit; before/after audit values are explicitly deferred to a later phase. |
| Unknown-ID PUT behavior lacked an integration assertion. | low | The controller maps the service's `NOT_FOUND` exception to 404; added a PUT 404 test that asserts no balance, ledger, or audit side effects; patch applied. |
| Unauthenticated PUT lacked a dedicated test and 401 documentation. | false | Authentication is enforced by the shared security filter and applied globally in OpenAPI; the story matrix does not add a PUT-specific authentication contract. |
| Tests did not assert that edits leave notifications unchanged. | low | The spec explicitly forbids edit notifications; added a notification-count assertion to the successful edit integration test; patch applied. |
| Request lookup did not serialize concurrent updates. | medium | Same stale-reservation race as the blind-content finding, independently reproduced; the row lock and concurrency test address it; patch applied. |
| A short range ending at `LocalDate.MAX` overflows after counting the final date. | medium | A focused test reproduced `DateTimeException`; weekday counting now uses bounded arithmetic and passes the boundary test; patch applied. |
| Very large ranges could monopolize a request thread, and counts beyond `int` were not representable. | medium | The prior implementation iterated day by day. Replaced it with constant-time arithmetic and maps unrepresentable counts to `VALIDATION_FAILED`; unit and integration tests pass; patch applied. |
| PUT unknown-ID response lacked a test. | low | Independent verification confirmed the gap; the new integration test asserts 404 `NOT_FOUND` and unchanged balance, ledger, and audit state; patch applied. |
| The PUT contract does not explicitly list 401. | low | The 401 omission predates this implementation in the hand-written contract, while global `cookieAuth` security already marks protected operations; defer to a project-wide OpenAPI auth-response consistency task. |
| Omitting optional `notes` clears the saved notes. | false | This is a PUT replacement of the editable payload fields; the spec permits optional notes but does not require preserving omitted prior content. |
| The PUT operation is tagged with FR9. | false | The tag predates this implementation, and the operation preserves submission metadata; no mismatched runtime behavior was established by the finding. |
| The 400 examples do not show every invalid-date variant. | false | The contract has a general `VALIDATION_FAILED` example, and the matrix requires response behavior rather than a separate example for every validation cause. |
| The Code Map names `findById` while the update path uses `findByIdForUpdate`. | false | The new repository lookup adds the row lock required to prevent concurrent reservation deltas; the requested spec-only wording change is not an implementation defect. |
| The existing review log repeats two findings and uses an unclear cross-reference. | false | The overlapping entries record separate review observations and do not affect code behavior; a spec-only cleanup is rejected by the review rules. |
| The access-denied curl example does not check the second login token. | low | Added an explicit non-empty-token guard so a failed login cannot be mistaken for a 403 authorization result; patch applied. |
| The success test does not assert audit actor and timestamp fields. | false | The update passes the authenticated actor id to `AuditService.recordEvent`, which persists it with `Instant.now()`; the acceptance criterion requires one matching audit event, which the test asserts. |
| The curl guide offers a local-start option but only documents Docker Compose. | low | Narrowed the introduction to the Docker HTTPS setup actually shown; patch applied. |
| Required-field validation had no missing-field integration assertion. | low | Added a missing-`startDate` request test asserting 400 and unchanged request, reservation, ledger, and edit-audit state; patch applied. |
| The hand-written OpenAPI file was syntax-checked but not semantically asserted. | low | Added an integration assertion for the static PUT schema references, response set, and documented problem codes; patch applied. |
