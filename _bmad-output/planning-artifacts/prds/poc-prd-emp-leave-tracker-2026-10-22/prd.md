# Leave Tracking System – POC PRD

Oct 22, 2026 · @Anil

## Purpose

The POC demonstrates the in-scope functional requirements of the Employee Leave Tracking System end to end: submit → approve/reject → track → audit. It runs locally or on a single dev server.

**POC goals**

- Prove the core value: employees see request status without chasing, managers decide in a few clicks, HR sees a complete audit trail.
- Cover all 45 in-scope functional requirements.
- Validate the data model, role-based access and audit design.
- Give the manager and client a working demo with three roles: Employee, Manager, HR/Admin.

**Source:** Employee Leave Tracking System PRD by Anil, dated 2026-10-15. FR numbers below match that document.

**Suggested size:** 1–2 developers, about 4 weeks.

## Scope

| Area | In POC |
| --- | --- |
| Leave requests | Submit, modify while pending, cancel, status, history, balances (FR1–FR9) |
| Approvals | Dashboard, context, approve/reject, comments, team scope; managers submit leave to their own manager (FR10–FR17) |
| Users and access | Account creation, roles, manager link, password reset, RBAC, access logging (FR18–FR26) |
| Balances | Set, adjust, ledger, reservation while pending, no negative balance (FR27–FR31) |
| Audit | Immutable audit log, History tab (FR42–FR45) |
| Notifications | Submit/approve/reject events, email content (template) and notification history (FR50–FR54), routed through a `sendNotification()` method that writes the `notifications` row to the DB and prints a log line — it does not call a mail server. Real SMTP dispatch to a local mail catcher is a later integration, not a POC gap |
| Security | HTTPS via local proxy, password hashing, lockout after 5 tries, 30-min session timeout, access logs (FR55–FR59) |

## Functional requirements

The POC covers 45 FRs: FR1–FR31, FR42–FR45, FR50–FR59. "Coverage" marks whether an FR is fully built (Full), built minus one documented gap (Partial), or acknowledged but not built (Deferred).

| FR | Requirement (short) | Coverage | How the POC meets it |
| --- | --- | --- | --- |
| FR1 | Submit request with leave type | Full | Form with Vacation, Sick, Parental, Sabbatical; whole days only |
| FR2 | Start and end dates | Full | Date pickers; end ≥ start; days counted as working days (Mon–Fri) |
| FR3 | Optional notes | Full | Free-text field |
| FR4 | View request status | Full | My Requests list with Pending/Approved/Rejected/Cancelled |
| FR5 | Leave history | Full | Filterable list of own requests |
| FR6 | Balance by type | Full | Cards show total, reserved (pending) and available days |
| FR7 | Modify pending request | Full | Edit allowed only while Pending; reservation recalculated; change logged |
| FR8 | Cancel pending request | Full | Status Cancelled, reserved days released, never deleted |
| FR9 | Record submitter and time | Full | Stored on request and audit event |
| FR10 | Manager pending dashboard | Full | Pending Approvals page for direct reports |
| FR11 | Rich request context | Full | Detail panel: employee, dates, type, balance, recent history |
| FR12 | Approve with optional comment | Full | Approve converts reserved days to deducted days |
| FR13 | Reject with required comment | Full | Reject disabled until comment entered; reserved days released |
| FR14 | Team leave history | Full | Team view of approved leave by date |
| FR15 | Discussion comments | Full | Comment thread on each request. No messaging or notifications for comments: users refresh the page to see new comments |
| FR16 | Record decision, time, approver | Full | Stored on request and audit event |
| FR17 | Block decisions outside team | Full | Only the requester's own manager (manager\_id) can decide; HR users decide their own requests |
| FR18 | Create user accounts | Full | HR admin page |
| FR19 | Assign roles | Full | Role and manager chosen at creation; top-level managers report to an HR user |
| FR20 | Reset passwords | Full | HR sets temporary password; user must change on login |
| FR21 | RBAC, three roles | Full | Spring Security role checks on every endpoint |
| FR22 | Employees see own data only | Full | Queries scoped by user id |
| FR23 | Managers see direct reports only | Full | Queries scoped by manager\_id |
| FR24 | HR sees all | Full | No scope filter for HR role |
| FR25 | Block unauthorized access | Full | 403 and an access-denied log entry |
| FR26 | Log sensitive data access | Full | Access log table written by a request filter; no read API or UI in the POC (see note under Main API endpoints) |
| FR27 | HR views all balances | Full | Balances page |
| FR28 | HR sets balances | Full | Set action, reason required |
| FR29 | HR adjusts balances | Full | +/- adjustment, reason required |
| FR30 | Record adjustments | Full | Balance ledger: time, amount, reason, actor |
| FR31 | No negative balance | Full | Always blocked: request days must be ≤ available (total − reserved); HR adjustments also cannot go below zero |
| FR42 | Audit trail of transactions | Full | Audit event per action |
| FR43 | Time and user on every action | Full | Required columns on audit table |
| FR44 | Immutable audit records | Full | Insert-only table; DB trigger blocks update/delete |
| FR45 | History tab per request | Full | Timeline on request detail (submit, edits, comments, decision) |
| FR50 | Email on approval | Full | Approval triggers `sendNotification()`, which builds the subject/body from the email template, writes a `notifications` row to the DB, and prints a log line instead of calling a mail server. Real dispatch to a local mail catcher is a later integration |
| FR51 | Email on rejection | Full | Same as FR50, triggered on reject; `sendNotification()` writes the DB row and prints a log line, no mail server call yet |
| FR52 | Email to manager on new request | Full | Same as FR50, triggered on submit; `sendNotification()` writes the DB row and prints a log line, no mail server call yet |
| FR53 | Notification history | Full | In-app list reads the `notifications` rows that `sendNotification()` writes to the DB — independent of whether a mail server was ever called |
| FR54 | Request details in emails | Full | Email template (type, dates, days, status, link) is built and used to populate the logged notification; kept ready for when real sending is added |
| FR55 | HTTPS | Partial | HTTPS via local reverse proxy with a self-signed certificate; real certificates are a production concern, out of POC scope |
| FR56 | Username/password login | Full | BCrypt-hashed passwords |
| FR57 | Lockout after 5 failures | Full | 15-minute lockout |
| FR58 | Session timeout | Full | 30-minute inactivity timeout |
| FR59 | Access logs | Full | Same as FR26 |

