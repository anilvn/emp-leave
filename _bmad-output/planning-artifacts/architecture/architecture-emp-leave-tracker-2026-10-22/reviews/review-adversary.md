# Adversarial Review: ARCHITECTURE-SPINE.md (emp-leave-tracker core API)

Method: two units one level down (module or developer feature), each obeying every AD literally, then checked for incompatible builds. The spine itself is not edited. Each hole lists the two units, the clash, and a tightened AD wording.

Verdict: the spine is not yet buildable as two independent teams would read it. Five holes, each reachable by two literal-compliant implementations that cannot interoperate or that violate the invariant the AD exists to protect.

---

## Hole 1: Refresh session vs access token (AD-6, AD-7, AD-12)

**Unit A (web adapter / SPA token handling).** Keeps the user signed in by calling the refresh endpoint on a timer, ahead of the 15-minute `exp` (AD-6). Compliant: AD-6 says the access token is refreshed; nothing forbids timer refresh.

**Unit B (identity refresh).** Resets the 30-minute idle TTL "on each rotation" (AD-7). Compliant: AD-7 says so literally.

**Clash.** A tab left open with no user activity is refreshed every 14 minutes by Unit A. Each rotation resets the idle TTL in Unit B, so the 30-minute inactivity rule (AD-6 says it "takes effect at refresh") never fires. The PRD's inactivity rule is therefore silently dropped. Both units obey the AD.

**Second clash, reuse detection vs concurrent refresh.** AD-7 stores only the SHA-256 of the current token and says reuse of a rotated token revokes the whole session. Two browser tabs, or a refresh racing a retry, present the same token at nearly the same time. The first rotates; the second now looks like reuse and revokes the whole session. A benign race logs the user out. Unit C (storage) overwrites the hash on rotation, so the prior hash needed to recognise reuse is gone. Unit D (reuse check) either cannot detect reuse or flags every stale request.

**Tightened AD-7 wording:**
> Idle TTL (30 min) is extended only by an authenticated business API request that presents a valid, unexpired access token. Token refresh alone does not extend it. The SPA calls refresh only after a 401 from the API or in response to user activity; it never refreshes on a timer. Rotation is an atomic compare-and-set on a generation counter in Redis. The record keeps the hash of the current token and of the previous token for a 30-second grace window. A presentation of the previous token inside the grace window returns 401 with no revocation. A presentation of an older token, outside the window, revokes the whole session.

**Tightened AD-6 wording (addition):**
> The SPA must not refresh the access token on a timer. A 401 from any endpoint triggers at most one refresh; a failed refresh sends the user to sign-in.

---

## Hole 2: Outbox publisher vs transaction boundary (AD-4, AD-5, AD-15)

**Unit A (leave submit/cancel).** Calls `OutboxFacade.enqueue(...)` and `AuditFacade.record(...)` inside its own transaction. AD-4 says "one DB transaction" but does not say whether facade calls join the caller's transaction. Unit A's audit facade uses `REQUIRES_NEW` so that rejected attempts are audited even when the state change rolls back. Compliant with AD-5, which only says the audit facade inserts.

**Unit B (outbox publisher).** Implements AD-15 literally as "reads pending rows with `FOR UPDATE SKIP LOCKED`, publishes to SQS, and marks them sent." Two readings both comply:
- B1 keeps the row lock open across the SQS call. Long locks, pool exhaustion under load.
- B2 publishes outside the transaction, marks sent in a second transaction. After a crash, duplicates are sent. This is allowed (at least once), but the `eventId` must then be stable across retries. AD-15 does not say when `eventId` is minted. If the publisher mints it at publish time, the worker's dedupe by `eventId` never matches.

**Clash, facade propagation.** If Unit A's outbox or audit write uses `REQUIRES_NEW`, the outbox row can commit while the leave state rolls back, which produces the phantom email AD-15 exists to prevent. If it joins, a failed audit insert rolls back the state change, which is the intent. The spine does not say which.

**Clash, ordering.** Two publisher instances (AD-21 runs at least 2 tasks) with `SKIP LOCKED` can publish `LeaveRequestApproved` before `LeaveRequestSubmitted` for the same request. AD-15 says nothing about ordering, and the worker dedupes only by `eventId`.

