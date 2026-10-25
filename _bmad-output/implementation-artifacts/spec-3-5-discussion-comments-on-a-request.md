---
title: 'Story 3.5: Discussion Comments on a Request'
type: 'feature'
created: '2026-10-25'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '6496a0d4218b17f1a5bb1edf15be80a59a02af0f'
context:
  - '{project-root}/backend/AGENTS.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-company-leave-2026-10-22/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Participants have no in-system channel to discuss a leave request; questions, caveats, and handover notes must happen outside the application and are lost to the audit trail.

**Approach:** Add a `request_comments` table owned by `leave`, expose `GET`/`POST /api/requests/{id}/comments` with the same owner-manager-HR authorization as other request read paths, write an immutable audit event per comment so comments appear as `COMMENT` entries in the request history timeline, and promote `Comment` from a local `approvals` DTO to a proper `leave`-module type so both endpoints share one schema.

## Boundaries & Constraints

**Always:** Apply `AccessPolicy.requireOwnerManagerOrHr` on every comment read and write; accept comment on any request status (PENDING, APPROVED, REJECTED, CANCELLED); write audit event with entity `REQUEST_COMMENT`, action `REQUEST_COMMENT_ADDED`, `details_json` containing `requestId` (long) and `comment` (text), after the `request_comments` row is saved; return comments oldest first; replace `approvals.ApprovalComment` with `leave.Comment` so `ApprovalDetail.comments` and `GET .../comments` share the same type and ID source.

**Never:** Trigger email or any in-app notification on a comment; allow comment delete or edit; restrict comment posting by request status; emit the audit event before the `request_comments` row is committed.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Add comment (authorized) | Authorized caller, existing request, non-blank body | 201 `Comment` with `id`, `requestId`, `authorId`, `authorUsername`, `body`, `createdAt`; audit event written | N/A |
| List comments (authorized) | Authorized caller, existing request | 200 `Comment[]`, oldest first | N/A |
| Blank/missing body | POST with blank or absent body | 400 `VALIDATION_FAILED` | Comment not saved |
| Unauthorized caller | Non-owner, non-manager, non-HR caller | 403 `ACCESS_DENIED`; `access_log` row `allowed=false` | N/A |
| Request not found | POST or GET for non-existent requestId | 404 `NOT_FOUND` | N/A |
| Comment on terminal request | POST on APPROVED/REJECTED/CANCELLED | 201 — status does not restrict comments | N/A |

</frozen-after-approval>

## Code Map

- `backend/src/main/resources/db/migration/V13__create_request_comments_table.sql` -- new; create `request_comments (id BIGINT GENERATED ALWAYS AS IDENTITY, request_id BIGINT NOT NULL REFERENCES leave_requests(id), author_id BIGINT NOT NULL REFERENCES users(id), body TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now())`
- `backend/src/main/java/com/company/leave/leave/RequestComment.java` -- new JPA entity; `@Entity @Table(name="request_comments")`; `@Value`-style Lombok won't work for JPA, use `@Getter @NoArgsConstructor @AllArgsConstructor @Builder`; id is `@GeneratedValue(strategy = IDENTITY)` per NFR12
- `backend/src/main/java/com/company/leave/leave/RequestCommentRepository.java` -- new, package-private; extends `JpaRepository<RequestComment, Long>`; add `findByRequestIdOrderByCreatedAtAscIdAsc`
- `backend/src/main/java/com/company/leave/leave/Comment.java` -- new response DTO replacing `approvals.ApprovalComment`; `@Value @Builder`; `@Schema(name = "Comment")` to keep schema name; fields: `long id`, `long requestId`, `long authorId`, `String authorUsername`, `String body`, `Instant createdAt`
- `backend/src/main/java/com/company/leave/leave/CommentCreateRequest.java` -- new request DTO; `@Value` with `@NotBlank String body`
- `backend/src/main/java/com/company/leave/leave/LeaveRequestService.java` -- add `listComments(long requestId)` (load request, requireOwnerManagerOrHr, read from repository, join usernames via UserService, return `List<Comment>`); add `addComment(long requestId, String body)` (load request, requireOwnerManagerOrHr, validate body non-blank, save RequestComment, write audit event via `encodeCommentDetails(requestId, body)`, return `Comment`); add private `encodeCommentDetails(long requestId, String body)` → `{"requestId": N, "comment": "..."}` JSON string
- `backend/src/main/java/com/company/leave/leave/LeaveRequestController.java` -- add `GET /{requestId}/comments` → `listComments`; add `POST /{requestId}/comments` returning `ResponseEntity<Comment>` with status 201 → `addComment`
- `backend/src/main/java/com/company/leave/approvals/ApprovalComment.java` -- delete; `ApprovalDetail` and `ApprovalService` will import `leave.Comment` instead
- `backend/src/main/java/com/company/leave/approvals/ApprovalDetail.java` -- change `List<ApprovalComment>` → `List<com.company.leave.leave.Comment>`
- `backend/src/main/java/com/company/leave/approvals/ApprovalService.java` -- replace `auditService.findLeaveRequestComments` call with `leaveRequestService.listComments(request.getId())`; remove unused `authorUsernames` lookup (now inside `listComments`); update import from `ApprovalComment` to `leave.Comment`
- `backend/src/test/java/com/company/leave/leave/LeaveRequestControllerIntegrationTest.java` -- extend: add comment happy path (POST + GET), unauthorized attempt, blank body, comment on non-existent request, and verify audit event via JDBC; verify comment appears as `COMMENT` entry in history
- `backend/curl/story-3.5-discussion-comments-on-a-request.md` -- new; runnable POST and GET examples, unauthorized example, blank-body example

