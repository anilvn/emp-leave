---
id: SPEC-emp-leave-tracker
companions:
  - acceptance-scenarios.md
  - fr-coverage.md
  - data-model.md
  - delivery-plan.md
  - ../../planning-artifacts/architecture/architecture-company-leave-2026-10-22/ARCHITECTURE-SPINE.md
  - ../../planning-artifacts/architecture/architecture-frontend-company-leave-2026-10-23/ARCHITECTURE-SPINE.md
  - ../../../backend/src/main/resources/openapi.yaml
sources:
  - ../../planning-artifacts/prds/poc-prd-emp-leave-tracker-2026-10-22/prd.md
---

> **Canonical contract.** This SPEC and the files in `companions:` are the complete, preservation-validated contract for what to build, test, and validate. Source documents listed in frontmatter are for traceability — consult them only if you need narrative rationale or prose color this contract intentionally omits.

# Employee Leave Tracking System — POC

## Why

**A pain to solve, plus a mandate to meet.** Employees chase their managers to find out what happened to a leave request, managers decide slowly without context, and HR has no complete record of who did what. The POC must prove the fix end to end (submit → approve/reject → track → audit) as a working demo for the manager and client with three roles: Employee, Manager and HR/Admin. It covers all 45 in-scope FRs and validates the data model, role-based access and audit design, built by 1–2 developers in about 4 weeks.

## Capabilities

- **CAP-1**
  - **intent:** An employee submits a leave request (leave type, start and end date, optional notes). It is counted in working days and reserved against their balance.
  - **success:** Scenario 1: Thu–Tue counts as 4 working days, status PENDING, 4 days reserved, History shows the submission. Scenario 10: a request exceeding available days is blocked with a clear message.
- **CAP-2**
  - **intent:** An employee sees the status and history of their own requests, and their balance per leave type.
  - **success:** My Requests lists own requests with PENDING/APPROVED/REJECTED/CANCELLED, filterable. Balance cards show total, reserved and available days that match the ledger after submit, approve and cancel.
- **CAP-3**
  - **intent:** The owner of a request can modify or cancel it only while it is PENDING; the request is never deleted.
  - **success:** Scenario 2: the reservation updates on edit and is released on cancel, both appear in History, and the cancelled request stays visible. Changing the leave type on edit is refused.
- **CAP-4**
  - **intent:** An approver sees every request awaiting their decision, with the context needed to decide (employee, dates, type, current balance, History, comments).
  - **success:** Pending Approvals shows exactly the caller's decidable PENDING requests (a manager's direct reports; for HR, top-level managers' and their own), each with full context and no extra navigation.
- **CAP-5**
  - **intent:** An approver approves a pending request (comment optional) or rejects it (comment required), recording the decision, time and approver.
  - **success:** Scenario 3: approval moves reserved days to deducted, notifies the employee and appears in History. Scenario 4: rejection is blocked without a comment, then releases reserved days and notifies the employee with the reason.
- **CAP-6**
  - **intent:** Each request routes to exactly one approver, the requester's manager, and nobody else may decide it.
  - **success:** Scenario 6: a team manager's request goes to the top-level manager. Scenario 7: the top-level manager's goes to HR. An HR user approves their own request, audited as self-approval. Scenario 8: another team's manager gets 403.
- **CAP-7**
  - **intent:** The owner, their manager and HR discuss a request in a comment thread and see its full History timeline in order.
  - **success:** Scenario 5: comments appear after a page refresh with no alert, and date changes, comments and the decision appear in History in order.
- **CAP-8**
  - **intent:** A manager sees their team's approved leave over a date range.
  - **success:** The Team view lists only direct reports' APPROVED leave in the chosen range, each entry with its comments and History.
- **CAP-9**
  - **intent:** HR creates user accounts with a role and a manager, and resets passwords; a user holding a temporary password must change it before doing anything else.
  - **success:** Scenario 11: HR creates a user, assigns a manager and resets the password; the user logs in and is forced to change the temporary password.
- **CAP-10**
  - **intent:** Access is scoped by role: employees see only their own data, managers their direct reports', HR everything. Unauthorized attempts are refused and logged.
  - **success:** Scenarios 8 and 9: the API returns 403 and an access-log row is written. The UI hides features the role cannot use.