**Tightened AD-4 wording (addition):**
> Audit and outbox inserts are made by their owning facades with mandatory propagation: they join the caller's transaction and fail if none is open. `REQUIRES_NEW` is forbidden for any write from a state-changing path. A rejected attempt that must be audited is recorded by a separate, explicitly named audit call outside the state transaction, with its own rule.

**Tightened AD-15 wording:**
> `eventId` is assigned when the outbox row is inserted and never regenerated. The publisher claims rows in a short transaction (`FOR UPDATE SKIP LOCKED`, status set to CLAIMED with a `claimed_until` lease), publishes to SQS outside any transaction, then marks rows SENT in a second short transaction. Lease expiry returns a row to PENDING. Events for the same aggregate are published in outbox sequence order, enforced by claiming only the lowest unsent sequence per aggregate. The worker tolerates out-of-order delivery across aggregates only.

---

## Hole 3: Idempotency key scope and owner (AD-2, AD-4, AD-16)

**Unit A (leave submit endpoint).** Uses key scope (actor, key) as written. The actor here is an employee, possibly also a manager.

**Unit B (approval decision endpoint).** Same actor, same key namespace. A client helper generates one key per form. The same key appears on `POST /leave-requests/{idA}/approve` with body `{"comment":"ok"}` and later on `POST /leave-requests/{idB}/approve` with the same body. The second request is answered with the cached response for `idA`, so `idB` is never approved. The body does not include the path id, and AD-16 hashes only the body ("same key with a different body returns 422"). Both units comply.

**Clash, owner.** AD-16 names no owning module. AD-2 requires one. Unit A puts the table in `leave`. Unit B puts it in `shared` (AD-1 allows only ids and primitives there, so this is a rule break, but a team could do it). Two tables, two owners.

**Clash, atomicity.** AD-16 stores the response. If the key row is written in a separate transaction after the state change, a crash between the two leaves a committed submission with no key row. A retry then creates a second leave request. If the key row is written first in its own transaction, a concurrent in-flight duplicate is not handled. AD-4 requires the state change, audit row, outbox row and response to commit together, but AD-16 does not put the key row in that set.

**Clash, in-flight.** Two concurrent requests with the same key. The loser's unique-constraint violation has no defined response (422 would be wrong, since the body matches).

