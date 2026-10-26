---
title: 'Postman Persona API Flows'
type: 'feature'
created: '2026-10-26'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The deployed API definition does not provide an easy, ordered multi-persona Postman walkthrough for exercising the seeded application flows.

**Approach:** Add a separate importable Postman collection and local environment for HR, manager, and employee journeys, with numbered requests, clear rationale, and scripts that save and retrieve cookies, request IDs, and user IDs through environment variables.

</frozen-after-approval>

## Implementation Notes

- Added `postman/collections/company-leave-persona-flows.postman_collection.json`, a 22-request Postman v2.1 flow using seeded HR, manager, and employee accounts for approve, cancel, reject, notification, balance, ledger, and audit scenarios.
- Added `postman/environments/company-leave-local.postman_environment.json` with the direct backend URL, seeded demo credentials, configurable leave type, and environment variables for cookies, user IDs, request IDs, and dynamically generated dates.
- Request scripts save and retrieve environment values with `pm.environment`; login failures or unexpected roles stop the collection run, and each create request clears its old ID before sending.
- The collection description warns that the flows mutate local data. The environment documents the Docker Compose base URL and self-signed certificate setting as an alternative to the direct backend URL.
- Validation: parsed both JSON files, checked all 22 request numbers and environment references, parsed all 30 embedded scripts, and validated request-body JSON.

## Review Triage Log

- low — Patched: the environment now explains the README's Docker Compose URL and the self-signed certificate setting while retaining the direct server URL specified by `backend/deployed.yaml`.
- low — Patched: login scripts stop the collection on an unsuccessful response, role mismatch, or missing access-token cookie instead of continuing with missing credentials.
- low — Patched: the initial employee balance check asserts the seeded Vacation entitlement and available days.
- low — Patched: the manager dashboard checks exact request IDs in the returned approval objects.
- low — Patched: the cancellation flow verifies the Vacation reservation is released.
- low — Patched: employee notification checks require the matching request ID and decision kind.
- low — Patched: request history checks require the expected approval or rejection event.
- low — Patched: HR's all-balances check asserts JSmith's post-approval balance.
- low — Patched: the ledger check requires the matching one-day DEDUCT entry.
- low — Patched: the audit checks compare exact request IDs, final status, and lifecycle history.
