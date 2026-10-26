---
title: 'Rewrite Postman API Success-Path Guide'
type: 'chore'
created: '2026-10-26'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `postman-req.md` describes API behavior and a test sequence that do not fully match the current implementation, so testers cannot reliably execute the documented flow or verify successful end-to-end behavior.

**Approach:** Rewrite the guide as an ordered, success-focused Postman walkthrough that uses the requested HR → Sunil → PD → employee hierarchy, provides concrete calls and payloads for every implemented API route, and states observable success checks. Correct cookie authentication, temporary-password changes, balance setup, direct-manager approval, supported notifications, and the undocumented `/auth/me` discrepancy.

</frozen-after-approval>

## Implementation Notes

- Rewrote `postman-req.md` as an ordered, success-focused guide for all 26 implemented routes.
- Documented the actual Secure cookie login, temporary-password change/reset flows, dynamically captured user/request IDs, direct-manager decisions, balance provisioning, and supported notification events.
- Added concrete Postman request payloads and expected success checks, and noted that `/auth/me` is documented but not implemented.
- Verification: manually matched the guide's 26-route checklist against controller mappings; `git diff --check` passed. No automated tests were needed for this documentation-only change.