- **CAP-11**
  - **intent:** HR views all balances, sets or adjusts them with a required reason, and reads the ledger behind each balance.
  - **success:** Scenario 12: the ledger shows amount, reason, actor and time for set and adjust, and an adjustment below zero is refused.
- **CAP-12**
  - **intent:** Every transaction leaves an immutable audit record of action, actor and time, and HR can review the full trail per request.
  - **success:** Scenario 13: an update or delete on an audit row fails. HR's audit view lists every request in any status, with its comments and History nested.
- **CAP-13**
  - **intent:** Submit, approve and reject each produce a templated notification (type, dates, days, status, link) to the right person, and every user can see their notification history.
  - **success:** Scenarios 1, 3 and 4 each produce one notification row and log line for the correct recipient, and the Notifications page lists the user's rows.
- **CAP-14**
  - **intent:** Users sign in with a username and password over HTTPS, are protected by lockout and an idle timeout, and stay signed in across page refreshes while the session is valid.
  - **success:** Scenario 14: 5 wrong passwords lock the account for 15 minutes. Scenario 15: after 30 idle minutes the next action asks for login. A refresh or deep link with a valid session keeps the user on that page.

## Constraints

- **Stack:** Angular 22 + Tailwind CSS 4 front end; Java 21 Spring Boot back end (`pom.xml` pins 4.1.1); PostgreSQL 18; Flyway. The whole stack starts with one `docker compose up`.
- **Working days:** leave days are Monday–Friday between start and end inclusive, public holidays ignored, whole days only, computed server-side.
- **Balance lifecycle:** submit reserves, edit re-reserves, approve deducts, reject or cancel releases. Each step writes a ledger row in the same transaction. Available days (total − reserved) never go below zero, including HR adjustments.
- **Audit:** the audit and access-log tables are insert-only, enforced by a database trigger. Audit rows are written in the service layer inside the triggering transaction.
- **Access control:** RBAC is enforced server-side on every API endpoint; hiding things in the UI is convenience only.
- **Notifications:** all go through `sendNotification()`, which writes a DB row and a log line and never calls a mail server.
- **Comments:** they trigger no email or in-app alert; new comments appear on refresh.
- **Security:** BCrypt password hashing; lockout after 5 failures for 15 minutes; 30-minute idle timeout; HttpOnly/Secure/SameSite cookies; HTTPS through a local reverse proxy with a self-signed certificate; passwords at least 8 characters with letters and numbers.
- **Configuration:** environment variables only; no hard-coded secrets.
- **Migrations:** schema via Flyway from day one. Seed data lives in separate versioned migrations, added incrementally as each epic's entities land.
- **Identity display:** people are shown by username plus a readable role label. There are no full-name or job-title fields.
- **Organization:** single jurisdiction, English only, one manager per user. Top-level managers report to an HR user.
- **UI:** responsive and desktop-first for Chrome and Edge, with semantic HTML, labelled fields and keyboard navigation on main flows.
- **Tests:** unit and API tests (JUnit 5, Testcontainers Postgres) for RBAC, approval rules and audit writes; Playwright for 2–3 Angular flows.
- **Demo data:** 1 HR admin, 1 top-level manager, 2 team managers, 8 employees.

## Non-goals

- Phase 2 FRs FR32–FR41, FR48 and FR49: balance history view, carryover, reports, exports, audit export, and before/after values on audit entries.
- Backups and retention (FR46, FR47, FR60, FR61).
- Real email delivery (SMTP or a mail catcher).
- A read API or UI for the access log.
- Public-holiday calendars, half days, and multi-level approval chains.
- Real-time push of comments or list updates.
- Production TLS certificates and multi-environment deployment (FR55 is Partial).

## Success signal

- All 15 acceptance scenarios pass on the seeded demo data from a single `docker compose up`, and are demonstrated live to the manager and client, switching between Employee, Manager and HR users.

## Assumptions

- The Spring Boot version is 4.1.1 as pinned in `pom.xml`; the PRD's "Spring Boot 3" is stale.
- `openapi.yaml` is the authoritative API surface. It supersedes the PRD's summary endpoint table, which lacks `GET /auth/me` and `GET /leave-types`.
- The error codes are those in `openapi.yaml` (`ACCESS_DENIED`, `INSUFFICIENT_BALANCE`, …). The backend spine's AD-11 example names (`RBAC_DENIED`, `BALANCE_INSUFFICIENT`) are stale.
