---
title: 'Story 2.6: Cancel a Pending Request'
type: 'feature'
created: '2026-10-25'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '724254eb7d1b2c6830ca0f2cc536c5c9d5ba8342'
context:
  - '{project-root}/backend/AGENTS.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Employees need to withdraw pending leave requests they no longer need without losing the request record or leaving reserved balance unavailable.

**Approach:** Add an owner-only cancellation operation for pending leave requests. Atomically mark the request cancelled, release its reserved working days, record the release in the balance ledger, and audit the action.

## Boundaries & Constraints

**Always:** Lock the request while transitioning it; enforce ownership through the shared `AccessPolicy`; allow cancellation only from `PENDING`; keep request, balance, ledger, and audit changes in one transaction; preserve the request row and its visibility in the caller's request list; release exactly the request's working days; use the existing API error contract and denied-access logging.

**Never:** Delete a request, allow a manager or HR to cancel another employee's request, change submission/edit behavior, add a migration or frontend UI, or write balance and ledger state outside `BalanceService`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Owner cancels pending request | Authenticated owner; request is `PENDING` | 200 response with `CANCELLED`; row remains visible | N/A |
| Owner cancels terminal request | Request is `APPROVED`, `REJECTED`, or `CANCELLED` | No state or balance changes | 409 `INVALID_TRANSITION` |
| Non-owner attempts cancellation | Request belongs to another employee | No request data or mutation | 403 `ACCESS_DENIED`; denied access-log entry |
| Unknown request | Request ID does not exist | No mutation | 404 `NOT_FOUND` |

</frozen-after-approval>

## Code Map

- `backend/src/main/java/com/company/leave/leave/LeaveRequestController.java` — add `POST /{requestId}/cancel` beside existing request routes and match their API conventions.
- `backend/src/main/java/com/company/leave/leave/LeaveRequestService.java` — orchestrate the transaction; reuse `findByIdForUpdate()`, owner authorization, pending-state validation, response mapping, and audit patterns from `update()`.
- `backend/src/main/java/com/company/leave/leave/LeaveRequest.java` — add an encapsulated pending-to-cancelled transition; `RequestStatus` already includes `CANCELLED`.
- `backend/src/main/java/com/company/leave/balances/BalanceService.java` — add/use a release operation that decreases reserved days and writes one negative `RELEASE` ledger row with actor and request context. Do not reuse edit adjustment in a way that writes a zero-day `RESERVE`.
- `backend/src/main/java/com/company/leave/security/AccessPolicy.java` — use `requireOwner()`; rely on the existing access-log mechanism for denied calls.
- `backend/src/main/java/com/company/leave/audit/AuditService.java` — use `recordEvent()` for the cancellation actor and timestamp.
- `backend/src/test/java/com/company/leave/leave/LeaveRequestControllerIntegrationTest.java` — extend the existing PostgreSQL-backed MockMvc tests alongside pending-request edit coverage.
- `backend/src/main/resources/openapi.yaml` — cancellation is already documented; verify the implementation matches its response and error contract.
- `backend/curl/` — add a runnable Story 2.6 usage example following the existing authenticated curl-guide patterns.
- `_bmad-output/planning-artifacts/epics.md` — authoritative Story 2.6 acceptance criteria.

## Tasks & Acceptance

**Execution:**
- [x] `backend/src/main/java/com/company/leave/leave/LeaveRequest.java` — implement a guarded cancellation transition so only pending requests can become cancelled.
- [x] `backend/src/main/java/com/company/leave/balances/BalanceService.java` — release the request reservation and write the matching `RELEASE` ledger entry in the caller's transaction.
- [x] `backend/src/main/java/com/company/leave/leave/LeaveRequestService.java` — implement locked, owner-only, pending-only cancellation with atomic balance release, persistence, and audit.
- [x] `backend/src/main/java/com/company/leave/leave/LeaveRequestController.java` and `backend/src/main/resources/openapi.yaml` — expose the cancellation operation and keep documented responses aligned with runtime behavior.
- [x] `backend/src/test/java/com/company/leave/leave/LeaveRequestControllerIntegrationTest.java` — cover success effects, visibility/no deletion, terminal states, unauthorized ownership and access logging, unknown ID, and atomicity.
- [x] `backend/curl/story-2.6-cancel-a-pending-request.md` — document a runnable successful cancellation and relevant error example.

