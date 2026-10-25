---
review: adversarial
target: ARCHITECTURE-SPINE.md (company-leave-frontend, draft 2026-10-25)
checked_against: backend/src/main/resources/openapi.yaml, prds/poc-prd-emp-leave-tracker-2026-10-22/prd.md, backend/src/main/java (approver routing)
date: 2026-10-25
verdict: NOT READY. 12 holes, 4 of them blocking.
---

# Adversarial Review: Frontend Architecture Spine

## Method

For each AD, I set two builders side by side (for example feature A and feature B, or a shared component and a feature). Both follow every AD exactly, and I looked for places where their outputs still fail to fit together. Each such pair is a hole. For each hole I propose the smallest rule change that closes it. Holes are ranked by severity. **Blocking** means the app fails an acceptance scenario or will not build. **High** means two builders will build things that don't fit together. **Medium/Low** means drift that will need rework.

## Verdict

The spine is a strong start. The paradigm, the layering, the error taxonomy and the type generation are the right choices. But it fixes each unit's local behaviour and leaves out the rules that hold units together:

- how state is invalidated across features and across users
- the URL contract for links between features
- what happens to a request after the interceptor has handled it
- where the shared/core boundary sits for components that need state

Two holes also break acceptance scenarios: scenario 5, "comments appear after page refresh", and same-tab user switching in the Playwright journey. A third breaks `npm run gen:api` against the current spec.

---

## H1 — Blocking: data goes stale across features and across users. AD-5 reloads cannot reach resources owned by another feature (AD-2).

**Pair A: `approvals` × `requests` (HR self-approval).** In the backend, the approver of an HR user's own request is that same HR user (`LeaveRequestService`: "caller's manager, or self for HR with null manager"). So the request appears both in HR's `/approvals/pending` and in HR's my-requests and my-balances, which `requests` owns.

- The `approvals` builder follows AD-5: approve calls `tap()` and reloads "pending approvals".
- AD-2 stops `approvals` from importing `RequestsService`, so my-requests (still PENDING) and my-balances (still reserved, not deducted) stay stale.
- The same applies to `notifications`: the self-approval writes an APPROVED notification to the same user.

**Pair B: `requests` comment × `requests` history.** Posting a comment adds a `COMMENT` History entry. AD-5's example list ("submit or cancel reloads my-requests and my-balances") never mentions history. AD-5 also says "comments refresh only on explicit reload or navigation". One builder reloads comments and history after the post. Another reads the FR15 line literally and reloads nothing, so the author's own comment never appears.

**Pair C: same tab, two users (AD-4 × AD-1).** The Playwright journey from AD-9 logs in as an employee, logs out, then logs in as a manager.

- If `RequestsService` is `providedIn: 'root'`, its `httpResource`s keep the employee's my-requests, my-balances and notifications. Nothing in the spine says feature-service scope, and AD-2 only requires `providedIn: 'root'` for `core/`.
- Lazy route injectors are never destroyed, so even route-level providers keep the employee's data under `/employee` when that shell is visited again.
- An in-flight response that lands after logout repopulates the data.

**Pair D: parameterized resources.** Request detail needs an id.

