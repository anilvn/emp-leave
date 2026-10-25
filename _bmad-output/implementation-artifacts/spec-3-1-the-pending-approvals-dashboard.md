---
title: 'Story 3.1: The Pending Approvals Dashboard'
type: 'feature'
created: '2026-10-25'
status: 'in-review'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'da44c6dc4a1d3415d9293543b3c4192577578a91'
context:
  - '{project-root}/backend/AGENTS.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/epics.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Managers lack one page of pending requests from their direct reports with enough context to decide without making follow-up calls.

**Approach:** Add an authenticated pending-approvals API that returns each in-scope request together with employee and leave-type names, current balance, comments, and request history. Enforce approval scope in the service/repository path, using the shared `AccessPolicy`.

## Boundaries & Constraints

**Always:** Return only pending requests assigned to the caller. For managers, scope to their direct reports; for HR, include requests assigned to HR (their own and top-level managers who report to them). Use `AccessPolicy.currentScope()` and scoped repository querying, not controller filtering. Include all `LeaveRequest` fields, `employeeUsername`, `leaveTypeName`, the employee's current balance for the request's leave type, comments, and history in `ApprovalDetail`. Read balance and history through their owning public services; present comments/history oldest first. Return `200` with an empty array when no approvals are pending. Unauthorized employee access returns `403 ACCESS_DENIED` and is logged.

**Never:** Query another module's repositories/entities, expose requests outside the caller's approval scope, mutate requests or balances, implement approve/reject decisions or comment creation, or add a frontend dashboard in this story.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Manager queue | Authenticated manager with pending requests | `200`; only pending requests assigned to this manager, each with complete decision context | N/A |
| Empty queue | Authenticated manager with no assigned pending requests | `200` with `[]` | N/A |
| Employee access | Authenticated employee calls the queue endpoint | No approval data; `403 ACCESS_DENIED` | Denial is recorded by the existing access-log filter |
| HR queue | Authenticated HR with pending requests assigned to HR or top-level managers reporting to HR | `200`; only requests awaiting one of those decisions | N/A |

</frozen-after-approval>

## Code Map

- `backend/src/main/java/com/company/leave/approvals/` — package exists but has no controller, service, or response DTO; add the pending-approvals API here.
- `backend/src/main/java/com/company/leave/security/AccessPolicy.java` and `UserScope.java` — reuse `currentScope()` and the shared scope types; authorization failures already map to `403 ACCESS_DENIED`.
- `backend/src/main/java/com/company/leave/leave/LeaveRequestRepository.java` — existing scope queries do not guarantee caller-as-approver filtering (manager scope includes manager-owned requests; HR scope is global); add/use a query that returns pending requests assigned to the scope's approver IDs.
- `backend/src/main/java/com/company/leave/leave/LeaveRequestResponse.java` — source for the request fields to include in `ApprovalDetail`.
- `backend/src/main/java/com/company/leave/balances/BalanceService.java` — use `getBalancesForUser` to read current balance without crossing module ownership.
- `backend/src/main/java/com/company/leave/audit/AuditService.java` and `LeaveRequestService.java` — use existing history reads and mapping; comment text is present in immutable request-history audit events and can populate the comments thread without a new persistence path.
- `backend/src/main/java/com/company/leave/users/UserService.java` — reuse username and user lookups for employee and leave-type display data as applicable.
- `backend/src/main/resources/openapi.yaml` — pending endpoint and `ApprovalDetail` schema are already documented; keep runtime DTOs aligned.
- `backend/src/test/java/com/company/leave/leave/LeaveRequestControllerIntegrationTest.java`, `backend/src/test/java/com/company/leave/security/AccessLogFilterIntegrationTest.java`, and `backend/src/test/java/com/company/leave/common/OpenApiDocsIntegrationTest.java` — reuse PostgreSQL-backed integration, denial logging, and contract-test patterns.

## Tasks & Acceptance

**Execution:**
- [x] `backend/src/main/java/com/company/leave/leave/LeaveRequestRepository.java` and its service caller — add the pending approval query using `UserScope` and approver IDs so it returns only assigned `PENDING` requests, including HR's own and direct-report top-level-manager approvals.
- [x] `backend/src/main/java/com/company/leave/approvals/ApprovalController.java`, `ApprovalService.java`, and `ApprovalDetail.java` — add the authenticated endpoint and compose request, employee, leave type, current balance, comments, and history through owning module services.
- [x] `backend/src/test/java/com/company/leave/approvals/ApprovalControllerIntegrationTest.java` and `backend/src/test/java/com/company/leave/common/OpenApiDocsIntegrationTest.java` — verify manager/HR scoping, full detail fields and ordering, empty results, employee denial/access logging, authentication, and contract alignment.

**Acceptance Criteria:**
- Given an authenticated manager with direct reports' pending approvals, when `GET /api/approvals/pending` is called, then it returns `200` with only requests whose `approverId` is the caller.
- Given a pending item in the response, when its `ApprovalDetail` is inspected, then it includes every request field, employee username, leave-type name, current matching balance, full comments, and full history without follow-up calls.
- Given a manager with no pending approvals, when the endpoint is called, then it returns `200` with an empty array.
- Given an authenticated employee, when the endpoint is called, then it returns `403 ACCESS_DENIED` and records a denied access-log entry.
- Given an authenticated HR user, when the endpoint is called, then it returns only pending decisions assigned to HR, including the HR user's own request and requests from top-level managers reporting to HR.
- Given comments and history in the response, when entries are ordered, then both are oldest first and balance data is read through `BalanceService`.

## Implementation Notes

