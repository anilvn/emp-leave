# Reconcile review — frontend ARCHITECTURE-SPINE vs PRD / OpenAPI / backend spine

Reviewed: 2026-10-25
Spine: `architecture-frontend-company-leave-2026-10-23/ARCHITECTURE-SPINE.md` (draft)
Inputs: PRD `poc-prd-emp-leave-tracker-2026-10-22/prd.md`; `backend/src/main/resources/openapi.yaml`; backend spine `architecture-company-leave-2026-10-22/ARCHITECTURE-SPINE.md`. Backend source was spot-checked only where it decides UI behaviour (list scoping, cookie refresh, email link).

## Verdict

**Revise before build.** The spine is structurally sound: layering, error envelope, generated types and the same-origin envelope all match the contract. But AD-4 ("no session restore, no return URL") conflicts with two acceptance scenarios, and the spine leaves several decisions to each builder: what "My Requests" means for MANAGER and HR, how names are shown for ids that have no `*Username`, and the UI gating for FR7 and FR13. There are 2 critical, 5 high and 8 medium findings.

---

## Critical

### C1. A page refresh logs the user out, which breaks scenario 5 (FR15)
- **Spine:** AD-4 keeps `currentUser` in memory only. "After a page refresh it is `null`, so `authGuard` redirects to `/login`."
- **Input:** PRD FR15 and Decisions: "users refresh the page to see new comments". Scenario 5: "Comments appear after **page refresh**".
- **Problem:** the documented way to see new comments is a browser refresh, and a refresh forces a re-login. The demo flow for scenario 5 becomes "refresh, log in again, navigate back". The backend cookie is still valid at that point (it slides on every call, per `JwtAuthenticationFilter`), so the forced re-login is a frontend artefact. It also leaves an orphan `active_sessions` row each time.
- **Fix (pick one and record it as an AD):**
  - (a) Preferred: add `GET /api/auth/me` → `CurrentUser` to `openapi.yaml`. It is a thin read of the authenticated principal. Then resolve `currentUser` in an `APP_INITIALIZER` / `provideAppInitializer`, where a 401 means "not logged in".
  - (b) Keep the contract unchanged: store the non-secret `CurrentUser` (id, username, role, mustChangePassword) in `sessionStorage`. The server stays the authority, and any 401 clears it. Relax the "no browser storage for auth state" line to "no token or secret in browser storage".
  - Also add an explicit in-page "Refresh" action on request detail and the approvals panel that calls `.reload()`, so FR15 does not depend on F5.

### C2. Scenario 9 ("opens another employee's request **by URL**") is blocked by AD-4
- **Spine:** AD-4 has no return-URL restore and lands the user on their shell home after login. AD-9 Playwright journey (2) asserts the access-denied state when the user opens the URL.
- **Problem:** pasting or typing a URL is a full page load, so `currentUser` is `null` and the user is sent to `/login` and then to shell home. The 403 `LoadError` is never reached, and the Playwright journey can only pass through in-app `router.navigate`, which is not what the scenario says. The FR54 email link (`/requests/{id}`, see H5) has the same problem.
- **Fix:** C1(a) or C1(b) solves it once the user is logged in. In addition, `authGuard` should redirect to `/login?returnUrl=<url>` and log in should navigate to `returnUrl` after validating that it is same-origin and inside the user's shell. A query parameter is not browser storage, so this does not conflict with AD-4's intent. Update the "Deferred" bullet to match.

---

## High

### H1. "My requests" is undefined for MANAGER and HR, because `GET /requests` without a filter is not "mine"
- **Spine:** AD-3 maps `requests` as "my requests …" for all three roles.
- **Contract:** `GET /requests` without `employeeId` returns own requests **plus direct reports'** for a MANAGER, and **every request in the company** for HR_ADMIN (FR24; confirmed in `LeaveRequestService.list` with `findByScope`).
- **Problem:** a builder calling `GET /api/requests` gives a manager or HR user a "My Requests" list full of other people's requests. Edit and cancel buttons would then appear on requests the user cannot act on (each attempt returns 403).
- **Fix:** add to AD-5 or AD-3: "My Requests always calls `GET /api/requests?employeeId={currentUser.id}` (status filter optional)." The backend accepts self as `employeeId` because the owner check passes. If the team or HR "all requests" list is wanted, it belongs in `team` or `admin`, not `requests`.

