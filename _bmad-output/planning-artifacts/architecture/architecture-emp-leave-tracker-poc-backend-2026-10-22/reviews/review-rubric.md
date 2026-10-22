# Rubric review: ARCHITECTURE-SPINE (emp-leave-tracker POC backend)

Reviewer: rubric walker · Date: 2026-10-22 · Inputs: ARCHITECTURE-SPINE.md, .memlog.md, POC PRD (2026-10-22), brownfield `pom.xml` + `src/` (Initializr scaffold: Boot 4.0.8, Java 21, `com.company.leave`, `application.properties`, one `contextLoads` test, no CI).

## Verdict

A strong, terse spine that fixes most of the real divergence points (state machine, balance arithmetic, approver snapshot, transaction scope, audit vocabulary, auth/cookie/CSRF contract). It is not yet build-ready: the module-boundary contract (AD-1 + the `common` leaf rule + the Structural Seed) is internally contradictory in ways the very `verify()` test it relies on will expose in week 1, and several transaction-semantics gaps (failed-login counter, after-commit listener exceptions, cross-module reads) will make two builders diverge. All fixes are small text amendments.

## Checklist walk

| Criterion | Result | Notes |
| --- | --- | --- |
| Fixes the real divergence points, misses none | Partial | Misses: cross-module read queries (F4), failed-login tx semantics (F5), comments rules (F8), lock ordering on transitions (F10), error status for several codes (F11) |
| Every AD Rule enforceable and prevents its divergence | Partial | AD-1's enforcement claim is false as written (F2); AD-8 filter placement conflicts with AD-1/AD-2 (F3); AD-11 does not prevent the user-visible failure it targets (F6) |
| Nothing under Deferred lets units diverge | Pass | Deferred items are either accepted risks or production envelope. "Caddy routing detail" is safe only if the env var names are fixed (F12) |
| Named tech verified-current | Pass with caveats | Boot 4.1.1, Modulith 2.1.1, Boot-4 starter name sourced in memlog. Caddy 2.11 is marked unconfirmed in the memlog. Testcontainers coordinates under Boot 4 not named (F13) |
| Ratifies, not contradicts, brownfield | Mostly | Bump 4.0.8 to 4.1.1 and move to `backend/` are explicit. `application.yml` vs scaffold's `application.properties` is not called out (F13) |
| Covers PRD backend capabilities | Mostly | FR11 context and FR14 team view have no legal dependency path for the data they need (F4). FR45 History tab is mapped to a module that cannot build it (F1) |
| Every owned dimension decided / deferred / open | Partial | No Open Questions section; the memlog's open FR15 question was dropped (F8). Environment variable contract, container build, health/readiness not addressed (F12) |
| Mermaid validity | Pass | All three diagrams parse (dotted labelled edge `A -. text .-> B`, `[( )]` cylinder, ER self-relation and `""` labels are valid). One content inconsistency (F14) |
| Internal contradictions (graph vs AD text vs ownership table) | Fail | F1, F2, F3 |

## Findings

### F1 [critical] DTO subpackage and History tab placement contradict AD-1 and the Modulith test

- **DTOs:** the Design Paradigm says the module's **root package** is the public API ("one `*Service`, plus DTO records and events"), but the Structural Seed puts DTOs in `{module}/dto/`. Spring Modulith treats every subpackage as internal unless it is exposed as a `@NamedInterface`. The first cross-module call that passes a `leave.dto.*` record fails `ApplicationModules.verify()`. Builders will split between "move DTOs to root" and "annotate `dto`".
- **History tab:** the Capability Map assigns "Audit, History tab (FR45)" to `audit`, and AD-7 says the tab is read from `audit_events` plus `request_comments`. `request_comments` is owned by `leave`, and `audit` "depends on nothing but `common`". If `audit` composes the tab, it needs an `audit → leave` edge, which makes a cycle (`leave → audit`).
- **Fix:** (a) In the seed, DTOs and events live in the module root package, or each `dto/` has a `package-info.java` with `@NamedInterface("dto")`. Pick one. (b) State that `GET /api/requests/{id}/history` lives in `leave/web`. `leave` calls a read method on `AuditService`, such as `findByEntity("LEAVE_REQUEST", id)`, and merges in its own comments. Move FR45 to the `leave` row of the Capability Map.