**Acceptance Criteria:**
- Given an owner's pending request, when it is cancelled, then the response reports `CANCELLED`, the request remains stored and visible in `GET /api/requests`, and its reserved days become available for another submission.
- Given a successful cancellation, when the transaction commits, then reserved days decrease by exactly the request's working-day count and one `RELEASE` ledger row records the change, actor, request ID, and timestamp.
- Given a successful cancellation, when the transaction commits, then an audit event records the cancellation, actor, and timestamp, atomically with the status and balance changes.
- Given an approved, rejected, or already-cancelled request, when cancellation is attempted, then the API returns 409 `INVALID_TRANSITION` without changing the request, balance, ledger, or audit state.
- Given a request owned by another employee, when cancellation is attempted, then the API returns 403 `ACCESS_DENIED` and writes a denied access-log row without exposing or changing the request.
- Given an unknown request ID, when cancellation is attempted, then the API returns 404 `NOT_FOUND`.

## Implementation Notes

The release should be a dedicated balance-service operation rather than an edit adjustment that may emit an unrelated reservation ledger row. The request lock, balance mutation, ledger write, status transition, and audit event must share the existing database transaction.

Implementation follows the layered modular monolith's direct service-call pattern (AD-1/AD-2): the leave service owns the request transition and delegates balance writes to `BalanceService`. The cancellation path locks the request first, releases through a dedicated mandatory-transaction balance operation, and records the cancellation audit before commit so an audit failure rolls back all state changes.

## Spec Change Log

## Review Triage Log

