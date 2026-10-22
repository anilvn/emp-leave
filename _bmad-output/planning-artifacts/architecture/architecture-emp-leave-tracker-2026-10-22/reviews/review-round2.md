# Review round 2: ARCHITECTURE-SPINE.md (emp-leave-tracker core API)

- Reviewed: `../ARCHITECTURE-SPINE.md` (396 lines, updated 2026-10-22) and `../.memlog.md`.
- Cross-checked against the PRD (`_bmad-output/planning-artifacts/prds/prd-emp-leave-tracker-2026-10-15/prd.md`) for FR7, FR15, FR31, FR45-FR54, FR55, FR58, and retention lines 478, 503, 508, 828, 894.
- The spine was not edited.

## Verdict

Not ready to finalize. Most listed holes now have concrete wording, but at least six still let two AD-compliant implementations diverge or produce a defect. Three of these are new defects introduced by the revision (AD-6/AD-7 session mechanics, the AD-7 grace reply, and the AD-16 IN_PROGRESS state).

---

## (a) Status of the previous holes

| # | Hole | Status | Why |
| --- | --- | --- | --- |
| 1 | Balance account lock and availability (AD-13) | OPEN | Ledger writes other than submit/modify do not lock; account row creation is unowned; `version` vs `FOR UPDATE` ambiguous; ledger rows carry no request reference; deadlock with AD-14 paths. See H1, H2, H3. |
| 2 | Session inactivity and refresh reuse (AD-7) | OPEN | Grace reply cannot be implemented as written; idle extension cannot be implemented with the cookie path and JWT claims given. See H4, H5. |
| 3 | Outbox contract vs FR54 and recipient routing (AD-15) | PARTIAL | Recipient and FR54 fields added. Still open: no-manager recipient, requester name missing, no modify or comment event, claim step has no status or lease, sequence source undefined, FAILED predecessor halts an aggregate. See H6, H7. |
| 4 | Entry services, transactions, table ownership, prefixes (AD-2, AD-4) | OPEN | `identity` is not in AD-4 Binds, so login failure rolls back the lockout counter; denial and read audit need a transaction they may not have; `identity_session_audit` and the leave type catalogue have no clear owner. See H8, H9, H10. |
| 5 | Operations envelope (AD-23) | PARTIAL | Backup retention, PITR, KMS, and rollout are fixed. Cross-region copy cadence and copy KMS key are undefined, so the 24-hour RPO is not verifiable. Outbox 3-year retention has no purge owner. See H11. |
| 6 | Idempotency scope and owner (AD-16) | OPEN | Scope and owner are now clear, but IN_PROGRESS/409 is unreachable under the atomic-write rule. Missing-header status undefined. See H12. |
| 7 | Leave-day count vs ledger | OPEN | Negative day counts are not rejected, so a HOLD can increase the balance. Ledger has no link to the request. See H13. |
| 8 | Leave status ownership (AD-14) | MOSTLY CLOSED | `LeaveFacade.decide` is the only decision path and `leave_request.status` has one owner. Still open: the approval module's role, where the decision comment is stored, and the employee-decide status code (403 vs 404). See H14. |

### Detailed clashes for the open holes

