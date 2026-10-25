---
title: 'Story 2.7: The History Timeline on a Request'
type: 'feature'
created: '2026-10-25'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '2a246cd58c26880d1bb37ab24f9f94b21824fbb0'
context:
  - '{project-root}/backend/AGENTS.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/epics.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Employees and other authorized viewers cannot see an ordered account of what happened to a leave request, even though submission, edit, and cancellation already create audit events.

**Approach:** Expose a request-history endpoint assembled by `leave` from audit events returned through `AuditService`. Store readable snapshots with future lifecycle audit events so the timeline can describe the submission, edits, and cancellation without changing the append-only audit model.

## Boundaries & Constraints

**Always:** Return history oldest first with deterministic ordering for equal timestamps. Include the exact event types `SUBMITTED`, `EDITED`, `CANCELLED`, `APPROVED`, `REJECTED`, and `COMMENT`; Epic 2 populates the first three and Epic 3 may populate the others. Each response entry includes its type, actor ID, occurrence time, and readable detail, aligned with the documented response schema. Use `AccessPolicy.requireOwnerManagerOrHr(...)` for visibility and retain denied-access logging. Keep history assembly in `leave` and audit reads behind `AuditService`; audit rows remain insert-only. For legacy events without a detail snapshot, explicitly state that event details were not recorded rather than implying a change summary.

**Never:** Let `audit` read leave tables; expose audit persistence entities or general CRUD; bypass shared authorization; alter request lifecycle or balance behavior; add a migration, frontend UI, or unrelated API changes.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Authorized history read | Existing request; owner, direct manager, or HR | 200 with the documented history-entry shape, oldest first | N/A |
| Lifecycle history | Request submitted, edited twice, then cancelled | One `SUBMITTED`, two `EDITED`, and one `CANCELLED` entry in action order; details summarize the captured changes | N/A |
| Unauthorized history read | Existing request outside caller's permitted scope | No request/history data; access log records denial | 403 `ACCESS_DENIED` |
| Unknown request | Nonexistent request ID | No history returned | 404 `NOT_FOUND` |

</frozen-after-approval>

## Code Map

- `backend/src/main/java/com/company/leave/leave/LeaveRequestController.java` — add the history route beside the existing request detail route.
- `backend/src/main/java/com/company/leave/leave/LeaveRequestService.java` — reuse request lookup and `AccessPolicy.requireOwnerManagerOrHr(...)`; assemble/map entries here after authorization.
- `backend/src/main/java/com/company/leave/audit/AuditService.java` and `AuditEventRepository.java` — add a narrow read-only query for supported request lifecycle events, including request comments selected by their request ID in audit details, ordered by occurrence time with a stable tie-breaker; do not add audit CRUD or leave lookups.
- `backend/src/main/java/com/company/leave/users/UserService.java` and `UserRepository.java` — batch-read actor usernames through the owning `users` service to match the existing history schema.
- `backend/src/main/resources/openapi.yaml` — history path and `HistoryEntry` schema already exist; keep runtime behavior and documented enum/fields aligned.
- `backend/src/test/java/com/company/leave/leave/LeaveRequestControllerIntegrationTest.java` — extend PostgreSQL-backed authorization and lifecycle-history coverage.
- `backend/src/test/java/com/company/leave/common/OpenApiDocsIntegrationTest.java` — assert the history route/schema remains documented.
- `backend/curl/story-2.4-see-my-requests-and-their-status.md` — follow its authenticated request-detail pattern for the new story guide.
- `backend/curl/story-2.7-the-history-timeline-on-a-request.md` — add a runnable history example and expected success/denial outcomes.
- `backend/src/main/resources/db/migration/V2__add_audit_immutability_trigger.sql` — existing update/delete protection is a constraint to preserve, not modify.

## Tasks & Acceptance

**Execution:**
- [x] `backend/src/main/java/com/company/leave/audit/AuditEventRepository.java` and `AuditService.java` — expose only the request-event read needed by history, retaining audit ownership and immutability.
- [x] `backend/src/main/java/com/company/leave/leave/LeaveRequestService.java` and `LeaveRequestController.java` — authorize and implement history retrieval, map audit types to the exact enum, and return the documented response.
- [x] `backend/src/main/java/com/company/leave/leave/LeaveRequestService.java` — add submission/edit/cancellation detail snapshots at their existing audit-write points without changing transaction or balance semantics.
- [x] `backend/src/main/java/com/company/leave/leave/HistoryEntry.java` — add the typed response model matching the OpenAPI fields, including actor username.
- [x] `backend/src/main/java/com/company/leave/users/UserService.java` and `UserRepository.java` — batch-resolve actor usernames without cross-module repository access.
- [x] `backend/src/main/resources/openapi.yaml` — document authentication/not-found responses, require the response fields, and retain all six event types.
- [x] `backend/src/test/java/com/company/leave/leave/LeaveRequestControllerIntegrationTest.java` and `backend/src/test/java/com/company/leave/common/OpenApiDocsIntegrationTest.java` — cover timeline content/order, access/404 behavior, legacy details, and schema compatibility.
- [x] `backend/curl/story-2.7-the-history-timeline-on-a-request.md` — add authenticated timeline, unauthorized, and unknown-request examples.