### F2 [high] AD-1's "verify() enforces the graph" is false without `allowedDependencies`

`ApplicationModules.verify()` catches cycles and access to internal packages. It does **not** reject an edge just because the edge is missing from a diagram. Unless each module declares `@ApplicationModule(allowedDependencies = {...})`, any module may call any other module's root-package service. Two examples: `balances` calling `UsersService`, or `approvals` calling `BalancesService`. Neither is in the graph, and the build stays green. AD-1's statements that "the dependency graph above is the complete allowed set" and "fails the build on any violation" are therefore not enforced. The dashed `leave -. publishes .-> notifications` edge also reads as a permitted `leave → notifications` dependency, which with `notifications → leave` would be a cycle.
**Fix:** Require a `package-info.java` per module with `allowedDependencies` that mirrors the graph exactly. Mark `common` as `@ApplicationModule(type = Type.OPEN)`, or give it named interfaces. Redraw the event edge as a note, or as `notifications` listening to `leave` events, so that no arrow points from `leave` to `notifications`.

### F3 [high] `common` is declared a leaf but is assigned work that needs `auth`, `audit` and module beans

`common` holds "config, security wiring" and "depends on none of them". Three pieces of that wiring cannot be built under the rule:
- The security filter chain needs the JWT encoder/decoder, the sliding re-issue filter and the cookie bearer-token resolver, all of which are `auth` concerns (AD-9).
- AD-8's access-log servlet filter is "the sole writer of `access_log`". AD-2 says only `audit` writes `access_log`. A filter in `common` cannot call `AuditService`, so it would have to write the table directly, which breaks AD-2.
- The Capability Map puts the "permission beans" under `common` security. Permission beans need module data, such as `approver_id` and `manager_id`.

Builders will each choose a different place for these pieces.
**Fix:** State explicitly:
- `SecurityFilterChain` and the JWT/cookie/CSRF configuration live in `auth` (add the edge `auth → audit` if it is needed).
- The access-log filter lives in `audit` (`audit/web` or `audit/internal`), is registered inside the security chain after authentication so the actor is still in context, and records the status after `chain.doFilter`.
- Each permission bean lives in its own module (`leave/internal/LeavePermissions`, and so on).
- `common` keeps only error handling, `Clock`, the working-day function and the `PasswordEncoder` bean.

### F4 [high] Cross-module **reads** are undecided; FR11, FR14 and HR balances have no legal data path

AD-2 governs writes only, and AD-1 forbids reaching into another module's repositories. Several read views need data from more than one module:
- The FR11 pending detail shows the employee, the balance and recent history. That needs `approvals → balances` and `approvals → users`, and neither edge exists.
- The HR balances page (FR27) needs user and leave-type names. `balances → users` is not an edge, and `balances → leave` would create a cycle.
- The FR14 team view and the scoped list queries join `leave_requests` with the live `users.manager_id`.

One builder will write native SQL joins across tables owned by other modules, which Modulith cannot detect. Another will compose the view through service calls. A third will add edges. The results diverge, and the spine says nothing about which is allowed.
**Fix:** Add one rule to AD-1 or AD-2. Example: "Read-only SQL in a module's repository may join other modules' tables by id. Writes go only through the owner." Or the reverse: "Views are composed in the use-case module through services, and these edges are added: `approvals → balances`, `approvals → users`, `balances → users`." Also state where `leave_types` reference data comes from for `balances`.

### F5 [high] Failed-login counter and lockout audit roll back with the failed login

AD-9 says five failures set `locked_until` "through `UsersService`". AD-3 makes the use-case entry point transactional and allows `REQUIRES_NEW` only for `access_log` and `notifications`. A failed login ends in an authentication exception. If the login use case is `@Transactional`, the `failed_logins` increment and the `USER_LOGIN_FAILED`/`USER_LOCKED` audit rows roll back, and scenario 14 never locks the account. Builders will fix this in incompatible ways: `noRollbackFor`, `REQUIRES_NEW`, or a non-transactional login.
**Fix:** In AD-3, add `UsersService.recordFailedLogin` / `recordSuccessfulLogin`, together with their audit writes, to the `REQUIRES_NEW` list. Alternatively, rule that the login entry point is not transactional and returns a failure result instead of throwing inside the transaction.