**Hole 1, balance (AD-13).**
- Clash A (negative balance race). AD-13 says submission and modification lock `balance_account` before reading. It does not say HR `ADJUST` locks. Implementation X locks in ADJUST. Implementation Y does not. Sequence: balance is 5. Submit (3 days) locks, reads 5, and passes. HR `ADJUST` of -5 inserts without a lock and commits. Submit then inserts HOLD -3 and commits. Result: -3, which AD-13 says is rejected. Both implementations satisfy the text.
- Clash B (account row creation). Balance module creates `balance_account` rows either at user creation (provisioned) or on first access (lazy). With lazy creation, two concurrent first submissions for the same employee and leave type each see no row. `SELECT ... FOR UPDATE` on a missing row locks nothing, so both insert.
- Clash C (locking mechanism). AD-13 declares a `version` column and also `SELECT ... FOR UPDATE`. One implementation uses pessimistic locking. The other uses `@Version` and retries or returns 409 on conflict. Both are AD-compliant and users see different results under contention.
- Clash D (ledger link). Ledger entries have employee, leave type, delta, reason, actor. Nothing links a RELEASE to the HOLD it cancels. Two implementations can record a release twice or against the wrong days. The AD-22 test cannot assert per-request reconciliation.
- Clash E (modify across types). FR7 allows changing "dates, type, notes". AD-13 says modify does RELEASE on the old hold and HOLD on the new days, without saying which account the new HOLD uses. Implementation X keeps the leave type fixed. Implementation Y moves the hold to the new type's account.
- Clash F (check ordering). "RELEASE the old hold, check and HOLD the new days" does not say the check is against the balance net of the old hold. Balance 5, pending 3, modify to 6 days. Post-release check passes (8 available). Pre-release check fails (5 available).

**Hole 2, session (AD-7).**
- Clash A (grace reply). "Presenting the previous token within a 30-second grace window returns the current pair." The server stores only the SHA-256 of the current refresh token, so it cannot return it. Implementation X mints a new refresh token on a grace hit. Tab A's cookie then holds the token that becomes "previous" after its own next refresh. Once the grace window closes, a presentation of that token revokes the session. That is the spurious logout the rule is meant to prevent. Implementation Y returns only a new access token and leaves the refresh cookie unchanged. Both satisfy the text.
- Clash B (inactivity extension). AD-7 says idle TTL is extended only by API requests carrying a valid access token. Three facts block that: (1) the `sid` cookie is scoped to `/api/v1/auth`, so leave and balance API calls never send it; (2) the access token claims are `sub`, `role`, `iat`, `exp` only, with no session id; (3) extending by `sub` is ambiguous when one user has two sessions (two devices). The resource server therefore cannot identify which Redis record to extend. This is also an AD-6 vs AD-7 contradiction (section b).

**Hole 3, outbox (AD-15).**
- Clash A (claim step). Step 1 claims with `FOR UPDATE SKIP LOCKED` in a short transaction, then commits. Commit releases the lock, and no status or lease is written. Two publisher tasks can both claim row X and publish it. The rule "never publish before predecessor is marked sent" is then checked by neither or both.
- Clash B (sequence source). "Per-leave-request sequence number" does not say where it comes from. A DB sequence leaves gaps on rollback, so a predecessor that never commits blocks all successors forever. Using `leave_request.version` under the row lock avoids gaps but is a different rule.
- Clash C (FAILED predecessor). After 10 attempts a row is FAILED. The ordering rule then blocks all later rows for that aggregate. The alarm fires, but no rule says whether the aggregate is skipped, halted, or replayed.
- Clash D (no manager). `LeaveRequestSubmitted` goes to the manager. If an employee has no `manager_id`, one implementation throws inside the submit transaction and rolls the leave back. Another skips the row. The spine is silent.
- Clash E (FR54 name). FR54 says emails include "relevant request details". The payload has employee id, not employee name, and the worker has no database or identity access. The manager email cannot name the requester unless the contract adds it.
- Clash F (missing events). The capability map lists `outbox` under "Modify or cancel", but AD-15 defines no modify event. PRD FR7 and the narrative at PRD line 211 (a comment triggers an email to the employee) imply events the spine does not define.
- Clash G (routing vs AD-9). AD-15 stores the manager's email copied from the reporting line at write time. AD-9 says other modules "never store a copy" of the reporting line. Either reading is possible.

