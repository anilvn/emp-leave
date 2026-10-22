---
review: adversarial (pairwise-compliant-yet-incompatible builds)
target: ../ARCHITECTURE-SPINE.md (status: draft, 2026-10-22)
context: ../.memlog.md, prds/poc-prd-emp-leave-tracker-2026-10-22/prd.md
date: 2026-10-22
---

# Adversarial Review: emp-leave-tracker POC backend spine

## Verdict

**Not ready for parallel build yet. The skeleton is sound, but six holes must be closed first.** The module graph, single table ownership, the state machine, the balance locking and after-commit email all hold up. The holes are in the places where the spine says "every" or "one" without naming *which* component. That covers who writes the auth cookie, who writes the audit row, which transaction a lockout counter lives in, and which scope a list endpoint uses. In each case two developers can follow the text exactly and still produce code that breaks a named acceptance scenario (11, 14, 15, 1–5, 8/9, 52). All the fixes below are rule tightenings of one to three lines. None of them changes the paradigm.

Severity is judged for *this* 4-week, 1–2 dev POC: **Critical** breaks an acceptance scenario on the happy path. **High** breaks one under normal demo use or silently. **Medium** costs rework or an integration day. **Low** is a nuisance or an edge case.

---

## Ranked holes

### H1 — Two writers of the auth cookie: sliding re-issue clobbers logout and change-password [Critical]

- **ADs:** AD-9
- **Build A (auth stories: logout, change-password):** `POST /api/auth/logout` sets `Set-Cookie: token=; Max-Age=0`. `POST /api/auth/change-password` issues a fresh token with `must_change_password=false`.
- **Build B (common security, "every authenticated response re-issues the token"):** a `OncePerRequestFilter` re-issues the *incoming* token's claims with `exp=now+30m` on every authenticated request. Logout and change-password are authenticated requests (AD-9 says they accept the token), so they get this too.
- **Why both comply:** AD-9 says "every authenticated response re-issues". It does not exclude the endpoints that set the cookie themselves, and it does not name a single cookie writer.
- **Consequence:** There are two `Set-Cookie` headers for the same name, and the outcome depends on their order:
  - If B writes after A, logout doesn't log out and the user is stuck in the forced-password-change loop forever (**Scenario 11 fails**).
  - If B tries to write after `chain.doFilter`, the response is already committed. The header is silently dropped, so sliding expiry never happens and users are kicked out 30 min after login even while active (**Scenario 15 is "passed" for the wrong reason, and FR58 is broken**).
- **Rule fix (AD-9 addendum):** "One `AuthCookieWriter` in `common` is the only code that emits the auth cookie. The sliding re-issue runs **before** the handler (pre-`doFilter`) and is skipped for `/api/auth/login`, `/api/auth/logout` and `/api/auth/change-password`, which set the cookie themselves through `AuthCookieWriter`. Change-password issues a new token with `must_change_password=false` and keeps the original `auth_time`."

### H2 — The lockout counter is rolled back by the login transaction [Critical]

- **ADs:** AD-3 × AD-9 × AD-2
- **Build A (auth):** `AuthService.login()` is `@Transactional` (it is the use-case entry point under AD-3). On a bad password it calls `UsersService.recordFailedLogin(userId)` and then throws `BadCredentialsException`, which maps to 401.
- **Build B (users):** `recordFailedLogin` is `@Transactional` with `REQUIRED`. AD-3 *forbids* `REQUIRES_NEW` here, because only `access_log` and `notifications` may use it.
- **Why both comply:** Both follow AD-3 to the letter. The runtime exception rolls back the joined transaction, which takes the `failed_logins++`, the `locked_until` value and the `USER_LOCKED` audit row with it. If auth catches the exception instead, the inner `REQUIRED` method has already marked the transaction rollback-only, which produces an `UnexpectedRollbackException`.
- **Consequence:** The account never locks (**Scenario 14 fails**, FR57).
- **Rule fix (AD-3 + AD-9):** "`AuthService.login` is **not** transactional. `UsersService.recordLoginFailure(username)` and `UsersService.recordLoginSuccess(userId)` are each their own use-case entry point (their own transaction, committed before auth decides the HTTP outcome). Login never throws out of a transaction that wrote lockout state." Alternatively, add "lockout counter updates" to the `REQUIRES_NEW` list. The first option is cleaner.

### H3 — Who emits the audit row is undefined, so rows are duplicated or missing and their shapes differ [High]