**Out of scope (Phase 2):** FR32–FR41, FR48, FR49 — balance history view, carryover, reports, exports, audit export, before/after values on audit entries. Not built in the POC. The `audit_events.details_json` column already has room to carry before/after values when this is picked up.

**Not planned for this POC:** FR46, FR47, FR60, FR61 — daily backup script, 3-year audit-log retention documentation, automated daily backups, and backup/recovery (restore) testing. Not built, and out of scope for architecture and implementation on this POC. Revisit if/when this moves toward a real deployment.

## Technical requirements

- Relational, ACID database: PostgreSQL.
- REST API with role checks on every endpoint; the UI hides features the role can't use.
- Insert-only audit and access-log tables, protected by a database trigger.
- Password hashing, lockout after 5 failures for 15 minutes, 30-minute session timeout, HttpOnly/Secure/SameSite cookies.
- Password rule: minimum 8 characters, letters and numbers.
- Responsive layout for Chrome and Edge, desktop-first.
- Basic accessibility: semantic HTML, labelled form fields, keyboard navigation on main flows.
- Database migrations from day one (Flyway). Schema and seed data are separate, versioned migrations; seed data is loaded in multiple incremental steps as each epic's entities land, not all at once in the first story.
- Configuration through environment variables, no hard-coded secrets.
- Unit and API tests for RBAC, approval rules and audit writes.

## Architecture and data model

A three-tier app: Angular front end, Java Spring Boot back end, PostgreSQL. A local mail catcher (Mailpit) is planned for when real email sending is integrated; it is not wired up yet — `sendNotification()` writes the `notifications` DB row and prints a log line instead of calling a mail server. Everything runs with one `docker compose up`.

**Stack**

| Layer | Choice | Why |
| --- | --- | --- |
| Front end | Angular (TypeScript), tailwind css | Structured forms, routing and guards |
| Back end | Java 21, Spring Boot 3 (Web, Security, Data JPA, Validation), Maven | Mature RBAC and session support, typed REST API |
| Migrations | Flyway | Versioned schema from day one; seed data is its own set of versioned migrations, added incrementally in multiple steps as each epic's entities are built, not all at once |
| Database | PostgreSQL 18 | ACID, relational |
| Email | Deferred: Mailpit (local SMTP catcher), Spring Mail | For now `sendNotification()` writes the DB row and prints a log line, no mail server call; Mailpit and the Mail dependency are added when real sending is integrated |
| Tests | JUnit 5, Spring Boot Test, Testcontainers (Postgres); Playwright for 2–3 Angular flows | RBAC, balance and audit rules are the riskiest parts |

**Back-end packages** (one per domain, each with controller, service, repository, DTOs)

1. auth — login, session, lockout, password rules (Spring Security)
2. users — accounts, roles, manager link
3. leave — requests, working-day calculation, modification, cancellation, comments
4. approvals — manager decisions and team-scope checks
5. balances — balances, reservation, ledger
6. audit — audit events, access log, History tab data
7. notifications — builds the email content from a template, then `sendNotification()` writes the `notifications` row to the DB and prints a log line instead of calling a mail server. Notification history (FR53) reads those DB rows already. Real SMTP dispatch via Mailpit is a later integration, not a change to this package's shape