## Tasks & Acceptance

**Execution:**
- [x] `backend/src/main/resources/db/migration/V13__create_request_comments_table.sql` -- create `request_comments` table with FK to `leave_requests` and `users`; `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- [x] `backend/src/main/java/com/company/leave/leave/RequestComment.java` -- JPA entity for `request_comments`; Lombok `@Getter @NoArgsConstructor @AllArgsConstructor @Builder`; id `IDENTITY` strategy
- [x] `backend/src/main/java/com/company/leave/leave/RequestCommentRepository.java` -- package-private `JpaRepository`; `findByRequestIdOrderByCreatedAtAscIdAsc(long requestId)`
- [x] `backend/src/main/java/com/company/leave/leave/Comment.java` -- response DTO with `@Schema(name = "Comment")`; replace `approvals.ApprovalComment`
- [x] `backend/src/main/java/com/company/leave/leave/CommentCreateRequest.java` -- `@NotBlank String body` field
- [x] `backend/src/main/java/com/company/leave/leave/LeaveRequestService.java` -- add `listComments` and `addComment` methods; `addComment` saves row then writes audit event; `encodeCommentDetails` uses `{"requestId": N, "comment": "..."}` so existing `decodeDetail("REQUEST_COMMENT_ADDED", json)` in same class continues to work for history
- [x] `backend/src/main/java/com/company/leave/leave/LeaveRequestController.java` -- `GET /{requestId}/comments` (200 list) and `POST /{requestId}/comments` (201 Comment)
- [x] `backend/src/main/java/com/company/leave/approvals/ApprovalComment.java` -- delete file
- [x] `backend/src/main/java/com/company/leave/approvals/ApprovalDetail.java` -- swap `ApprovalComment` → `leave.Comment`
- [x] `backend/src/main/java/com/company/leave/approvals/ApprovalService.java` -- swap `auditService.findLeaveRequestComments` + manual `ApprovalComment` build → `leaveRequestService.listComments(request.getId())`
- [x] `backend/src/test/java/com/company/leave/leave/LeaveRequestControllerIntegrationTest.java` -- add Testcontainers cases (see ACs)
- [x] `backend/curl/story-3.5-discussion-comments-on-a-request.md` -- curl guide

**Acceptance Criteria:**
- Given an authorized user (owner, assigned manager, or HR), when `POST /api/requests/{id}/comments` is called with a non-blank body, then the response is 201 with `id`, `requestId`, `authorId`, `authorUsername`, `body`, and `createdAt` set; an `audit_events` row with `entity='REQUEST_COMMENT'`, `action='REQUEST_COMMENT_ADDED'`, `actor_id=authorId`, and `details_json` containing the `requestId` and `comment` text is committed in the same transaction.
- Given a request with added comments, when `GET /api/requests/{id}/comments` is called, then the response is 200 with all comments in `createdAt ASC, id ASC` order, each carrying `authorUsername`.
- Given a comment is added, when `GET /api/requests/{id}/history` is called, then a `COMMENT` history entry appears at the correct position with `detail` equal to the comment body.
- Given an unauthorized caller (not owner, not manager, not HR), when either comments endpoint is called, then the response is 403 `ACCESS_DENIED` and an `access_log` row is written with `allowed=false`.
- Given a POST with a blank, whitespace-only, or absent body, then the response is 400 `VALIDATION_FAILED` and no row is written.
- Given a comment is posted on a request with status APPROVED, REJECTED, or CANCELLED, then the response is still 201 — status does not block comment posting.
- Given a demo: employee and manager exchange comments, employee edits dates, manager approves — when `GET /api/requests/{id}/history` is called, then comment, edit, and approval entries all appear in chronological order (demo scenario 5).
- Given `GET /api/approvals/pending`, when comments exist on a pending request, then each `ApprovalDetail.comments` entry uses `leave.Comment` and its `id` matches the `id` returned by `GET /api/requests/{id}/comments` for the same comment.

## Implementation Notes

## Spec Change Log

## Review Triage Log

**Review pass 1 (2026-10-25)**

- BH-1 Missing index on `request_comments.request_id` — **low / patch** — real gap; `findByRequestIdOrderByCreatedAtAscIdAsc` needs a covering index; fix: add `CREATE INDEX` to V13 migration.
- BH-2 No `ON DELETE CASCADE` on FK — **false** — the application has no delete-leave-request path; `author_id REFERENCES users(id)` (no cascade) prevents deletion of a user who has comments, so the violation is unreachable.
- BH-3 `created_at` set in both Java and DB default — **low / rejected** — clock skew is unlikely in co-located deployment; fix requires adding `@CreationTimestamp` Hibernate annotation, which is more than a direct correction.
- BH-4 / VG-1 No HR-role test for comments endpoints — **medium / patch** — confirmed: existing tests for other `requireOwnerManagerOrHr` endpoints loop over HR, but the new comment tests use only EMPLOYEE and MANAGER. Grouped.
- BH-5 / EC-5 `addComment_idMatchesBetweenCommentsEndpointAndApprovalDetail` never calls `GET /approvals/pending` — **medium / patch** — confirmed: the test only queries the DB and the comments endpoint; AC-8 cross-endpoint ID claim is untested. Grouped.
- BH-6 No HR-role curl example — **low / patch** — direct addition to the curl guide.
- BH-7 `@ApiResponse` success responses missing `@Content`/`@Schema` — **false** — existing endpoints in the same controller (lines 62, 80) also omit content schemas on 200; new endpoints follow the established pattern.
- BH-8 `review_loop_iteration` = 0 after entering review — **false** — counter is incremented only on loopback per step-04; 0 is correct on first entry.
- BH-9 / EC-4 Dead `AuditService.findLeaveRequestComments` and `AuditEventRepository.findRequestCommentEvents` — **false** — spec's Design Notes explicitly record the decision to leave them in place.
- BH-10 Blank-body validation duplication in service — **low / rejected** — service-level check is defensive for direct callers; removing it weakens the invariant, fix is not a direct correction.
- BH-11 Missing `audit_events` assertion for blank-body test — **false** — `@Valid`/`@NotBlank` fires before service is entered; audit event cannot be emitted for blank-body request.
- BH-12 `## Implementation Notes` empty — **false** — section is legitimately empty at planning time per the spec template.
- EC-1 `authorUsername` null if author deleted — **false** — `author_id REFERENCES users(id)` (no cascade) prevents deletion of a user who has comments; `.get()` cannot return null.
- EC-2 `authorUsername` null in `addComment` 201 — **false** — same FK reasoning; author row is guaranteed present at comment-insert time.
- EC-3 Unauthorized + blank body returns 400 not 403 — **low / rejected** — standard Spring MVC: `@Valid` fires before controller body; fix requires structural reordering of validation and authorization.
- EC-6 AC-7 combined ordering scenario untested — **false** — all event types write to the same `audit_events` table ordered by `created_at`; mechanism is shared and covered by individual-type tests.

