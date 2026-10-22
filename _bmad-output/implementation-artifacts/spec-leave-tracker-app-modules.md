---
title: 'Document and modularize the leave tracker prototype runtime'
type: 'refactor'
created: '2026-10-16'
status: 'in-review'
route: 'dispatch'
baseline_commit: '85a8c20f0cba75b991c97c23e563cd3da96d6218'
review_loop_iteration: 0
context:
  - 'leave-tracker-prototype-v1/README.md'
  - 'leave-tracker-prototype-v1/js/data.js'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The prototype's `app.js` mixes mutable state, business rules, HTML rendering, and DOM event handling in one file. This makes behavior difficult to understand, maintain, and extend safely.

**Approach:** Separate those responsibilities into documented plain-script modules, with `app.js` retained as the bootstrap entry point. Document public functions and the seed/runtime data models so future changes can find the correct boundary.

## Boundaries & Constraints

**Always:** Keep the application dependency-free and compatible with direct `index.html` opening and `python3 -m http.server`. Load classic scripts in dependency order. Preserve existing behaviors, role checks, accessibility, current seed configuration, and current uncommitted `app.js`/`data.js` changes. Keep all mutable demo state in memory and the seed dataset separate.

**Never:** Add a framework, package manager, build step, backend, persistence, ES module imports, or unrelated UX/data changes. Do not modify `data.js` seed values as part of the refactor.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|----------------------------|----------------|
| Open directly | Browser opens `index.html` from disk | Ordered classic scripts initialize the same login and workspace as before | No module fetch/CORS dependency; log a clear initialization error if a required module is missing |
| Role-scoped route | Persona opens a request hash or workspace route | Renderer receives authorized view data; unauthorized or missing requests reveal no request details | Access-denied/not-found view remains intact |
| Mutating action | Valid or invalid form/action | Event handler delegates to business rule; state API performs controlled mutation; renderer refreshes | Existing validation, denial, and user feedback remain intact |

</frozen-after-approval>

## Code Map

- `leave-tracker-prototype-v1/index.html` — owns the DOM shell and classic script order; add the new module tags before the `app.js` bootstrap.
- `leave-tracker-prototype-v1/js/data.js` — owns immutable-by-convention seed configuration, people, balances, requests, and ledger. Preserve current contents and document its published model.
- `leave-tracker-prototype-v1/js/app.js` — currently owns cloned state, helper/domain functions, all view templates, render routing, mutations, and delegated DOM events. Reduce to documented initialization/wiring.
- `leave-tracker-prototype-v1/README.md` — current run instructions and feature map; extend with module responsibilities and the data-model reference.
- Reuse the existing selectors, route hashes, form IDs, data-action attributes, and accessible markup contracts to avoid behavior regressions.

## Tasks & Acceptance

**Execution:**
- [x] `leave-tracker-prototype-v1/js/state-api.js` — own documented in-memory state and explicit read/mutation APIs, cloned from `LeaveSeed`.
- [x] `leave-tracker-prototype-v1/js/business-rules.js` — own documented date, scope, validation, authorization, request, balance, account, and activity rules; avoid DOM access.
- [x] `leave-tracker-prototype-v1/js/rendering.js` — own documented templates and route rendering; use injected module APIs and preserve access checks.
- [x] `leave-tracker-prototype-v1/js/events.js` — own documented DOM listeners, forms, modal/focus/mobile behavior, delegating mutations to business rules.
- [x] `leave-tracker-prototype-v1/js/app.js` and `index.html` — wire the modules in classic-script order and initialize without module loading or build tooling.
- [x] `leave-tracker-prototype-v1/README.md` — document module boundaries, callable APIs, and seed/runtime data models.

**Acceptance Criteria:**
- Given any supported persona and action, when the refactored prototype is used, then the current role-scoped workflows and visible outcomes are preserved.
- Given direct-file launch or a local Python HTTP server, when the page loads, then login and navigation initialize without module/import errors.
- Given the source modules, when a maintainer reads their JSDoc and README, then each module's responsibilities, public functions, and data shapes are clear.
- Given valid and invalid actions, when delegated through the event layer, then only business-rule/state APIs mutate data and existing validation/access checks remain effective.

## Implementation Notes

Planning investigation confirms classic scripts are required for local-file compatibility. Implemented state/API, business-rule, rendering, and event modules under `window.LeaveApp`; `app.js` now composes the modules and starts event registration. The original in-memory seed configuration and employment status values were preserved, with data-model documentation added to `data.js` and `README.md`.

