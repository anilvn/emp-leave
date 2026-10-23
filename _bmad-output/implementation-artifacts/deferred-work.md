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
