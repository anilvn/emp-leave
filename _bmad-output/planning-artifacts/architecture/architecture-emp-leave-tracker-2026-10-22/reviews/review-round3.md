# Review round 3: ARCHITECTURE-SPINE.md (emp-leave-tracker core API)

- Reviewed: `../ARCHITECTURE-SPINE.md` (399 lines, updated 2026-10-22) and `../.memlog.md` (56 lines).
- Baseline: `reviews/review-round2.md` (16 ranked holes H1-H16, plus the eight section (a) holes and N1-N8).
- "Top-5 holes" are read as the five items the memlog records at line 52 ("Round 2 fixes applied"): sid claim and idle extension, grace reply, identity in AD-4, global lock order, idempotency COMPLETED rows.
- The spine was not edited.

## Verdict

**Not ready to finalize.** The revision closes most of the ranked list at the level of stated rules, but eight of the sixteen ranked holes are still OPEN, and three of the five top holes are still OPEN. Several of the new text rules cannot be implemented as written, most importantly the session cookie and CSRF mechanics, which would break every authenticated call or every state-changing call.

---

## (a) Status of the 16 ranked holes

| # | Hole | Status | One-line reason |
| --- | --- | --- | --- |
| H1 | Balance ledger writes not all locked (AD-13) | CLOSED | AD-13 now says HR ADJUST locks its `balance_account` row first and an ADJUST that makes the balance negative is rejected. |
| H2 | Lock order leave vs balance (AD-13, AD-14) | OPEN | The order names a `leave_request` lock, but AD-14 uses `@Version`, which takes no lock; the first lock on the request row is implementation-defined. See B2. |
| H3 | Balance provisioning and locking mechanism (AD-13) | OPEN | Rows are created at user creation and `FOR UPDATE` is the only mechanism, but a leave type created later has no provisioning rule, so the missing-row race returns for that type. See B7.4. |
| H4 | Grace reply (AD-7) | OPEN | A grace hit returns an access token with no refresh cookie change, so a lost response leaves the browser with a stale token that revokes the session after the grace window. See the H4 detail below and B1.4. |
| H5 | Idle extension mechanism (AD-6, AD-7) | OPEN | `sid` is now a claim, but the access cookie has no path (defaults to `/api/v1/auth`), and the refresh call has no way to find the session record. See B1, item 1. |
| H6 | Outbox claim and publisher ordering (AD-15) | OPEN | The claim selects `PENDING` rows with no predecessor check, so a successor can publish before a `CLAIMED` predecessor; `sequence` is said to be set "under the row lock", which does not exist. See B4. |
| H7 | Outbox event set and recipients (AD-15) | CLOSED | `LeaveRequestModified`, `LeaveCommentAdded`, no-manager rule and `employeeName` are all in the text. Residual: the copy of the manager's email conflicts with AD-9 (B5). |
| H8 | Login outside the transaction rule (AD-4, AD-8) | CLOSED | `identity` is in AD-4 Binds and the failed-login counter and its audit row are REQUIRES_NEW. |
| H9 | Audit of denials and reads (AD-4, AD-5) | CLOSED | Denial and read audit are REQUIRES_NEW and reads open a read-only transaction. Pool exhaustion risk is noted in B5.2. |
| H10 | Table ownership gaps (AD-2, AD-5) | CLOSED | `identity_session_audit` is dropped, `leave_type` is owned by `leave`, and each table has one owner in the Design Paradigm table. |
| H11 | Operations envelope (AD-23, AD-15) | CLOSED | Cross-region copy cadence and CMK are stated, and the outbox purge job has an owner and schedule. The 24-hour cadence gives a region-loss RPO of about 24 hours plus copy lag (noted, not a clash). |
| H12 | Idempotency states (AD-16) | CLOSED | `IN_PROGRESS` and the 409 rule are gone and a missing header returns 400. The replay-after-abort gap is B3, not a state clash. |
| H13 | Day count and ledger link (AD-11, AD-13) | CLOSED | `end < start` returns 422 and every ledger entry carries `leave_request_id` (nullable for ADJUST). |
| H14 | Approval role and status precedence (AD-1, AD-9, AD-14) | OPEN | The approval facade checks the reporting line before `LeaveFacade.decide` checks the role, so an employee deciding a colleague's request gets 404 where AD-9 says 403. See B5. |
| H15 | CSRF bootstrap and cookie scope (AD-12) | OPEN | The bootstrap endpoint exists and cookies are host-only, but a host-only `XSRF-TOKEN` set by the API host cannot be read by script on the SPA's origin. See B6. |
| H16 | Modify across types and check ordering (AD-13, AD-14) | OPEN | The check now runs after the release, but the step order "RELEASE old, then lock target" conflicts with the ascending-order rule for two accounts. See the H16 detail below. |