Compose the queue using public service APIs and keep approval-scope filtering out of the controller. Reuse existing audit-history entries for the comments array; do not add comment persistence or write operations. Preserve the existing documented response shape and access-log behavior.

Comment events use `details_json.requestId` to identify the request; their `entity_id` is not a request identifier. Filter both timeline and comment reads by the details request ID so unrelated comment events are not included.

## Spec Change Log

## Review Triage Log

- **false — rejected:** Review claimed the static OpenAPI contract lacked `/approvals/pending` and `ApprovalDetail`; both already existed before this diff, and this change adds `employeeUsername` to the existing detail schema.
- **false — rejected:** Review claimed request history omitted comment events; `AuditEventRepository.findRequestHistoryEvents` already selects `REQUEST_COMMENT_ADDED` rows and orders all history oldest first.
- **false — rejected:** Review claimed the context documents contain mojibake; both decode as UTF-8, the searched mojibake markers are absent, and the epic-context BOM is valid.
- **low — rejected:** Per-request service reads could become costly with a large queue, but this POC's 12-user seeded cast is small and the contract requires the full pending queue in one response; pagination or a broader batching surface is not warranted here.
- **medium — patch applied:** A Java `String` status would leave generated OpenAPI unconstrained; `ApprovalDetail.status` now declares the four lifecycle values, with an OpenAPI integration assertion.
- **low — patch applied:** JSON `null` is valid audit JSON but has no object fields; `AuditService.commentBody` now rejects a non-object root with an explicit state error.
- **medium — patch applied:** Endpoint documentation promises 401 for unauthenticated callers; the approval integration test now covers missing authentication.
- **false — rejected:** The edge-case reviewer returned a generic “input empty or undecodable” record without a code location; the reviewed diff existed and contained 46,598 bytes.
- **medium — patch applied:** Generated OpenAPI could name the balance component differently from the hand-written contract; `BalanceResponse` now uses schema name `Balance`, and contract tests assert the reference and field.
- **medium — patch applied:** Comment audit-event lookup matched only the JSON request id, allowing a mismatched `entity_id` event to appear in a request thread; the query now checks both identifiers and the integration fixture includes a mismatched event.
- **low — rejected:** The response intentionally returns the full queue as specified, the seeded POC contains 12 users, and pagination or batching would add complexity without a demonstrated everyday performance problem.
- **low — patch applied:** The static contract did not require the five context properties promised on every detail; both OpenAPI and runtime schema now mark those fields required, and the contract test asserts the requirement.
- **low — patch applied:** The shared `Comment` description named a persistence table even though this response reconstructs comments from history; the description is now storage-neutral.
- **low — patch applied:** The pending-detail example omitted author usernames present in the schema and runtime; both example comments now include `authorUsername`.
- **medium — patch applied:** The HR integration case did not distinguish pending from approved decisions assigned to HR; it now includes an approved HR-assigned request and asserts the exact pending employees returned.
- **false — rejected:** Review asked for a successful CI run before treating integration criteria as verified, but the Verification section explicitly records the Docker-blocked run and leaves that coverage unverified; it does not claim acceptance is fully verified.
- **medium — patch applied:** The manager fixture lacked a pending request owned by that manager but assigned upward; it now includes one and the response-size plus expected-item assertions prove it is excluded.
- **medium — corrected:** Existing comment events use `entity_id` for the comment event and `details_json.requestId` for the request association. Removed the invalid `entity_id` constraint and updated fixtures; both dashboard and existing request-history integration coverage now pass.
- **low — patch applied:** The static `ApprovalDetail` schema described history as recent even though the endpoint returns the complete timeline; OpenAPI now documents full history.
- **low — patch applied:** Static and generated schemas did not require all request fields on every detail; both schemas now mark the complete response fields required, and the contract test asserts the exact required set.
- **medium — deferred:** The concurrent Story 3.2 specification does not state the approval endpoint path or request/success response shape explicitly; this is outside Story 3.1 and should be resolved in Story 3.2.
- **low — deferred:** The concurrent user-owned Postman history request uses GET but retains a generated POST description and body; this is unrelated to Story 3.1 and remains untouched.
- **medium — patch applied:** A legacy comment audit event without a `comment` field caused the entire pending dashboard to fail; comment reconstruction now uses the established history fallback while malformed JSON, non-object roots, and non-text comment values still fail explicitly.
- **low — rejected:** An additional empty HR-queue test would duplicate the shared empty-list behavior already covered by the manager empty-queue test; the HR populated-queue test verifies HR-specific scope.
- **low — patch applied:** The runtime OpenAPI test checked only the first status enum value; it now asserts the complete lifecycle enum.
- **low — patch applied:** Comment coverage did not verify both comments' request IDs, authors, names, and timestamps; the manager integration test now asserts those fields and both identifiers.
- **low — patch applied:** History coverage did not verify all event details, actors, and chronological timestamps; the manager integration test now asserts the full timeline in order.

## Verification

**Commands:**
- `.\mvnw.cmd test` (from `backend`) — expected: backend unit and integration tests pass, including the new approval-dashboard and OpenAPI coverage.

**Results (2026-10-25):** `.\mvnw.cmd clean -DskipTests test` compiled all production and test sources successfully. `.\mvnw.cmd "-Dtest=AccessPolicyTest,WorkingDayCalculatorTest" test` passed all 6 tests. After starting Docker Desktop, `.\mvnw.cmd "-Dtest=ApprovalControllerIntegrationTest,LeaveRequestControllerIntegrationTest,OpenApiDocsIntegrationTest" test` passed all 62 tests (0 failures, 0 errors, 0 skipped). `git diff --check` and OpenAPI YAML parsing also passed.
