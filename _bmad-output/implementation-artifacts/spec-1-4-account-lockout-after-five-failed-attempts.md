---
title: 'Story 1.4: Account Lockout After Five Failed Attempts'
type: 'feature'
created: '2026-10-23'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Repeated password guesses can currently be made against an account without limit, even though the `users` table already has fields for failure counts and lock expiry.

**Approach:** Lock an account for 15 minutes after five consecutive wrong passwords. Return `423 ACCOUNT_LOCKED` on the fifth attempt and every attempt during the lock window, including correct-password attempts; create no session while locked. Reset failures after a successful login or expired lock, and revoke all existing sessions when a new lockout starts. Preserve the existing generic `401 INVALID_CREDENTIALS` response for unknown usernames and non-locking failures. Follow the Lombok-based entity patterns established by Story 1.3 without adding broad setters or unrelated boilerplate.

</frozen-after-approval>

## Implementation Notes

- Reused the existing `users.failed_logins` and `users.locked_until` columns; no migration or API contract change was needed.
- Kept lockout state changes inside the login transaction and serialized lookups by username, so concurrent failed attempts cannot lose counter increments or create a session after a lockout wins the row lock.
- Added narrow `User` state methods rather than Lombok setters, consistent with the existing entity pattern.
- Counted wrong-password attempts for inactive accounts while preserving their generic invalid-credentials response; matched the lockout detail to the existing OpenAPI example.
- Added PostgreSQL-backed integration coverage for the fifth failure, inactive accounts, concurrent failures and a login/lockout race, locked correct/incorrect attempts, expiry stability, existing-session revocation, successful-login reset, and expired-lock recovery.
- Verified with `backend\mvnw.cmd -f backend\pom.xml test`: 22 tests passed, with no failures or errors.

## Review Triage Log

| Verdict | Finding and evidence |
|---|---|
| medium (patch) | Inactive accounts initially returned before counting wrong passwords; counter updates now occur before active-account rejection, covered by `failedPasswordsForInactiveAccountStillTriggerLockout`. |
| low (patch) | The lockout detail differed from the existing OpenAPI example; the response now uses the documented wording, verified by the integration assertion. |
| medium (patch) | Concurrent counter and session behavior lacked coverage; added parallel failed-login and successful-login-versus-fifth-failure integration tests that assert consistent lock/session outcomes. |
| low (patch) | Locked attempts lacked an expiry-stability assertion; the integration test now verifies `locked_until` remains unchanged for correct- and wrong-password attempts during lockout. |