### F6 [high] AD-11 listener exceptions still reach the user; SMTP timeouts unset

Exceptions thrown from `@TransactionalEventListener(AFTER_COMMIT)` run inside `afterCommit` and propagate to the caller. An SMTP failure therefore turns a committed approval into an HTTP 500. The SPA retries the approval, gets a 409, and the user believes the action failed. That is the failure AD-11 says it prevents. The listener also runs synchronously on the request thread, so an SMTP server that hangs blocks the request indefinitely unless timeouts are set.
**Fix:** Add to AD-11: "The listener catches and logs every exception and never rethrows. `spring.mail.properties.mail.smtp.connectiontimeout` and `timeout` are set (e.g. 5 s)." Optionally use `@Async` on a bounded executor. Name the listener annotation explicitly as "not `@ApplicationModuleListener`" (in Modulith 2.x that annotation is async and pulls in the event registry).

### F7 [medium] AD-5 approver snapshot vs view permission: the approver may be unable to open the request

Decision authority and the pending list use `approver_id`, but "list visibility" and, by the PRD endpoint table, `GET /api/requests/{id}` ("Owner, their manager, HR") use the live `manager_id`. If the manager changes while a request is pending, the old manager sees the request on Pending, cannot open its detail or history (403), and so cannot use the FR11 context. The new manager can view the request but cannot decide it. Nothing covers reassignment or deactivation of the approver either, so the request is orphaned.
**Fix:** Define `@leavePermissions.canView` as owner, `approver_id`, live manager, or HR. Add either "HR changing `manager_id` re-points the user's PENDING `approver_id` (audited)" or an explicit Deferred or accepted-risk line.

### F8 [medium] No Open Questions section; the memlog's open FR15 question was dropped

The memlog's last entry leaves open which statuses accept comments and who may post them. The spine is silent on this. AD-4 calls terminal states "immutable", which a builder can read as "no comments after a decision". It is also undecided whether posting a comment writes a `REQUEST_COMMENT_ADDED` audit event. If it does, AD-7's History tab (`audit_events` plus `request_comments`) shows every comment twice. Separately, `decision_comment` on reject or approve is not stated to be (or not be) a `request_comments` row.
**Fix:** Add an Open Questions section, or decide in AD-4/AD-7:
- comments are allowed on any status, by anyone with `canView`;
- comments are audited, and History reads comments from `request_comments` only and skips the comment audit action, or the other way round;
- `decision_comment` lives only on the request and in the audit details.

### F9 [medium] Flyway timestamp versions without `outOfOrder`; seed versions interleave

Timestamped migration names remove filename collisions. When two developers' branches merge, though, the earlier-timestamped migration lands after the later one has already been applied, and Flyway's validation fails on each developer's local database. Seed files in `db/seed`, added only under `demo`, have the same problem whenever the demo profile is switched on against an existing database. Separately, AD-7's DELETE-blocking triggers break any test cleanup that uses `DELETE` or `deleteAll()`.
**Fix:** Set `spring.flyway.out-of-order=true` for `dev`/`test`, or state "reset the local DB on pull". Name the seed files `R__demo_seed.sql` (repeatable), or reserve a version range for them. Add to Tests: "isolate with a fresh container per suite, or with TRUNCATE (not blocked by row triggers); never DELETE".

### F10 [medium] Transition lock order is unspecified

Concurrent approve and cancel both pass the status check on version N. Each then takes the balance row lock in turn and adjusts `reserved_days`. Whichever fails first decides the outcome. It can be the `@Version` check (409), or it can be the `CHECK reserved_days >= 0` (a `DataIntegrityViolation`, which becomes a 500), depending on Hibernate's flush order. An edit that changes leave type touches two balance rows. If the lock order is not fixed, two such edits can deadlock.
**Fix:** In AD-4/AD-6, every transition first loads the request with `PESSIMISTIC_WRITE` (or flushes the version increment), re-checks the status, and then locks balance rows in ascending `leave_type_id` order. Also state whether the leave type is editable while PENDING.

