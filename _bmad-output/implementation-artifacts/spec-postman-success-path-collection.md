---
title: 'Generate Postman Success-Path Collection'
type: 'feature'
created: '2026-10-26'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The success-path walkthrough is not directly executable as an importable Postman collection, and the current uncommitted base URL edit is malformed.

**Approach:** Correct the guide to use the documented HTTPS proxy URL and produce an importable Postman collection plus local environment that run the guide's persona flow, preserve cookies, capture dynamic IDs, and assert successful outcomes across all implemented API routes.

</frozen-after-approval>

## Implementation Notes

- Preserved the existing uncommitted workspace files and corrected the malformed base URL to the documented HTTPS proxy URL, required for the Secure auth cookie.
- Added a workspace-native collection with 95 ordered requests and an environment, plus standard Postman v2.1 collection/environment JSON exports. The flow uses generated unique usernames and future weekdays, captures IDs, switches persona cookies through login, and asserts successful results for all 26 implemented API routes.
- Tightened successful response checks for user/request IDs, request ownership/approver/date/type, notifications, balance release/deduction, ledger correlation/order, and logout cookie clearing.
- Validation: parsed the collection/environment JSON and workspace YAML; parsed all embedded JavaScript; verified valid request-body JSON, no Authorization headers, and complete 26-route coverage. The collection was not run against a live API/database because it creates accounts and mutates balances and requests.

## Review Triage Log

- Patched — The guide implied users must supply `runId` and test dates even though collection startup overwrites them; it now documents them as generated.
- Patched — HR login credentials and the user-list assertion were hard-coded in the workspace collection; they now use environment variables.
- Patched — User creation stored IDs without asserting they were returned; every create request now validates its ID before saving it.
- Patched — Request creation did not assert employee ownership; all request submissions now check the expected employee ID.
- Patched — Request creation did not assert the assigned approver; employee requests now assert PD and PD's request asserts Sunil.
- Patched — Request creation did not assert leave type and dates; each request now checks its selected type and generated date range.
- Patched — The final balance check omitted the rejected employee's released days; HR's all-balances check now verifies Jay's reserved and available days.
- Patched — PD's submission notification assertions omitted PG; they now verify submissions from all four employees and PD's approval notification.
- Patched — Anil's ledger checks did not correlate request events or ordering; it now checks request-linked reserve/deduct rows and chronological order.
- Patched — Logout only checked for a cookie header; it now checks the cookie is empty with `Max-Age=0`.