**Hole 4, entry services and transactions (AD-2, AD-4).**
- Clash A (lockout counter lost). AD-8 says failed login increments a counter on `identity_user`. AD-4 binds leave, approval, balance, audit, outbox, idempotency. It does not bind identity. Implementation X increments inside the login transaction and then throws a domain exception to map to 401. The rollback discards the increment, so lockout never triggers. Implementation Y commits the increment separately. Both are AD-compliant because the spine does not say which.
- Clash B (denial and read audit). AD-5 requires audit of every authorization denial and every read of employee data. AD-4 says audit writes are MANDATORY, meaning they join the caller's transaction. A denial throws 403 or 404, so the joined audit row rolls back. Read endpoints such as `LeaveFacade.history` have no named transaction, so MANDATORY throws. Implementation X uses REQUIRES_NEW for audit. Implementation Y joins and loses the rows.
- Clash C (session audit). `identity_session_audit` is owned by identity. AD-5 says only the audit facade inserts audit rows, and append-only triggers cover only `audit_event`, `audit_access_log`, and `balance_ledger`. Login and logout events can therefore land in either table.
- Clash D (leave type catalogue). The catalogue is used by balance keys, cached in AD-17, and assumed HR_ADMIN-owned (open question). No module or table owns it in the Design Paradigm table, which violates AD-2 "every table has exactly one owning module".
- Clash E (approval role). AD-1 requires each module to expose a facade. AD-14 says `LeaveFacade.decide` is the only decision path and the approval module never writes `leave_request`. The approval module's facade is therefore either a thin pass-through or unused. The capability map lists `LeaveFacade.decide` as entry service and does not list approval under "Lives in".

**Hole 5, operations (AD-23).**
- Clash A (cross-region copy). "Backups are copied to a second region" has no cadence and no destination KMS key. PITR is in-region only. Copy every 24 hours and copy every 6 hours both fit the text, and they give different RPO for a region loss.
- Clash B (retention). AD-15 says outbox history is kept 3 years "matching the audit retention". AD-5 forbids DELETE on audit, so audit has no purge. Outbox is not append-only, and no job, owner, or schedule is named for the 3-year purge (AD-16 names its cleanup job, AD-15 does not).

**Hole 6, idempotency (AD-16).**
- Clash. AD-16 says the key row is written in the same transaction as the state change (MANDATORY). A concurrent duplicate then blocks on the unique index until the first commits, and gets a unique violation. It then reads COMPLETED and replays. An IN_PROGRESS row is never committed, so the 409 rule can never fire. Implementation X writes IN_PROGRESS in a separate committed transaction, which breaks the atomic rule. Implementation Y keeps the atomic rule and never produces IN_PROGRESS. Both are AD-compliant.
- Missing `Idempotency-Key` header: status code is not stated (400 or 428).

**Hole 7, day count vs ledger (AD-11, AD-13).**
- Clash. AD-11 gives `end - start + 1` and does not require `end >= start`. With start 10 and end 7, the count is -2. HOLD is a negative delta, so `-(-2)` is +2 and the balance increases. The spine does not reject it.

**Hole 8, status ownership (AD-14, AD-9).**
- Employee POST to decide on a colleague's request: AD-9 gives 403 for "role not permitted" and 404 for "outside scope". Both apply. Implementation X checks role first (403). Implementation Y checks scope first (404). The spine gives no precedence.
- Decision comment storage: "Rejection requires a comment" does not say whether it is stored in `leave_comment` or on `leave_request`.

---

## (b) New contradictions and defects from the revision

**N1. AD-6 vs AD-7 (access token and idle rules).** AD-6 says the resource server "validates signature and exp on every request" and checks nothing else. AD-7 requires each authenticated API request to extend a Redis idle TTL. Both cannot hold without naming the mechanism. The claim set and cookie path make the extension unimplementable (see Hole 2 clash B). Also, AD-6 says logout is not enforced on access tokens, while AD-7's extension would still write to a deleted session record after logout, which is harmless only if the write is a no-op on a missing key. The spine should say so.

**N2. AD-7 grace window.** The reply "returns the current pair" is not implementable (Hole 2 clash A). It is a new defect, because the earlier draft did not state the reply.

