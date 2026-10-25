# Rubric Review — company-leave-frontend ARCHITECTURE-SPINE.md

- Reviewed: 2026-10-25
- Spine: `../ARCHITECTURE-SPINE.md` (status: draft)
- Companion: `../../architecture-company-leave-2026-10-22/ARCHITECTURE-SPINE.md`
- Evidence also read: `backend/src/main/resources/openapi.yaml`, `proxy/nginx.conf`, `docker-compose.yml`, `frontend/Dockerfile`, `backend/.../security/JwtAuthenticationFilter.java` (grep), PRD acceptance scenarios, `.memlog.md`

## Verdict

The spine is tight and mostly well-formed, and it fixes most of the obvious divergence points (state, HTTP style, errors, forms, styling, types). It is **not ready to finalize**. One decision (AD-4, refresh logs the user out) contradicts the PRD's acceptance scenarios and the spine's own E2E journey. Its layering diagram contradicts its own seed. The dev envelope as written does not work against the real proxy and backend paths. And three real cross-feature divergence points are left open: cross-feature links and request-detail ownership, feature-service lifetime, and id→name resolution where the API gives no `*Username`.

## Checklist results

| # | Criterion | Result |
| --- | --- | --- |
| 1 | Fixes real divergence points, misses none | **Partial.** Misses: cross-feature navigation and shared request-detail data ownership (H1), feature-service provisioning scope and freshness (H2), ids without a username sibling (M1), per-role `GET /requests` semantics (M2), date-only value handling on input (M4), zoneless vs zone.js (M5), loading/empty/success-feedback states (L1). |
| 2 | Every Rule enforceable and prevents its divergence | **Partial.** AD-2 layering cannot be followed as seeded (H3). AD-4 makes scenarios 5 and 9 unreachable (C1). AD-5's reload rule covers only resources inside the same feature (H2). AD-12 local dev is unspecified in a way that breaks (H4). |
| 3 | Nothing Deferred lets units diverge | **Fail on one item.** "Session restore across refresh / return-URL" is not safely deferrable (C1). The other deferrals are safe. |
| 4 | Tech plausible/current | **Pass with nits.** Angular 22.2 / TS 6 / Tailwind 4.3 / Vitest default / Playwright 1.64 are plausible for Oct 2026. Nits: `nginx:1.27-alpine` is an out-of-support line, and "Node 22 LTS or newer" is too loose for a Docker build image (L3). Depth is left to the version reviewer. |
| 5 | Inherited ADs not weakened/contradicted | **Pass, with a naming drift to record (M3).** AD-7, AD-8, AD-10 and AD-13 are honoured. AD-8 is verified: `JwtAuthenticationFilter` re-issues the cookie on every request, so the "no frontend refresh/idle timer" stance is correct. The backend spine names `RBAC_DENIED` / `BALANCE_INSUFFICIENT`. The openapi contract (and this spine) use `ACCESS_DENIED` / `INSUFFICIENT_BALANCE`. |
| 6 | Every owned dimension decided/deferred/open, incl. ops envelope | **Partial.** Dev proxy target and path rewrite (H4), E2E TLS and stack prerequisites, the build-image Node pin, and the `gen:api`-vs-Docker-context relationship are all unstated (L2, L3). |
| 7 | Terseness / diagrams / mermaid | **Pass with nits.** All four mermaid blocks parse. The AD-3 role table and AD-3 graph say the same thing (L4). Rules are mostly decision-only. |

---

## Findings

### CRITICAL

**C1. AD-4 (refresh = logged out, no return-URL) contradicts the PRD and the spine's own AD-9 journey.**
- PRD FR15 and acceptance scenario 5 say comments "appear after **page refresh**". Under AD-4, a browser refresh drops `currentUser`, `authGuard` sends the user to `/login`, and after re-login they land on shell home, not on the request they were reading. The demo scenario becomes "log in again and navigate back".
- PRD scenario 9 is "Employee opens another employee's request **by URL**". Typing or pasting a URL is a full page load, so the user goes to `/login` and then to shell home. The access-denied state is never shown. AD-9 Playwright journey (2) ("opens another employee's request URL → access-denied state") therefore cannot pass as specified.
- The stated reason ("Prevents adding endpoints outside the OpenAPI contract") is already undercut: this same run extended `openapi.yaml` with `GET /api/leave-types` and the `*Username` fields.
- **Fix:** add `GET /api/auth/me` → `CurrentUser` (one backend method; same follow-up track as the existing Deferred backend item). Bootstrap `AuthService.currentUser` with `provideAppInitializer` (401 → `null`). Also make `authGuard` redirect to `/login?returnUrl=<path>`, and have login navigate to `returnUrl` when it resolves inside the user's own shell. Then remove the Deferred bullet, or move it to Open Questions if the backend change is refused. If the endpoint is refused, the only alternative is a `sessionStorage` `CurrentUser` *hint* (non-secret, validated by the first API call). That conflicts with AD-4's "nothing is persisted", so it needs an explicit decision.

