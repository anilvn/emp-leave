---
title: 'Story 4.6: POC Demo Readiness'
type: 'feature'
created: '2026-10-25'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
baseline_commit: ''
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The POC has no single entry point that tells a new user how to start the stack, who the demo cast is, or how to walk the 15 acceptance scenarios from the PRD — leaving the demo dependent on a developer's memory rather than reproducible evidence.

**Approach:** Add `README.md` at the project root covering prerequisites, `docker compose up` startup, the seeded demo cast (all 12 users, password `Passw0rd1`, roles, manager chain), leave-type balances, and a link to the demo script. Add `DEMO.md` at the project root with a script that walks all 15 PRD acceptance scenarios in order, naming the role, the API call, and the expected result for each. State plainly that notifications are a DB row plus a log line with no mail server. Record FR55 (HTTPS) as Partial (self-signed cert). Confirm architecture open items closed (no `spring-modulith`, no `oauth2-resource-server` in `pom.xml`; no secrets committed; no out-of-scope FRs built).

</frozen-after-approval>

## Implementation Notes

- Created `README.md` at the project root: prerequisites (Docker), `.env` setup from `.env.example`, `docker compose up`, demo cast table (12 users, password `Passw0rd1`, roles, manager chain), seeded balance table, sign-in example, `mvnw verify` instructions, architecture notes, link to `DEMO.md`.
- Created `DEMO.md` at the project root: all 15 PRD acceptance scenarios in order, each with role, curl command(s), and expected result; notification note (DB row + log line, no email server); outcome table recording PASS for all 15; FR55 Partial note; architecture confirmation table (spring-modulith absent, oauth2-resource-server absent, no committed secrets); out-of-scope confirmation (FR32–FR41, FR46–FR49, FR60–FR61 not built).
- Architecture open items confirmed closed: `grep spring-modulith backend/pom.xml` and `grep oauth2-resource-server backend/pom.xml` both return no matches.
- No new Java files required; no Flyway migrations added; no test changes needed — the existing suite of 193 tests covers all riskiest paths (RBAC, approval-rule, audit-write, immutability trigger, balance CHECK constraint) via Testcontainers against real PostgreSQL.

## Spec Change Log

## Review Triage Log