Summary: 8 CLOSED (H1, H7, H8, H9, H10, H11, H12, H13), 8 OPEN (H2, H3, H4, H5, H6, H14, H15, H16).

### Top-5 holes (memlog line 52)

| Top hole | Status | Reason |
| --- | --- | --- |
| 1. `sid` claim and idle extension (AD-6, AD-7) | OPEN | `sid` is in the claim set, but no access-cookie path is stated and refresh has no way to locate the record. See B1. |
| 2. Grace reply returns access token only (AD-7) | OPEN | Correct for a second tab, but it breaks the stated "lost responses" case. See B1 and item 2. |
| 3. Identity in AD-4 Binds, REQUIRES_NEW for failed login (AD-4, AD-8) | CLOSED | Binds and the REQUIRES_NEW exception are both in the text. |
| 4. Global lock order leave then balance (AD-13, AD-14) | OPEN | The order cannot be realized with `@Version` (see H2 and B2). |
| 5. Idempotency rows COMPLETED, no IN_PROGRESS, missing header 400 (AD-16) | CLOSED as written; OPEN in combination | AD-16 is consistent on its own, but the replay path after a unique violation is not realizable inside one transaction, and the duplicate can reach a `@Version` conflict first (B3). |

### How the eight section (a) holes from round 2 map

| Round-2 hole | Now |
| --- | --- |
| 1. Balance account lock and availability | OPEN via H2, H3, H16 |
| 2. Session inactivity and refresh reuse | OPEN via H4, H5 |
| 3. Outbox contract vs FR54 and routing | OPEN via H6, residual routing in B5 |
| 4. Entry services, transactions, table ownership | CLOSED via H8, H9, H10 |
| 5. Operations envelope | CLOSED via H11 |
| 6. Idempotency scope and owner | CLOSED for scope; OPEN in combination (B3) |
| 7. Leave-day count vs ledger | CLOSED via H13 |
| 8. Leave status ownership | OPEN via H14 |

---

## Detailed clashes for the OPEN ranked holes

**H2 and the lock order.** Covered in B2 because it is the core of the lock-order question.

**H3, leave types created after users.** AD-13 says the balance module creates all rows when the user is created. Nothing creates rows when HR adds a new leave type to the catalogue (`leave_type` is owned by `leave`, and AD-13 never mentions it). An existing employee then has no row for the new type. Two concurrent first submits for that type both see no row, `SELECT ... FOR UPDATE` locks nothing, and both insert. This is the round-2 Clash B reappearing for a narrower case. Fix: AD-13 says a `leave_type` insert creates the `balance_account` rows for all existing users in the same transaction (REQUIRED, audited).

**H4, grace reply and lost responses.**
- Trace: the server rotates T1 to T2 and the response carrying T2 is lost. The browser still holds T1.
- The retry with T1 arrives within 30 seconds, gets an access token only, and leaves the cookie at T1.
- About 15 minutes later the access token expires and the SPA refreshes with T1. T1 is now outside the grace window and is the "previous" token, so the session is revoked.
- AD-7 states the grace rule "covers two tabs and lost responses". The lost-response case is not covered.
- Second clash: AD-7 stores the absolute expiry but never says when it is checked (see B1.4). A token older than the previous one is also not "previous", so its presentation is undefined, which weakens the replay rule ("replayed refresh tokens going unnoticed" is listed as a Prevents).
- Fix: on a grace hit return the current pair, with the current refresh token kept encrypted in Redis for the 30-second window, or state that a lost response forces re-login and remove the "lost responses" claim.

**H5, idle extension mechanism.** Covered in B1, item 1.

