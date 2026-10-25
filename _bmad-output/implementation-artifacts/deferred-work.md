- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-immutable-audit-trail-access-log.md`
  summary: Harden `audit_events`/`access_log` immutability against `TRUNCATE`, which PostgreSQL row-level `BEFORE UPDATE OR DELETE` triggers never fire for.
  evidence: PostgreSQL only fires statement-level (`FOR EACH STATEMENT`) triggers for `TRUNCATE`; the story's trigger is `FOR EACH ROW`, so raw SQL `TRUNCATE audit_events`/`access_log` bypasses it entirely and erases all rows. The story's own framing ("even against someone with database access") implies this threat model, but the frozen spec Approach and architecture AD-6 (`[ADOPTED]`) both scope the mechanism to exactly "a BEFORE UPDATE OR DELETE trigger", matching FR44's literal wording ("DB trigger blocks update/delete") — so this is a deliberate upstream scope boundary, not an oversight in this story's execution. Fix: add a `BEFORE TRUNCATE ... FOR EACH STATEMENT` trigger per table reusing the existing `reject_audit_mutation()` function, plus a test asserting `TRUNCATE` fails on both tables.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-immutable-audit-trail-access-log.md`
  summary: The audit immutability guarantee has no DB role/privilege separation backing it — the table-owning role (the app's own DB role) can `ALTER TABLE ... DISABLE TRIGGER` or `DROP TRIGGER` and then mutate rows freely.
  evidence: Flyway creates `audit_events`/`access_log` as the app's own DB role, which by default retains full DDL rights (disable/drop trigger, alter table) over tables it owns. Architecture AD-6 frames the DB trigger as the trigger-only "belt-and-braces" control with no stated role-separation requirement, so this is a pre-existing architecture-level scope decision rather than a defect this story introduced. Addressing it would need a non-owning, more restricted app DB role without `TRIGGER`/`ALTER` privileges on these two tables — a cross-story (likely deployment/AD-13) decision, not a local code fix.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-immutable-audit-trail-access-log.md`
  summary: `audit_events`/`access_log` have no index beyond their primary key, even though both are append-only logs later stories will filter by actor/entity/time.
  evidence: Cheaper to add now while tables are empty than later via an online migration against a live, immutable, growing table. Not added in this story because the architecture spine explicitly defers `access_log` querying/reads to "Phase 2" (no read API/UI in this POC) — there is no known query/filter pattern yet to index for, so any index now would be speculative.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-3-password-login-idle-expiring-session.md`
  summary: Revisit CSRF/origin protections for cookie-authenticated endpoints when the trusted-origin and deployment threat model is defined.
  evidence: `SameSite=Strict` blocks cross-site requests but not requests from an untrusted sibling subdomain. The frozen story intent explicitly excludes a CSRF token scheme and accepts `SameSite=Strict` for this POC, so additional protection needs a broader trusted-origin decision.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-5-one-permission-layer-for-every-endpoint.md`
  summary: Define how the access log selects one `resourceId` for routes containing multiple numeric path segments.
  evidence: `AccessLogFilter` currently selects the last numeric segment, but composite/nested route shapes can contain multiple IDs and the story does not define which one is the audited resource. Route-specific semantics are needed to settle the correct value without misattributing access.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-5-one-permission-layer-for-every-endpoint.md`
  summary: Define the client-visible and transactional behavior when access-log persistence fails after an API operation has run.
  evidence: `AccessLogFilter` writes the audit row after the request chain and propagates write failures, but no fault-injection integration test establishes whether a completed state change remains committed or what response the client receives. Test a state-changing request with a forced audit-write failure to determine whether a mutation can succeed while the caller sees an error and may retry.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-6-hr-creates-accounts-with-a-role-and-a-manager.md`
  summary: The documented `POST /users` 403 example body (`"Only HR can create user accounts."` in `openapi.yaml`) doesn't match the actual generic `"Access is denied"` produced by `GlobalExceptionHandler.handleAccessDenied`.
  evidence: Pre-existing: this is the same centralized, single-message `AccessDeniedException` handling established in Story 1.5 and reused exactly as Story 1.6's spec instructed ("mirroring `AuthController.logout`"); not introduced by Story 1.6. Fixing it would mean either giving `AccessPolicy`/`GlobalExceptionHandler` a way to carry per-endpoint detail text, or updating every contract example to the generic message — a cross-cutting AD-11 decision, not a local fix.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-6-hr-creates-accounts-with-a-role-and-a-manager.md`
  summary: `UserCreateRequest`'s Lombok `@Value` has no `toString` exclusion on `temporaryPassword`, so a future logging/debug statement on the request object would print the plaintext password.
  evidence: Pre-existing: `LoginRequest` (password field, no `toString` exclusion) has had the identical characteristic since an earlier story; Story 1.6's spec explicitly directed mirroring that exact convention. A fix (e.g. `@ToString.Exclude` or a custom `toString()`) should be applied codebase-wide to every password-carrying DTO, not just the newest one.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-7-forced-password-change-and-hr-password-reset.md`
  summary: Confirm whether a deactivated (`active=false`) user can still change or have reset their own password through a stale, not-yet-expired session, since nothing in the filter chain re-checks `active` after initial login.
  evidence: Neither `JwtAuthenticationFilter`/`SessionRegistry.touchOrReject` nor the new `changePassword`/`resetPassword` paths check `active` post-login in this diff — if true, every endpoint in the app shares this exposure, not just the two added by Story 1.7, so it is not this story's problem to fix alone. What would settle it: trace whether `active` is enforced anywhere beyond the initial `/auth/login` check.
- source_spec: `_bmad-output/implementation-artifacts/spec-2-4-see-my-requests-and-their-status.md`
  summary: Correct the access-token cookie headers in the existing Postman leave-submission and notification requests.
  evidence: Their uncommitted request definitions put the raw token variable in `Cookie` without the required `access_token=` name, so those requests will not authenticate; these user-owned Postman edits were left untouched during Story 2.4.
- source_spec: `_bmad-output/implementation-artifacts/spec-2-4-see-my-requests-and-their-status.md`
  summary: Correct the Postman request named `my-leave-req-by-id` so it targets leave-request detail rather than the balance endpoint.
  evidence: The uncommitted request is named as request detail but targets `/balances/me` and also supplies a raw token as its cookie value; it was left untouched as user-owned Postman work.
- source_spec: `_bmad-output/implementation-artifacts/spec-2-5-edit-a-pending-request.md`
  summary: Align hand-written OpenAPI 401 response documentation across protected operations.
  evidence: The PUT operation omits an explicit 401 response even though callers must satisfy the global cookie-auth requirement; this omission predates the story implementation and should be resolved consistently across the API contract.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-6-cancel-a-pending-request.md`
  summary: Prevent the planned frontend `/auth/me` initializer from reloading the login page repeatedly when no session exists.
  evidence: The frontend architecture applies its startup initializer before routing, while its interceptor sends any empty-body 401 to a hard reload of `/login`; a signed-out visit to `/login` can therefore repeat the same request and reload. This independent frontend issue is outside Story 2.6.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-6-cancel-a-pending-request.md`
  summary: Implement the frontend architecture's planned `/auth/me`, `/leave-types`, and username-enriched response fields.
  evidence: These operations and fields are present in the OpenAPI document but absent from backend controllers/response DTOs; the frontend architecture explicitly records them as a backend follow-up, so they are not part of this cancellation story.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-6-cancel-a-pending-request.md`
  summary: Complete the `ApprovalDetail` OpenAPI example with the username fields present in its referenced response schemas.
  evidence: The example omits `approverUsername` and the nested comment/history username fields. The frontend architecture already notes that operation-level examples do not yet show the new fields; correcting those independent examples is outside Story 2.6.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-6-cancel-a-pending-request.md`
  summary: Clarify in the frontend architecture that FR26 access logging is backend-only and has no frontend feature surface.
  evidence: The architecture binds FR1–FR31 but does not map FR26 in its capability table, leaving implementers without an explicit boundary. This is independent of cancellation behavior.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-6-cancel-a-pending-request.md`
  summary: Reconcile the frontend adversarial review with the revised architecture spine and its `final` status.
  evidence: Review findings H1/H2/H3/H5/H6 still describe missing route-scoped services, auth bootstrap, an `Unauthorized` response, correct core UI placement, and `AppLinks`, although the spine now specifies them; the review still says `NOT READY` while the spine says `final`. This could mislead frontend implementation and is unrelated to cancellation.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-6-cancel-a-pending-request.md`
  summary: Define which successful writes invalidate the frontend audit data-version signal.
  evidence: Frontend AD-5 includes `audit` among data-version signals but does not list any mutation that bumps it, so audit screens can remain stale after audit-producing changes. This is separate from the backend cancellation implementation.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-6-cancel-a-pending-request.md`
  summary: Restore missing request-history, status-filter, and notification details in the Epic 2 context.
  evidence: The revised context omits the `GET /requests` status filter and `GET /requests/{id}/history` requirements, notification payload details, and owner-only notification visibility that appeared in its prior version. The context also leaves notification-write failure behavior unspecified. These are unrelated to cancellation and should be reconciled against their source requirements.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-7-the-history-timeline-on-a-request.md`
  summary: Correct the user-owned Postman history request so it calls the history endpoint.
  evidence: `postman/collections/emp-leave/get-leave-history.request.yaml` currently sends `PUT /requests/9` with an edit body instead of `GET /requests/{requestId}/history`. The untracked user-owned request was preserved during Story 2.7.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-7-the-history-timeline-on-a-request.md`
  summary: Fix authentication cookie names in the user-owned Postman history, cancellation, and edit requests.
  evidence: The `Cookie` values in `get-leave-history.request.yaml`, `cancel-a-pending-leave-req.request.yaml`, and `put-req-change-leave-req-1.request.yaml` omit the required `access_token=` name. These user-owned requests were preserved during Story 2.7.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-7-the-history-timeline-on-a-request.md`
  summary: Replace copied submission descriptions in the user-owned Postman history, cancellation, and edit requests.
  evidence: The descriptions in the history, cancellation, and edit request definitions still describe submitting a request, so they do not match their operations. These user-owned requests were preserved during Story 2.7.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-7-the-history-timeline-on-a-request.md`
  summary: Define pagination or retention behavior if a request accumulates a very large history.
  evidence: Story 2.7 specifies a complete oldest-first timeline but no pagination contract or response bound. Adding an implicit limit could hide history; define explicit pagination if production history volume warrants it.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-7-the-history-timeline-on-a-request.md`
  summary: Align the user-owned Postman history request description and body with its GET operation.
  evidence: `postman/collections/emp-leave/get-leave-history.request.yaml` targets `GET /requests/9/history` but retains a generated POST-submission description and a JSON edit body; this unrelated request was preserved during Story 3.1.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2-approve-a-request-with-an-optional-comment.md`
  summary: Restrict the pending approval detail status schema to values this endpoint can return.
  evidence: The in-progress Story 3.1 queue returns only PENDING requests, while ApprovalDetail currently documents APPROVED, REJECTED, and CANCELLED values as possible statuses.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2-approve-a-request-with-an-optional-comment.md`
  summary: Mark guaranteed comment response fields as required in the static OpenAPI schema.
  evidence: The in-progress Story 3.1 approval detail includes comment id, request id, author id, body, and creation time, but the static Comment schema leaves them optional.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2-approve-a-request-with-an-optional-comment.md`
  summary: Document oldest-first ordering for approval detail history.
  evidence: The in-progress Story 3.1 contract requires oldest-first history and the repository orders by creation time and id, but the static schema description does not state the ordering.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2-approve-a-request-with-an-optional-comment.md`
  summary: Avoid presenting incomplete legacy audit events as user-authored comment text.
  evidence: The in-progress Story 3.1 comment assembly returns “Event details were not recorded.” for an audit event with no comment field, which can be mistaken for a real comment.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2-approve-a-request-with-an-optional-comment.md`
  summary: Correct pending approval history documentation that implies a pending request already has a decision event.
  evidence: The in-progress Story 3.1 ApprovalDetail history description includes decisions even though the queue selects only PENDING requests.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2-approve-a-request-with-an-optional-comment.md`
  summary: Align the user-owned Postman history request description and body with its GET operation.
  evidence: The in-progress Story 3.1 collection change updates the history route to GET but retains a generated POST-submission description and a JSON edit body.

- source_spec: `_bmad-output/implementation-artifacts/spec-4-3-hr-adjusts-a-balance-up-or-down.md`
  summary: Test that `adjustBalance` correctly creates a new balance row when none exists for the given user/leaveType pair.
  evidence: `BalanceService.adjustBalance` uses `orElse(new LeaveBalance(balanceId, 0))` like `setBalance`, so a positive adjustment on a user with no existing balance row should insert a new row; untested because all 12 seeded users already have all 4 leave type rows. Failure mode (maybe-false): if a DB FK constraint on `leave_balances` were violated, only production traffic or a test that deletes the row first would reveal it.
