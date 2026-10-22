---
review: reconcile
target: ../ARCHITECTURE-SPINE.md
source: _bmad-output/planning-artifacts/prds/poc-prd-emp-leave-tracker-2026-10-22/prd.md
decision-log: ../.memlog.md
date: 2026-10-22
---

# Reconciliation: POC PRD vs backend Architecture Spine

## Verdict

**Mostly aligned. Amend before build.** Every backend FR in scope (FR1–FR31, FR42–FR47, FR50–FR61) maps to a module and an AD. I found no unlogged hard contradiction of a PRD business rule. What remains is a set of **gaps**: PRD requirements or acceptance scenarios whose backend behaviour the spine leaves undecided, so two builders could implement them differently. Six of these are medium severity (G1–G6). The rest are low. The logged overrides (Boot 4.1, stateless JWT, separate origins, annotation-driven RBAC) need PRD wording changes, listed in section 3.

Line numbers refer to `prd.md`.

## 1. Gaps: PRD content not landed in the spine

### Medium

| # | PRD reference | Gap | Suggested fix |
| --- | --- | --- | --- |
| G1 | FR1 (L44), FR18 (L61), Acceptance preamble (L191); spine AD-13 | AD-13 puts all seed data in `db/seed`, which runs only under the `demo` profile. Two things then exist in no other profile. (a) **Leave types** (Vacation, Sick, Parental, Sabbatical) are reference data that FR1 requires everywhere. (b) **A first HR admin.** FR18 lets only HR create users, so `dev`, `test` and the eventual production build have no way to log in. | In AD-13, state that leave types are inserted by a `db/migration` file (all profiles). Add a bootstrap rule for the first HR admin, such as an env-var-driven initial admin created at startup when no `HR_ADMIN` exists. |
| G2 | FR57 (L88), Tech req L104, Scenario 14 (L208); spine AD-3, AD-9 | Lockout requires the `failed_logins` increment (and `USER_LOCKED` audit) to **commit even though login fails**. Under AD-3 the login entry point owns the transaction and the failure exception rolls it back. The only allowed `REQUIRES_NEW` writes are `access_log` and `notifications`. A builder following AD-3 literally ships a lockout that never triggers. | In AD-3 or AD-9, name the failed-login write (`UsersService.recordLoginFailure`) as a third sanctioned own-transaction write, or require login to run non-transactionally, with the failure write in its own committed call. |
| G3 | FR11 (L54): "employee, dates, type, balance, recent history" | The approval context needs the requester's **balance** and **recent leave history**. AD-1 calls the dependency graph complete, and in it `approvals` depends only on `leave`. The spine does not say who composes FR11 or which endpoint serves it. The PRD endpoint list (L172–173) has none. | Either add an `approvals --> balances` edge, or make `LeaveService` expose a "request context" DTO that includes balance plus the last N requests. Name the endpoint (for example, extend `GET /api/requests/{id}` for the approver). |
| G4 | Endpoints L172 ("Owner, **their manager**, HR"), FR17 (L60); spine AD-5, AD-8 | AD-5 introduces two sources of authority: `approver_id` for decisions and the live `manager_id` for lists. It does not say which one grants **record-level read** on `GET /api/requests/{id}`, `/history` and `/comments`. If `manager_id` changes while a request is pending, the approver can be refused a request they must decide, or the new manager can see it but not act on it. | In AD-8, define `@leavePermissions.canView` as owner OR `approver_id` OR live `manager_id` OR `HR_ADMIN`. Use the same rule for comment posting (see G5). |
| G5 | FR15 (L58), Decisions L281, Endpoints L172, Scenario 5 (L199); memlog L37 (open question) | The comment rules are still undecided: who may post, in which statuses, and whether a comment writes an `audit_events` row. AD-7 says the History tab reads `audit_events` plus `request_comments`. If comments also produce a `COMMENT_ADDED` audit row, they appear twice in the timeline. | Close the memlog question in the spine. Proposed rule: anyone who passes `canView` may post in any status. Comments write an audit row, and the History tab takes comment bodies from `request_comments` while skipping their audit rows (or the reverse). |
| G6 | FR13 (L56), Scenario 4 (L204): "Reject blocked without a comment" | The PRD states this as UI behaviour ("Reject disabled until comment entered"). The spine never requires the **server** to refuse a reject without a comment. AD-8 says UI hiding is not a control, but no AD carries this rule. | In AD-4, add: reject requires a non-blank `decisionComment`, otherwise 400 with code `COMMENT_REQUIRED`. Approve takes an optional comment. Both are stored in `decision_comment`. |

### Low