**N3. AD-12 SameSite Lax vs CSRF.** Double-submit is sound only if the `XSRF-TOKEN` cookie cannot be set by a sibling subdomain. AD-12 assumes `app.example.com` and `api.example.com` share a registrable domain. If `XSRF-TOKEN` carries `Domain=example.com`, a sibling host can plant it. The spine does not require host-only cookies. Also, the first state-changing request is `POST /auth/login` from a fresh browser: no `XSRF-TOKEN` cookie has been issued yet, so the double-submit check returns 403 unless login is exempt. The spine does not say which. The Lax rationale ("keeps notification deep links working") is weak under the same-site assumption: the email link lands on a static SPA page that needs no cookie, and the SPA's API calls are same-site, so Strict would also work. This is not a clash, but the stated reason does not support the choice.

**N4. AD-13 hold semantics vs AD-14 modify.** Hole 1 clashes E and F. In addition, AD-14 allows modify only while PENDING, so an approved hold can never change. That is consistent with AD-13 but means a wrong approved leave type cannot be corrected by the employee. The PRD does not say otherwise, so this needs a stated decision.

**N5. AD-16 IN_PROGRESS.** Introduced by the atomic-write rule plus the 409 rule (Hole 6). The prior draft had the same contradiction in a different form.

**N6. Capability map vs ADs.**
- "Modify or cancel" lists `outbox` but AD-15 has no modify event.
- "HTTPS, backups, retention, encryption (FR46, FR47, FR55, FR60, FR61)" is governed by AD-12, which is cookies and CSRF. The correct references are AD-21 (TLS at ALB) and AD-23.
- "Accounts, roles, temp passwords, reset (FR18-FR20)" lists AD-8, AD-9 but not AD-5, which the entry service writes to through audit.
- "Balance view" lists AD-2, AD-5, AD-13 but not AD-9 for data scope, which AD-9 requires for every facade method.
- "Approve or reject" names `LeaveFacade.decide` but not the approval module (see Hole 4 clash E).

**N7. AD-22 test list.** The integration-test list covers AD-4, AD-5, AD-7, AD-13, AD-14, AD-15, AD-16. It omits AD-8 (lockout), AD-9 (scope and status codes), and AD-12 (CSRF). The lockout test would have caught the rollback in Hole 4 clash A.

**N8. AD-5 vs AD-4 for denials.** A denial throws, so a transaction-joined audit row cannot survive (Hole 4 clash B). This is new in the sense that AD-5 now requires denial audit, and AD-4 is written only for state changes.

---

## Remaining holes (ranked), with clash and one-line fix

