---
title: Adversarial Review — ARCHITECTURE-SPINE.md (company-leave-backend)
reviewed-doc: ../ARCHITECTURE-SPINE.md
method: construct two developers one level below the spine, each obeying every AD to the letter, and show they still build incompatibly
date: 2026-10-22
---

# Adversarial Review

**Verdict:** the spine is well-formed as a set of individually sound rules, but it has at least five places where two developers, each 100% compliant with the letter of the ADs, produce code that cannot interoperate — two of them (AD-8's ownership vs. dependency graph, and AD-4's "which method is *the* mutating method" ambiguity) are structural and should block Story kickoff for `auth`/`security` and for `approvals`/`leave` respectively until a new/tightened AD lands.

---

## Finding 1 — AD-8 names `auth` as owner of `active_sessions`; AD-1's dependency graph forbids `security` from calling `auth`

**Severity: Critical — blocks `auth` and `security` module implementation on day one.**

AD-8: *"a Postgres table `active_sessions` ... This table is owned by `auth`."* The JWT filter that must check this table on **every protected request** is placed in the `security` package per the Structural Seed: `security/ # ... JWT filter/decoder config (AD-7/AD-8)`.

AD-2 is unambiguous that a module's repository/entity is only referenced inside that module, and all cross-module access "goes through the owning module's `@Service` interface." So if `active_sessions` is owned by `auth`, the `security` package's per-request filter must call `auth`'s service bean to validate/bump every request.

But the AD-1 dependency graph states the edge is `auth --> security` (auth depends on security), and explicitly: *"No edge above may be reversed without a new AD."* It also states `common` and `security` "depend on nothing domain-specific." A call from the `security` filter into `auth`'s service is exactly a `security --> auth` edge — reversed, and domain-specific.

**Two compliant-but-incompatible builds:**
- **Developer A (owns `auth`):** reads AD-8 literally — `ActiveSession` entity + repository live in `auth`, behind an `AuthSessionService`. Expects the `security` filter to call it.
- **Developer B (owns `security`):** reads AD-1's graph literally — `security` must not gain a domain-specific outgoing edge, so places the `ActiveSession` entity/repository *inside* `security` itself and implements the filter as a pure `security`-local DB check, with `auth`'s login/logout only calling into `security`'s session-registry service (the one declared direction, `auth --> security`) to create/delete rows.

These two designs produce different entity ownership, different package for the Flyway-migrated table's JPA mapping, and — worse — if `auth`'s login flow is written by Developer A's assumption (session row created in `auth`) while the hot-path filter is written by Developer B's assumption (session row read/bumped in `security`, separate `DataSource`/entity mapping or even separate repository bean on the *same* table), you get two `@Repository`s over one table with divergent transaction/locking behavior and no single place enforces "look up and bump" atomically.

**Fix direction:** add an AD that either (a) explicitly carves `active_sessions` out of AD-2's "owning module" rule as a shared/`security`-owned table (contradicts AD-8's literal wording — needs rewording there too), or (b) adds the missing edge `security --> auth` (or a narrower "auth exposes a session-check port that security is allowed to call" exception) to the graph, closing the reversal conflict.

---

## Finding 2 — `AccessPolicy` (AD-10) has no return-type/contract spec, and no method covers single-resource ownership checks

**Severity: High — guaranteed per-controller divergence in `leave` and `approvals`.**

AD-10 names four methods (`requireRole`, `scopeToOwnRequests`, `scopeToTeam`, `scopeToAll`) but specifies **no signatures**: no return type, no indication of whether they throw-and-return-void (a pure gate) or return a filter/criteria object a controller composes into its query. Nothing states call order relative to fetching the entity being authorized.

All four named methods take a *known id* (`userId`, `managerId`) — i.e. they only support **list/collection scoping**, where the scope is decided before any repository call. Nothing in AD-10 covers the common **single-resource ownership check**: "is leave request #123, once loaded, actually owned by this caller / this caller's direct report?" That check requires loading the entity (a service call) *before* the authorization decision can be made — which conflicts with AD-10's own sequencing rule, "every controller method calls the relevant `AccessPolicy` method **before** invoking its service."