| # | PRD reference | Gap | Suggested fix |
| --- | --- | --- | --- |
| G7 | FR7 (L50): "reservation recalculated" | AD-6 handles an edit only as a `+ delta` on the same balance row. An edit that changes the **leave type** must release the old type's row and reserve on the new one. Locking two rows also needs a fixed lock order to avoid deadlock. | In AD-6, add: an edit that changes the type is a release on the old row plus a reserve on the new row in one transaction, with rows locked in `leave_type_id` order. Alternatively, forbid type changes on edit. |
| G8 | Endpoints L171 ("own requests (managers too)"), FR24 (L68); spine AD-8 | AD-8 says list endpoints are scoped "everyone for HR". Applied to `GET /api/requests`, that would make HR's "My Requests" list every request. The PRD defines that endpoint as own requests for every role. The spine does not name the endpoint through which HR sees all requests. | Add a per-endpoint scope table to AD-8: `/api/requests` = self for all roles; `/api/team/leave` = live direct reports; `/api/approvals/pending` = `approver_id`; HR-wide views (`/api/balances`, `/api/audit`, `/api/users`, plus a named HR request list if one is wanted). |
| G9 | Scenario 12 (L206): "Ledger shows amount, reason, actor and time"; FR30 (L73) vs FR32 deferred (L93) | No endpoint exposes `balance_ledger` (PRD L177 has only set and adjust), and the balance history view (FR32) is deferred. Scenario 12 cannot be demonstrated through the API. | Either add `GET /api/balances/{user}/{type}/ledger` (HR, paged) as the FR30 read, or state that scenario 12 is shown through `GET /api/audit` (the `LEAVE_BALANCE_*` events carry amount, reason and actor). |
| G10 | Endpoints L170–179; spine AD-9 (HttpOnly cookie) | The JWT sits in an HttpOnly cookie, so the SPA cannot read `role` or `must_change_password` after a page reload. The PRD lists no current-user endpoint. A list endpoint for **leave types** (FR1 form) is also missing. | Add `GET /api/auth/me` (id, username, role, mustChangePassword) and `GET /api/leave-types` to the contract. Optionally have login return the `me` body as well. |
| G11 | Tech req L110: "Unit and API tests for RBAC, **approval rules and audit writes**"; Stack L140 ("RBAC, balance and audit rules are the riskiest parts") | The spine's Tests convention requires Modulith verify, RBAC scenarios 6–9 and the trigger test for scenario 13. It does not require state-machine, balance-lifecycle or no-negative tests, or assertions that each use case writes its audit row. | Extend the Tests row: one test per AD-4 transition (including 409s), AD-6 operation table rows (including scenario 10 and the HR-below-reserved refusal), and an audit-row assertion per state-changing use case. |
| G12 | FR52, FR54 (L83, L85): "link" in email; Project structure `templates/email/` (L236) | The recipient of FR52 is unstated (`approver_id` or live manager). FR54 requires a link to the request, which needs the SPA base URL, and no config key names it. No template engine appears in Stack, and `spring-boot-starter-mail` has none. | In AD-11: the FR52 recipient is `approver_id`, and FR50 and FR51 go to `employee_id`. Add env var `APP_BASE_URL`. Pin the template engine (for example Thymeleaf, managed by Boot) in Stack. |
| G13 | Acceptance preamble L191; Scenarios 1, 6, 7, 10 | Seed composition is unspecified: hierarchy (8 employees → 2 team managers → top-level manager → HR admin), opening balances per type (scenario 1 needs ≥ 4 vacation days, and scenario 10 needs a tight balance), and how demo passwords are documented. | List the seed contents in AD-13 or the Structural Seed, or point to a seed fixture spec. |
| G14 | FR19 (L63): "Role and manager chosen at creation; top-level managers report to an HR user" | AD-5 says every non-HR user must have a `manager_id`. It does not say the manager must be an **active** user with role `MANAGER` or `HR_ADMIN`, or that cycles are forbidden. It also gives no reassignment rule for pending requests whose approver is later deactivated. | Add the validation to `UsersService` (400 `INVALID_MANAGER`). Accept the stuck-approver case as a known POC limitation in Deferred. |
| G15 | FR43 (L76): "Required columns"; FR57 | The audit `actor_id` is NOT NULL, but some events have no clear actor: lockout of an existing user (system, or the user themselves?) and failed logins for unknown usernames. | Define the actor for system-initiated events (for example, the affected user for `USER_LOCKED`; no audit row for unknown usernames). |
| G16 | FR44 (L77), Scenario 13 (L209) | A trigger on UPDATE/DELETE does not block `TRUNCATE`, and an app role that owns the table can disable the trigger. | Add a `BEFORE TRUNCATE` statement trigger. Optionally run the app as a non-owner DB role (or defer that to production hardening). |
| G17 | FR47 (L80): "retention policy documented"; FR5 (L48) "filterable" | The spine covers "no purge code" but does not say where the retention policy is documented (README). FR5 filter parameters, and paging for `/api/requests`, are unspecified. | Note the README as the retention-policy home. Add `status`, `type` and date-range filters to the API conventions. |