- **H1. Balance ledger writes are not all locked (AD-13).** Clash: HR `ADJUST` of -5 commits unlocked during a 3-day submit, leaving balance -3. Fix: AD-13 says every ledger write (HOLD, RELEASE, ADJUST) locks `balance_account` first, and the negative block applies to ADJUST unless FR31's HR-approval exception is stated as a flag.
- **H2. Lock order between leave and balance (AD-13, AD-14).** Clash: modify locks balance then updates `leave_request`; cancel and decide update `leave_request` then lock balance. Two concurrent operations on one request deadlock, and the outcome is an arbitrary abort. Fix: AD-13 sets one global order (leave_request row, then balance_account) and every path follows it.
- **H3. Balance account provisioning and locking mechanism (AD-13).** Clash: two concurrent first submits on a missing `balance_account` row both insert. Also `version` vs `FOR UPDATE` yields 409 in one build and retry in another. Fix: BalanceFacade provisions one row per employee and leave type at user creation (REQUIRED, audited), and AD-13 says `FOR UPDATE` only, with `version` removed or documented as an audit counter.
- **H4. Grace reply (AD-7).** Clash: "returns the current pair" either mints a new refresh token, which makes the other tab's cookie revoke the session after 30 seconds, or returns nothing usable. Fix: a grace hit returns a new access token only and leaves the refresh cookie unchanged.
- **H5. Idle extension mechanism (AD-6, AD-7).** Clash: `sid` is scoped to `/api/v1/auth`, the access token has no session claim, and extension by `sub` is ambiguous across two sessions. Fix: add a `sid` claim to AD-6, cookie path `/api/`, and state that the resource server extends `sid`'s idle TTL on each authenticated request.
- **H6. Outbox publisher ordering and claim (AD-15).** Clash: a claim that commits without a status or lease allows double publish; a rolled-back predecessor or a FAILED predecessor blocks the aggregate forever. Fix: claim sets `CLAIMED` with a lease timestamp, sequence = `leave_request.version` assigned under the row lock, and a FAILED predecessor halts only that aggregate with an alarm and an operator replay action.
- **H7. Outbox event set and recipients (AD-15).** Clash: modify and comment produce no event; no-manager submit either rolls back or silently skips; manager email cannot name the requester. Fix: add `LeaveRequestModified` and `LeaveCommentAdded` with recipients, define no-manager as "no row written and submit proceeds", and add `employeeName` to the payload.
- **H8. Login outside the transaction rule (AD-4, AD-8).** Clash: the failed-login counter increment rolls back when login throws to map to 401. Fix: add `identity` to AD-4 Binds and state that failed-login counters and login audit commit in REQUIRES_NEW.
- **H9. Audit of denials and reads (AD-4, AD-5).** Clash: denial audit and read audit join a transaction that is rolled back or does not exist. Fix: AD-5 audit of denials and reads runs in REQUIRES_NEW, and AD-4 says read endpoints open a read-only transaction.
- **H10. Table ownership gaps (AD-2, AD-5).** Clash: `identity_session_audit` has no append-only trigger and may be written outside the audit facade; leave type catalogue has no owner table. Fix: name the leave module as owner of `leave_type` and route session audit through the audit facade, or drop `identity_session_audit`.
- **H11. Operations envelope (AD-23, AD-15).** Clash: "copied to a second region" has no cadence or KMS key; the 3-year outbox retention has no purge job. Fix: AD-23 states the copy schedule (for example every 24 hours), the destination CMK, and the outbox purge owner and schedule.
- **H12. Idempotency states (AD-16).** Clash: IN_PROGRESS and 409 are unreachable under the atomic-write rule; missing header status undefined. Fix: write the key row COMPLETED in the state transaction, delete IN_PROGRESS and the 409 rule, and state that a missing `Idempotency-Key` returns 400.
- **H13. Day count and ledger link (AD-11, AD-13).** Clash: `end < start` yields a negative count that increases the balance; ledger rows cannot be tied to a request. Fix: AD-11 rejects `end < start` with 422, and AD-13 requires every ledger entry to carry `leave_request_id` (nullable for ADJUST).
- **H14. Approval module and status precedence (AD-1, AD-9, AD-14).** Clash: approval module role undefined; employee deciding a colleague's request gets 403 or 404 depending on check order. Fix: state that approval is a policy-only facade called by the web adapter, that role is checked before scope (403 first), and where the decision comment is stored.
- **H15. CSRF bootstrap and cookie scope (AD-12).** Clash: first login POST has no `XSRF-TOKEN`; a sibling subdomain can plant a domain-scoped token. Fix: issue `XSRF-TOKEN` on `GET /api/v1/auth/csrf` before login, and require host-only cookies.
- **H16. Modify across leave types and check ordering (AD-13, AD-14).** Clash: FR7 type change has no rule for the target account; the check runs before or after the release. Fix: modify = RELEASE on the old account, then check net balance and HOLD on the target account, locked in ascending order.

---

## Checked and not a clash

- AD-1, AD-3, AD-10, AD-18, AD-19, AD-20, AD-21 are internally consistent with each other and with the Design Paradigm table.
- Table prefixes match module names in every table listed. Fine.
- Retention values for backups (30 days, PRD line 508) and audit (3 years, PRD line 503) match the PRD.
- The PRD's Session timeout (30 minutes, PRD line 818) and lockout (5 attempts, FR57) match AD-7 and AD-8.
- The accepted risk on access-token revocation is recorded in AD-6, AD-7, and the memlog. The PRD amendment is listed as an open question.
