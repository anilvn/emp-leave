---
title: 'Employee Leave Tracker clickable prototype'
type: 'feature'
created: '2026-10-16'
status: 'in-review'
route: 'dispatch'
baseline_commit: 'NO_VCS'
review_loop_iteration: 0
context:
  - '_bmad-output/planning-artifacts/prds/prd-emp-leave-tracker-2026-10-15/prd.md'
  - '_bmad-output/planning-artifacts/ux-designs/ux-emp-leave-tracker-2026-10-15/ui-ux-design.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Employee Leave Tracking System needs a reviewable front-end prototype that demonstrates its role-scoped leave workflows and transparency model without a backend.

**Approach:** Build a standalone plain HTML/CSS/JS prototype in `leave-tracker-prototype-v1/`, using only the provided PRD and UX planning docs as product sources. Seed in-memory data, simulate mutations and notifications, and provide role-specific navigation and interactions.

## Boundaries & Constraints

**Always:** Direct-file and Python HTTP-server compatible; no frameworks, dependencies, build step, backend, or persistence. Refresh resets seeded data. Enforce role scope on every list and detail. Use the specified status palette, tokens, responsive shell, accessible labels/status/timeline, and direction 3 cards/detail tabs. Keep balance adjustments in an append-only ledger and cancellations as state changes.

**Never:** Real authentication, persistence, real email, Phase-2 reporting/export, calendar, accrual, integrations, or other backend claims.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Submit request | Valid leave type and future weekday range within balance | New pending request, attributed event and manager notification toast | Inline validation; prevent invalid range or insufficient balance |
| Resolve request | Manager acts on direct report pending request | Approve deducts balance; reject requires inline reason; append history and notification | Reject blank reason; deny out-of-scope/non-pending mutation |
| Open deep link | Hash names request outside user's scope or invalid ID | Show authorized detail only; otherwise access denied/not found | No request data exposed |
| Adjust balance | HR selects user/type, amount, reason | Update balance and append immutable ledger row | Require reason; prevent negative result |

</frozen-after-approval>

## Code Map

- `_bmad-output/planning-artifacts/prds/prd-emp-leave-tracker-2026-10-15/prd.md` — functional requirements FR1–FR61; core MVP rules include employee request lifecycle, manager decisions, RBAC, HR balance actions, audit and notifications. FR32–41 and FR48–49 identify deferred Phase-2 scope.
- `_bmad-output/planning-artifacts/ux-designs/ux-emp-leave-tracker-2026-10-15/ui-ux-design.md` — authoritative visual tokens, direction 3, role navigation, card, badge, timeline, form, responsive and accessibility patterns.
- `leave-tracker-prototype-v1/` — new standalone artifact; no existing implementation to reuse or modify.

## Tasks & Acceptance

**Execution:**
- [x] `leave-tracker-prototype-v1/index.html` — create semantic app shell, persona login, navigation, main content and accessible live regions.
- [x] `leave-tracker-prototype-v1/css/styles.css` — implement specified custom properties, status treatments, card/timeline patterns and breakpoints.
- [x] `leave-tracker-prototype-v1/js/data.js` — seed eight role-linked users, per-type balances, status-diverse requests, REQ-1042 event chain and balance ledger.
- [x] `leave-tracker-prototype-v1/js/app.js` — implement in-memory mock API, RBAC, employee/manager/HR flows, history/comments, hash routing and queued-notification toasts.
- [x] `leave-tracker-prototype-v1/README.md` — document direct and server run paths, personas, feature/FR mapping, and explicit scope exclusions.

**Acceptance Criteria:**
- Given each seeded persona, when selected at login, then navigation and visible records match employee, direct-report manager, or HR scope.
- Given an in-scope request, when opened by hash or card, then Details, History and Comments are navigable; out-of-scope detail exposes no request data.
- Given valid employee input, when submitted or edited while pending, then working-day/balance preview is accurate and history records the action and date diff; cancel preserves the record.
- Given a manager queue, when approving or rejecting, then oldest pending appears first, conflicts are visible, approval deducts balance, and rejection requires an inline comment.
- Given HR actions, when creating an account or adjusting balance, then people/balance views update and adjustments require reasons and append to the audit ledger.
- Given the prototype files, when `node --check` is run on both JS files and the site is opened directly or served with Python, then it loads without dependencies; all three personas can complete their primary flows.
- Given any status, validation error, toast, or timeline, when viewed or announced, then text/icon accompanies color, inputs are labelled, focus is visible, live messages are announced, and timeline is an ordered list.

## Implementation Notes

- Created the five-file standalone prototype. The eight seeded people and manager reporting lines match the approved request; REQ-1042 includes submitted, commented, and modified date-diff events.
- Added the tablet icon rail and mobile navigation drawer with `aria-expanded` state to meet the requested breakpoints.
- `node --check` passes for both JS files. Seed assertions pass for user count, manager scopes, and REQ-1042 history. Local Python HTTP server returned HTTP 200 for `/`, `/js/app.js`, and `/js/data.js`. Interactive browser verification could not run because no browser executable/tool is available in this environment.

## Verification

**Commands:**
- `node --check leave-tracker-prototype-v1/js/data.js` — expected: syntax valid.
- `node --check leave-tracker-prototype-v1/js/app.js` — expected: syntax valid.
- Open `leave-tracker-prototype-v1/index.html` and serve the folder with `python3 -m http.server` — expected: app renders and persona flows operate without console errors.

**Manual checks (if no CLI):**
- Click through Sarah employee submit/edit/cancel, Alex manager approve/reject/comment, and Jamie HR people/balance/audit views; verify Lee sees only Tom's requests.