**Two compliant-but-incompatible builds:**
- **Developer A (`leave`, implementing cancel-own-request, `DELETE /api/leave-requests/{id}`):** there is no userId in the path to pre-scope with, so `scopeToOwnRequests` can't run "before invoking the service" in any meaningful way. Developer A calls the service to fetch the request first, then does a manual `request.getUserId() == currentUserId` comparison **inline in the controller or service**, reasoning that since AD-10's listed methods don't cover this case, a direct comparison isn't "inventing a role/scope check" (it's not a role check, it's a plain id comparison) — and is technically outside what AD-10 forbids by its literal examples (`if (user.getRole() == ...)`).
- **Developer B (`approvals`, implementing approve-one-request, `PATCH /api/approvals/{id}`):** reads the same ordering rule strictly and refuses to let the service run before `AccessPolicy` is consulted at all. Implements `scopeToTeam(managerId)` to return a `Set<Long>` of direct-report ids (an assumption about its return type that Developer A, writing a different endpoint, never makes), calls it first, then separately queries `leave`'s service for the request and intersects manually in the controller.

Result: identical "is this mutation allowed on this specific row" problem is solved two different ways — one with an ad hoc inline ownership check AD-10 was meant to eliminate, one with an invented `AccessPolicy` return contract (`Set<Long>`) nobody else agreed to, that a third module (`users`, if it ever needs the same team-scope helper) has no way of discovering without reading `approvals`' source.

**Fix direction:** specify `AccessPolicy`'s exact method signatures (return types, exception thrown on denial, e.g. `void requireOwner(long resourceOwnerId)` / a `Specification<T>`-returning scoping method), and add an explicit AD for the single-resource-ownership case (load-then-check), stating whether that still counts as "AccessPolicy runs before the service" or whether AD-10's ordering rule has a stated exception for load-then-authorize flows.

---

## Finding 3 — "Direct, synchronous call" (AD-1) to `notifications` doesn't say what happens when `sendNotification()` throws, and AD-4 silently excludes `notifications` from the same-transaction guarantee

**Severity: High — directly affects whether a notifications bug can block leave submission or approval.**

AD-1/Capability Map: notification calls from `leave`/`approvals` are "direct, synchronous." AD-4 binds `leave, approvals, balances, audit` to the same-transaction rule for audit/ledger writes — **`notifications` is conspicuously not in that bind list**, even though `notifications` also performs a DB write (per the Stack table: `sendNotification()` writes a DB row + log line). Nothing states whether that omission means (a) notification writes are deliberately *excluded* from the critical transaction so a failure there must never roll back the leave/approval action, or (b) notifications simply wasn't mentioned but is still called inside the same `@Transactional` method by virtue of AD-1's "synchronous, in the caller's thread" rule, so an uncaught exception propagates and rolls back everything, audit row included.

**Two compliant-but-incompatible builds:**
- **Developer A (`leave`, `submitRequest()`):** reads AD-4's bind list literally — `notifications` isn't named, so a notification failure must not undermine the leave-submission guarantee AD-4 *does* protect. Wraps `notificationsService.sendNotification(...)` in try/catch inside the same transactional method, logs and swallows any exception, and returns success to the caller regardless.
- **Developer B (`approvals`, `approveRequest()`):** reads AD-1 literally — a module-to-module call is "a public `@Service` bean method, in-process, in the caller's thread," no different from any other call, and nothing in the spine grants `notifications` special exception-handling treatment. Lets the exception propagate normally; since this call sits inside the same `@Transactional approveRequest()` method that also writes the audit row and balance ledger entry (AD-4), a `notifications` DB failure now rolls back the approval's audit and balance writes too.

Result: identically-shaped "state change + notify" operations behave oppositely across two modules for the same class of failure — one module treats notification failure as fire-and-forget, the other treats it as transaction-poisoning, and QA testing one module's happy path will not catch the other's regression.

**Fix direction:** add an AD stating explicitly whether `notifications` calls are (a) inside or outside the triggering `@Transactional` boundary, and (b) whether `sendNotification()` failures are caught-and-logged at the call site (specify by which module/layer) or allowed to propagate. If (a) is "outside," also specify how a post-commit call is made without `@Async`/events, since AD-1 forbids both.

---

## Finding 4 — AD-4's "any service method that mutates..." doesn't say which method, in a cross-module call chain, is "the" mutating method — risk of double audit writes or zero audit writes on approve/reject

**Severity: Critical — this is exactly the failure mode AD-4 exists to prevent, reintroduced by ambiguity in AD-4 itself.**

AD-4: *"any service method that mutates `leave_requests` or `leave_balances` calls `AuditService`/`BalanceLedgerService` directly, inside the same `@Transactional` method, before returning."*

An approval decision is a multi-hop call: `approvals.approveRequest()` (entry point, per AD-2 only `leave` may write `leave_requests`, so it) calls into `leave`'s service to flip `status`, and also calls into `balances` to commit the reservation. Both `leave.updateStatus(...)` *and* `approvals.approveRequest(...)` can honestly be described as "a service method that mutates `leave_requests`" — the former does the literal row write, the latter is the business action that causes it. AD-4 binds **both** `leave` and `approvals`, so both developers can point to the same sentence as their mandate.