### F11 [medium] Error mapping gaps that the SPA contract depends on

The spine does not give an HTTP status for any of these:
- `INSUFFICIENT_BALANCE`;
- a token restricted by `must_change_password` hitting another endpoint, for which the SPA needs a stable code such as `PASSWORD_CHANGE_REQUIRED` in order to redirect;
- a failed login or a locked account (401 with a single code?);
- a missing id that is also out of scope (404 or 403?).

AD-12 bans counting days on the client, but FR2/scenario 1 implies that the form shows the day count before submit. With no endpoint, the SPA will count days itself.
**Fix:** Extend the Errors row: `INSUFFICIENT_BALANCE` → 409 (or 422), `PASSWORD_CHANGE_REQUIRED` → 403, `BAD_CREDENTIALS` → 401, and a missing record → 404 before the scope check. Add `GET /api/working-days?start=&end=` or state "count shown only after submit".

### F12 [medium] Environments: env var contract, container build and readiness are not decided

The dev/demo/prod table is good. It does not fix the names of the environment variables that `application.yml`, `docker-compose.yml` and the README must agree on: JWT secret, CORS origins, DB URL and credentials, SMTP host, and the SPA base URL used for the FR54 email link. Also missing are how the backend image is built (Dockerfile or `spring-boot:build-image`) and readiness, meaning a compose `depends_on: condition: service_healthy` for Postgres and/or Actuator health. The "demo profile = seed data plus TLS" wording conflates a Spring profile with a compose concern: Caddy is not controlled by Spring.
**Fix:** Add a Config row that lists the canonical variable names, e.g. `LEAVE_JWT_SECRET` (≥256-bit for HS256), `LEAVE_CORS_ORIGINS`, `LEAVE_APP_BASE_URL`, plus `SPRING_DATASOURCE_*` and `SPRING_MAIL_*`. Add one line deciding the image build and the Postgres healthcheck. Reword `demo` as "Spring profile `demo` = seed; compose adds Caddy".

### F13 [medium] Brownfield and stack details to ratify explicitly

- The scaffold has `application.properties`, but the spine names `application.yml`. Say "convert" so that both files do not end up existing.
- The scaffold lacks the oauth2-resource-server starter, Modulith (`spring-modulith-starter-core`, `spring-modulith-starter-test`) and Testcontainers. Boot 4 manages Testcontainers 2.x, whose module coordinates changed (e.g. `org.testcontainers:testcontainers-postgresql`), along with `spring-boot-testcontainers`. Name the exact artifacts so that two builders do not add different ones.
- The resource server reads bearer tokens from the `Authorization` header by default. AD-9's cookie transport needs a custom `BearerTokenResolver`. Name that so nobody adds a header fallback.
- With stateless sessions, AD-10's CSRF needs a non-session `CsrfTokenRepository`, such as `CookieCsrfTokenRepository`. Otherwise the default creates an `HttpSession`. Name it.
- Versions: Boot 4.1.1 and Modulith 2.1.1 are sourced from spring.io in the memlog and are consistent with each other (Modulith 2.1 targets Boot 4.1). Caddy 2.11 is marked unconfirmed in the memlog; the major-minor tag is an acceptable pin.

### F14 [low] Diagram content nits

- In the Structural Seed, the browser reaches the SPA directly over "https", but Caddy fronts only the API. The memlog says Caddy terminates TLS for **each** origin in demo, and in dev both origins are plain http. Route the SPA through Caddy as well, or label the edges per environment.
- The ER diagram omits `audit_events`, `access_log` and `notifications → users`. That is acceptable as a seed, but `notifications ||--o{` currently shows only the request link.

## What is solid (keep)

- AD-4 and AD-6 together give an unambiguous arithmetic for each operation, with a DB CHECK backstop and a single owner of the transitions.
- AD-5 cleanly separates decision authority from visibility. It needs only the F7 view-permission patch.
- AD-9 and AD-10 form a coherent cookie, SameSite, CORS and CSRF contract, with honest accepted risks.
- AD-12 has a single working-day function and stores the result.
- The Deferred list matches the PRD's deferred table, and nothing in it is a hidden divergence point.