**Core tables**

| Table | Key fields |
| --- | --- |
| users | id, username, password\_hash, role, manager\_id, must\_change\_password, failed\_logins, locked\_until, active |
| leave\_types | id, name |
| leave\_requests | id, employee\_id, approver\_id, leave\_type\_id, start\_date, end\_date, working\_days, notes, status, submitted\_at, decided\_at, decision\_comment |
| request\_comments | id, request\_id, author\_id, body, created\_at |
| leave\_balances | user\_id, leave\_type\_id, total\_days, reserved\_days (available = total − reserved) |
| balance\_ledger | id, user\_id, leave\_type\_id, change\_days, kind (set/adjust/reserve/release/deduct), reason, actor\_id, request\_id, created\_at |
| audit\_events | id, entity, entity\_id, action, actor\_id, details\_json, created\_at (insert-only) |
| access\_log | id, actor\_id, resource, resource\_id, allowed, created\_at (insert-only) |
| notifications | id, user\_id, request\_id, kind, subject, sent\_at |

**Main API endpoints**

| Method and path | Role |
| --- | --- |
| POST /api/auth/login, POST /api/auth/logout, POST /api/auth/change-password | All |
| GET/POST /api/requests, PUT /api/requests/{id}, POST /api/requests/{id}/cancel | Any user, own requests (managers too) |
| GET /api/requests/{id}, GET /api/requests/{id}/history, GET/POST /api/requests/{id}/comments | Owner, their manager, HR |
| GET /api/approvals/pending, POST /api/approvals/{id}/approve, POST /api/approvals/{id}/reject | Manager, or HR for top-level managers |
| GET /api/team/leave | Manager |
| GET/POST /api/users, POST /api/users/{id}/reset-password | HR |
| GET /api/balances/me | All |
| GET /api/balances, PUT /api/balances/{user}/{type}, POST /api/balances/{user}/{type}/adjust | HR |
| GET /api/notifications | All (own) |
| GET /api/audit | HR (view only) |

`GET /api/audit` returns `audit_events` rows only — the business-action trail (submit, edit, approve, reject, comments, balance and user changes). `access_log` (FR26, FR59) has no read API or UI in this POC: it's a backend-only security record, written by the request filter and checked directly against the database for acceptance scenarios 8 and 9. An access-log view or export is Phase 2 work, alongside the audit export already out of scope (FR48–FR49).

**Business rules**

- Leave days = working days Monday–Friday between start and end, inclusive. Public holidays are ignored. Whole days only.
- Approval chain is single level: a request goes to the requester's `manager_id`. Employees go to their manager, managers to their own manager, and top-level managers to an HR user. HR users approve their own requests; the audit event marks these as self-approved.
- Balance lifecycle: submit reserves days; edit re-reserves; approve moves reserved days to deducted; reject or cancel releases them. Every step writes a ledger row in the same transaction.
- Submission is refused if working days exceed available days. Balances can never go negative.
- Comments are saved and shown on refresh. They send no email or in-app alert.

## Acceptance criteria

The POC is accepted when these demo scenarios pass on seeded data: 1 HR admin, 1 top-level manager, 2 team managers, 8 employees.

| # | Scenario | Pass when | FRs |
| --- | --- | --- | --- |
| 1 | Employee submits vacation Thu–Tue | Days counted as 4 working days; status Pending; 4 days reserved; manager's notification is a DB row plus a log line (`sendNotification()`, no mail server call); History tab shows the submission | FR1–FR4, FR6, FR9, FR42–FR45, FR52, FR54 |
| 2 | Employee edits then cancels a pending request | Reservation updates on edit and is released on cancel; both steps in History; cancelled request stays visible | FR7, FR8, FR45 |
| 3 | Manager approves with a comment | Status Approved; reserved days become deducted; employee's notification is a DB row plus a log line (`sendNotification()`, no mail server call); decision in History | FR10–FR12, FR16, FR50 |
| 4 | Manager rejects | Reject blocked without a comment; reserved days released; employee's notification (with the rejection reason) is a DB row plus a log line (`sendNotification()`, no mail server call) | FR13, FR51 |
| 5 | Manager and employee discuss, dates change, then approval | Comments appear after page refresh with no alert; date change and decision appear in order | FR15, FR45 (Journey 4) |
| 6 | Team manager submits leave | Request goes to the top-level manager, who can approve it | FR17, FR19 |
| 7 | Top-level manager submits leave | Request goes to the HR user, who can approve it | FR17, FR19 |
| 8 | Manager tries to act on another team's request | API returns 403; attempt logged | FR17, FR23, FR25, FR26 |
| 9 | Employee opens another employee's request by URL | API returns 403; attempt logged | FR22, FR25, FR26 |
| 10 | Request exceeds available days (including pending reservations) | Submission blocked with a clear message | FR31 |
| 11 | HR creates a user, assigns a manager, resets a password | User can log in and must change the temporary password | FR18–FR20 |
| 12 | HR sets and adjusts a balance | Ledger shows amount, reason, actor and time; adjustment below zero refused | FR27–FR31 |
| 13 | Someone tries to change an audit row in the database | Update/delete fails | FR44 |
| 14 | 5 wrong passwords | Account locked 15 minutes | FR57 |
| 15 | Idle 30 minutes | Session expires; next action asks for login | FR58 |