## 2. Spine items that diverge from the PRD without a memlog decision

These are small, but the memlog does not record them as deliberate.

| # | PRD | Spine | Note |
| --- | --- | --- | --- |
| D1 | L235 `db/migration/ # Flyway V1__init.sql ...` | AD-13 `V<yyyyMMddHHmm>__<module>_<description>.sql` | AD-13 is tagged [ADOPTED], but the memlog records only the seed-location choice (11.a). Log the naming decision, or amend the PRD example. |
| D2 | FR31 (L74), Scenario 12: HR adjustment "cannot go below **zero**" | AD-6: refused if the result is below **reserved** | This is a stricter, consistent reading (available ≥ 0), backed by memlog L21/L28 (CHECK). Amend the PRD wording to "cannot make available days negative". |
| D3 | Tech req L103: insert-only **audit and access-log** tables | AD-7 also makes `balance_ledger` insert-only | Logged in memlog L27. Add it to the PRD for traceability. |
| D4 | L129: "Everything runs with one `docker compose up`" | `dev` runs the API through `mvnw` and the SPA through `ng serve`; only `demo` is full compose | Harmless. Amend the PRD to "the demo runs with one `docker compose up`". |
| D5 | Risks L269: "Central permission layer" | AD-8: `@PreAuthorize` plus one permission bean per resource | Logged (memlog L18, user chose 4.b). Amend the PRD wording. |

## 3. PRD amendments required by logged overrides

| PRD location | Current wording | Amend to |
| --- | --- | --- |
| Stack, L136 | "Java 21, Spring Boot 3 (…)", why "Mature RBAC and **session** support" | Spring Boot **4.1.x**, plus Spring Modulith 2.1 and `spring-boot-starter-security-oauth2-resource-server`. Why: "mature RBAC and JWT resource-server support". |
| Back-end packages, L144 | "auth — login, **session**, lockout, password rules" | "auth — login, JWT cookie issuance and refresh, lockout policy (state stored by `users`), password rules" |
| Tech req, L104 | "30-minute session timeout, HttpOnly/Secure/SameSite cookies" | "30-minute sliding token expiry (8 h absolute cap), JWT in an HttpOnly/Secure/SameSite=Strict cookie, CSRF token on state-changing calls" |
| FR58, L89; Scenario 15, L209 | "Session timeout" / "Session expires" | Keep the wording, but note the mechanism: a JWT not re-issued for 30 min expires, and the API returns 401. |
| FR55, L86 | "HTTPS via local reverse proxy" | "HTTPS via Caddy in the demo compose profile; dev uses http on localhost" |
| Implementation plan, L218 | "Spring Security login, **sessions**, lockout" | "Spring Security JWT login, sliding expiry, CSRF/CORS, lockout" |
| Architecture, L129 | Three-tier, single origin implied | SPA and API on **separate origins sharing one registrable domain** (`localhost:4200`/`:8080`, then `app.`/`api.`), with explicit CORS allow-list from an env var |
| Endpoints, L170 | auth endpoints | Add `GET /api/auth/csrf`. Note that logout only clears the cookie. Add `GET /api/auth/me` (G10). |
| Risks, L265–272, and the client-facing deferred list (L272) | — | Add two accepted risks: (1) no token revocation, so a copied token stays valid up to 30 min after logout, password reset, deactivation or role change, and role claims may be stale for up to 30 min; (2) an email is lost if SMTP is down or the app crashes after commit. |
| Core tables, L158 | `leave_requests` fields | Add a `version` column (optimistic lock, giving 409 on concurrent transitions). |
| Project structure, L226–235 | `leave-poc/` root; `V1__init.sql` | Repo root = `leave-poc/`. Add `db/seed/` (demo profile only) and the timestamped migration naming (D1). Show module `internal/` and `web/` subpackages. |

## 4. Coverage confirmed (no action)

FR2/FR9 (AD-12, AD-7), FR4/FR7/FR8 transitions and no-delete (AD-4), FR10/FR16/FR17 approver snapshot and HR self-approval with the audit flag (AD-5), FR12/FR13 balance moves (AD-6), FR21–FR26/FR59 RBAC and access log (AD-8), FR27–FR31 lock, ledger, reason and no-negative (AD-6), FR42–FR45/FR47 (AD-7), FR50–FR53 after-commit email and history (AD-11), FR56 BCrypt and the password rule (AD-9), FR57/FR58 numbers (AD-9; see G2), FR46/FR60/FR61 (Deferred), the Flyway-only schema, env-var config, Postgres Testcontainers, and the Phase 2 items (Deferred).