- **ADs:** AD-7, AD-1, AD-6
- **The rule as written:** "Every state-changing service method calls `AuditService.record`."
- **Build A (approvals):** `ApprovalsService.approve()` is a public, state-changing service method, so it records `LEAVE_REQUEST_APPROVED`.
- **Build B (leave):** `LeaveService.approve()` also records `LEAVE_REQUEST_APPROVED`. `BalancesService.deduct()` records `LEAVE_BALANCE_DEDUCTED` as well.
- **Result:** One approval produces 1–3 audit rows depending on who built what. The History tab (FR45) shows the decision twice.
- **More pairs from the same gap:**
  - **Comments:** `LeaveService.addComment()` audits `REQUEST_COMMENT_ADDED` against entity `LEAVE_REQUEST`. The History tab is defined as `audit_events` plus `request_comments`, so every comment appears **twice** in History (Scenario 5).
  - **Login:** auth audits `AUTH_LOGIN_FAILED`, and users audits `USER_LOGIN_FAILED` and `USER_LOCKED` for the same event.
  - **Balance `entityId`:** `leave_balances` has a composite key (user, type) but `record()` takes one `entityId`. One dev passes `user_id`, another adds a surrogate `id`, and the HR audit view filter breaks.
  - **`details_json` keys:** AD-7 itself writes `self_approved` (snake_case) while the API convention is camelCase. One dev writes `{"selfApproved":true}` and another writes `{"self_approved":true}`, and only when true. Edit rows carry `{"workingDays":3}` in one build and `{"oldWorkingDays":4,"newWorkingDays":3}` in the other.
- **Consequence:** Scenarios 1–5 (History) and 12 (HR audit) show duplicated or inconsistent rows, and tests that assert audit counts are flaky across stories.
- **Rule fix (AD-7, replace "every state-changing service method"):** "Exactly one audit row per use case per aggregate, written by the module that **owns** the aggregate. `approvals` and `auth` never call `AuditService`; drop the `auth → audit` edge. Balance operations nested in a leave use case (reserve, release, deduct) are recorded by the ledger only. HR set and adjust write `LEAVE_BALANCE_SET` / `LEAVE_BALANCE_ADJUSTED` with `entity=LEAVE_BALANCE`, `entityId=user_id` and `leaveTypeId` in the details. Comments are audited as `entity=REQUEST_COMMENT`, and History reads comments only from `request_comments`. The spine carries an **action catalog** table listing action, entity, entityId source and the exact camelCase `details` keys (including `selfApproved`)."

### H4 — List scoping is defined per role, but endpoints are scoped per purpose [High]

- **ADs:** AD-8 vs the PRD endpoint table
- **Build A (leave, "My Requests", FR4/FR5):** `GET /api/requests` returns the caller's own requests for every role. The PRD says "Any user, own requests (managers too)".
- **Build B (RBAC tests or another leave dev reading AD-8 literally):** "List endpoints are scoped: self for employees, live direct reports for managers, everyone for HR." So `GET /api/requests` returns a manager's team and **every request in the company for HR**. The same reading applies to `GET /api/notifications` ("HR sees everyone's notifications").
- **Consequence:** The HR and manager "My Requests" pages show other people's leave. The RBAC test suite and the feature disagree, and one of them gets "fixed" the wrong way during week 4.
- **Rule fix (AD-8):** Replace the per-role sentence with a **per-endpoint scope table**:

| Endpoint | Scope |
| --- | --- |
| `/api/requests`, `/api/balances/me`, `/api/notifications` | self for all roles |
| `/api/approvals/pending` | `approver_id = caller` |
| `/api/team/leave` | live direct reports |
| `/api/balances`, `/api/users`, `/api/audit` | HR, all |

### H5 — Scope checks in services break internal cross-module calls, so emails are silently dropped [High]

