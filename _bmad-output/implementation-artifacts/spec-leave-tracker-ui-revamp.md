---
title: 'Revamp the leave tracker prototype UI'
type: 'feature'
created: '2026-10-16'
status: 'in-review'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '_bmad-output/planning-artifacts/ux-designs/ux-emp-leave-tracker-2026-10-15/ui-ux-design.md'
  - '_bmad-output/implementation-artifacts/spec-leave-tracker-app-modules.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The prototype's current UI is functional but visually dense and inconsistent across its workspaces, which weakens the calm, transparent experience defined by the UX specification.

**Approach:** Refresh the visual hierarchy and component presentation across the login, employee, manager, and HR views, using the established Leavewise teal design system and authoritative UX patterns. Keep the existing data, interactions, role scope, plain-script architecture, and direct-file compatibility intact.

## Boundaries & Constraints

**Always:** Follow the UX spec's exact color and spacing tokens, system font stack, dark role-scoped sidebar, card-based request queues, tabbed request details, and attributed history timeline. Preserve semantic/accessibility behavior, responsive desktop/tablet/mobile layouts, existing IDs/data attributes, role-scoped content, and all current interactions.

**Never:** Add dependencies, frameworks, build tooling, backend/persistence, new product behavior, or changes to seeded values and business rules. Do not replace the specified palette/status colors, remove working controls, or use decorative charts/gamification.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Employee workspace | Employee persona on dashboard, requests, balances, form, and detail routes | Refreshed hierarchy makes balances, request status, next action, and primary task easy to scan; all existing actions work | Preserve empty, validation, and status states with their current messages and semantics |
| Manager workspace | Manager persona on approvals, team requests, balances, and request details | Approval cards emphasize decision context and one primary action; team scope and inline rejection/comment flows remain intact | Empty approvals and conflicts remain understandable without relying on color alone |
| HR workspace | HR persona on overview, people, balances, and audit | Metrics, people/balance tables, recent activity, and adjustment form use a consistent, readable admin presentation | Dense tables remain horizontally usable on small screens; search and adjustment actions remain available |

</frozen-after-approval>

## Code Map

- `_bmad-output/planning-artifacts/ux-designs/ux-emp-leave-tracker-2026-10-15/ui-ux-design.md` — source of truth for tone, tokens, layouts, status treatments, components, responsive behavior, and accessibility.
- `leave-tracker-prototype-v1/css/styles.css` — existing tokens and component styles; primary visual refresh surface. Retain token values and breakpoints while improving consistent hierarchy, spacing, states, and responsive polish.
- `leave-tracker-prototype-v1/js/rendering.js` — route templates and screen composition; adjust markup only where needed for clearer hierarchy, preserving selectors and data hooks consumed by events.
- `leave-tracker-prototype-v1/index.html` — static login and app shell; change only if the refreshed shell needs a small structural adjustment, preserving landmarks and module script order.
- `leave-tracker-prototype-v1/js/events.js` — delegated interactions and keyboard behaviors; do not change unless markup requires a compatible event-hook adjustment.
- `leave-tracker-prototype-v1/js/business-rules.js`, `js/state-api.js`, and `js/data.js` — existing data and role/business boundaries; reuse without product behavior or seed changes.

The requested UI-alignment spec from the IDE context is not present in the workspace. The UX design document and prior prototype implementation spec are available and define the visual constraints used here.

## Tasks & Acceptance

**Execution:**
- [x] `leave-tracker-prototype-v1/css/styles.css` — organize and refine the visual system across shell, navigation, typography, cards, forms, tables, detail tabs, timeline, and responsive states while preserving exact token values.
- [x] `leave-tracker-prototype-v1/js/rendering.js` — refine screen composition and semantic grouping where current markup limits hierarchy; preserve all existing IDs, `data-*` hooks, route behavior, and escaping.
- [x] `leave-tracker-prototype-v1/index.html` — update only shell markup required by the visual refresh, keeping landmarks, accessibility hooks, and classic script load order.
- [x] `leave-tracker-prototype-v1/README.md` — summarize the visual refresh and retain current run/setup documentation.

**Acceptance Criteria:**
- Given desktop, tablet, and mobile viewport widths, when the app is opened, then the refreshed layout follows the UX specification's full sidebar, condensed rail, and mobile drawer patterns without clipping primary actions.
- Given each of the three personas, when their primary screens are viewed, then requests, balances, metrics, tables, and detail history have a clear and consistent hierarchy while respecting role scope.
- Given keyboard or assistive-technology use, when navigating refreshed controls, then labels, focus indicators, status icon-plus-text, live regions, ordered timeline semantics, and tab behavior remain available.
- Given any existing request, balance, account, search, filter, modal, or navigation action, when used after the visual refresh, then its prior behavior remains unchanged.
- Given direct `index.html` opening or Python HTTP serving, when the prototype loads, then it initializes using the existing classic scripts with no dependency or build step.

## Implementation Notes

Implemented per the UX spec's design system using the BMAD build workflow (step-02 → step-03 path, freeform route). The spec's `<frozen-after-approval>` intent was preserved throughout.

**CSS (`styles.css`):** Reorganized into named sections with consistent `:root` token structure — added `--primary-muted`, `--radius-xl`, `--shadow-xs`, full sidebar palette variables (`--sidebar-*`), and a complete spacing scale. Typography scale now includes `--text-md: 15px` and `--text-xs: 11px`. All pre-existing token values (`--primary: #0f766e`, all four status color pairs, breakpoints at 768px/1023px/767px) preserved verbatim. Added `transition` to `.nav-link`, `.button`, `.request-card`, and form inputs for calm state changes (suppressed by `prefers-reduced-motion`). Progress bar upgraded to `role="progressbar"` with `aria-valuenow/min/max`.

**JS (`rendering.js`):** Three targeted markup improvements: (1) `header()` correctly labels HR role as "HR ADMIN WORKSPACE" to match sidebar label; (2) `balanceCards()` uses a type→icon lookup object and adds `aria-label` on each article and `role="progressbar"` on the progress bar; (3) `hrOverview()` adds `aria-label` on each metric article and uses `&#215;`/`&#8212;` instead of `x`/`>` for rejected/cancelled status icons, consistent with `statusBadge()`. All IDs, `data-*` attributes, route hashes, and event selectors unchanged.

**HTML (`index.html`):** Skip link target corrected from non-existent `#login-main` to `#login-form` (a valid focusable element). Prototype small-print uses an em dash for correct typography.

**Verification:** `node --check` passed for all six JS files. Smoke tests (2/2) pass. No whitespace errors. Direct-file opening and Python HTTP server load unmodified.

## Spec Change Log

## Review Triage Log

## Design Notes

Use calm, restrained surfaces and typography to create hierarchy rather than introducing new colors. Keep teal for primary actions and navigation emphasis; preserve locked status hues only for lifecycle meaning. Favor readable table density for HR data and context-rich cards for employee/manager requests. Reuse current responsive breakpoints and avoid changing workflow semantics.

## Verification

**Commands:**
- `node --check` for all JavaScript files — expected: all scripts parse successfully.
- `git diff --check` — expected: no whitespace errors.

**Manual checks:**
- Open the prototype directly and through `python3 -m http.server`; inspect at desktop, tablet, and mobile widths.
- Click through employee, manager, and HR primary screens; verify request detail tabs, navigation, filters, and current forms/actions still work.