| Finding | Verdict | Route | Evidence |
|---|---|---|---|
| Startup `/auth/me` 401 can reload the signed-out login page repeatedly. | high | defer | carried: The frontend initializer runs before route selection, and AD-6 sends any empty-body 401 to a hard reload; this is an independent frontend issue, not caused by Story 2.6. |
| OpenAPI routes `/auth/me` and `/leave-types` are not implemented by backend controllers. | medium | defer | carried: The frontend architecture explicitly records these endpoints as backend follow-up; they are unrelated to cancellation. |
| OpenAPI `*Username` fields are not returned by backend DTOs. | medium | defer | carried: These fields are likewise explicitly listed as a backend follow-up in the frontend architecture, outside this story. |
| `ApprovalDetail`'s sample omits the advertised username fields. | low | defer | carried: The sample omission is acknowledged in the frontend architecture's deferred backend follow-up; schema generation reads the schema, not this example. |
| The frontend architecture bumps data versions after cancellation `409` responses. | false | — | carried: A 409 can mean a request displayed as pending was concurrently approved or rejected; refreshing after that stale-state conflict is useful and safe. |
| The frontend capability table does not map FR26. | low | defer | carried: FR26 is within the frontend architecture's FR1–FR31 scope but is not mapped; clarifying its backend-only surface is separate architecture work. |
| The curl guide uses Bash-specific syntax without stating the shell requirement. | low | patch | Added an explicit Bash/Git Bash/WSL requirement to the guide. |
| Cancellation can return `409 INSUFFICIENT_BALANCE` when its balance row is missing, but OpenAPI documented only `INVALID_TRANSITION`. | medium | patch | `BalanceService.releaseReservation()` has a missing-row `INSUFFICIENT_BALANCE` branch; OpenAPI and the controller description now document it, and the contract test asserts it. |
| Verification-gap reviewer independently reported the same missing `409 INSUFFICIENT_BALANCE` contract case. | medium | patch | Same root cause and evidence as the preceding finding; the OpenAPI 409 examples and contract assertion cover both codes. |
| Frontend adversarial review H1 still presents resolved state invalidation and feature-service constraints as open. | medium | defer | The revised spine now defines route-scoped feature services and data-version signals, while H1 still describes those as missing; the stale review may mislead frontend implementation and is unrelated to cancellation. |
| Frontend adversarial review H2 still says a page refresh logs the user out despite the `/auth/me` bootstrap. | medium | defer | The revised spine specifies startup `GET /auth/me`, but H2 still describes refresh as losing the session; this stale review may mislead frontend implementation and is unrelated to cancellation. |
| Frontend adversarial review H3 still describes a dangling `Unauthorized` OpenAPI reference. | medium | defer | `components.responses.Unauthorized` now exists in the OpenAPI document, but H3 still says it is undefined; this stale review may mislead frontend contract generation work and is unrelated to cancellation. |
| Frontend adversarial review H5 still places `LeaveTypeNamePipe` in `shared/`. | medium | defer | The revised architecture places the stateful pipe under `core/ui`, but H5 still identifies it as a `shared`/`core` layering violation; this stale review may mislead frontend implementation and is unrelated to cancellation. |
| Frontend adversarial review H6 still says there is no cross-feature URL contract. | medium | defer | The revised architecture defines canonical request routes and `core/routing/AppLinks`, but H6 still says those are absent; this stale review may mislead frontend implementation and is unrelated to cancellation. |
| The frontend architecture frontmatter says `final` while its adversarial review says `NOT READY`. | medium | defer | These conflicting artifact states leave it unclear whether frontend implementation may proceed; reconciling the architecture/review state is unrelated to cancellation. |
| The frontend architecture defines an `audit` data-version signal but lists no write that bumps it. | medium | defer | `AD-5` includes `audit` among versioned data areas, but its mutation list has no audit-producing write; audit screens may remain stale after changes. This is outside the cancellation backend scope. |
| The frontend architecture's “My Requests” filter contract is inconsistent for managers. | false | — | The API description says a manager *may* filter to a direct report, not that this is the only allowed `employeeId`; `LeaveRequestService.list()` and `AccessPolicy.requireOwnerManagerOrHr()` allow the owner to filter to their own ID. The current-user filter therefore works for managers. |
| The revised Epic 2 context omits the status-filter and request-history API requirements. | medium | defer | The prior context named the `GET /requests` status filter and `GET /requests/{id}/history` contract, while the revised context omits them; this context-file change is unrelated to cancellation and needs reconciliation with its source requirements. |
| The revised Epic 2 context omits notification payload and owner-only visibility details. | medium | defer | It no longer names the notification contents or that users read only their own notifications; this context-file change is unrelated to cancellation. |
| The revised Epic 2 context does not specify what “best-effort” means for notification failures. | medium | defer | The wording does not establish whether a failed notification write rolls back or is swallowed; this is an unrelated, pre-existing context question. |
| The curl guide's foreground `docker compose up --build` blocks the following examples. | low | patch | Changed startup to `docker compose up --build -d` so the guide can continue to its curl commands. |
| Cancellation's missing-balance `409 INSUFFICIENT_BALANCE` branch had no integration coverage. | medium | patch | Added `cancellingWithoutBalanceReturnsInsufficientBalanceWithoutMutation`, asserting the API code and unchanged request, absent balance, ledger, and audit state; all 42 integration tests passed. |
| Verification-gap reviewer independently found the missing-balance cancellation branch untested. | medium | patch | Same root cause as the preceding finding; the new integration test covers this branch and all 42 integration tests passed. |

## Verification

**Commands:**
- `backend\mvnw.cmd -f backend\pom.xml -Dtest=LeaveRequestControllerIntegrationTest test` — cancellation integration coverage passes.
- `backend\mvnw.cmd -f backend\pom.xml -Dtest=OpenApiDocsIntegrationTest test` — API contract verification passes.
- `python -c "import yaml; yaml.safe_load(open(r'backend\src\main\resources\openapi.yaml', encoding='utf-8'))"` — OpenAPI YAML parses successfully.