- **ADs:** Consistency Conventions ("Business rules (balance, transitions, **scope**) are checked in services") vs AD-8 (permission beans at `@PreAuthorize`) vs AD-11
- **Build A (users):** `UsersService.getUser(id)` enforces scope inside the service: caller is self, the caller's manager, or HR. The conventions tell it to.
- **Build B (notifications):** the AFTER_COMMIT listener runs on the request thread under the **submitting employee's** `SecurityContext`. It calls `UsersService.getUser(approverId)` to address the email. The employee may not view their manager, so the call throws `AccessDeniedException`. Spring logs and swallows exceptions from transaction-completion listeners.
- **Consequence:** The manager never gets the "new request" email (**FR52 / Scenario 1 fails silently**). The 403 may also land in `access_log` as a false denial.
- **Rule fix (AD-8 + Conventions):** "Record-level authorization lives **only** in the permission beans invoked from controllers. Public module services trust their caller and never consult the `SecurityContext` for scope; they take an explicit `actorId` where the actor matters. 'Scope' is removed from the services line of the Validation convention, but list queries still receive the scope parameter from the controller."

### H6 — No `email` or name column on `users`, so the notifications build has no recipient address [High]

- **ADs:** AD-2, AD-11 (the PRD core table is inherited silently)
- **Build A (users, week 1):** creates `users` exactly per the PRD: `id, username, password_hash, role, manager_id, …`. There is no email and no display name.
- **Build B (notifications, week 2):** needs a recipient address (FR50–52) and the employee name in the template (FR54). It either uses `username` as the address, which works only if seeds happen to look like emails, or it needs a users migration it doesn't own (AD-13 forbids it). FR11's "employee" context also has no name to show.
- **Rule fix (AD-2 or the structural seed):** "`users` carries `email` (unique, not null) and `full_name`. `UsersService` exposes `UserSummary(id, fullName, email, role, managerId)` as the only cross-module user DTO."

### H7 — Two denial and error shapes, and an access-log filter that cannot see all 403s [Medium-High]

- **ADs:** Conventions (one `@RestControllerAdvice` in `common`), AD-8 (one filter writes `access_log` for every 403), AD-9, AD-10, `common` depending on no module
- **Pair 1:** The `balances` dev throws `InsufficientBalanceException`. `common`'s advice cannot import it, because `common` depends on no module. So the dev adds a second `@ExceptionHandler` in `balances.web` returning 409. Meanwhile the `leave` dev returns `ResponseStatusException(400)` for the same rule on edit. **Scenario 10** gets two status codes, and the SPA message handling diverges.
- **Pair 2:** `@PreAuthorize` denials go through the advice and return a `ProblemDetail` with `code: ACCESS_DENIED`. CSRF failures, the `must_change_password` gate (naturally built as a filter) and 401s from the resource server are handled by Spring Security's filter chain and return a bare 403/401 with no `code`. The SPA interceptor keyed on `code` never redirects to change-password.
- **Pair 3:** The access-log filter is placed either:
  - **outside** the security chain, where `SecurityContext` is already cleared by the time the status is known, so `actor_id` is null; or
  - **after** it as a normal `@Component` filter, where 403s raised inside the chain never reach it, so they go unlogged.

  Either way, **Scenarios 8/9 ("attempt logged")** depend on placement. `resource` is also free text: `/api/requests/42` vs `LEAVE_REQUEST`.
- **Rule fix (Conventions + AD-8):**
  - "`common` defines `ApiException(HttpStatus, code)`. Every module throws subclasses, and no module declares exception handlers."
  - "The security chain's `AuthenticationEntryPoint` and `AccessDeniedHandler` write the same `ProblemDetail` through one shared writer."
  - "The spine lists the code catalog with statuses: `VALIDATION_FAILED` 400, `INVALID_CREDENTIALS` 401 (also used for locked and inactive accounts), `UNAUTHENTICATED` 401, `PASSWORD_CHANGE_REQUIRED` 403, `CSRF_INVALID` 403, `ACCESS_DENIED` 403, `NOT_FOUND` 404, `INSUFFICIENT_BALANCE` 409, `INVALID_TRANSITION` 409, `VERSION_CONFLICT` 409, `LEAVE_TYPE_CHANGE_NOT_ALLOWED` 400."
  - "The access-log filter is registered **inside** the security chain immediately after `BearerTokenAuthenticationFilter`. It captures the actor before `doFilter` and the status after. `resource` is an enum: `LEAVE_REQUEST`, `BALANCE`, `USER`, `AUDIT`, `NOTIFICATION`."

### H8 — The principal shape is undefined (role authority prefix and the type of `sub`) [Medium]