### HIGH

**H1. Cross-feature links and request-detail ownership are not fixed.**
- Several features show or link to one request: `notifications` (a notification is about a request), `approvals` (detail panel with History + comments), `team` (entry → detail), `admin` (audit → request), and `requests`. AD-2 forbids feature→feature imports. AD-3 allows only relative in-feature links. `shared/` holds no injectable state.
- So two builders will diverge on (a) the URL a notification or team entry links to: `../requests/:id`, a hard-coded `/manager/requests/:id`, or an in-feature duplicate detail route; and (b) who calls `GET /requests/{id}`, `/history`, `/comments` and `POST /comments`. As written, each of four features writes its own copy.
- **Fix:** add a Rule to AD-2/AD-3: "The canonical request detail is `requests/:requestId` inside the current shell. Other features link to it only through `core/navigation` `requestDetailLink(id)`, which resolves the shell from `currentUser.role`. Request detail, history and comments data are owned by one `core/requests/RequestDetailService` (root, `httpResource` keyed by an id signal). `HistoryTimeline` / `CommentThread` take inputs and emit outputs only." Name the route param (`requestId`) to match the openapi `RequestId` parameter.

**H2. Feature-service lifetime and cross-feature freshness are undecided, and state leaks across users.**
- AD-1 and AD-5 say feature state is `httpResource` refs in `<feature>.service.ts`, but not where those services are provided. `providedIn: 'root'` and lazy-route `providers` both outlive navigation, so their resources do not re-fetch on re-entry. A component-level provider does re-fetch. Builders will choose differently.
- AD-5's reload rule only reaches resources the mutating service can see. An approve in `approvals` cannot `.reload()` `team`'s approved-leave list, the manager's `requests` list (which includes reports' requests) or `notifications`. An HR balance adjust in `admin` cannot reload `requests`' my-balances.
- Logout or a 401 clears only `currentUser`. Root- or route-scoped resources keep the previous user's data. The demo switches users in one tab all the time (employee → manager → HR), so the next user can briefly see the previous user's lists.
- **Fix:** add to AD-1/AD-5: "Feature services are provided on the feature's root route component (`providers: [...]`), never `providedIn: 'root'`. Feature resources are therefore created on route entry and discarded on exit, so cross-feature freshness comes from navigation. Logout and session-expired do a hard navigation (`window.location.assign('/login')`) so no in-memory state survives a user switch." The second half is independent of the first and is the cheapest guarantee.

**H3. The AD-2 layering diagram contradicts the structural seed (dependency cycle).**
- Diagram and paradigm: `core → shared`, and nothing from `shared` to `core`. But the seed and other ADs put `shared/pipes/LeaveTypeNamePipe` on top of `core/reference/LeaveTypeService` (AD-11), and `shared/components/Toast` renders `core/errors/ToastService` state (AD-6/AD-7). Those are `shared → core` edges. Meanwhile `core` imports `shared/models` (AD-10), which makes a cycle.
- **Fix:** move generated types to a leaf, e.g. `core/api-types/` (or top-level `src/app/api/`). Redraw as `features, layouts → shared → core → api-types`, with `features, layouts → core`. Rule: "shared may inject core root services but declares no providers or state of its own; core never imports from shared." Alternatively, move `LeaveTypeNamePipe` and the toast host into `core/`.

**H4. The AD-12 local-dev rule breaks against the actual proxy and backend paths.**
- `proxy/nginx.conf` maps `location /api/ { proxy_pass http://backend:8080/; ... X-Forwarded-Prefix /api }`. It strips `/api`. Controllers are mapped at `/auth`, `/requests`, and so on. `PasswordChangeRequiredFilter` and `AccessLogFilter` key on `X-Forwarded-Prefix: /api`. Inherited AD-13 also makes the proxy the only exposed service, so the compose backend is not reachable on `localhost:8080`.
- The natural `proxy.conf.json` (`{"/api": {"target": "http://localhost:8080"}}`) therefore 404s, or, with a bare rewrite, skips the prefix-dependent filter logic.
- **Fix:** fix the dev target in AD-12: "`proxy.conf.json` forwards `/api` to `https://localhost` (the compose reverse proxy) with `secure: false`, `changeOrigin: true` and no path rewrite. Developers run `docker compose up` for the API and `ng serve` for the SPA." Add one line noting that the backend's `Secure` cookie is accepted on `http://localhost:4200` by Chromium and Firefox. Alternatively use `ng serve --ssl`.

### MEDIUM