**Acceptance Criteria:**
- Given an authorized viewer and an existing request, when `GET /api/requests/{requestId}/history` is called, then it returns HTTP 200 with the documented entry fields and chronological order.
- Given a request submitted, edited twice, and cancelled, when history is fetched, then it contains the corresponding four entries in action order and readable details reflect the event snapshots rather than current request state.
- Given the history event type, when the API contract is checked, then the enum contains exactly the six specified values, with approval/rejection/comment allowed but not fabricated by Epic 2.
- Given a caller outside the owner's permitted owner/manager/HR scope, when history is fetched, then it returns 403 `ACCESS_DENIED` and writes a denied access-log entry without exposing request data.
- Given an unknown request ID, when history is fetched, then it returns 404 `NOT_FOUND`.
- Given history is read, when events share a timestamp, then their order remains deterministic and audit rows are not updated or deleted.

## Implementation Notes

Use the package-by-feature layered modular monolith: controller → leave service → audit service → audit repository. The `leave` module owns history assembly; audit exposes a filtered read-only service method and never reads leave data. Keep reads and lifecycle audit writes in the existing transaction paths, pass actor IDs explicitly, and preserve the database-enforced append-only audit invariant.

Implemented the read-only event query, including Epic 3's `REQUEST_COMMENT_ADDED` audit format, six-value response enum/DTO, authorized history endpoint, and timestamp/ID ordering. Only supported request-history actions are selected. New lifecycle audit rows include a JSON detail snapshot; legacy rows without a text detail return the approved explicit fallback. Updated the handwritten OpenAPI contract with 401/404 responses and required history-entry fields, and added integration coverage plus a runnable curl guide.

## Spec Change Log

## Review Triage Log

| Finding | Route | Triage |
|---|---|---|
| Blind 1: history Postman request sends an edit instead of fetching history | Defer | The request is an untracked, user-owned Postman change present before this implementation; it was left untouched and recorded in `deferred-work.md`. |
| Blind 2: Postman cookie headers omit `access_token=` | Defer | The three affected request files are user-owned Postman changes; preserved and recorded in `deferred-work.md`. |
| Blind 3: Postman descriptions still describe request submission | Defer | These are user-owned request definitions, not story implementation changes; preserved and recorded in `deferred-work.md`. |
| Blind 4: history OpenAPI omits 401 | Patch | Added the 401 problem response and runtime/OpenAPI contract assertions. |
| Blind 5: `HistoryEntry` does not require its fields | Patch | Marked all five response fields required and asserted the contract. |
| Blind 6: response examples omit `actorUsername` | Patch | Added usernames to each example entry and made the field required to match the runtime response. |
| Blind 7: description implies events before their writers exist | Patch | Clarified the staged lifecycle coverage: Epic 2 writes submission/edit/cancellation; later events appear when their owning actions record them. |
| Blind 8: comment audit format cannot reach history | Patch | Query now includes `REQUEST_COMMENT_ADDED` by `details_json.requestId`; mapping reads the `comment` field, with integration coverage. |
| Blind 9: unsupported request actions can abort history | Patch | Repository query now selects only the five supported request actions plus `REQUEST_COMMENT_ADDED`; unknown actions are excluded from the endpoint query. |
| Blind 10: complete histories are unbounded | Defer | The story requires the complete ordered timeline and specifies no paging contract. A limit could silently truncate history; a future pagination contract is recorded in `deferred-work.md`. |
| Blind 11: unrelated manager denial is not tested | Patch | Added a denial and access-log assertion for a manager other than the request owner's direct manager. |
| Edge 1: valid JSON without a text `detail` aborts history | Patch | Missing/null detail fields now use the approved legacy fallback; malformed JSON and present non-text values still fail explicitly. |
| Edge 2: unrelated `/auth/me` and `/leave-types` paths appear in the diff | False | `openapi.yaml` was already user-modified at task start; these paths were pre-existing changes and were not added or altered by Story 2.7. |
| Verification-gap reviewer | No finding | Reviewer could not access its diff input and returned no review findings. |

## Design Notes

Lifecycle event details must be captured at the point of mutation because later request state cannot reliably reconstruct prior edits. Keep the audit read filtered by event target/type and request ID; mapping of domain-specific history remains in `leave`.

## Verification

**Commands:**
- `backend\\mvnw.cmd -f backend\\pom.xml -Dtest=LeaveRequestControllerIntegrationTest,OpenApiDocsIntegrationTest,AuditServiceImmutabilityTest test` — 60 tests pass, including history ordering, comment events, legacy details, access, OpenAPI, and audit immutability checks.