- **ADs:** AD-8, AD-9
- **Build A (auth):** issues `role: "HR_ADMIN"` and `sub: "42"`. Resource-server defaults map only `scope`/`scp` to `SCOPE_*` authorities.
- **Build B (controllers):** write `hasRole('HR_ADMIN')`, which needs `ROLE_HR_ADMIN`. The permission beans compare `authentication.getName()` (the String `"42"`) with a `Long employeeId`, so `equals` is false and everyone gets 403. A third dev parses `jwt.getClaim("sub")` differently.
- **Rule fix (AD-9):** "A `JwtAuthenticationConverter` in `common` maps `role` to the single authority `ROLE_<role>`. A `CurrentUser` accessor in `common` (`long id()`, `Role role()`) is the only way code reads the caller. Permission beans and controllers never read the `Jwt` directly."

### H9 — Editing can change the leave type or produce a zero delta, and AD-6 assumes a single type [Medium]

- **ADs:** AD-6, AD-4
- **Build A (leave, `PUT /api/requests/{id}`):** treats the body as a full replacement, `leaveTypeId` included (FR7 "modify"). It calls `BalancesService.adjustReservation(user, newType, newDays - oldDays)`.
- **Build B (balances):** implements "Edit: + delta" against one (user, type) row, exactly as tabulated.
- **Consequence:**
  - Vacation(4) → Sick(4) gives delta 0. The 4 reserved Vacation days are never released, and nothing is reserved on Sick.
  - On approve, `deduct` on Sick drives `reserved_days` negative, the CHECK fails, and the API returns 500.
  - Separately, "exactly one ledger row" per operation forces a 0-day `reserve` row for edits that only touch notes, while another dev skips the call entirely.
- **Rule fix (AD-6):** "Edit may not change `leave_type_id` (400 `LEAVE_TYPE_CHANGE_NOT_ALLOWED`; cancel and resubmit instead). An edit with delta 0 makes no balance call and writes no ledger row, but still writes the `LEAVE_REQUEST_EDITED` audit row."

### H10 — Read composition is not possible inside the allowed graph [Medium]

- **ADs:** AD-1 (the graph is the complete allowed set), AD-2
- **Pair 1 (FR11 rich context):** The approvals dev builds the detail panel with employee, balance and recent history, but `approvals` may only call `leave`. The dev either adds `approvals → balances/users` edges (a spine amendment mid-sprint) or asks the `leave` dev for an aggregate method nobody planned.
- **Pair 2 (balances pages, FR6/FR27):**
  - `balances` may call neither `users` (for names, or to list users without balance rows) nor `leave` (owner of `leave_types`, for type names). Adding `balances → leave` would create a cycle.
  - One dev writes a native SQL join onto `users` and `leave_types` from `balances.internal`. This is legal under AD-2 because it only restricts writes, but it is invisible to Modulith.
  - Another returns bare ids and expects the SPA to resolve them, yet no `GET /api/leave-types` endpoint exists in the PRD list.
- **Rule fix (AD-1/AD-2):**
  - "Move `leave_types` ownership to `balances`. It is reference data at the bottom of the graph, `leave` reads it via `BalancesService`, and `balances` serves `GET /api/leave-types`."
  - "Add the read-only edge `balances → users`."
  - "FR11 context is served by `LeaveService.getDecisionContext(requestId)`, which aggregates balances, users and audit."
  - "No module's SQL may reference another module's tables, except FK constraints."

### H11 — The Flyway rules collide on seed ordering, out-of-order merges and shared trigger functions [Medium]

- **ADs:** AD-13, AD-7
- **Pair 1:**
  - Dev A writes `db/seed/V202610231000__users_seed.sql` in week 1.
  - Dev B later adds `V202610291400__users_add_email.sql` with a NOT NULL column, which H6 makes likely.
  - In the `dev` and `test` profiles (no seed) everything passes.
  - In the `demo` profile Flyway applies the seed first, and the ALTER then fails on the existing rows.
  - Fixing the seed means editing an applied migration, which AD-13 forbids.
  - **This first shows up in week 4, at demo build.**
- **Pair 2:** Two devs create migrations on Monday and Tuesday and merge Wednesday and Tuesday respectively. The local Postgres of whoever pulled first now has a pending migration with a *lower* version, and Flyway's default (`outOfOrder=false`) fails validation.
- **Pair 3:** The audit migration and the balances migration each `CREATE FUNCTION reject_mutation()` for the immutability triggers, so the second one fails or silently replaces the first body. "Each file touches only its module's tables" doesn't cover functions.
- **Rule fix (AD-13):**
  - "Seed data lives in repeatable `R__demo_seed.sql` (idempotent upserts) or an `afterMigrate` callback, so it always runs after every versioned migration."
  - "`spring.flyway.out-of-order=true` in the `dev` profile only."
  - "Database functions are prefixed with their owning module (`audit_reject_mutation`, `balances_reject_mutation`), or one `common` migration owns the shared function."