**Tightened AD-16 wording:**
> Idempotency scope is (actor id, HTTP method, route template, path resource ids, key). The request hash covers method, route, path ids and the canonical body. A key that matches on scope but not hash returns 422. A key row in state IN_PROGRESS returns 409 with a problem-details type `idempotency-in-progress`. The key row, the state change, the audit row, the outbox row, and the stored response are written in the same transaction (AD-4). The table `idempotency_key` is owned by the module named here: `idempotency` (a new module added to AD-1's table, with its own facade). Keys are retained 24 hours and purged by that module.

---

## Hole 4: Leave-day count vs balance ledger (AD-11, AD-13, AD-14, AD-17)

This hole has four clashes that fit one shared shape: the count and the ledger entry.

**Clash A, calendar as input.** AD-11 says "weekends and public holidays are counted." The holiday calendar then has no effect on the count. AD-17 caches the holiday calendar (1-hour TTL, evicted on HR change), and Open Questions say HR maintains it. Unit A (leave) reads the calendar to exclude holidays, a common reading of "public holiday" as a non-working day. Unit B (balance) counts plain calendar days. Both quote AD-11 and AD-17. Separately, a 1-hour TTL across two tasks can give the same request two different counts.

**Clash B, ledger writes.** AD-13 says submission and approval lock the account and check availability, and the ledger holds signed days. It does not say which event writes an entry. Unit A debits on submit and writes a reversal on cancel or reject. Unit B debits on approval and writes nothing on reject. Both are append-only and both comply. Balances after a reject differ by the full leave amount. Modification (allowed while PENDING, AD-14) changes the count, and no ledger correction rule exists, so the stored count and the ledger diverge.

**Clash C, "available balance".** If only approval debits, two concurrent submissions can each pass the check and then both be approved past zero. If submission debits, "available" needs a hold concept the spine does not define.

**Clash D, lock target.** AD-13 says `SELECT ... FOR UPDATE` on "the employee's balance account row." No such table exists in the ERD. The ledger is append-only, so there is no row to lock. `SELECT SUM(...) FOR UPDATE` is invalid in PostgreSQL. Unit A locks a `balance_account` row. Unit B takes `pg_advisory_xact_lock(employee)`. Both obey AD-13 and do not exclude each other. Lock ordering between balance and `leave_request` (`@Version`) is also unspecified.

**Tightened AD-11 wording:**
> Leave days = calendar days from start date to end date inclusive, as an integer. The holiday calendar is not an input to the count. Public holidays are displayed, not subtracted. The count is computed once at submission (or modification) and stored on `leave_request.days`. It is never recomputed from reference data after submission.

**Tightened AD-13 wording:**
> The table `balance_account (employee_id, leave_type, version)` is owned by `balance`, one row per employee and leave type, seeded at account creation. All ledger writes go through the balance facade, which first locks the account row `SELECT ... FOR UPDATE`. Ledger events and their entries: submission writes a HOLD entry of -days; approval writes a DEBIT entry of -days and removes the HOLD; cancel or reject writes a RELEASE entry of +days that removes the HOLD; modification writes an ADJUST entry for the difference. Available balance = sum of ledger entries minus open holds, computed inside the locked transaction. Lock order is `balance_account` before `leave_request`, always.

---

## Hole 5: Two owners of the leave state and the reporting line (AD-2, AD-4, AD-9, AD-14)

**Clash A, who writes `leave_request.status`.** The capability map gives approval "manager decisions." AD-2 gives each table one owner, and `leave_request` belongs to `leave`. The approval module therefore has one of two ways to record a decision:
- It writes `leave_request.status` through its own repository. This violates AD-2 and the AD-1 facade rule, but reads as compliant with AD-4 ("one application-service method"), since approval has a method.
- It writes an `approval_decision` table of its own and then updates status. This is two state stores for one fact.

Meanwhile `leave` owns cancel and modify transitions in its domain code. Two application-service methods mutate the same status. AD-4 says "one mutation path," which both units satisfy in their own module.

**Clash B, reporting line owner.** AD-9 scopes a manager to "direct reports," which needs `manager_of`. The ERD shows `EMPLOYEE }o--|| EMPLOYEE : manager_of`. AD-8 puts the user row in `identity`. The capability map gives approval "team scope." Two owners of one relation: identity (`user.manager_id`) and approval (team table). Both pass AD-2 individually.

**Clash C, employee id shape.** The JWT `sub` (AD-6), the ledger's "employee" (AD-13), the outbox's "employee id" (AD-15) and the ERD's `EMPLOYEE` entity are not tied to one id. Unit A may use `user.id`; Unit B may use a separate employee number. Event consumers then cannot join.

**Tightened AD-14 wording (addition):**
> `leave` owns `leave_request` and every status transition. The approval module owns no table that stores status. It decides by calling `LeaveFacade.decide(requestId, actor, decision, comment, idempotencyKey)`; that method is the only writer of `leave_request.status`. Any approval-owned table holds decision inputs only and is written inside that same call's transaction.

**Tightened AD-9 wording (addition):**
> Identity owns `user_account`, including `manager_id`, and is the sole owner of the reporting line. The approval module resolves direct reports through `IdentityFacade.directReportsOf(userId)`. No other module stores a reporting line. The employee id is `user_account.id` (UUID), which is the JWT `sub`. It is the only employee id used in ledger rows, outbox payloads and audit rows.

---

## Further notes (not holes; do not block)

- **AD-22 coverage.** Dedicated integration tests are required for AD-5, AD-13 and AD-14 only. Holes 1 to 3 (refresh rotation, outbox claim/publish, idempotency) have no required test. Propose adding AD-7, AD-15 and AD-16 to AD-22.
- **AD-6 role claim.** `role` is in the access token. An HR role change takes up to 15 minutes to affect access, and AD-6 says the role is never trusted from the client but does not say whether a role change is checked server-side on each request. Propose stating that the role is read from the identity record at refresh and that role changes take effect at the next refresh.
- **AD-12 path.** The refresh cookie is path-scoped to `/api/v1/auth`, but the access token cookie has no path rule. Propose stating it explicitly so a logout endpoint elsewhere cannot leave it behind.
- **Numeric types.** `leave_request.days` (integer) and ledger "signed days" (type unstated) should share one type.