## Implementation plan

Four one-week milestones, each ending in a short demo. Audit and RBAC are built first because every later feature writes to them.

| Week | Milestone | Delivers | FRs |
| --- | --- | --- | --- |
| 1 | Foundation | Maven and Angular projects, Docker Compose, Spring Boot initial setup, Flyway schema and DB entities, Spring Security login, sessions, lockout, roles, manager chain, audit and access-log tables with trigger. Seed data starts here (users and roles) and continues in later weeks as each week's entities land — it is not all seeded in this one story | FR18–FR26, FR43, FR44, FR56–FR59 |
| 2 | Employee flow | Submit, working-day calculation, edit, cancel, status, history, balances with reservation, History tab, notification to manager as a DB row plus a log line (`sendNotification()`, no mail server call) | FR1–FR9, FR31, FR42, FR45, FR52, FR54 |
| 3 | Manager and HR flow | Pending dashboard, request context, approve/reject, comments (refresh to see), team view, manager-submits-to-own-manager routing, balance set/adjust and ledger, notification history (from `sendNotification()`'s DB rows) | FR10–FR17, FR27–FR30, FR50, FR51, FR53 |
| 4 | Hardening and demo | HTTPS proxy, Testcontainers and Playwright tests, demo script, README | FR55 |

**Project structure**

```
leave-poc/
  backend/                         # Spring Boot, Maven
    pom.xml
    src/main/java/com/company/leave/
      auth/  users/  leave/  approvals/
      balances/  audit/  notifications/
      common/                      # config, security, exceptions, working-day util
    src/main/resources/
      application.yml
      db/migration/                # Flyway V1__init.sql ... (schema); separate seed migrations added incrementally, one per epic's entities
      templates/email/
    src/test/java/...              # JUnit 5, Testcontainers
  frontend/                        # Angular
    src/app/
      core/                        # auth service, guards, HTTP interceptor
      features/requests/  features/approvals/
      features/team/  features/admin/  features/notifications/
      shared/
  docker-compose.yml               # postgres, backend, frontend, proxy (mailpit added when real email is integrated)
  README.md
```

## Assumptions

- POC scope = the 45 in-scope functional requirements listed above. Phase 2 FRs are deferred; FR46, FR47, FR60 and FR61 (backups/retention) are not planned for this POC.
- Single jurisdiction, English only, one manager per user; top-level managers report to an HR user.
- Working days are Monday–Friday; public holidays are ignored; whole days only.
- Seeded demo data only; no real employee data in the POC.

## Risks

| Risk | Mitigation |
| --- | --- |
| RBAC gaps leak data across teams | Central permission layer plus tests for scenarios 6 and 7 |
| Audit or ledger writes skipped in some code path | Audit written in the service layer, inside the same DB transaction |
| Scope creep | New asks are checked against the in-scope FR list before being accepted |

## Decisions

Confirmed on 22 Oct 2026.

| Topic | Decision |
| --- | --- |
| Request modification (FR7) | Included, only while Pending |
| Comments (FR15) | Included, with no messaging or notification system; users refresh the page to see new comments |
| Leave day counting | Working days (Mon–Fri); holidays ignored |
| Negative balance (FR31) | Always blocked |
| Managers' own leave | Allowed; approved by their own manager; top-level managers are approved by HR |
| Balance on pending requests | Reserved while pending, deducted on approval, released on reject or cancel |
| Tech stack | Angular (TypeScript) front end, Java Spring Boot back end, PostgreSQL |
| HR users' own leave | HR users approve their own requests (self-approval); the audit log records it as a self-approval |
| Notifications (FR50–FR54) | In scope (Full). `sendNotification()` writes the `notifications` row to the DB and prints a log line, instead of calling a mail server. Real SMTP dispatch to a local mail catcher is a later integration |
| Backups and retention (FR46, FR47, FR60, FR61) | Not planned for this POC. No backup/restore script and no retention-policy documentation are built; excluded from architecture and implementation. Revisit for a real deployment |