## Design Notes

The audit event's `details_json` for `REQUEST_COMMENT_ADDED` is `{"requestId": N, "comment": "body"}`. The `requestId` field is required by the existing `AuditEventRepository.findRequestHistoryEvents` JSONB query that joins REQUEST_COMMENT audit events into the history timeline by matching `details_json ->> 'requestId'`. The `comment` field is consumed by the existing `decodeDetail` method in `LeaveRequestService` which uses `"comment"` as the key for `REQUEST_COMMENT_ADDED` actions.

`ApprovalComment` in `approvals` is removed and replaced by `Comment` in `leave` because `request_comments` is owned by the `leave` module (AD-2). `ApprovalService` calls `leaveRequestService.listComments` so both the approvals dashboard and the direct comments endpoint return Comments with the same `id` (the `request_comments.id`).

`AuditService.findLeaveRequestComments` and `AuditEventRepository.findRequestCommentEvents` were written in anticipation of this story and are left in place — they are now unused but harmless.

## Verification

**Commands:**
- `cd backend; .\mvnw.cmd -Dtest=LeaveRequestControllerIntegrationTest test` -- expected: all cases pass including new comment cases
- `cd backend; .\mvnw.cmd -Dtest=ApprovalControllerIntegrationTest test` -- expected: existing approvals suite passes after ApprovalComment → Comment refactor