Verification completed: `node --check` passed for all JavaScript files; `git diff --check` passed; Node smoke checks covered role scope, request validation/submission, approval and balance deduction, no duplicate approval, reasoned HR adjustment/lower-bound rejection, and seed isolation. A local `python -m http.server` served the page and module assets with HTTP 200; a mocked-DOM check was also run for startup, form handling, and role-scoped request details. A real browser click-through could not be run in this environment.

Additional documentation detail requested during implementation: use JSDoc model/interface typedefs for each exported API call's input, output, and mutation result; keep the runtime plain JavaScript with no TypeScript or build dependency.

Implemented JSDoc data models and API contracts in `data.js`, `state-api.js`, `business-rules.js`, `rendering.js`, and `events.js`. The contracts describe public method arguments, results, mutation guards, and listener disposal where applicable; README explains that these are editor-facing contracts only. The two checked-in Node smoke tests pass after this addition.

## Design Notes

Split by responsibility: state/API owns the in-memory database and state transitions exposed to other modules; business rules own domain decisions and must not query or mutate the DOM; rendering owns markup and route-to-view selection; events translate browser input into rule calls and render requests. `app.js` only composes and starts these pieces. Seed records and mutable runtime copies must be documented separately.

## Review Triage Log

| Finding | Verdict and evidence | Route |
|---|---|---|
| Blind: login leaves keyboard focus on hidden submit button | **medium** — the login handler hides the focused form without moving focus; this behavior was present before the modular refactor. | defer |
| Blind: reversed date error can be replaced by weekday error | **low** — `validateRequest()` can overwrite `errors.end` when the range is reversed; the same validation sequence existed before this refactor. | defer |
| Blind: person ID timestamp suffix can collide | **medium** — `createPerson()` uses a name slug and short timestamp suffix, and current state insertion has no uniqueness guard. | patch (grouped with duplicate-person insertion findings) |
| Blind: new employee balances start at zero | **medium** — `createPerson()` initializes zero balances, so an employee needs an HR adjustment before requesting leave; this behavior predates the refactor. | defer |
| Blind: manager selector remains visible for non-employee roles | **low** — the optional selector is visible after role changes; hiding it is a low-impact UI branch in an infrequent account-creation flow. | rejected (low impact; fix adds UI branching) |
| Blind: state person insertion does not guard duplicate IDs | **medium** — the new state API appends duplicate IDs, which makes person lookup ambiguous and may overwrite a balance record. | patch (grouped with ID-collision findings) |
| Blind: request resolution accepts arbitrary status values | **low** — the public state mutation accepts values beyond `approved`/`rejected`, allowing a caller to bypass lifecycle rules. | patch |
| Blind: balance mutation omits finite/half-day checks | **low** — the public state API can store `NaN` or unsupported fractions when called outside `Rules.adjust()`. | patch |
| Blind: balance table uses approved styling for all employment statuses | **false** — all seeded and newly created people have `Active`; no application action sets another value, so the described outcome is not reachable now. | rejected |
| Blind: active employee label counts non-active employees | **false** — all current employee records are Active and account creation defaults to Active, so the label and count agree for reachable data. | rejected |
| Edge: duplicate person IDs can alias accounts | **medium** — this is the same timestamp-collision and unguarded-insert root cause as the blind findings above. | patch (grouped with ID-collision findings) |
| Verification gap: no repeatable account-creation assertion | **medium** — no checked-in test asserts manager linkage and initial balances for HR-created employees. | patch |

| Patch evidence: person ID collisions and duplicate insertion | **fixed** â€” `nextPersonId()` checks a monotonic per-session sequence against current people; `addPerson()` rejects missing or duplicate IDs. Smoke test creates same-name users and checks unique IDs and duplicate rejection. | complete |
| Patch evidence: request status and balance guards | **fixed** â€” state resolution only permits approved/rejected; balance mutation enforces finite nonzero half-day amounts and prevents totals below used leave. Smoke test verifies rejected mutations leave records unchanged. | complete |
| Patch evidence: account-creation coverage | **fixed** â€” checked-in smoke test asserts generated IDs, manager association, and zeroed per-type balances. | complete |

## Verification

**Commands:**
- `node --check` for each JavaScript file — expected: syntax check succeeds.
- Manual direct-file and `python3 -m http.server` launch — expected: sign-in and core role-scoped flows remain usable.

**Manual checks:** Open employee, manager, and HR personas; inspect request deep-link access, representative mutations, tabs, and responsive navigation.