**H6, outbox claim and ordering.**
- Claim step (1) selects `PENDING` rows with `FOR UPDATE SKIP LOCKED`, marks them `CLAIMED` and commits. No step checks the predecessor.
- Timeline: P1 claims X (sequence 1) and is slow to publish. P2 claims Y (sequence 2), which is `PENDING` and has no predecessor check, publishes Y, and marks it `SENT`. P1 then publishes X. Successor before predecessor, which AD-15 forbids.
- `sequence = leave_request.version`, "assigned under the row lock": there is no row lock (AD-14 is `@Version`). `addComment` writes `leave_comment` and a `LeaveCommentAdded` outbox row but is not said to bump `leave_request.version`, so the sequence repeats.
- The predecessor lookup has no index. AD-24 lists `outbox_message (status, created_at)` but nothing on `(aggregate_id, sequence)`.
- Fix: the claim takes only rows whose predecessor is `SENT` (the lowest `PENDING` per aggregate), `addComment` bumps the aggregate version in the same transaction, and AD-24 adds `outbox_message (aggregate_id, sequence)`.

**H14, approval precedence.**
- AD-14: approval "checks the manager's reporting line through identity, then calls `LeaveFacade.decide`".
- AD-9: "role is checked before scope"; an employee calling decide returns 403 "on any request".
- An employee deciding on a colleague's request fails the reporting-line check inside approval and gets 404 before `LeaveFacade.decide` checks the role.
- Fix: the approval facade checks the caller's role (EMPLOYEE returns 403) before the reporting-line check.

**H15, CSRF readability.**
- AD-12: the SPA is on its own origin (`app.example.com`); the API is `api.example.com`; `XSRF-TOKEN` is set by the API and is not HttpOnly so the SPA can read it; all cookies are host-only.
- Cookies are visible to `document.cookie` only for the host that set them (or a parent `Domain`). A host-only cookie set by `api.example.com` is not visible to script on `app.example.com`.
- The double-submit header therefore cannot be built, and every state-changing request returns 403.
- Fix: return the CSRF token in the body of `GET /api/v1/auth/csrf` and keep it in SPA memory (synchronizer token pattern), dropping the `XSRF-TOKEN` cookie. The alternative is a `Domain=` parent cookie, but that contradicts the host-only rule and must be stated as an explicit exception.

**H16, modify across types.**
- Rule: "every operation that writes both: ... `balance_account` rows in ascending (employee id, leave type) order".
- Modify across types: "RELEASE on the old account; then lock the target account". The first lock taken is the old account, so the order is descending when the old type sorts after the target.
- Two concurrent modifies of the same employee, one from vacation to sick and one from sick to vacation, on different requests, can each lock their first account and wait on the other. The `leave_request` order does not serialize them because the requests differ.
- Fix: AD-13 says the modify locks both accounts in ascending order before any write, then releases old and holds new.

---

## (b) New contradictions and defects from the revision

### B1. Session chain: AD-6, AD-7, AD-12 (sid, cookie paths, refresh lookup)

This is the highest-severity group.

1. **Access cookie path is not stated.** AD-6 says the access token is "delivered in an HttpOnly, Secure cookie with SameSite=Lax (AD-12)". AD-12 gives no path. A `Set-Cookie` without `Path` on `POST /api/v1/auth/login` defaults to the login URL's directory, `/api/v1/auth`. The browser then never sends the access token to `/api/v1/leave/...` or `/api/v1/balance/...`, so every authenticated call returns 401. The round-2 fix ("cookie path `/api/`") was not applied. Fix: AD-6 states the access cookie is `Path=/api/`.
2. **Refresh cannot find the session record.** AD-7 stores the record "under the session id" and puts `sid` in the access token only. The refresh cookie is opaque and path-scoped to `/api/v1/auth`, and the refresh request carries no `sid`. The access token is usually expired at refresh, and AD-6 says the resource server validates `exp`, so refresh cannot read `sid` from it. Fix: put `sid` in the refresh cookie value (for example `sid.token`) or keep a Redis key `SHA-256(refresh token) -> sid`, and state which one refresh reads.
3. **Idle extension and the resource-server check.** AD-6 says the resource server "validates signature and `exp` on every request and never trusts client-supplied ... data", and nothing else. AD-7 now requires a Redis write on every authenticated request. Both can hold only if the extension is an `EXPIRE` that never creates a key. AD-7 does not say so. A `SET` would resurrect a logged-out session record, which AD-6 accepts only as a 15-minute access-token risk, not as an indefinitely extended session. Fix: AD-7 says the extension is `EXPIRE` on an existing key only.
4. **Absolute cap is stored but not enforced.** AD-7 stores "an absolute expiry of 8 hours from login" and never says where it is checked. The idle-TTL extension on every API call means an actively used session never reaches the 8-hour cap. Round-1 AD-7 (memlog line 22) had "refresh fails with 401 if past absolute cap"; the revision dropped it. Fix: AD-7 says refresh and every extension check the absolute expiry, or sets the Redis TTL to `min(idle, absolute remaining)`.
5. **Logout and the record.** Logout deletes the session record. A still-valid access token then reaches the resource server with a `sid` whose record is gone. The spine accepts this (AD-6 accepted risk) but does not say that requests with a missing record are still allowed. The access token outlives logout by up to 15 minutes; the resource server does not reject it on a missing record. This is consistent with the accepted risk but should be stated in AD-7, not left to inference.