**M1. AD-11 "names, never raw ids" has no rule for ids that lack a `*Username` sibling.**
`Balance.userId`, `BalanceLedgerEntry.userId` / `actorId` (PRD scenario 12 requires "actor" on the ledger), and `User.managerId` have no display join. Admin builders will diverge: show `#5`, join against `GET /users`, or ask for API changes. **Fix:** either extend `openapi.yaml` (`userUsername`/`actorUsername` on ledger/balance, `managerUsername` on `User`) inside the existing Deferred backend follow-up, or add a Rule: "HR-only screens resolve user ids through `core/reference/UserDirectoryService` (`GET /api/users`, loaded once per HR session)."

**M2. `GET /requests` returns different data per role, which undercuts the "shell-agnostic" `requests` feature.**
Without `employeeId`, a manager gets their own requests plus their direct reports' requests (openapi `/requests` GET description). The "my requests" screen will show reports' requests for managers unless the builder adds a filter. **Fix:** "`requests` list always sends `employeeId = currentUser.id`; other people's requests are reached only through `approvals`, `team`, `admin`."

**M3. Error-code naming drift from the parent spine, and an overstated "only branching key".**
Backend spine AD-10/AD-11 name `RBAC_DENIED` and `BALANCE_INSUFFICIENT`. The openapi enum, which this spine follows, has `ACCESS_DENIED` and `INSUFFICIENT_BALANCE`. Separately, the Inherited row "`code` is the only branching key" contradicts AD-4/AD-6, which branch on **status** 401 and 423. **Fix:** in the Inherited Invariants table, state "codes are the generated `ProblemDetail['code']` union from `openapi.yaml`; the backend spine's example names are superseded (raise a one-line correction there)". Reword to "status 401 drives session handling; `code` drives everything else."

**M4. Date-only values: AD-11 governs display only.**
A builder converting `<input type="date">` values through `new Date(...)` / `toISOString()` produces off-by-one dates in negative-UTC zones. **Fix:** add to AD-11 or Conventions: "`startDate`/`endDate` stay `yyyy-MM-dd` strings end-to-end (input → model → request body). JS `Date` is used only for date-time instants."

**M5. Zone vs zoneless is not decided.**
The memlog says "zoneless optional". The spine is silent. This changes how code may be written (reliance on zone-triggered change detection, third-party libs that assume zone.js). **Fix:** one line in the Paradigm/AD-1: "Zoneless (`provideZonelessChangeDetection()`, the v22 default); zone.js is not installed."

**M6. Gaps at the edges of AD-6.**
(a) `LoadError` shows ProblemDetail `title`/`detail`, but a status-0 network failure or a proxy 502 HTML body has neither. Add "generic message for non-ProblemDetail", mirroring the ErrorHandler rule. (b) AD-3 puts `/change-password` outside the shells but does not say it carries `authGuard`. State that, and that voluntary change (FR56) returns to shell home.

### LOW

- **L1. UX state conventions.** Add one Conventions row: loading uses `resource.isLoading()` and a shared `Spinner`; empty lists use a shared `EmptyState`; mutation success uses a toast (or stays inline, pick one); destructive actions (cancel, reject) go through `ConfirmDialog`.
- **L2. E2E envelope.** AD-9 should state that Playwright needs `ignoreHTTPSErrors: true` (self-signed proxy cert) and that the suite needs `docker compose up` with seed migrations applied. State that `gen:api` is never run in the Docker build (its path `../backend/...` is outside the `./frontend` build context). It relies on the committed `api.ts`.
- **L3. Build image pins.** Replace "Node 22 LTS or newer" with one major for the `Dockerfile` build stage and local `.nvmrc` (e.g. Node 24). `nginx:1.27-alpine` is an unsupported line; let the version reviewer choose the replacement.
- **L4. Terseness.** The AD-3 role → feature table and the AD-3 mermaid graph duplicate each other. Keep the table (it is the binding artifact) and drop or simplify the graph. The seed sequence diagram's `B-->>U` arrows skip the proxy participant (cosmetic). AD-4's "the user logs in again and lands on their shell home" is narrative consequence, not rule. It shrinks naturally once C1 is fixed.
- **L5. Inherited table completeness.** Add backend AD-9 (lockout → 423 `ACCOUNT_LOCKED`), since AD-4 depends on it. Optionally note on the AD-8 row that the backend re-issues the cookie on every authenticated request, which is why no refresh call exists.

## What is solid (keep)

- AD-5's write-then-`.reload()` rule with named examples, and its no-optimistic-update stance.
- AD-6's interceptor carve-outs for `/auth/login` and `/auth/change-password` 401s, and the read (inline `LoadError`) vs write (inline-known-codes / toast) split, with "shown exactly once".
- AD-10 generated types-only from the single `openapi.yaml`, with aliases and "never re-declare fields".
- AD-7 Tailwind 4 CSS-first config with tokens in one `@theme`, and CDK for behaviour wrapped only in `shared/`.
- AD-12 same-origin `/api` with no CORS and no `withCredentials`. This is correct for `withFetch()` same-origin credentials, and it is consistent with inherited AD-7/AD-13.
- The Deferred list (other than session restore) cannot cause divergence between units.