### H2. FR7/FR8 UI gating (edit and cancel only while PENDING and only by the owner) is not stated
- **Spine:** AD-8 covers the edit form (leave type read-only) and AD-6 renders `INVALID_TRANSITION` inline. The spine never says when Edit and Cancel are shown.
- **Input:** PRD Decisions: "Request modification (FR7): only while Pending". The contract allows PUT and cancel only for the owner (403 otherwise).
- **Problem:** request detail is reachable by a manager (direct report's request), by HR (any request) and from notification links. Without a rule, builders will show Edit and Cancel on APPROVED requests or on other people's requests.
- **Fix:** add one rule, e.g. in AD-8 or a new "Request actions" bullet: `canModify = status === 'PENDING' && employeeId === currentUser.id`. Edit and Cancel are rendered (not merely disabled) only when it is true, and Cancel goes through `ConfirmDialog`. The edit route is reachable only when `canModify` is true and otherwise redirects to detail. Server 409 `INVALID_TRANSITION` remains the backstop.

### H3. AD-11 "names, never raw ids" cannot be met for ledger, balances and users, and nothing says how to resolve them
- **Spine:** AD-11 says people are shown via the `*Username` fields "the API returns next to each person id".
- **Contract:** these person ids have **no** `*Username` field:
  - `BalanceLedgerEntry.actorId` and `.userId`
  - `Balance.userId` (HR `GET /balances`)
  - `User.managerId`
  - `Notification.userId` (does not matter, since it is always "me")
- **Problem:** scenario 12 requires the "Ledger shows amount, reason, **actor** and time". Under AD-11 the actor would show as `#1`, or each builder invents a lookup.
- **Fix:** add an AD-11 sub-rule. Inside `features/admin`, person ids without a `*Username` are resolved from one `httpResource` on `GET /api/users` (HR-only, same feature) through a `userId → username` `computed()` map. Alternatively, extend `openapi.yaml` with `actorUsername` on `BalanceLedgerEntry`, `username` on `Balance` and `managerUsername` on `User`, and list them in the Deferred backend follow-up. The second option is cleaner and matches how the other DTOs were extended.

### H4. Data freshness across features and navigation is undefined, and AD-2 stops a feature reloading another's resources
- **Spine:** AD-5 says to reload "every resource the change affects" and "comments refresh only on explicit reload or navigation". AD-1/AD-2 put `httpResource` refs in feature services and forbid cross-feature imports. Where feature services are provided is not stated.
- **Problem:**
  1. An `httpResource` in a `providedIn: 'root'` service (or in lazy-route `providers`, whose environment injector is not destroyed on navigation) **does not refetch on navigation**. The promise that navigation refreshes, and FR15 with it, is therefore false by default.
  2. When HR approves their own request (PRD Decisions: self-approval), `approvals` must refresh `requests` (my requests and my balances), which AD-2 forbids it from touching.
  3. Notifications created by a submit are not visible until something reloads `notifications`.
- **Fix:** state that feature services are provided at the **routed page component** (`providers: [RequestsService]`), so each visit creates fresh resources and navigation equals refetch. Alternatively, keep root services and call `.reload()` in each page's constructor. For cross-feature invalidation, add a tiny `core/` `DataVersion` signal (e.g. `leaveDataChanged` counter) that mutations bump and that `requests` resources include in their request function. That keeps AD-2 intact.

### H5. The FR54 email/notification link `/requests/{id}` has no SPA route
- **Contract and backend:** the notification template renders `View request: {{requestLink}}` with `requestLink = "/requests/" + id` (`NotificationTemplateRenderer`). `Notification.requestId` is the obvious in-app link target for FR53.
- **Spine:** every route lives under `/employee|/manager|/hr`, `/` redirects to the shell home, and there is no shell-agnostic `/requests/:id`.
- **Fix:**
  - Add a top-level redirect route `requests/:id` → `/<role shell>/requests/:id`, implemented as a `redirectTo` function that reads `AuthService`, behind `authGuard`, and combined with C2's return URL.
  - Define the notifications → request-detail link as relative (`../requests/:id`). The `requests` detail must then be safe for non-owners (manager viewing a report's request, see H2).

---

## Medium

### M1. FR13 "Reject disabled until comment entered" is only implied
AD-8 lists "reject comment non-blank" as a validator hint. The PRD wants the **button disabled** (scenario 4: "Reject blocked without a comment"). Approve and reject share one optional or required comment field.
- **Fix:** state it in AD-8: "Reject button `[disabled]` while `comment().trim()` is empty. Approve is always enabled. One comment textarea feeds both. Server `COMMENT_REQUIRED` is rendered inline as a backstop."

### M2. "Reset password" is listed as a form, which contradicts the PRD Decision and the contract
- AD-8 binds "reset password" as a form. `POST /users/{id}/reset-password` has **no request body**. It **generates** and returns `TemporaryPassword.temporaryPassword` (PRD Decisions: intentionally asymmetric with create-user).
- **Fix:** remove it from the AD-8 forms list. Specify `ConfirmDialog` → POST → show the returned temporary password once in a dialog, with copy-to-clipboard and a warning that it will not be shown again. Never put it in a toast or a log. Also state that the create-user form's `temporaryPassword` field applies the same password-rule hint.

### M3. Responsive, desktop-first, Chrome/Edge technical requirement is missing
The PRD technical requirements say "Responsive layout for Chrome and Edge, desktop-first". The spine is silent: no browser targets and no breakpoint rule.
- **Fix:** add a Conventions row. Supported browsers are the latest 2 Chrome and Edge (set in `.browserslistrc`). Layouts are designed at ≥1280px first, use Tailwind `md`/`lg` breakpoints to collapse the nav and the approvals detail panel into a stacked view, and avoid horizontal page scroll down to 768px (tables may scroll inside their container).

### M4. Using the URL to choose the 401 path breaks session expiry on change-password
AD-6 lets a 401 on `POST /auth/change-password` pass through as "bad credentials". A session that expired while the user sat on `/change-password` also returns 401. The user then sees an inline error forever instead of being sent to `/login`. Also, `INVALID_CREDENTIALS` on change-password is not in the AD-6 inline list, so it would toast while AD-4 suggests inline handling.
- **Fix:** branch on the body. A 401 with `code === 'INVALID_CREDENTIALS'` passes through (login and change-password render it inline). Any other 401, including one without a ProblemDetail body, follows the session-expired path. Add `INVALID_CREDENTIALS` and `ACCOUNT_LOCKED` to the AD-6 inline list so AD-4 and AD-6 agree.

### M5. Session-expired UX (scenario 15) gives no message
AD-4 clears the user and navigates to `/login` silently. Scenario 15 says "Session expires; next action asks for login". A bare login page after an action that looks like it failed is confusing.
- **Fix:** navigate to `/login?reason=expired` (or set a `core/` signal), and have the login page show "Your session expired after 30 minutes of inactivity. Please sign in again." through `LiveAnnouncer`.

### M6. The AD-5 reload list misses edit, comments and detail resources
The examples cover submit, cancel, approve and reject. FR7 edit changes the reservation (scenario 2: "Reservation updates on edit"), so it must reload my balances, my requests, request detail and History. Posting a comment must reload that request's comments and History, or the pending-approvals resource when posted from the approvals panel. Otherwise the author does not see their own comment, which is not what "refresh to see" means.
- **Fix:** turn the examples into an exhaustive table:

| Mutation | Resources to reload |
| --- | --- |
| submit | my requests, my balances |
| edit | my requests, my balances, detail, history |
| cancel | my requests, my balances, detail, history |
| comment | comments, history (or pending approvals when posted from approvals) |
| approve / reject | pending approvals (+ the H4 invalidation for self-approval) |
| set / adjust | balances list, ledger |
| create user / reset password | users list |

### M7. Where comments can be posted is undefined in team and admin, and FR15 says the thread includes HR
AD-2 and the capability map put `CommentThread` in `requests`, `approvals`, `team` and `admin`, but do not say whether it is read-only or can post there. FR15 and `TeamLeaveEntry` describe comments "between employee, manager and HR". HR can comment on any request through `POST /requests/{id}/comments`, but only the audit view gives HR a path to other people's requests.
- **Fix:**
  - `CommentThread` takes an `@Input canPost` and emits a `post` event, so the owning feature service performs the POST (`shared/` stays stateless).
  - Posting is enabled in `requests` detail and `approvals`.
  - In `team` (approved, historical) and `admin`/audit it is read-only unless the PO wants HR posting. Record that decision.

### M8. The backend spine's AD-11 error codes contradict the contract, and the frontend's inherited row does not flag it
The backend spine AD-11 names `BALANCE_INSUFFICIENT` and `RBAC_DENIED`. The contract (and `common.ErrorCode`) uses `INSUFFICIENT_BALANCE` and `ACCESS_DENIED`. The frontend spine correctly uses the contract codes, but its inherited AD-11 row reads as if the parent spine were authoritative. The backend spine also says the JWT lifetime is 15 min, while code is 30 min with a sliding re-issue; the frontend's assumption that expiry surfaces as a 401 still holds.
- **Fix:** in the inherited-invariants row, add: "codes are the `ProblemDetail.code` enum in `openapi.yaml` (the generated union), not the backend spine's examples". Raise a one-line correction on the backend spine.

---

## Low / notes

- **FR5 "filterable list":** the capability map does not mention the status filter. Bind it to `GET /requests?status=` through the `httpResource` request signal, not client-side filtering, so it stays consistent with H1.
- **Scenario 9 renders three `LoadError`s:** detail, history and comments all return 403. Have the detail page show one access-denied state driven by the primary `GET /requests/{id}` and skip the child resources when it errors.
- **`ApprovalDetail.leaveTypeName` and `AuditRequestReport.leaveTypeName` duplicate `LeaveTypeNamePipe`.** Either is fine. Pick one ("always the pipe") so builders do not mix them.
- **`TeamLeaveEntry` description in openapi says "a manager or HR"** while its 403 says MANAGER only. The spine's MANAGER-only `team` matches the PRD endpoint table and the 403. Flag the description to the API owner.
- **Date gotcha:** AD-11's "dates shown without timezone conversion" should name the pitfall. `new Date('2026-10-29')` parses as UTC midnight and shifts the day west of UTC. Use a date-only formatter or string formatting.
- **`/approvals/pending` for HR:** state that HR's own pending requests (self-approval, PRD Decisions) appear in HR's approvals list. Nothing blocks it, but the demo script will ask.
- **Create user with role HR_ADMIN:** `managerId` may be null. The AD-8 hint already covers this. Make sure the Manager dropdown is hidden or cleared when HR_ADMIN is chosen.
- **Balances that do not exist yet:** HR "set" needs a user and type picker for combinations missing from `GET /balances`. Building the grid from users × leave types avoids an invisible-row dead end.

---

## Coverage matrix (summary)

| Item | Home in spine | Status |
| --- | --- | --- |
| FR1–FR3 submit / dates / notes | requests, AD-8, DateRangeFields | OK |
| FR4–FR5 status / history | requests | **H1** (scoping), low (filter) |
| FR6 balances | requests, BalanceCard | OK |
| FR7–FR8 edit / cancel | requests, AD-8 | **H2**, M6 |
| FR9, FR16 recorded by / at | display via AD-11 | OK |
| FR10–FR11 dashboard / context | approvals, ApprovalDetail | OK |
| FR12–FR13 approve / reject | approvals, AD-8 | **M1** |
| FR14 team | team (MANAGER) | OK |
| FR15 comments | shared CommentThread | **C1**, H4, M6, M7 |
| FR17 decision scope | server + 403 toast | OK |
| FR18–FR20 users / reset | admin | **M2** |
| FR21–FR25 RBAC UX | guards, LoadError | OK; C2 for scenario 9 |
| FR26 / FR59 access log | none (backend-only per PRD) | OK |
| FR27–FR30 HR balances / ledger | admin | **H3** (actor names) |
| FR31 no negative | inline INSUFFICIENT_BALANCE | OK |
| FR42–FR45 audit / History | admin, HistoryTimeline | OK |
| FR50–FR54 notifications | notifications | **H5** (link) |
| FR55 HTTPS | AD-12 / inherited AD-13 | OK |
| FR56–FR57 login / lockout | AD-4 inline | OK (M4 alignment) |
| FR58 timeout | AD-4 / AD-6 | **M5**, M4 |
| Scenarios 1, 3, 4, 6, 7, 8, 10–14 | — | OK (4 depends on M1, 12 on H3) |
| Scenario 2 | — | M6 |
| Scenario 5 | — | **C1** |
| Scenario 9 | — | **C2** |
| Scenario 15 | — | M5 |
| Tech requirement: responsive / browsers | — | **M3** |
| Tech requirement: accessibility | Conventions row | OK |
| Tech requirement: password rule | AD-8 | OK |
| Tech requirement: cookie flags | inherited AD-7, AD-12 | OK |

## Role → endpoint reachability (AD-3 vs contract)

| Endpoint | EMPLOYEE | MANAGER | HR_ADMIN | AD-3 feature | Match |
| --- | --- | --- | --- | --- | --- |
| POST /auth/login, logout, change-password | 200/204 | 200/204 | 200/204 | login | ✓ |
| GET /leave-types | 200 | 200 | 200 | core/reference | ✓ |
| GET /requests | own | own + reports | all | requests | **H1**: must pass `employeeId=self` |
| POST /requests, PUT /requests/{id}, POST /requests/{id}/cancel | owner | owner | owner | requests | ✓ (H2 gating) |
| GET /requests/{id}, /history, /comments; POST /comments | owner | owner or manager | any | requests, approvals | ✓ |
| GET /balances/me | 200 | 200 | 200 | requests | ✓ |
| GET /notifications | 200 | 200 | 200 | notifications | ✓ |
| GET /approvals/pending; approve / reject | — | direct reports | top-level managers + self | approvals (M, HR) | ✓ |
| GET /team/leave | 403 | 200 | 403 | team (M only) | ✓ |
| GET/POST /users, GET /users/managers, POST reset-password | 403 | 403 | 200 | admin (HR) | ✓ |
| GET /balances, PUT set, POST adjust, GET ledger | 403 | 403 | 200 | admin (HR) | ✓ |
| GET /audit | 403 | 403 | 200 | admin (HR) | ✓ |

No AD-3 grant routes a role to an endpoint that returns 403 for that role. The only reachability defect is H1: the endpoint is allowed, but its default scope is wrong for "My Requests".