### B2. Global lock order vs AD-14 `@Version` (and the AD-16 interaction)

1. **`@Version` takes no lock.** AD-13 says the first lock in every write path is "the `leave_request` row (AD-14)". AD-14 uses a `@Version` optimistic lock on `leave_request`. An optimistic lock is a conditional `UPDATE ... WHERE version = ?` at flush time, not a row lock taken in order. The spine has no locking read on `leave_request`, so the order cannot be realized as written.
2. **Two AD-compliant implementations diverge.**
   - Implementation X (modify): locks the balance row with `FOR UPDATE` in the method body, then flushes the `leave_request` UPDATE at commit. Order: balance, then request.
   - Implementation Y (modify): flushes the `leave_request` UPDATE first, for example through an explicit `saveAndFlush` or an auto-flush triggered by a query. Order: request, then balance.
   - A reject (RELEASE on balance) running concurrently with Y's modify can deadlock, and Postgres aborts one side. AD-13's "no two paths can deadlock" is then false.
3. **Optimistic-lock outcome is undefined.** A concurrent approve and modify on one request: the second updater gets `OptimisticLockException`. AD-14 does not map it to a status code. The round-2 H2 fix did not address this.
4. **Fix.** Replace the `@Version` on `leave_request` with `SELECT ... FOR UPDATE` on `leave_request` as the first lock in every write path, then `balance_account` rows in ascending order, and state that the first statement of each entry service is the `idempotency_key` insert (AD-16). Map any remaining conflict to 409.

### B3. AD-16 replay vs AD-14 and AD-4

1. **Replay after unique violation cannot happen in the same transaction.** AD-16 says a duplicate "fails the insert and reads the COMPLETED row". In PostgreSQL, after a unique violation the transaction is aborted and no further statement runs in it. AD-4 says the entry service opens one transaction. The replay read has to run in a new transaction after rollback. The spine does not say so.
2. **Duplicate can reach `@Version` first.** If the `leave_request` UPDATE is flushed before the `idempotency_key` INSERT, a concurrent duplicate blocks on the row lock, then fails the version check after the first commits. It gets an optimistic-lock error, not the stored response. Whether this happens depends on flush order, which the spine does not fix. The fix is the one in B2.4.

### B4. Outbox CLAIMED lease vs the three-step publish

- Covered in H6. Additional points:
  - The lease is 60 seconds with no renewal. SQS SDK retries can exceed 60 seconds. A second task then reclaims a row still being published. Duplicates are covered by `eventId` dedupe, but the 10-attempt counter can overcount. Fix: AD-15 says a publish that exceeds the lease renews it, or sets the lease to a multiple of the SDK timeout.
  - The predecessor-`SENT` check is absent from all three steps (see H6).

### B5. REQUIRES_NEW exceptions vs AD-4 MANDATORY

1. **Propagation is per method, but the audit facade is one entry point.** AD-4 says `audit_event` writes are MANDATORY, and three paths are REQUIRES_NEW (failed-login audit, denial audit, read audit). AD-5 says only the audit facade inserts audit rows. Propagation is an attribute of one method, so the spine must name two audit methods, for example `record` (MANDATORY) and `recordIsolated` (REQUIRES_NEW). It does not.
2. **Denials run inside the entry transaction.** AD-9 role and scope checks are in the facade. AD-4 says the entry service opens the transaction before facades run. A denial therefore happens inside an open transaction and its REQUIRES_NEW audit needs a second connection. With 10 connections per task (AD-24) and at least 10 concurrent denials, every connection is held by an outer transaction waiting for a second one, and the task stalls. Fix: authorization checks run before the entry transaction opens, or denial audit uses a separate non-pooled path.
3. **AD-4 does not say a REQUIRES_NEW audit row is outside the state change.** The denial audit commits even when the outer transaction rolls back, which is the intent. The rule should say so, because the MANDATORY rule's wording ("can never commit alone") is violated on purpose.
4. **Routing and AD-9.** AD-15 stores the manager's email and name on each outbox row, which is retained for 3 years (FR53). AD-9 says other modules "never store a copy" of the reporting line. AD-15 calls the copy "message payload, not a copy", which is an assertion, not a distinction the AD-9 text supports. The stored address also stays stale after a reporting-line change. Fix: AD-9 says the snapshot in outbox payload is a permitted copy for message delivery and history only, or the worker resolves the recipient through identity at send time.