**Two compliant-but-incompatible builds:**
- **Developer A (`leave`):** implements `LeaveService.updateStatus(id, newStatus, actorId)` to satisfy AD-4 literally at the point of the actual row mutation — it is, after all, the method that executes the `UPDATE leave_requests` statement — so it calls `AuditService.record(...)` itself, inside its own `@Transactional`, before returning, for *every* status change regardless of caller.
- **Developer B (`approvals`):** implements `ApprovalsService.approveRequest(id, managerId)` as the outer transactional boundary (the controller's entry point, and the method AD-10 says must be authorized first) and, since AD-4 explicitly binds `approvals` to the audit obligation, *also* calls `AuditService.record(...)` after calling `leaveService.updateStatus(...)` and `balancesService.commit(...)`, reasoning that the business-level "approval" is the action that needs to be audited, not the internal setter.

Net effect: every approval/rejection writes **two** `audit_events` rows for one action (or, under the mirror-image assumption — each developer assuming the *other* module already covers it — **zero** rows get written, silently reintroducing the PRD risk AD-4 was created to close).

A second variant of the same root ambiguity: nothing states `@Transactional` propagation across this call chain. If Developer A marks `leave.updateStatus()` `@Transactional` with default `REQUIRED` it silently joins the caller's transaction (safe); if instead (a very common default instinct — "each service method manages its own transaction") it's written as `REQUIRES_NEW`, the status flip commits independently and before `approvals.approveRequest()` goes on to call `balances`/`audit` — so a later failure in the balance or audit step leaves an `APPROVED` request with no matching ledger or audit row, a partial-commit split that is invisible in either developer's own module's tests.

**Fix direction:** add an AD that names the *single* method/layer responsible for the audit (and ledger) call per cross-module action — e.g. "the method invoked directly by the controller is the only one that may call `AuditService`/`BalanceLedgerService`; service methods invoked by another module's service never call them" — and explicitly states default transaction propagation (`REQUIRED` everywhere, no `REQUIRES_NEW` across this call graph) to close the partial-commit variant.

---

## Finding 5 — No AD states table-to-module ownership explicitly; it's inferred from package-name matching, which `approvals` can plausibly break

**Severity: Medium — a "missing rule" gap, not yet a guaranteed clash, but the spine never actually forecloses it.**

AD-2 says a module's `@Repository`/`@Entity` are "package-private or otherwise only referenced inside that module" and cross-module access "goes through the owning module's `@Service` interface" — but no AD anywhere enumerates *which module owns which table*. It's left entirely to the reader inferring from package names in the Structural Seed (`leave # requests, ... modify, cancel`) and the ERD. For `leave_requests` specifically, the Structural Seed's one-line gloss for `approvals` is "manager decisions, team-scope checks" — which reads, to a developer under deadline pressure, as "approvals is where decisions (i.e., status writes) happen." Nothing in the spine explicitly states "the `LeaveRequest` JPA entity and its repository are defined inside `leave`; `approvals` must call `leave`'s service for every status mutation and must never map its own entity/repository over `leave_requests`."

**Two compliant-but-incompatible builds:**
- **Developer A (`leave`):** maps `LeaveRequest` as its own entity/repository, exposes `updateStatus(...)` on `LeaveService`, expects every status change — including manager approve/reject — to flow through it.
- **Developer B (`approvals`):** under the same deadline, and reading its own module gloss ("manager decisions") as license to own the decision outcome, maps its own lightweight `@Entity` (or native query) directly against the `leave_requests` table to flip `status` and `decided_at` in one round trip, since nothing it has read says that table belongs exclusively to `leave`, and its JPA dependency is already on the classpath.

Both are AD-2-compliant on paper only if you accept "my module's business gloss implies ownership" as a valid inference; the spine never actually pins ownership per table, so this is a coin flip resolved by whichever developer reads the ERD first.

**Fix direction:** add a one-line ownership table (table → owning module) to the spine, parallel to the Capability → Architecture Map, removing any inference step.

---

## Minor note — AD-11 error codes are examples, not an enum

AD-11 gives three example `code` values (`BALANCE_INSUFFICIENT`, `RBAC_DENIED`, `ACCOUNT_LOCKED`) but no central registry/enum. Two modules hitting conceptually identical "invalid state transition" errors (e.g. `leave` cancelling an already-approved request vs. `approvals` approving an already-cancelled request) are free to invent two different `code` strings for the same class of error, which the Angular client cannot branch on consistently. Low severity for a POC, but worth a one-line fix: a shared `ErrorCode` enum in `common`.
