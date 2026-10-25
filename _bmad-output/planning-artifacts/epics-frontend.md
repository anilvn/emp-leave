---
status: draft
stepsCompleted: [1, 2]
scope: frontend-only
inputDocuments:
  - _bmad-output/planning-artifacts/prds/poc-prd-emp-leave-tracker-2026-10-22/prd.md
  - _bmad-output/planning-artifacts/architecture/architecture-frontend-company-leave-2026-10-23/ARCHITECTURE-SPINE.md
  - backend/src/main/resources/openapi.yaml
  - _bmad-output/specs/spec-emp-leave-tracker/SPEC.md
  - _bmad-output/specs/spec-emp-leave-tracker/fr-coverage.md
  - _bmad-output/specs/spec-emp-leave-tracker/acceptance-scenarios.md
  - _bmad-output/specs/spec-emp-leave-tracker/data-model.md
  - _bmad-output/specs/spec-emp-leave-tracker/delivery-plan.md
companions:
  - _bmad-output/planning-artifacts/epics.md  # backend breakdown (final); not modified by this document
---

# company-leave (Frontend) - Epic Breakdown

## Overview

This document breaks the **Angular 22 + Tailwind CSS 4 frontend** of company-leave (Employee Leave Tracking System POC) into epics and stories. It draws on the POC PRD, the frontend Architecture Spine, the OpenAPI contract (`backend/src/main/resources/openapi.yaml`) and the canonical spec (`_bmad-output/specs/spec-emp-leave-tracker/`).

**Scope decisions (confirmed with anil_vn, 2026-10-25):**

- Stories cover the **frontend SPA only** (`frontend/`). The backend breakdown lives in `planning-artifacts/epics.md` and is not modified here. Each backend capability is treated as a dependency the UI consumes through `openapi.yaml`.
- **No UX design contract exists** (`planning-artifacts/ux-designs/` is absent). UX Design Requirements (UX-DRs) are **derived** from the PRD's UI constraints and the frontend spine's AD-7, Consistency Conventions and `shared/ui` / `core/ui` component inventory.
- Acceptance criteria are written at the UI level (what the user sees and does, which API call is made, how errors render). They can be verified with Vitest units, render smoke tests and Playwright journeys against `docker compose` (frontend AD-9).

## Requirements Inventory

### Functional Requirements

The POC covers 45 in-scope FRs: FR1-FR31, FR42-FR45, FR50-FR59. FR numbers match the source PRD. Each FR below is restated from the **frontend's** side: the screen or behaviour the UI must provide, and the API operation behind it. FRs that are enforced purely server-side are kept for traceability and marked **[backend-enforced]** with the UI surface they still need, if any.

**Leave requests (FR1-FR9)**

FR1: An employee can submit a leave request from a form whose leave-type dropdown is populated from `GET /api/leave-types` (Vacation, Sick, Parental, Sabbatical); whole days only. Submitted via `POST /api/requests`.
FR2: The submit form takes a start date and an end date (native date inputs, `yyyy-MM-dd`), validating end ≥ start on the client; the working-day count shown afterwards is the server's `workingDays` value (Mon-Fri inclusive), never computed by the UI.
FR3: The submit form has an optional free-text notes field; blank notes are sent as `null`.
FR4: An employee can see each of their requests on My Requests with its status rendered as Pending, Approved, Rejected or Cancelled.
FR5: My Requests is filterable by status (`GET /api/requests?employeeId=<me>&status=`), always scoped to the current user's own requests.
FR6: An employee can see one balance card per leave type showing total, reserved (pending) and available days, from `GET /api/balances/me`.
FR7: The owner can edit a request only while it is Pending, using the submit form pre-filled with the current values and the leave type read-only (`PUT /api/requests/{id}`); the updated working days and balances reflect the server's recalculation.
FR8: The owner can cancel a Pending request after confirming (`POST /api/requests/{id}/cancel`); the request then shows as Cancelled, stays visible in My Requests, and the reserved days are released in the balance cards.
FR9: Request detail shows the submitter (`employeeUsername`) and submission time (`submittedAt`, local time) **[recording is backend-enforced]**.

**Approvals (FR10-FR17)**