---

## Lower-priority holes (fix if cheap)

| # | Hole | Conflicting builds | Minimal rule |
| --- | --- | --- | --- |
| L1 | Ledger `change_days` sign and meaning per kind | balances-write dev: reserve = `+n` (reserved delta), set = new value `x`. Ledger-view or test dev: reserve = `−n` (available delta), set = `x − old` | AD-6: add a `change_days` column to the operations table, defined as the signed change to the column that kind touches: reserve `+n`, release `−n`, deduct `−n` (total), set `x − old_total`, adjust `±x` |
| L2 | Password hashing and policy are split | auth's change-password hashes and validates the policy. users' HR reset-password (users may not call auth) re-implements the policy, and the two drift | `PasswordPolicy` and the `PasswordEncoder` bean live in `common`. `UsersService.setPassword(userId, raw, mustChange)` is the only hashing path |
| L3 | FR15 comment permission is still an open question in the memlog | `@leavePermissions.canComment` = canView at any status in one build, and owner + approver while PENDING only in another | AD-8: "canComment ≡ canView, any status." Close the memlog question |
| L4 | Decision comment storage | approvals writes `decision_comment` only. The History or email dev expects it in `request_comments` too, or reads it from there | "The decision comment lives only in `leave_requests.decision_comment` and the decision audit row. It is never copied into `request_comments`" |
| L5 | 403 vs 404 for a non-existent id | AD-8 says "403, never 404". Conventions say "404 missing in scope". The permission bean returns false for a missing row (403 plus an access-log row), while the service throws `NotFound` (404) | "Permission beans return false for a missing record, so the result is 403. A 404 is used only for collection-level misses that need no record check" |
| L6 | Approve racing cancel | Both pass the PENDING check. The second waits on the balance lock, then releases or deducts again, the CHECK is violated, and the API returns 500. `@Version` would only have fired at flush | AD-4: "A transition flushes the `leave_requests` version bump (or takes `PESSIMISTIC_WRITE` on the request row) **before** calling `balances`, and a CHECK violation maps to 409 `INVALID_TRANSITION`" |
| L7 | History paging over two sources | audit returns `Page<AuditEventDto>` and leave appends all comments, so page boundaries are wrong | "History (FR45) is an unpaged list per request, merged by `createdAt`. Only audit search and notifications are paged" |
| L8 | Synchronous listener on the request thread | Mailpit hangs and the approve request hangs, because JavaMail's default timeouts are infinite | AD-11: "`spring.mail` connect, read and write timeouts are 5 s, and the listener catches everything" (or annotate it `@Async`) |
| L9 | Detail visibility vs approver | `canView` uses the live `manager_id` per AD-5's "list visibility" clause, so a request can appear on the approver's Pending dashboard and still return 403 on its detail page | AD-5: "canView(request) includes `approver_id`." This is moot while no user-update endpoint exists, but it costs one line |
| L10 | Email to self on HR self-approval | The notifications dev emails `approver_id` (HR emails themselves). Another uses `manager_id` (null, so it is skipped or throws an NPE) | AD-11: "Submitted mail goes to `approver_id` and is skipped when approver = requester" |

## Spine-internal contradictions to fix while editing

1. AD-3 bans `REQUIRES_NEW` beyond two tables, and that makes FR57 lockout unbuildable (H2).
2. AD-8 says "never 404" while the conventions say "404 missing in scope" (L5).
3. AD-7's `self_approved` is snake_case while the JSON convention is camelCase (H3).
4. The graph has an `auth → audit` edge with no defined audit action for auth (H3).
5. The conventions put "scope" in services while AD-8 puts it in permission beans (H5).
6. The memlog's open FR15 question is unresolved, and the spine is silent on it (L3).

## What held up (no attack found)

- Single writer of request status (AD-4 plus `approvals` owning no tables).
- Approver snapshot for decision authority (AD-5).
- The balance lock plus CHECK backstop against double-spend.
- The working-day function and stored `working_days` (AD-12).
- The CORS/CSRF origin contract (AD-10).
- No email on rollback (AD-11).
- The ApplicationModules `verify()` gate.