- Builder 1 puts `selectedId = signal()` in the service and one service-level `httpResource`.
- Builder 2 adds a service factory, `detailResource(id: Signal<number>)`, called in the component's injection context.
- AD-5 says "the service `tap()`s and reloads every resource", but a service cannot reload a resource it holds no reference to (Builder 2's case).

**Proposed rule (new AD-5a — invalidation by version signal, not by reference):**

> `core/data/DataVersionService` (root) exposes one `version = signal(0)` per data domain: `leave` (requests, history, comments, approvals, team, audit, notifications) and `balances`. Every user-scoped `httpResource` reads the relevant `version()` and `auth.currentUser()?.id` inside its request function. When `currentUser()` is `null`, the function returns `undefined`, so no request is sent. Every successful mutation bumps the versions it touches: request or comment writes bump `leave`; submit, edit, cancel, approve, reject and HR set/adjust bump both. `.reload()` by reference is no longer needed. Feature services are `providedIn: 'root'`. Parameterized resources come from service factory methods called in the component's injection context. `logout()` and the 401 path end with `window.location.assign('/login')`, a full document reload, so no in-memory state survives a user switch.

## H2 — Blocking: "refresh to see" (FR15, scenario 5) contradicts AD-4's "a page refresh logs you out".

PRD scenario 5 passes when "Comments appear after **page refresh** with no alert". Scenario 15 expects that the "next action asks for login" only after 30 minutes idle. Under AD-4, a browser refresh sets `currentUser` to `null`, so `authGuard` sends the user to `/login`, and the session cookie is still valid.

- The `requests` builder follows FR15 and adds no refresh control ("refresh the page").
- The `login` builder follows AD-4.

Combined, scenario 5 turns into "re-login to see comments", and the demo fails.

**Proposed rule (one of these, decided in the spine):**

- **(a), preferred within the contract.** Keep a non-secret `CurrentUser` hint (`id`, `username`, `role`, `mustChangePassword`) in `sessionStorage`. It is not a token. It is only a hint, and the first 401 clears it. Amend AD-4 and the "no browser storage" convention to allow this one key.
- **(b)** Add `GET /auth/me` to the backend (already listed under Deferred), and have `authGuard` restore from it once.
- **(c), minimum.** Every list and detail screen gets an in-app **Refresh** button that bumps the H1 version signal. The spine then states that "page refresh" in FR15 and scenario 5 means this button, and records that interpretation in the PRD.

## H3 — Blocking: AD-10 code generation fails or produces unusable types from the current spec.

- **Dangling ref.** `POST /requests/{requestId}/cancel` → `'401': $ref: '#/components/responses/Unauthorized'` (openapi.yaml line 538), but `components.responses` defines only `Forbidden`, `PasswordChangeRequired`, `NotFound` and `ValidationError`. openapi-typescript (Redocly resolver) fails on unresolved refs, so `npm run gen:api` breaks.
- **Everything optional.** No response schema (`LeaveRequest`, `Balance`, `CurrentUser`, `ProblemDetail`, …) has a `required` list, so every generated field is `field?: T`.
  - Builder 1 writes `req.id!`.
  - Builder 2 writes `req.id ?? 0`.
  - Builder 3 wraps the type in a view model with non-optional fields. That re-declares fields, which AD-10 forbids, yet it is the obvious response.
  - Templates disagree on what `undefined` means for `status` and `workingDays`.
- **No 401 shape.** `ProblemDetail.code` has no session-expired value, and `/notifications` 401 has no body. That feeds H4.

**Proposed rule (tighten AD-10):**

> The spec must resolve cleanly (`redocly lint`) before generation. Response schemas declare `required`, or `gen:api` runs with `--properties-required-by-default` so that only `nullable` fields are optional. Add `components.responses.Unauthorized` (401 ProblemDetail, `code: UNAUTHENTICATED`) to the backend follow-up in Deferred, and add `UNAUTHENTICATED` to the `code` enum.

## H4 — Blocking: the interceptor redirects, then passes the error on, so errors are reported twice or not at all, and the change-password 401 exclusion traps an expired session.

AD-6 says the interceptor handles a 401 or `PASSWORD_CHANGE_REQUIRED` by navigating, and "everything else passes through". It does not say what happens to the handled error.

- **Pair: interceptor × write without an error callback.** On 401 the interceptor navigates to `/login` and rethrows. The subscription has no error callback, so `ErrorHandler` toasts "Unauthorized" on the login page while the redirect is in progress, which is double reporting. If the interceptor instead returns `EMPTY`, any component that cleared a `saving` flag in `next` stays busy forever.
- **Pair: interceptor × read.** On 401 a `LoadError` flashes on the page you are leaving. On `PASSWORD_CHANGE_REQUIRED` a `LoadError` renders, then the page navigates away.
- **Pair: `change-password` × expired session.** The 401 exclusion is matched by URL. A user whose 30-minute session expired on the change-password screen gets a 401 that passes through, and the form shows "Current password is incorrect" no matter what they type. They are stuck, and scenario 15 fails on this screen. Nothing in AD-3 says whether `/change-password` (outside the shells) has `authGuard`.

**Proposed rule (tighten AD-6 and AD-4):**

> When the interceptor handles a 401 (session) or a 403 `PASSWORD_CHANGE_REQUIRED`, it navigates and then returns `EMPTY`. The error never reaches a component, `LoadError` or `ErrorHandler`. Components clear busy flags in `finalize()`, never in `next`/`error`. The login and change-password exclusion applies only when `error.code === 'INVALID_CREDENTIALS'`. Any other 401 on those URLs takes the session path. `/change-password` has `canActivate: [authGuard]`. `logout()` clears local state in `finalize()`, so a failed `POST /auth/logout` (for example, the session already expired) still logs the user out locally.

## H5 — High: the shared/core layering contradicts itself for stateful pipes and components.

The AD-2 graph has `core → shared` and no `shared → core` edge, and "shared holds no injectable state". But the Structural Seed places:

- `LeaveTypeNamePipe` in `shared/pipes/`. It must inject `core/reference/LeaveTypeService`, which is `shared → core`, so with `core → shared` that is a cycle.
- `Toast` in `shared/components/`. It must read `core/errors/ToastService`'s queue, which is the same violation.
- `ConfirmDialog` in `shared/components/`. Something must open it with CDK `Dialog`, and AD-7 says "features never use CDK overlays directly". Is the opener an injectable in `shared` or in `core`? The spine doesn't say.

**Pair:** builder 1 injects the core service in a shared pipe and breaks the layering. Builder 2 passes the leave-type list as a pipe argument from every caller. The two produce incompatible pipe signatures.

**Proposed rule (tighten AD-2):**

> `shared/` never injects a `core/` service. Anything that reads root state lives in `core/`: `core/reference/LeaveTypeNamePipe`, `core/errors/ToastOutletComponent`, and `core/ui/ConfirmService.confirm(opts): Observable<boolean>`, which owns CDK `Dialog` and renders `shared/ConfirmDialog`. The toast outlet is mounted once in the root `AppComponent`, not in the shells, so toasts also show on `/login` and `/change-password`.

## H6 — High: there is no URL contract for links between features. AD-3's "relative links only" breaks across feature boundaries.

The request detail lives in `requests`, but `notifications` (`Notification.requestId`), `approvals`, `team` (`TeamLeaveEntry.requestId`) and `admin` audit (`AuditRequestReport.requestId`) all need to link to it.

- **Pair: `notifications` × `requests`.** The notifications builder writes `routerLink="../requests/{{id}}"`, which depends on route depth. The requests builder defines `requests/view/:requestId` (or `:id`, or `detail/:id`). The link 404s, or lands on the list.
- **Pair: `notifications` × shells.** A builder who wants an absolute link hard-codes `/employee/requests/101`. AD-3 forbids that, but gives no alternative. A manager who follows that link is bounced by `roleGuard` to `/manager`, and the request id is lost (AD-3: "redirects to the user's own shell", which does not preserve the path).
- **Shell home is undefined.** AD-4 navigates to "the shell home" after login and after a password change, but no AD names each shell's default child.
- **Unclear target for a manager's SUBMITTED notification.** Should it open the request detail (no Approve button there) or the approvals panel? The spine doesn't say.

**Proposed rule (extend AD-3):**

> Each feature's child paths are fixed in the spine:
> - `requests`: `''` (list + balances), `new`, `:requestId`, `:requestId/edit`
> - `approvals`: `''`, `?requestId=` (preselects the panel)
> - `team`: `''`
> - `admin`: `users`, `balances`, `audit`
> - `notifications`: `''`
>
> Every shell's `''` redirects to `requests`. Links between features are built only with `core/routing/AppLinks` (`requestDetail(id)`, `approval(id)`, …), which prefixes the current user's shell. A SUBMITTED notification links to `approval(id)` when the current user is the approver, and otherwise to `requestDetail(id)`. A wrong-shell redirect keeps the rest of the path.

## H7 — High: owner-only actions vs. who can see a request, plus the manager's "my requests".

`GET /requests` without `employeeId` returns **the manager's own requests plus their direct reports'** (openapi lines 213–217). The AD-3 table labels the `requests` route "my requests … my balances".

- **Pair: list builder × detail builder.**
  - The list builder renders every row with Edit/Cancel, and a manager sees Edit on a report's request (the server returns 403, which shows as a toast).
  - Or the list builder filters client-side to `employeeId === me`, which hides reports' requests that FR23 says the manager should see.
  - The detail builder, reached by a manager or HR from `team` or `audit`, shows Edit/Cancel to a non-owner.
- **HR self-approval:** the HR user's own pending request appears in both `requests` (Cancel) and `approvals` (Approve/Reject). That is valid, but nobody owns the decision of whether HR may decide from the detail page.

**Proposed rule (new display rule under AD-3 or AD-11):**

> Edit and Cancel render only when `request.employeeId === currentUser.id && status === 'PENDING'`. Approve and Reject render only in `approvals`. The `requests` list shows every row the API returns, with an Employee column (username) for MANAGER and HR, and offers an "Only mine" filter that defaults to on. The filter is applied client-side by `employeeId`, because the API rejects a manager's own id as `employeeId`. My-balances always means `/balances/me`.

## H8 — Medium: errors are reported zero times or several times on multi-resource pages and stale lists.

- **Request detail has three resources** (request, history, comments). A deep link to another employee's request (scenario 9) shows three `LoadError`s for one 403. Builders will disagree on which to show.
- **Stale conflict.** A manager approves a request the employee cancelled a minute earlier. The server returns 409 `INVALID_TRANSITION`, which AD-6 renders inline, but AD-5 reloads only on success. The row stays "Pending", with an Approve button that will always fail.
- **Which screens render which codes is not mapped.** AD-6 lists the inline-capable codes globally, so:
  - Builder 1 renders `INVALID_TRANSITION` inline next to the Cancel button. Builder 2 toasts it.
  - `INSUFFICIENT_BALANCE` from HR set/adjust: inline or toast?
  - `VALIDATION_FAILED` "username already taken" on create user: inline at form level?
- **Non-ProblemDetail errors.** `LoadError` "shows title/detail" and gets nothing for status 0, a 502 HTML page from the proxy, or the bodiless 401. Each builder will extract `err.error` in a different way.

**Proposed rule (tighten AD-6):**

> One `core/errors/toProblem(err: unknown): ProblemDetail` (with a synthetic fallback) is the only way to read an error. `LoadError` takes `unknown`. A page with several resources renders `LoadError` for its primary resource only. Dependent resources return `undefined` from their request function until the primary has a value. Add a per-screen table, *screen → codes rendered inline*. Any code not in the table is toasted. On `INVALID_TRANSITION` or `NOT_FOUND` from a mutation, bump the same data versions as on success, so the stale row refreshes.

## H9 — Medium: date handling drifts at the edges.

AD-11 covers display only. Builders will diverge on:

- **"Today"** (default team-view range, a `min` on the date input). `new Date().toISOString().slice(0, 10)` is the UTC date, which is off by one near midnight in non-UTC zones.
- **Parsing.** `new Date('2026-10-29')` is UTC midnight. Comparing or formatting it outside Angular's `DatePipe` shifts it to the 28th west of UTC.
- **Display format** is unspecified, so screens show "29 Oct 2026", "10/29/2026" and "2026-10-29" side by side.
- **Times inside text.** The 423 `ACCOUNT_LOCKED` `detail` carries a raw UTC instant ("until 2026-10-22T15:45:00Z"), which AD-6 says to display verbatim. That contradicts AD-11's "local time".

**Proposed rule (tighten AD-11):**

> Dates stay as `yyyy-MM-dd` strings from API to form and back, and are compared as strings. Never call `new Date(dateString)`. "Today" comes only from `core/time/today(): string`, built from local date components. Dates display as `DatePipe 'd MMM y'` and date-times as `'d MMM y, HH:mm'` with no timezone argument. Server text that contains an instant is shown as is, and the gap is noted (or the backend is asked for a structured `lockedUntil`).

## H10 — Medium: form validation drifts (AD-8).

- **Password rule.** "≥ 8 chars with letters and numbers" can be written two ways: `/^[A-Za-z0-9]{8,}$/` (which rejects the spec's own example `Temp#2026`) or `/(?=.*[A-Za-z])(?=.*\d).{8,}/`. The change-password form (`login` feature) and the create-user temporary-password field (`admin`) will each pick one. The spine also doesn't say whether HR's temporary password must follow the rule at all.
- **Blank optional strings.** Clearing notes on edit, or leaving the approve comment empty, sends `""` from one builder and `null` (or omits the field) from another. History then shows empty decision comments in some places and not others.
- **Reject comment.** "Non-blank" with or without trimming? The server's rule is "non-blank", so the client must trim the same way.

**Proposed rule (tighten AD-8):**

> Shared validators live in `core/forms/validators.ts`, one per server rule: `passwordRule = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/`, `nonBlank` (trims), `endOnOrAfterStart`. The temporary password at create user is validated with `passwordRule` only if the backend enforces it; check this and state it. Optional strings are trimmed, and an empty result is sent as `null`. No client rule exists that is not listed here.

## H11 — Medium: the same data has two names (AD-11 × the API).

`ApprovalDetail.leaveTypeName` and `AuditRequestReport.leaveTypeName` arrive embedded. AD-11 says to resolve leave-type names through `LeaveTypeNamePipe`. The `approvals` and `admin` builders will use the embedded field, the `requests` and `team` builders the pipe, and the two can disagree if a type is renamed. The identifier key also differs: `id` on `LeaveRequest`, `requestId` on `TeamLeaveEntry` and `AuditRequestReport`. A shared summary component typed as `LeaveRequest` can't take the other two.

**Proposed rule:**

> Always use the pipe, and ignore embedded `*Name` fields. `shared/` component inputs are typed as `Pick<>`s of the generated types (or the narrow types `HistoryEntry[]`, `Comment[]`, `Balance`), never as a whole request-like schema. A caller maps `requestId` to `id` only in a view type that wraps the generated type (AD-10).

## H12 — Low: smaller gaps.

- **`LeaveTypeService` "loaded once per session".** If it is injected before login (for example in a root component), it 401s and stays in error for the rest of the session. **Fix:** its request function depends on `currentUser()` (the H1 pattern).
- **Comment posting surfaces.** `CommentThread` is shared by `requests`, `approvals`, `team` and `admin`. Which of those may post? `team` and `admin` builders will disagree. **Fix:** posting is allowed only in `requests` detail and the `approvals` panel. `team` and `admin` are read-only.
- **Layout badges.** A shell nav with "Approvals (3)" has no legal data owner, because layouts may not touch the API or features. **Fix:** no counts in the shell navigation in the POC.
- **AD-9 journey 1** switches users in one tab and depends on the H1 fix. Use the full-reload logout, or a separate Playwright `browserContext` per user.

---

## Summary table

| # | Severity | Hole | Fix (AD) |
| --- | --- | --- | --- |
| H1 | Blocking | Cross-feature and cross-user staleness; reload by reference is impossible under AD-2 | New AD-5a: `DataVersionService` version signals, user-keyed request functions, full reload on logout/401, feature service scope fixed |
| H2 | Blocking | Page refresh means logout, which contradicts FR15 and scenario 5 | Amend AD-4: sessionStorage `CurrentUser` hint, or `/auth/me`, or an in-app Refresh button plus a PRD note |
| H3 | Blocking | Dangling `Unauthorized` ref breaks `gen:api`; all fields optional; no 401 code | Tighten AD-10: lint the spec, require `required` or `--properties-required-by-default`, add `UNAUTHENTICATED` |
| H4 | Blocking | Interceptor redirect plus rethrow means double reporting; change-password 401 traps an expired session | Tighten AD-6/AD-4: `EMPTY` after handling, exclusion by `code`, `finalize()`, authGuard on `/change-password` |
| H5 | High | `shared → core` cycle (LeaveTypeNamePipe, Toast, ConfirmDialog opener) | Tighten AD-2: stateful pipes and outlets move to `core`; one toast outlet at the app root |
| H6 | High | No URL contract between features; shell home undefined | Extend AD-3: fixed child-path table, `core/routing/AppLinks`, redirects keep the path |
| H7 | High | Owner-only actions; the manager's list includes reports' requests | New rule: action-visibility predicate, Employee column, "Only mine" filter |
| H8 | Medium | Triple `LoadError`; stale row after 409; no screen → code map | Tighten AD-6: `toProblem()`, primary-resource rule, per-screen code table, bump versions on 409/404 |
| H9 | Medium | Off-by-one for "today", parsing drift, mixed display formats | Tighten AD-11: string dates, `core/time/today()`, fixed formats |
| H10 | Medium | Password regex, blank vs `null`, trimming | Tighten AD-8: `core/forms/validators.ts`, empty becomes `null` |
| H11 | Medium | Embedded `leaveTypeName` vs pipe; `id` vs `requestId` | Use the pipe only; shared inputs typed as `Pick<>` |
| H12 | Low | LeaveTypeService before auth, comment-posting surfaces, layout badges, E2E user switching | See H12 |