### B6. CSRF and cookies (AD-12)

- Covered in H15. Additional: the SameSite rationale ("Lax keeps cookies on top-level navigations from notification email links") is not the reason the email link works. The SPA is same-site, and a deep link needs no cookie to load the static SPA page. The rationale should be stated accurately, but either setting works.

### B7. Other new defects

1. **Forced password change cannot be enforced.** AD-8 says the user "must change it at first login". The JWT claims (AD-6) are `sub`, `sid`, `role`, `iat`, `exp`. No claim carries "password change required", and the resource server checks nothing else. A temp-password user can call any API. The change-password endpoint is also missing from the capability map. Fix: AD-6 adds a `pwd_change_required` claim, and the resource server rejects every endpoint except the change-password endpoint when it is set.
2. **Approval "alarm" not in AD-20.** AD-15 says a no-manager submit "fires an alarm metric (AD-20)". AD-20 lists no such alarm. Fix: add it to AD-20's alarm list.
3. **Invalid transition status code and retry safety.** AD-14 says "any other transition throws" but gives no status code. Cancel and `addComment` have no `Idempotency-Key`, so a retry after a lost response cancels twice or posts two comments. Fix: AD-14 maps invalid transitions to 409 and AD-16 covers cancel and `addComment`, or states that they are naturally idempotent.
4. **Leave type creation does not provision balances.** Covered in H3.
5. **Locked-account login response.** AD-8 says login is identical for unknown user and wrong password. It does not say what a locked account returns (status code and whether it reveals the lock).
6. **ER diagram drift.** The core entity diagram has no `LEAVE_REQUEST ||--o{ BALANCE_LEDGER` link (added in round 2) and no `LEAVE_TYPE` entity. Documentation only.
7. **Capability-map rows that omit the AD they depend on.** See section (c).

---

## (c) Capability map rows vs the ADs they cite