FR10: A manager (and HR, for top-level managers' and their own requests) has a Pending Approvals page listing every request awaiting their decision, from `GET /api/approvals/pending`.
FR11: Each pending approval shows full decision context without extra navigation: employee username, dates, leave type name, working days, notes, the employee's current balance for that type, the History timeline and the comment thread (`ApprovalDetail`).
FR12: An approver can approve a pending request with an optional comment (`POST /api/approvals/{id}/approve`); afterwards the request leaves the pending list and shows as Approved.
FR13: An approver can reject a pending request only with a non-blank comment; the Reject action stays disabled until a comment is entered (`POST /api/approvals/{id}/reject`), and a server `COMMENT_REQUIRED` is rendered inline.
FR14: A manager has a Team view of their direct reports' approved leave for a chosen date range (`GET /api/team/leave?from=&to=`), each entry showing its comment thread and History timeline.
FR15: The owner, their manager and HR can read and post comments on a request (`GET/POST /api/requests/{id}/comments`) from request detail and from the approvals context panel; new comments appear on page refresh or an in-page Refresh action, with no live push and no alert.
FR16: Request detail and History show the decision, the decision time (`decidedAt`), the approver (`approverUsername`) and the decision comment **[recording is backend-enforced]**.
FR17: Only the request's assigned approver may decide; Approve/Reject appear only in the approvals feature on rows from `GET /api/approvals/pending`, and a 403 `ACCESS_DENIED` from a decision is still shown to the user **[enforcement is backend-side]**.

**Users and access (FR18-FR26)**

FR18: HR can create a user account from a "Create an account" form with username, role, manager and a temporary password (`POST /api/users`).
FR19: On that form, role is chosen from the three-value enum and manager from a dropdown populated by `GET /api/users/managers` (every MANAGER and HR_ADMIN); manager is required unless the role is HR_ADMIN.
FR20: HR can reset a user's password after confirming (`POST /api/users/{id}/reset-password`) and is shown the generated temporary password exactly once; a user holding a temporary password is forced to the change-password screen and can do nothing else until they change it (`POST /api/auth/change-password`).
FR21: The UI shapes navigation and routes for three roles (EMPLOYEE, MANAGER, HR_ADMIN) through role shells and guards; server-side RBAC stays the authority.
FR22: An employee only ever sees their own data; deep-linking to another employee's request renders an access-denied state from the API's 403 (scenario 9).
FR23: A manager sees only their direct reports' data in approvals and team views **[scoping is backend-enforced]**; a 403 is still rendered.
FR24: HR sees all users, balances, requests and the audit trail with no scope filter in the UI.
FR25: Any unauthorized access returns 403 `ACCESS_DENIED`, which the UI renders as a page-level access-denied state (reads) or a toast (writes), never silently.
FR26: Sensitive-data access is logged to `access_log` **[backend-enforced; no read API or UI in the POC]**.

**Balances (FR27-FR31)**

FR27: HR can view all users' balances on a Balances page (`GET /api/balances`, optional `userId` filter), with users and leave types shown by name.
FR28: HR can set a balance's total days with a required reason (`PUT /api/balances/{userId}/{leaveTypeId}`).
FR29: HR can adjust a balance by a signed number of days with a required reason (`POST /api/balances/{userId}/{leaveTypeId}/adjust`).
FR30: HR can read the ledger behind a balance (`GET /api/balances/{userId}/{leaveTypeId}/ledger`), showing per row the time, signed amount, kind, reason and actor (by username), oldest first.
FR31: No balance can go negative: an over-balance submit or edit shows the server's `INSUFFICIENT_BALANCE` detail inline on the request form (scenario 10), and an HR set/adjust that would go below zero or below reserved shows it inline on the balance form (scenario 12).

**Audit (FR42-FR45)**

FR42: HR can view the audit trail as one report per leave request in any status (`GET /api/audit`), read-only.
FR43: Each audit report shows, for every History entry, the action, the actor (by username) and the time.
FR44: Audit records are immutable **[backend-enforced by a DB trigger]**; the audit UI offers no edit or delete affordance.
FR45: Every request detail has a History tab showing the ordered timeline (`GET /api/requests/{id}/history`): submission, edits, cancellation, comments and decision, each with actor username, local time and detail.

**Notifications (FR50-FR54)**

FR50: When a request is approved, the employee's Notifications page lists an "approved" notification **[creation is backend-side via `sendNotification()`]**.
FR51: When a request is rejected, the employee's Notifications page lists a "rejected" notification **[creation is backend-side]**.
FR52: When a request is submitted, the approver's Notifications page lists a "submitted" notification **[creation is backend-side]**.
FR53: Every user has a Notifications page listing their own notification history (`GET /api/notifications`): kind, subject and sent time.
FR54: Each notification links to its request detail through `AppLinks.requestDetail(requestId)`, carrying the request details in its subject **[template content is backend-side]**.

**Security (FR55-FR59)**

FR55: The SPA is served only through the HTTPS reverse proxy, on the same origin as `/api` (Partial: self-signed certificate).
FR56: A user signs in on a login page with username and password (`POST /api/auth/login`), signs out from the shell header (`POST /api/auth/logout`), and can change their password; a wrong password shows `INVALID_CREDENTIALS` inline.
FR57: After 5 failed attempts the login page shows the `ACCOUNT_LOCKED` (423) message with its `detail` (locked-until time) **[lockout is backend-enforced]**.
FR58: After 30 minutes idle the next action sends the user to the login page with a "Your session expired" message and returns them to where they were after signing in **[timeout is backend-enforced; the UI runs no timer]**.
FR59: Access logging is the same as FR26 **[backend-enforced; no UI]**.

### NonFunctional Requirements

NFR1: **Browsers and layout:** desktop-first, Chrome and Edge, latest two versions. Fully usable from 1024px wide; below that the shell nav collapses and tables scroll horizontally.
NFR2: **Accessibility baseline:** semantic HTML with `header`/`nav`/`main` landmarks per shell, a `<label>` on every input, keyboard-operable main flows (login, submit/edit/cancel, approve/reject, comment, create user, reset password, set/adjust balance), a CDK focus trap in dialogs, and toasts announced via CDK `LiveAnnouncer`.
NFR3: **Auth token handling:** the frontend never reads, stores or sends a token. No `Authorization` header, and nothing auth- or app-state-related in `localStorage` or `sessionStorage`. The HttpOnly/Secure/SameSite=Strict cookie is attached by the browser.
NFR4: **Same-origin transport:** all API calls use the relative base `/api` over the HTTPS proxy. No CORS, no `withCredentials`, no absolute backend URLs, no frontend secrets.
NFR5: **Session continuity:** a page refresh or deep link with a valid session keeps the user on that page (`GET /api/auth/me` at startup). An expired session (30-min idle, server-side) surfaces on the next call and sends the user to login with `returnUrl`.
NFR6: **Server authority:** the server is the validation and business-rule authority. Client validators only mirror the documented rules (required fields; end ≥ start; password ≥ 8 characters with letters and numbers; non-blank reject comment; reason required for set/adjust; manager required unless HR_ADMIN). The UI never computes working days or availability.
NFR7: **RBAC as UX only:** the UI hides features a role can't use (nav, routes, buttons), but every 403 from the API is still rendered.
NFR8: **Identity display:** people appear as username plus a readable role label (e.g. "HR Admin"). Raw ids and hard-coded leave-type ids are never shown.
NFR9: **Freshness without push:** no polling, WebSocket or SSE. After any successful write (or a 409), every screen showing the affected data reflects the new server state without a manual reload. Comments refresh on navigation, page refresh or an in-page Refresh action.
NFR10: **Error reporting:** each error is shown exactly once, either inline, as a page-level error or as a toast. Read failures are never silent.
NFR11: **Language and locale:** English only, single jurisdiction. Dates (`yyyy-MM-dd`) are shown as calendar dates with no timezone conversion; date-times in browser local time.
NFR12: **Testing:** Vitest units for the cross-cutting core (auth, interceptor, guards, data versions, links, error handling, toasts), render smoke tests for feature components, and Playwright journeys (the PRD's "2-3 Angular flows") against the real `docker compose` stack. No snapshot tests.
NFR13: **Deployment:** the whole stack, including the frontend, starts with one `docker compose up`. Configuration comes from the environment, with no hard-coded secrets and a single environment.
NFR14: **Logging:** `console` in dev only; no client telemetry.

### Additional Requirements

From the frontend Architecture Spine (AD-1 to AD-13, Stack, Structural Seed, Deferred) and the OpenAPI contract.

**Starter / greenfield setup (impacts Epic 1 Story 1):**

- **Starter template:** scaffold `frontend/` with the Angular 22 CLI, `ng new --style=tailwind`: standalone, zoneless, Vitest as pinned by `ng new`, default 2025 style-guide file names, `app-` selector prefix. This replaces the current placeholder (`frontend/Dockerfile` serving a static `index.html` on `nginx:1.27-alpine`).
- **Pinned stack:** Angular core/router/forms/common-http + CLI/`@angular/build` 22.2.x; Angular CDK 22.2.x; TypeScript 6.0.x; Node.js 24 LTS; Tailwind CSS + `@tailwindcss/postcss` 4.3.x; openapi-typescript 7.13.x; @playwright/test 1.64.x; nginx 1.30-alpine.
- **Tailwind 4 CSS-first:** `.postcssrc.json` registers `@tailwindcss/postcss`; `src/styles.css` holds `@import "tailwindcss";` plus a single `@theme` block. There is no `tailwind.config.*`.
- **Structural seed:** `core/{auth,interceptors,errors,data,routing,reference,ui}`, `layouts/{employee,manager,hr}`, `features/{login,requests,approvals,team,admin,notifications}`, `shared/{ui,models}`, `e2e/`.
- **npm scripts:** `start` (`ng serve --proxy-config`), `build`, `test`, `gen:api`, `e2e`.

**API types (AD-10):**

- `npm run gen:api` runs `openapi-typescript ../backend/src/main/resources/openapi.yaml --properties-required-by-default -o src/app/shared/models/api.ts`. The output is committed and never hand-edited.
- `package.json` carries `"overrides": { "openapi-typescript": { "typescript": "$typescript" } }`.
- `shared/models/index.ts` re-exports named aliases (`LeaveRequest`, `ApprovalDetail`, `CurrentUser`, …). Code imports only these aliases. View types may wrap generated types but never re-declare their fields.
- Any spec change regenerates `api.ts` in the same change; a dangling `$ref` blocks the change.

**Dev and deploy envelope (AD-12, inherited AD-13):**

- `proxy.conf.json` proxies `/api` to `https://localhost` (the compose proxy) with `secure: false`, `changeOrigin: true`, no path rewrite.
- `frontend/Dockerfile` is a multi-stage build (Node 24 build → `nginx:1.30-alpine` serving the `browser` output). `frontend/nginx.conf` uses `try_files $uri $uri/ /index.html` so deep links work.
- The compose proxy routes `/api/` → backend (stripping `/api`) and everything else → frontend. The frontend is reachable at `https://localhost`.

**State, layering and routing (AD-1, AD-2, AD-3):**

- Feature API state lives in route-scoped `*.service.ts` (provided in the feature route's `providers`, never `providedIn: 'root'`) as `httpResource()` + `signal()`/`computed()`. No store library and no `BehaviorSubject` state holders.
- Root singletons are limited to `AuthService`, `DataVersionService`, `LeaveTypeService` and `ToastService`.
- Import direction is `models ← shared/ui ← core ← features/layouts`. A feature never imports another feature. `shared/ui` is injection-free.
- Routes: `/login`, `/change-password` (authGuard), `/` → role home, and shells `/employee`, `/manager`, `/hr` with `canActivate: [authGuard, passwordChangeGuard, roleGuard(role)]`. A wrong-role URL is rewritten to the same child under the user's own shell if that role has the feature, otherwise to the shell home.
- Every shell mounts the same lazy feature `*.routes.ts`. Feature/role matrix: `requests` and `notifications` for all; `approvals` for MANAGER and HR_ADMIN; `team` for MANAGER only; `admin` for HR_ADMIN only.
- There is one canonical request detail path, `<shell>/requests/:requestId`. All cross-feature URLs come from `core/routing/AppLinks` (`requestDetail(id)`, `myRequests()`, `approvals()`, …).

**Auth flow (AD-4, inherited AD-7/AD-8):**

- `AuthService.currentUser = signal<CurrentUser | null>`, set only from a 200 on `GET /api/auth/me` (fired once by `provideAppInitializer` before routing) or on `POST /api/auth/login`.
- No user → `authGuard` redirects to `/login?returnUrl=<url>`. After login, go to `returnUrl` if it lies inside the user's own shell, otherwise to the shell home.
- While `mustChangePassword` is true, `passwordChangeGuard` permits only `/change-password`. A 204 there sets it to false and navigates to the shell home.
- Logout calls `POST /api/auth/logout`, then does a full page load of `/login`.

**Freshness (AD-5):**

- `core/data/DataVersionService` holds one version signal per area: `requests`, `balances`, `approvals`, `notifications`, `users`, `audit`. Every `httpResource` request function reads the versions of the areas it shows.
- Writes are service methods returning `Observable<T>`. On success **and on 409** they bump the affected areas: submit/edit/cancel → requests + balances; approve/reject → requests, balances, approvals, notifications; comment → requests + approvals; user create/reset → users; balance set/adjust → balances.
- No optimistic updates, no list patching, no polling.

**Errors (AD-6, inherited AD-11):**

- One functional `authInterceptor`:
  - 401 with `code: INVALID_CREDENTIALS` passes through.
  - Any other 401 → full page load of `/login?returnUrl=…&expired=1`, swallowed (`EMPTY`).
  - 403 `PASSWORD_CHANGE_REQUIRED` → navigate to `/change-password`, swallowed.
  - The interceptor never shows UI and never matches on URL.
- Reads: `resource.error()` is rendered once at page level via `core/ui/LoadError` (`ProblemDetail.title`/`detail`, or a generic message). Templates read `value()` only behind `hasValue()`.
- Writes: inline only for `INSUFFICIENT_BALANCE`, `INVALID_TRANSITION`, `COMMENT_REQUIRED`, `LEAVE_TYPE_CHANGE_NOT_ALLOWED` and form-level `VALIDATION_FAILED`. Any other code → `ToastService.showProblem(err)`. A custom `ErrorHandler` plus `provideBrowserGlobalErrorListeners()` toasts `detail` for unhandled subscriptions.
- `code` is the only branching key; `detail` is the displayed text. Busy flags are cleared in `finalize()`.

**Forms (AD-8):**

- Signal Forms only (`@angular/forms/signals`, `form()` + `[formField]`). No `ReactiveFormsModule`/`FormsModule`.
- Native `<input type="date">`; the `yyyy-MM-dd` string is sent unchanged, never via `Date`.
- Optional text is trimmed and sent as `null` when blank.
- The edit form reuses the submit form with leave type read-only.

**Display (AD-11):**

- Non-HR screens use the API's `*Username` fields. HR admin screens resolve `userId`/`actorId`/`managerId` through the admin feature's `GET /api/users` resource.
- Leave types come from root `LeaveTypeService` (`GET /api/leave-types`, once per session) + `LeaveTypeNamePipe`. A `leaveTypeName` field may be shown directly.
- One label pipe per enum; `RoleLabelPipe` maps `HR_ADMIN` → "HR Admin".

**Action gating and list scoping (AD-13):**

- My Requests always calls `GET /api/requests?employeeId=<currentUser.id>`.
- Edit and Cancel render only when `status === 'PENDING' && employeeId === currentUser.id`.
- Approve/Reject appear only in `approvals` on rows from `GET /api/approvals/pending`. Reject is disabled until the comment is non-blank; Approve's comment is optional.
- Comments can be posted from request detail and from the approvals context panel.
- HR password reset is a `ConfirmDialog` followed by a one-time display of the generated temporary password; it is not a form.

**Testing (AD-9):**

- Vitest units: `AuthService` (incl. `/auth/me` bootstrap), `authInterceptor` (401 by code, `PASSWORD_CHANGE_REQUIRED`), guards with `returnUrl`, `DataVersionService`, `AppLinks`, `ErrorHandler`, `ToastService`. Feature components get render smoke tests only.
- Playwright against `docker compose` at `https://localhost` (`ignoreHTTPSErrors`) with seeded users, three journeys:
  1. submit → manager approves → balance and History update (scenarios 1, 3);
  2. an employee deep-links to another employee's request and sees access denied (scenario 9);
  3. an over-balance submit shows inline `INSUFFICIENT_BALANCE` (scenario 10).

**Backend dependencies (spine Deferred; open items):**

- `GET /api/auth/me` (exempt from `PASSWORD_CHANGE_REQUIRED`) **blocks AD-4** (refresh and deep-link session restore).
- `GET /api/leave-types` **blocks AD-11** leave-type names and the submit form dropdown.
- The `*Username` display fields on `LeaveRequest`, `Comment`, `HistoryEntry` and `TeamLeaveEntry` **block AD-11** name display on non-HR screens.
- Seeded demo users (1 HR admin, 1 top-level manager, 2 team managers, 8 employees) are needed for the Playwright journeys.

**Out of scope (spine Deferred):** live updates (polling/WebSocket/SSE), i18n, dark mode, PWA/offline, SSR, a full WCAG audit, Storybook/design system, CI/CD and multi-environment builds, and access-log/audit export UI (Phase 2, FR48/FR49).

### UX Design Requirements

Derived (no UX contract exists) from the PRD UI constraints, frontend spine AD-7/AD-11/AD-13, the Consistency Conventions table and the `shared/ui` / `core/ui` component inventory.

UX-DR1: **Design tokens:** one `@theme` block in `src/styles.css` defines the colour palette (brand/primary, neutral surface and text, success, warning, danger, focus ring), the spacing and type scale, and **one colour token per `RequestStatus`** (PENDING, APPROVED, REJECTED, CANCELLED). Templates use only token-backed utility classes, with no arbitrary hex values.
UX-DR2: **Role layout shells:** `EmployeeLayout`, `ManagerLayout` and `HrLayout`. Each has a `header` (app name, signed-in username + role label, Logout), a `nav` showing only that role's features per the AD-3 matrix, and a `main` content area. Below 1024px the nav collapses behind a keyboard-operable toggle.
UX-DR3: **`StatusBadge` (shared/ui):** renders a `RequestStatus` as its label text on its status colour token. Status is never conveyed by colour alone.
UX-DR4: **`BalanceCard` (shared/ui):** shows one leave type's name with total, reserved and available days as labelled numbers. Inputs only.
UX-DR5: **`HistoryTimeline` (shared/ui):** an ordered list (`<ol>`) of History entries, each showing the entry type label, actor username, local date-time and detail. It has an empty state.
UX-DR6: **`CommentThread` (shared/ui):** comments oldest first with author username, local date-time and body. It has an empty state ("No comments yet") and emits an add-comment event and a refresh event; the hosting feature owns the API call. The add-comment textarea is labelled, and blank comments can't be posted.
UX-DR7: **Enum label pipes (shared/ui):** `RequestStatusLabelPipe`, `RoleLabelPipe` (`EMPLOYEE` → "Employee", `MANAGER` → "Manager", `HR_ADMIN` → "HR Admin"), `HistoryEntryTypeLabelPipe`, `LedgerKindLabelPipe` and `NotificationKindLabelPipe`.
UX-DR8: **`LeaveTypeNamePipe` (core/ui):** resolves a `leaveTypeId` to its name via `LeaveTypeService`, and shows a neutral placeholder while leave types load.
UX-DR9: **`ConfirmDialog` (core/ui, CDK Dialog wrapper):** title, message, labelled Confirm/Cancel buttons; focus is trapped, Esc and Cancel close it, and focus returns to the trigger on close. It is used for cancelling a request and for HR password reset. Features never use CDK overlays directly.
UX-DR10: **`ToastOutlet` + `ToastService` (core/ui, core/errors):** the outlet is mounted once in the app root. `showProblem(err)` displays `detail` (or a generic message). Toasts are announced via `LiveAnnouncer`, can be dismissed by keyboard and auto-dismiss after a fixed delay. Success confirmations (e.g. "Request submitted") use the same outlet.
UX-DR11: **`LoadError` (core/ui):** the page-level error state for a failed read. It shows `ProblemDetail.title` and `detail`, or a generic message for non-ProblemDetail errors. It covers 403 `ACCESS_DENIED` (access-denied state, scenario 9) and 404 (not found), and offers a link back to the shell home.
UX-DR12: **Loading and empty states:** every resource-backed page shows a loading indicator while loading and an explicit empty-state message when the list is empty. Examples: "You have no leave requests yet", "No requests are waiting for your decision", "No approved team leave in this range", "No notifications yet".
UX-DR13: **Form conventions:**
  - every input has a visible `<label>`;
  - client-validation messages appear next to their field, linked by `aria-describedby`;
  - server inline errors (AD-6 codes) appear in a form-level error region announced to assistive tech;
  - the submit button is disabled and shows a busy state while a write is in flight, and re-enables in `finalize()`.
UX-DR14: **Date and date-time display:** a shared formatting convention. `yyyy-MM-dd` dates show as calendar dates (no timezone shift); date ranges show as "start – end"; date-times show in browser local time.
UX-DR15: **Data tables:** semantic `<table>` with `<th scope="col">` headers for My Requests, Pending Approvals, Team leave, Users, Balances, Ledger, Notifications and Audit. Tables sit in a horizontally scrollable container below 1024px.
UX-DR16: **Keyboard and focus:** every interactive element is reachable and operable by keyboard in DOM order, with a visible focus indicator from the focus-ring token. After navigating to a new page, focus moves to the page heading (`h1`).
UX-DR17: **Login page states:**
  - inline `INVALID_CREDENTIALS` message ("Username or password is incorrect.");
  - inline `ACCOUNT_LOCKED` message showing the server's `detail` (locked-until time);
  - a "Your session expired. Please sign in again." banner when `expired=1`.
UX-DR18: **One-time temporary password reveal:** after an HR reset is confirmed, the generated password appears once in a dialog with a Copy action and a warning that it won't be shown again. Closing the dialog discards it from memory.
UX-DR19: **Approvals master-detail layout:** the Pending Approvals page shows the list of pending requests alongside a context panel for the selected request (employee, dates, type, working days, notes, `BalanceCard`, `HistoryTimeline`, `CommentThread`, decision comment field, Approve and Reject). On narrow widths the panel stacks below the list.
UX-DR20: **Request detail layout:** a summary section (status badge, type, dates, working days, notes, submitter, submitted at, approver, decision and decision comment when decided) followed by tabs or sections for **History** and **Comments**. Edit/Cancel appear only per the AD-13 gating.

### FR Coverage Map

FR1: Epic 2 - Submit form with leave-type dropdown from `GET /leave-types`
FR2: Epic 2 - Start/end date inputs, end ≥ start, server `workingDays` shown
FR3: Epic 2 - Optional notes, blank sent as `null`
FR4: Epic 2 - My Requests with status badges
FR5: Epic 2 - Status filter on My Requests, scoped to own requests
FR6: Epic 2 - Balance cards (total / reserved / available)
FR7: Epic 2 - Edit a Pending request (reused form, leave type read-only)
FR8: Epic 2 - Cancel a Pending request via ConfirmDialog; stays visible as Cancelled
FR9: Epic 2 - Submitter and submitted-at on request detail
FR10: Epic 3 - Pending Approvals page
FR11: Epic 3 - Context panel (employee, dates, type, balance, History, comments)
FR12: Epic 3 - Approve with optional comment
FR13: Epic 3 - Reject disabled until comment; inline `COMMENT_REQUIRED`
FR14: Epic 3 - Team leave view by date range (MANAGER only)
FR15: Epic 3 - Comment thread on request detail and approvals panel; refresh-to-see
FR16: Epic 3 - Decision, decided-at, approver and comment shown on detail/History
FR17: Epic 3 - Approve/Reject only on pending rows; 403 rendered
FR18: Epic 4 - Create an account form
FR19: Epic 4 - Role enum + manager dropdown from `GET /users/managers`
FR20: Epic 1 (forced change-password flow, user side); Epic 4 (HR reset with one-time reveal)
FR21: Epic 1 - Role shells, guards, role-scoped navigation
FR22: Epic 2 - My-data scoping and access-denied deep-link state (scenario 9)
FR23: Epic 3 - Team-scoped approvals/team views; 403 rendered (scenario 8)
FR24: Epic 4 - HR sees all users, balances and audit
FR25: Epic 1 - 403 rendering infrastructure (LoadError page state / toast)
FR26: Epic 1 - Traceability only; backend-enforced, no UI
FR27: Epic 4 - HR Balances page
FR28: Epic 4 - Set balance with required reason
FR29: Epic 4 - Adjust balance ± with required reason
FR30: Epic 4 - Ledger view per balance
FR31: Epic 2 (inline `INSUFFICIENT_BALANCE` on submit/edit); Epic 4 (inline on set/adjust)
FR42: Epic 4 - Audit trail page, one report per request
FR43: Epic 4 - Action, actor and time per audit History entry
FR44: Epic 4 - Read-only audit UI, no edit/delete affordance
FR45: Epic 2 - History timeline on request detail
FR50: Epic 3 - Approval visible in employee's Notifications (page from Epic 2)
FR51: Epic 3 - Rejection visible in employee's Notifications (page from Epic 2)
FR52: Epic 2 - Submission visible in approver's Notifications
FR53: Epic 2 - Notifications page
FR54: Epic 2 - Notification links to request detail via `AppLinks`
FR55: Epic 1 - SPA served same-origin through the HTTPS proxy
FR56: Epic 1 - Login, logout, change password
FR57: Epic 1 - `ACCOUNT_LOCKED` message on login
FR58: Epic 1 - Session-expired redirect with `returnUrl`
FR59: Epic 1 - Traceability only; same as FR26, no UI

## Epic List

### Epic 1: Secure Sign-In and Role-Aware App Shell

Any seeded user (Employee, Manager or HR Admin) can open `https://localhost` and:

- sign in and land in their own role shell, with navigation that fits their role;
- stay signed in across refreshes and deep links;
- be forced to replace a temporary password before doing anything else;
- see clear messages for a wrong password, a locked account or an expired session;
- sign out.

The epic also lays down the scaffold and cross-cutting core that every later epic builds on: the Angular 22 + Tailwind 4 project, generated API types, the dev proxy, the Docker/nginx build, design tokens, the interceptor, error/toast/`LoadError`/`ConfirmDialog`, `DataVersionService` and `AppLinks`.

**FRs covered:** FR20 (user side), FR21, FR25, FR26, FR55, FR56, FR57, FR58, FR59
**Backend dependency:** `GET /api/auth/me`.

### Epic 2: Employee Self-Service Leave

Any user can manage their own leave end to end:

- submit a request against their real balance, with over-balance blocked inline;
- see My Requests (filterable by status) and their balance cards;
- open a request's detail and its History timeline;
- edit or cancel a request while it is Pending;
- see an access-denied state when deep-linking to someone else's request;
- browse their Notifications, each linking to its request.

**FRs covered:** FR1, FR2, FR3, FR4, FR5, FR6, FR7, FR8, FR9, FR22, FR31 (request side), FR45, FR52, FR53, FR54
**Backend dependencies:** `GET /api/leave-types`; `*Username` display fields.

### Epic 3: Approvals, Discussion and Team Visibility

- **Approvers** (managers, and HR for top-level managers' and their own requests) work a Pending Approvals master-detail view with full context. They approve with an optional comment, or reject with a required one.
- **The owner, manager and HR** discuss a request in a comment thread, on request detail and in the approvals panel.
- **Managers** see their team's approved leave for any date range.
- **A wrong-team action** shows the 403.

**FRs covered:** FR10, FR11, FR12, FR13, FR14, FR15, FR16, FR17, FR23, FR50, FR51
**Backend dependency:** backend Epic 3 (comments, approvals, team leave).

### Epic 4: HR Administration and Audit Oversight

HR Admins can:

- create accounts with a role and a manager, and reset passwords with a one-time reveal of the new temporary password;
- view every user's balances, set or adjust them with a required reason (a change that would go below zero is blocked inline), and read each balance's ledger;
- review the read-only audit trail, one report per request with its comments and History nested.

**FRs covered:** FR18, FR19, FR20 (HR side), FR24, FR27, FR28, FR29, FR30, FR31 (HR side), FR42, FR43, FR44
**Backend dependency:** user endpoints exist today; balances (list/set/adjust/ledger) and audit need backend Epic 4.

## Epic 1: Secure Sign-In and Role-Aware App Shell

Any seeded user (Employee, Manager or HR Admin) can open `https://localhost` and:

- sign in and land in their own role shell, with navigation that fits their role;
- stay signed in across refreshes and deep links;
- be forced to replace a temporary password before doing anything else;
- see clear messages for a wrong password, a locked account or an expired session;
- sign out.

This epic also lays down the scaffold and cross-cutting core that every later epic builds on.

### Story 1.1: Frontend scaffold served through the HTTPS proxy

As a demo presenter,
I want the Angular frontend built and served at `https://localhost` by a single `docker compose up`,
So that the SPA and the API share one HTTPS origin from day one and every later screen has a working home.

**Acceptance Criteria:**

**Given** the current placeholder `frontend/` (a static `index.html` on `nginx:1.27-alpine`)
**When** the project is scaffolded with the Angular 22 CLI using `ng new --style=tailwind` (standalone, zoneless, Vitest as pinned by `ng new`, `app-` prefix)
**Then** `frontend/package.json` pins Angular + CLI/`@angular/build` 22.2.x, Angular CDK 22.2.x, TypeScript 6.0.x, Tailwind CSS + `@tailwindcss/postcss` 4.3.x, openapi-typescript 7.13.x and @playwright/test 1.64.x
**And** it defines the scripts `start` (`ng serve --proxy-config proxy.conf.json`), `build`, `test`, `gen:api` and `e2e`
**And** the placeholder `index.html` is removed
**And** the folder skeleton from the Structural Seed exists: `core/{auth,interceptors,errors,data,routing,reference,ui}`, `layouts/`, `features/`, `shared/{ui,models}`, `e2e/`.

**Given** Tailwind 4 CSS-first configuration
**When** I inspect the styling setup
**Then** `.postcssrc.json` registers `@tailwindcss/postcss`
**And** `src/styles.css` contains `@import "tailwindcss";` and a single `@theme` block (UX-DR1) defining:
  - the colour palette: primary, neutral surface/text, success, warning, danger, focus ring;
  - a spacing and type scale;
  - one colour token per `RequestStatus` (PENDING, APPROVED, REJECTED, CANCELLED);
**And** no `tailwind.config.*` file exists.

**Given** the OpenAPI contract at `backend/src/main/resources/openapi.yaml`
**When** I run `npm run gen:api`
**Then** it runs `openapi-typescript ../backend/src/main/resources/openapi.yaml --properties-required-by-default -o src/app/shared/models/api.ts` and succeeds
**And** `package.json` carries `"overrides": { "openapi-typescript": { "typescript": "$typescript" } }`, so `npm install` succeeds on TypeScript 6
**And** the generated `api.ts` is committed
**And** `shared/models/index.ts` re-exports a named alias for every schema the app uses (e.g. `export type LeaveRequest = components['schemas']['LeaveRequest']`).

**Given** the compose stack is running
**When** I run `npm start` locally
**Then** `proxy.conf.json` forwards `/api` to `https://localhost` with `secure: false`, `changeOrigin: true` and no path rewrite
**And** no environment file or source file contains a backend host, uses `withCredentials`, or configures CORS (NFR4).

**Given** `frontend/Dockerfile` and `frontend/nginx.conf`
**When** I run `docker compose up --build`
**Then** the image is built in two stages: Node 24 runs `ng build`, then `nginx:1.30-alpine` serves the `browser` output
**And** `nginx.conf` uses `try_files $uri $uri/ /index.html`, so a deep link such as `https://localhost/employee/requests/101` returns the SPA instead of a 404
**And** the compose proxy serves the SPA at `https://localhost` while `/api/` still reaches the backend (FR55).

**Given** the Playwright setup
**When** I run `npm run e2e` against the running stack
**Then** `playwright.config.ts` targets `https://localhost` with `ignoreHTTPSErrors: true`
**And** a smoke test confirms the app loads over HTTPS
**And** `npm test` runs the Vitest suite green.

### Story 1.2: Sign in, land on my role home, and sign out

As an employee, manager or HR admin,
I want to sign in with my username and password, stay signed in when I refresh or follow a link, and sign out,
So that I reach my own area of the app securely without re-entering credentials on every page load.

**Acceptance Criteria:**

**Given** the app starts
**When** `provideAppInitializer` runs before routing
**Then** `AuthService` calls `GET /api/auth/me` exactly once
**And** a 200 sets `AuthService.currentUser` (a `signal<CurrentUser | null>`) to the response, and a 401 leaves it `null`
**And** nothing auth-related is read from or written to `localStorage` or `sessionStorage`
**And** no `Authorization` header is ever sent (NFR3).

**Given** I am not signed in
**When** I open any shell URL, e.g. `/employee/requests`
**Then** `authGuard` redirects me to `/login?returnUrl=%2Femployee%2Frequests`.

**Given** the login page
**When** it renders
**Then** it has a labelled Username field, a labelled Password field and a Sign in button, built with Signal Forms (`form()` + `[formField]`)
**And** Sign in is blocked client-side while either field is empty, with a message next to each empty field (UX-DR13)
**And** the whole form can be completed and submitted with the keyboard alone.

**Given** valid credentials
**When** I submit the login form
**Then** `POST /api/auth/login` is called with `{ username, password }`
**And** on 200, `currentUser` is set from the response body
**And** I am navigated to `returnUrl` if it lies inside my own role's shell, otherwise to my shell home (`/employee/requests`, `/manager/requests` or `/hr/requests`)
**And** the Sign in button shows a busy state while the call is in flight and re-enables in `finalize()`.

**Given** a wrong username or password
**When** I submit
**Then** the 401 `INVALID_CREDENTIALS` response is shown inline on the form as "Username or password is incorrect." (UX-DR17)
**And** I am not redirected
**And** the password field is cleared.

**Given** an account locked after 5 failed attempts
**When** I submit
**Then** the 423 `ACCOUNT_LOCKED` response's `detail`, including the locked-until time, is shown inline on the form (FR57, UX-DR17).

**Given** I am signed in
**When** I navigate to `/`
**Then** I am redirected to my role's shell home, `<shell>/requests`.

**Given** I am signed in with a valid session
**When** I refresh the browser or paste a deep link inside my shell
**Then** I stay on that page without being asked to sign in again (NFR5).

**Given** each shell route `/employee`, `/manager` and `/hr` exists in this story with a minimal placeholder `requests` page
**When** I am signed in
**Then** a Sign out control is available
**And** activating it calls `POST /api/auth/logout`, then does a full page load of `/login`, so no in-memory state survives (FR56).

**Given** the Vitest suite
**When** it runs
**Then** `AuthService` is unit-tested for the `/auth/me` bootstrap (200 and 401) and for login
**And** `authGuard` is unit-tested for the redirect with `returnUrl`.

### Story 1.3: Role-aware layout shells and navigation

As a signed-in user,
I want a consistent layout that shows who I am and only the areas my role can use,
So that I can find my way around and am never offered screens I'm not allowed to open.

**Acceptance Criteria:**

**Given** the three shells `/employee`, `/manager` and `/hr`
**When** I am signed in
**Then** they render `EmployeeLayout`, `ManagerLayout` and `HrLayout` respectively
**And** each layout has a `header` (app name, my username, my role label, Sign out), a `nav` and a `main` landmark (UX-DR2, NFR2)
**And** each shell route has `canActivate: [authGuard, passwordChangeGuard, roleGuard(role)]`, with `passwordChangeGuard` a pass-through stub until Story 1.5 adds its rule.

**Given** `RoleLabelPipe` in `shared/ui`
**When** a role is displayed
**Then** `EMPLOYEE` → "Employee", `MANAGER` → "Manager" and `HR_ADMIN` → "HR Admin" (UX-DR7, NFR8).

**Given** the AD-3 feature/role matrix
**When** a nav renders
**Then** it shows only the delivered features that the role has:
  - `requests` and `notifications`: all roles;
  - `approvals`: MANAGER and HR_ADMIN;
  - `team`: MANAGER only;
  - `admin`: HR_ADMIN only;
**And** the nav is driven by one per-role config, which later feature stories add their entries to.

**Given** I am signed in as an EMPLOYEE
**When** I open `/manager/requests` or `/hr/requests`
**Then** `roleGuard` rewrites me to `/employee/requests`, the same child path under my own shell.

**Given** I am signed in as an EMPLOYEE
**When** I open a URL for a feature my role doesn't have, e.g. `/manager/team`
**Then** `roleGuard` rewrites me to my shell home, `/employee/requests` (FR21).

**Given** `core/routing/AppLinks`
**When** a feature asks for `requestDetail(id)`, `myRequests()`, `notifications()`, `approvals()`, `team()` or `admin()`
**Then** it returns the URL under the current user's shell, e.g. `/manager/requests/101` for a manager
**And** `AppLinks` is the only source of cross-feature URLs.

**Given** a viewport narrower than 1024px
**When** the shell renders
**Then** the nav collapses behind a toggle button that is labelled, keyboard-operable and exposes `aria-expanded` (NFR1)
**And** at 1024px and wider the nav is always visible.

**Given** keyboard-only use
**When** I tab through the shell
**Then** every nav link, the toggle and Sign out are reachable in DOM order, with a visible focus ring from the focus-ring token
**And** after each route change, focus moves to the page's `h1` (UX-DR16).

**Given** the Vitest suite
**When** it runs
**Then** `roleGuard` (both rewrite rules) and `AppLinks` (per-shell resolution) have unit tests
**And** each layout has a render smoke test.

### Story 1.4: Session expiry, error feedback and access-denied states

As a signed-in user,
I want to be told clearly when my session has expired, or when something I asked for failed or isn't allowed,
So that I'm never left on a silent or broken screen and can pick up where I left off.

**Acceptance Criteria:**

**Given** the functional `authInterceptor` registered via `provideHttpClient(withInterceptors([...]))`
**When** any API call returns 401 with a body `code` of `INVALID_CREDENTIALS`
**Then** the error passes through unchanged to the caller.

**Given** my session expired after 30 idle minutes, or was revoked
**When** my next API call returns a 401 without `INVALID_CREDENTIALS` (an empty body)
**Then** the interceptor does a full page load of `/login?returnUrl=<current url>&expired=1` and swallows the error (`EMPTY`)
**And** no toast or inline error is shown for that call (FR58).

**Given** I land on `/login?expired=1`
**When** the page renders
**Then** a banner reads "Your session expired. Please sign in again." (UX-DR17)
**And** signing in returns me to `returnUrl` if it lies within my shell.

**Given** the interceptor
**When** I review its code
**Then** it never matches on URL and never renders UI
**And** every response other than the 401 cases (and the 403 `PASSWORD_CHANGE_REQUIRED` branch added in Story 1.5) passes through.

**Given** `core/errors/ToastService` and `core/ui/ToastOutlet`, mounted once in the app root
**When** `ToastService.showProblem(err)` is called with a `ProblemDetail`
**Then** a toast displays its `detail`, or "Something went wrong. Please try again." for a non-ProblemDetail error
**And** the toast is announced via CDK `LiveAnnouncer`, can be dismissed by keyboard, and auto-dismisses after a fixed delay
**And** `ToastService.showSuccess(message)` uses the same outlet (UX-DR10).

**Given** a custom `GlobalErrorHandler` and `provideBrowserGlobalErrorListeners()`
**When** an HTTP error reaches a subscription with no `error` callback
**Then** the handler toasts its `detail` once
**And** the same error is never also shown inline or at page level (NFR10).

**Given** `core/ui/LoadError`
**When** a page renders it with a resource error
**Then** a 403 `ACCESS_DENIED` shows an access-denied state with the `ProblemDetail` title and detail (FR25)
**And** a 404 shows a not-found state
**And** any other error shows its title and detail, or a generic message
**And** each state offers a link back to my shell home (UX-DR11).

**Given** the Vitest suite
**When** it runs
**Then** `authInterceptor` is unit-tested for:
  - 401 `INVALID_CREDENTIALS` pass-through;
  - other-401 redirect with `expired=1` and `returnUrl`;
  - pass-through of other statuses;
**And** `ToastService` and `GlobalErrorHandler` are unit-tested
**And** `LoadError` has a render test for the 403, 404 and generic states.

### Story 1.5: Forced and voluntary password change

As a user holding a temporary password,
I want to be taken straight to a change-password screen and kept there until I set my own password,
So that a temporary password gets me in but no further, as FR20 requires.

**Acceptance Criteria:**

**Given** `/change-password`, outside the shells and guarded by `authGuard`
**When** it renders
**Then** it shows labelled Current password, New password and Confirm new password fields (Signal Forms) and a Change password button.

**Given** client validation mirroring the server rule
**When** I enter a new password that is shorter than 8 characters, lacks either letters or numbers, or doesn't match the confirmation
**Then** a message appears next to the offending field and the form cannot be submitted (NFR6, UX-DR13).

**Given** a valid form
**When** I submit
**Then** `POST /api/auth/change-password` is called with `{ currentPassword, newPassword }`
**And** the button shows a busy state until the call completes.

**Given** the call returns 204
**When** it completes
**Then** `currentUser().mustChangePassword` is set to `false`
**And** I am navigated to my shell home
**And** a success toast confirms the change.

**Given** a wrong current password
**When** the call returns 401 `INVALID_CREDENTIALS`
**Then** "Current password is incorrect." is shown inline next to the Current password field
**And** I am not signed out, because the interceptor passes `INVALID_CREDENTIALS` through.

**Given** the server rejects the new password
**When** the call returns 400 `VALIDATION_FAILED`
**Then** its `detail` is shown in the form-level error region.

**Given** my `currentUser().mustChangePassword` is `true`, after signing in or after the `/auth/me` bootstrap
**When** I try to open any shell route
**Then** `passwordChangeGuard` redirects me to `/change-password`
**And** after login I am sent to `/change-password` instead of `returnUrl`.

**Given** an API call returns 403 with `code: PASSWORD_CHANGE_REQUIRED`
**When** the interceptor handles it
**Then** it navigates to `/change-password` and swallows the error.

**Given** I am signed in with a normal password
**When** I choose "Change password" from the shell header
**Then** I can change my password voluntarily on the same screen (FR56)
**And** a 204 returns me to my shell home.

**Given** the Vitest suite
**When** it runs
**Then** `passwordChangeGuard` and the interceptor's `PASSWORD_CHANGE_REQUIRED` branch have unit tests.

## Epic 2: Employee Self-Service Leave

Any user can manage their own leave end to end:

- submit a request against their real balance, with over-balance blocked inline;
- see My Requests (filterable by status) and their balance cards;
- open a request's detail and its History timeline;
- edit or cancel a request while it is Pending;
- see an access-denied state when deep-linking to someone else's request;
- browse their Notifications, each linking to its request.

### Story 2.1: My Requests page with balances and status filter

As an employee (or a manager or HR admin acting on my own leave),
I want one page showing my balance per leave type and all my leave requests with their status,
So that I can see how much leave I have and what happened to each request without chasing anyone.

**Acceptance Criteria:**

**Given** the `requests` feature
**When** any shell mounts it
**Then** `features/requests/requests.routes.ts` is lazy-loaded at `<shell>/requests` for all three shells
**And** it provides a route-scoped `RequestsService` (in the route's `providers`, never `providedIn: 'root'`)
**And** a "My Requests" entry is added to every role's nav config.

**Given** `core/data/DataVersionService`
**When** it is created as a root singleton
**Then** it holds one version signal per area (`requests`, `balances`, `approvals`, `notifications`, `users`, `audit`) and exposes `bump(...areas)`
**And** it has Vitest unit tests.

**Given** `core/reference/LeaveTypeService`
**When** a page first needs a leave type name
**Then** a root `httpResource` loads `GET /api/leave-types` once per session
**And** `LeaveTypeNamePipe` (core/ui) resolves a `leaveTypeId` to its name, showing a neutral placeholder while loading (UX-DR8)
**And** no leave-type id is hard-coded anywhere (NFR8).

**Given** I open My Requests
**When** the page loads
**Then** `RequestsService` reads `GET /api/requests?employeeId=<currentUser.id>`, always passing my own id even when I am a manager or HR (AD-13)
**And** it reads `GET /api/balances/me`
**And** both `httpResource` request functions read their data-version signals (`requests`; `balances`).

**Given** my balances have loaded
**When** they render
**Then** one `BalanceCard` (shared/ui, inputs only) is shown per leave type, with the leave type name and labelled total, reserved and available days taken as-is from the API (FR6, UX-DR4).

**Given** my requests have loaded
**When** they render
**Then** a semantic `<table>` with `<th scope="col">` headers lists each request's leave type name, date range, working days, submitted date and status (FR4, UX-DR15)
**And** the status is a `StatusBadge` (shared/ui) showing the label text on its status colour token, never colour alone (UX-DR3)
**And** dates display as calendar dates with no timezone shift, ranges as "start – end", and date-times in local time (UX-DR14)
**And** the table scrolls horizontally inside its container below 1024px.

**Given** label pipes in `shared/ui`
**When** enums are displayed
**Then** `RequestStatusLabelPipe` maps PENDING/APPROVED/REJECTED/CANCELLED to "Pending"/"Approved"/"Rejected"/"Cancelled" (UX-DR7)
**And** `HistoryEntryTypeLabelPipe`, `LedgerKindLabelPipe` and `NotificationKindLabelPipe` exist, each mapping its generated enum union to readable labels.

**Given** a labelled Status filter (All, Pending, Approved, Rejected, Cancelled)
**When** I choose a status
**Then** the list reloads via `GET /api/requests?employeeId=<me>&status=<status>`
**And** "All" omits the `status` parameter (FR5)
**And** the selected filter is kept in the URL query (`?status=`), so a refresh preserves it.

**Given** either resource is still loading
**When** the page renders
**Then** a loading indicator is shown in place of that section (UX-DR12).

**Given** I have no requests (or none match the filter)
**When** the list renders
**Then** it shows "You have no leave requests yet" (or "No requests match this filter").

**Given** either read fails
**When** the page renders
**Then** `LoadError` shows the failure once at page level
**And** templates read `value()` only behind `hasValue()` (AD-6).

**Given** the Vitest suite
**When** it runs
**Then** the My Requests page, `BalanceCard`, `StatusBadge` and each label pipe have render smoke or unit tests.

### Story 2.2: Submit a leave request against my real balance

As an employee,
I want to submit a leave request by choosing a leave type, dates and optional notes,
So that it goes to my approver with my days reserved, and I'm stopped clearly if I don't have enough days.

**Acceptance Criteria:**

**Given** My Requests
**When** I choose "New request"
**Then** I go to `<shell>/requests/new`, which shows a Signal Forms form with labelled fields:
  - Leave type: a select populated from `LeaveTypeService`;
  - Start date and End date: native `<input type="date">`;
  - Notes: an optional textarea (FR1, FR3, UX-DR13).

**Given** client validation mirroring the server rules
**When** a required field is empty or End date is before Start date
**Then** a message appears next to the offending field (linked by `aria-describedby`) and Submit is blocked (FR2, NFR6)
**And** the UI never computes or displays a working-day count before submission.

**Given** a valid form
**When** I submit
**Then** `POST /api/requests` is called with `{ leaveTypeId, startDate, endDate, notes }`
**And** the dates are sent as the inputs' `yyyy-MM-dd` strings unchanged (never via `Date`)
**And** notes are trimmed and sent as `null` when blank (AD-8)
**And** the Submit button is disabled with a busy state until the call finalizes.

**Given** the call returns 201
**When** it completes
**Then** `DataVersionService` bumps `requests` and `balances`
**And** a success toast reads "Request submitted"
**And** I return to My Requests, where the new request shows as Pending with the server's `workingDays`, and my balance card shows the reserved days (scenario 1).

**Given** the requested working days exceed my available days
**When** the call returns 409 `INSUFFICIENT_BALANCE`
**Then** the server's `detail` (e.g. "Requested 4 working days of Vacation but only 2 days are available…") is shown in the form's error region, announced to assistive tech (FR31, scenario 10)
**And** `requests` and `balances` are still bumped (AD-5)
**And** my entered values are kept.

**Given** the server rejects the input
**When** the call returns 400 `VALIDATION_FAILED`
**Then** its `detail` is shown in the form-level error region.

**Given** any other error code
**When** the call fails
**Then** it is shown once via `ToastService.showProblem(err)`.

**Given** keyboard-only use
**When** I complete and submit the form
**Then** every field and button is reachable and operable without a mouse.

**Given** the Playwright suite
**When** journey 3 runs against the compose stack with a seeded employee
**Then** an over-balance submit shows the inline `INSUFFICIENT_BALANCE` message and no new request appears.

### Story 2.3: Request detail with History timeline

As the owner of a request, or their manager or HR,
I want to open a request and see its full details and an ordered History of everything that happened to it,
So that I know exactly where it stands, who did what, and when.

**Acceptance Criteria:**

**Given** the canonical detail route `<shell>/requests/:requestId`
**When** I open it from a My Requests row (linked via `AppLinks.requestDetail(id)`) or by deep link
**Then** the page reads `GET /api/requests/{id}` and `GET /api/requests/{id}/history`, both keyed on the `requests` data version.

**Given** the request has loaded
**When** the summary renders (UX-DR20)
**Then** it shows:
  - the `StatusBadge`, leave type name, date range, working days and notes;
  - the submitter (`employeeUsername`) and submitted-at in local time (FR9);
  - the approver (`approverUsername`);
**And** once decided, it also shows the decision time (`decidedAt`) and decision comment (FR16)
**And** no raw user or leave-type ids are displayed (NFR8).

**Given** the History section
**When** entries have loaded
**Then** `HistoryTimeline` (shared/ui, inputs only) renders them in server order as an `<ol>`
**And** each entry shows its type label, actor username, local date-time and detail (FR45, UX-DR5)
**And** an empty history shows an empty-state message.

**Given** I am an employee
**When** I deep-link to another employee's request
**Then** the API's 403 `ACCESS_DENIED` is rendered once at page level by `LoadError` as an access-denied state (FR22, FR25, scenario 9)
**And** no request data is shown.

**Given** a request id that does not exist
**When** I open its URL
**Then** `LoadError` shows the not-found state.

**Given** I just submitted a request (Story 2.2)
**When** the 201 completes
**Then** I am now taken to the new request's detail page instead of My Requests, and its History shows the SUBMITTED entry (scenario 1).

**Given** the Playwright suite
**When** journey 2 runs with two seeded employees
**Then** employee A opening employee B's request URL sees the access-denied state.

**Given** the Vitest suite
**When** it runs
**Then** `HistoryTimeline` and the detail page have render smoke tests.

### Story 2.4: Edit a pending request

As the owner of a pending request,
I want to change its dates or notes using the same form I submitted it with,
So that I can correct my plans before my manager decides, with my reservation recalculated.

**Acceptance Criteria:**

**Given** request detail
**When** `status === 'PENDING' && employeeId === currentUser.id`
**Then** an Edit action is shown, linking to `<shell>/requests/:requestId/edit`
**And** in every other case (non-pending, or a manager or HR viewing someone else's request) Edit is not rendered (AD-13).

**Given** the edit route
**When** it loads
**Then** it reuses the submit form component, pre-filled with the request's current leave type, dates and notes
**And** the leave type is displayed read-only (FR7, AD-8).

**Given** I change the dates or notes and submit
**When** `PUT /api/requests/{id}` is called with `{ leaveTypeId (unchanged), startDate, endDate, notes }`
**Then** on 200, `requests` and `balances` are bumped, a success toast reads "Request updated", and I return to the request detail
**And** the detail shows the server's recalculated `workingDays` and an EDITED entry in History
**And** My Requests and my balance cards reflect the new reservation (scenario 2).

**Given** the lengthened range exceeds what's available to this request
**When** the call returns 409 `INSUFFICIENT_BALANCE`
**Then** the `detail` is shown in the form's error region
**And** my edits are kept
**And** `requests` and `balances` are bumped.

**Given** the request was decided or cancelled after I opened the form
**When** the call returns 409 `INVALID_TRANSITION`
**Then** the `detail` (e.g. "Request 101 is APPROVED and can no longer be edited.") is shown inline
**And** the versions are bumped, so returning to detail shows the current status.

**Given** the server refuses a leave-type change
**When** the call returns 400 `LEAVE_TYPE_CHANGE_NOT_ALLOWED`
**Then** its `detail` is shown inline.

**Given** a 400 `VALIDATION_FAILED`
**When** the call fails
**Then** its `detail` is shown in the form-level error region.

**Given** any other code
**When** the call fails
**Then** it is toasted once.

**Given** I deep-link to the edit route of a request I can't edit
**When** the page loads
**Then** a non-owner sees the `LoadError` state from the API's 403
**And** an owner whose request is no longer pending sees a message that it can no longer be edited, with a link back to its detail.

### Story 2.5: Cancel a pending request

As the owner of a pending request,
I want to cancel it after confirming,
So that my reserved days are released while the request stays on record as Cancelled.

**Acceptance Criteria:**

**Given** `core/ui/ConfirmDialog`, a wrapper over CDK Dialog
**When** it opens
**Then** it shows a title, a message and labelled Confirm and Cancel buttons
**And** focus is trapped inside it
**And** Esc or Cancel closes it without action
**And** focus returns to the triggering control on close (UX-DR9)
**And** features use only this wrapper, never CDK overlays directly.

**Given** request detail
**When** `status === 'PENDING' && employeeId === currentUser.id`
**Then** a "Cancel request" action is shown
**And** otherwise it is not rendered (AD-13).

**Given** I choose "Cancel request"
**When** the `ConfirmDialog` asks "Cancel this leave request? Reserved days will be released." and I confirm
**Then** `POST /api/requests/{id}/cancel` is called
**And** the action shows a busy state until it finalizes.

**Given** the call returns 200
**When** it completes
**Then** `requests` and `balances` are bumped and a success toast reads "Request cancelled"
**And** the detail now shows status Cancelled, no Edit or Cancel actions, and a CANCELLED entry in History
**And** the request is still listed in My Requests as Cancelled, and my balance card shows the days released (FR8, scenario 2).

**Given** the request is no longer pending
**When** the call returns 409 `INVALID_TRANSITION`
**Then** the `detail` is shown inline on the detail page
**And** the versions are bumped, so the page shows the current status.

**Given** any other error, including 409 `INSUFFICIENT_BALANCE` for a missing balance row
**When** the call fails
**Then** it is shown once via the toast.

**Given** the Vitest suite
**When** it runs
**Then** `ConfirmDialog` has a render test covering confirm, cancel and Esc.

### Story 2.6: My notification history

As any user,
I want a Notifications page listing the notifications the system has sent me, each linking to its request,
So that I can see what happened to my requests, or which requests await my decision, in one place.

**Acceptance Criteria:**

**Given** the `notifications` feature
**When** any shell mounts it
**Then** `features/notifications/notifications.routes.ts` is lazy-loaded at `<shell>/notifications` for all three roles, with a route-scoped `NotificationsService`
**And** a "Notifications" entry is added to every role's nav config.

**Given** I open Notifications
**When** the page loads
**Then** it reads `GET /api/notifications`, keyed on the `notifications` data version.

**Given** notifications have loaded
**When** they render
**Then** a semantic table lists each notification's kind label (via `NotificationKindLabelPipe`), subject and sent time in local time (FR53, UX-DR15)
**And** each row links to its request via `AppLinks.requestDetail(requestId)`, resolving under my own shell (FR54).

**Given** I am an approver and a report submitted a request
**When** I open Notifications
**Then** a "Submitted" notification for that request is listed (FR52)
**And** following its link opens the request detail, which I'm allowed to view as the approver.

**Given** I have no notifications
**When** the list renders
**Then** it shows "No notifications yet" (UX-DR12).

**Given** the read fails
**When** the page renders
**Then** `LoadError` shows it once at page level.

**Given** the Vitest suite
**When** it runs
**Then** the Notifications page has a render smoke test.