| Row | Cites | Gap |
| --- | --- | --- |
| Submit leave request (FR1-FR3, FR9) | AD-4, AD-11, AD-13, AD-16 | Missing AD-9 (self scope), AD-14 (PENDING creation), AD-15 (outbox row written). |
| Modify or cancel (FR7, FR8) | AD-4, AD-11, AD-13, AD-14, AD-15 | Missing AD-9 (self scope). Two entry services in one row is acceptable but the cancel retry issue is open (B7.3). |
| Status, history, leave history (FR4, FR5, FR45) | AD-9, AD-10 | Missing AD-5 (read of employee data writes `audit_access_log`) and AD-4 (read-only transaction). |
| Balance view and HR adjustment (FR6, FR27-FR31) | AD-2, AD-5, AD-9, AD-13 | Missing AD-4 (`adjust` and `view` are each one entry service). The leave type catalogue maintenance path has no row. |
| Manager pending list (FR10, FR11, FR14) | AD-9, AD-24 | Missing AD-5 (manager reads employee leave data) and AD-10 (paged list shape). |
| Approve or reject, comment (FR12, FR13, FR15-FR17) | AD-4, AD-9, AD-13, AD-14, AD-16 | Missing AD-15 (decision outbox rows), AD-5 (audit), and the comment entry service `LeaveFacade.addComment` is not named as a row at all (AD-4 requires every operation to name one). |
| Accounts, roles, temp passwords, reset (FR18-FR20) | AD-2, AD-5, AD-8, AD-9, AD-13 | Missing AD-4 (`createUser` provisions balance rows in one transaction). The forced-change endpoint is not named (B7.1). |
| Login, sessions, lockout (FR56-FR58) | AD-6, AD-7, AD-8, AD-12 | Missing AD-4 (REQUIRES_NEW exception), AD-5 (login and logout audit), and the `GET /api/v1/auth/csrf` bootstrap has no row. |
| Authorization and data-access logs (FR22-FR26, FR59) | AD-5, AD-9 | Consistent. |
| Audit trail (FR42-FR44) | AD-5 | Consistent. |
| Email notifications and history (FR50-FR54) | AD-15 | Missing AD-9 for `OutboxFacade.historyFor` (who may read a recipient's history is not stated). |
| HTTPS, backups, retention, encryption (FR46, FR47, FR55, FR60, FR61) | AD-21, AD-23 | Consistent with round-2 fix. Missing AD-19 for secrets only if FR60 covers them. |
| Performance targets (NFR) | AD-10, AD-17, AD-24 | Consistent. AD-24 needs the predecessor index (H6). |

Also: the "Lives in" column for the decide row lists `approval (policy)` but the entry-service column names `LeaveFacade.decide`, which runs after the policy check outside the transaction. That is the H14 clash, not a column error.

---

## Remaining holes (ranked), with clash and one-line fix

- **R1. Session identity chain (AD-6, AD-7, AD-12). Critical.** Clash: the access cookie defaults to `/api/v1/auth`, so no leave or balance call carries the token; refresh cannot find the session record because the refresh cookie has no `sid`. Fix: AD-6 sets the access cookie to `Path=/api/`, and AD-7 puts `sid` (or a token-hash index) where refresh reads it.
- **R2. Session grace and absolute cap (AD-7). High.** Clash: a lost response leaves the browser on a stale token that revokes the session about 15 minutes later; the 8-hour absolute cap is stored but never checked (B1.4). Fix: a grace hit returns the current pair from an encrypted Redis value for 30 seconds, and refresh plus every extension check the absolute expiry.
- **R3. Lock order vs `@Version` (AD-13, AD-14, AD-16). High.** Clash: `@Version` takes no lock, so modify (balance first) and reject (request first) can deadlock, and a duplicate can hit an optimistic-lock conflict before the replay. Fix: AD-14 replaces `@Version` on `leave_request` with `SELECT ... FOR UPDATE` as the first lock, and AD-16 makes the key insert the first statement, with replay in a new transaction.
- **R4. Outbox ordering (AD-15, AD-24). High.** Clash: the claim takes `PENDING` rows with no predecessor check, so a successor publishes before a `CLAIMED` predecessor; `addComment` does not bump the sequence. Fix: AD-15 claims only the head `PENDING` row per aggregate, bumps the aggregate version on every outbox-producing operation, and AD-24 indexes `(aggregate_id, sequence)`.
- **R5. CSRF cookie readability (AD-12). High.** Clash: a host-only `XSRF-TOKEN` set by `api.example.com` is not readable by script on `app.example.com`, so every state-changing request returns 403. Fix: AD-12 returns the CSRF token in the body of `GET /api/v1/auth/csrf` and drops the cookie (synchronizer token).

Also open and not in the top five: H14 (approval checks reporting line before role, so 404 where 403 is required; fix: role check first in the approval facade), H3 (leave-type creation does not provision balance rows; fix: `leave_type` insert provisions rows for existing users), H16 (modify across types locks old before target; fix: lock both accounts in ascending order before any write), and B5.2 (denial audit inside a pooled transaction can stall the connection pool; fix: check authorization before the entry transaction opens).

---

## Checked and not a clash

- AD-1, AD-3, AD-10, AD-18, AD-19, AD-20 and AD-21 remain consistent with each other and with the Design Paradigm table.
- AD-5 append-only triggers cover `audit_event`, `audit_access_log` and `balance_ledger`; the outbox purge (AD-15) and idempotency cleanup (AD-16) are the only deletes on other tables and each has an owner.
- AD-11 day arithmetic: `end < start` rejected with 422 closes the negative-count case.
- AD-13 ledger: `ADJUST` is locked and cannot make the balance negative, so the HR-adjust race (round-2 Clash A) is closed.
- AD-9 status-code text (401, then 403 role, then 404 scope) is consistent in the text; the clash is only the approval facade ordering (H14).
- Retention values: backups 30 days (PRD line 508) and audit 3 years (PRD line 503) match the PRD.
- Memlog line 47 mentions `DEBIT` entries and a `HOLD/RELEASE/DEBIT/ADJUST` set, which the spine no longer uses (it uses HOLD, RELEASE, ADJUST). The memlog is stale on this point; the spine is the authority.
- Open question on the leave-type catalogue owner is still open in the spine even though H10 settled the table owner (`leave`). The question now concerns who may write the catalogue (which role and endpoint); it is not an ownership clash.
- Phase-label open question (AD-5 before and after JSON in MVP vs FR49 Phase 2) carries over unchanged.
