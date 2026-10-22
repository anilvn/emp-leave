# Leave Tracking System – POC PRD

Oct 22, 2026 · @Anil

## Purpose

The POC demonstrates every MVP functional requirement of the Employee Leave Tracking System PRD (2026-10-15) end to end: submit → approve/reject → track → audit. It runs locally or on a single dev server, and its code is structured so it can grow into the production build without a rewrite.

**POC goals**

- Prove the core value: employees see request status without chasing, managers decide in a few clicks, HR sees a complete audit trail.
- Cover all 49 MVP functional requirements; the 12 Phase 2 FRs stay deferred.
- Validate the data model, role-based access and audit design that production will reuse.
- Give the manager and client a working demo with three roles: Employee, Manager, HR/Admin.

**Source:** Employee Leave Tracking System PRD by Anil, dated 2026-10-15. FR numbers below match that document.

**Suggested size:** 1–2 developers, about 4 weeks.

## Scope

The POC includes all user-facing behaviour. It leaves out the production qualities (scale, uptime, hardening, operations) that don't change what the system does.

| Area | In POC | Deferred |
| --- | --- | --- |
| Leave requests | Submit, modify while pending, cancel, status, history, balances (FR1–FR9) | Half-day leave (not needed) |
| Approvals | Dashboard, context, approve/reject, comments, team scope; managers submit leave to their own manager (FR10–FR17) | Multi-level approval, escalation |
| Users and access | Account creation, roles, manager link, password reset, RBAC, access logging (FR18–FR26) | SSO, self-service password reset |
| Balances | Set, adjust, ledger, reservation while pending, no negative balance (FR27–FR31) | Balance history view (FR32), carryover rules (FR33), accrual |
| Audit | Immutable audit log, History tab, 3-year retention by design (FR42–FR47) | Audit export (FR48), before/after values (FR49) |
| Notifications | Email on submit/approve/reject, in-app notification history (FR50–FR54) | Comment notifications, real email provider, retry queue |
| Security | Password hashing, lockout after 5 tries, 30-min session timeout (FR56–FR58) | TLS certificates, encryption at rest, OWASP testing |
| Backups | Scheduled pg\_dump script (FR46, FR60) | Managed daily backups, restore testing, DR plan (FR61) |
| Reports and analytics | — | All of FR34–FR41 (Phase 2) |

**Not in POC at all:** cloud deployment, IaC, auto-scaling, APM and alerting, CI/CD, zero-downtime deploys, full WCAG testing, calendar/HRIS/payroll integrations, mobile apps.

## Functional requirements

The POC covers the 49 MVP FRs: 45 Full, 3 Partial, 1 Deferred. The 12 Phase 2 FRs (FR32–FR41, FR48, FR49) are out of scope and listed at the end for traceability.

| FR | Requirement (short) | POC | How the POC meets it |
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
| FR26 | Log sensitive data access | Full | Access log table written by a request filter |
| FR27 | HR views all balances | Full | Balances page |
| FR28 | HR sets balances | Full | Set action, reason required |
| FR29 | HR adjusts balances | Full | +/- adjustment, reason required |
| FR30 | Record adjustments | Full | Balance ledger: time, amount, reason, actor |
| FR31 | No negative balance | Full | Always blocked: request days must be ≤ available (total − reserved); HR adjustments also cannot go below zero |
| FR42 | Audit trail of transactions | Full | Audit event per action |
| FR43 | Time and user on every action | Full | Required columns on audit table |
| FR44 | Immutable audit records | Full | Insert-only table; DB trigger blocks update/delete |
| FR45 | History tab per request | Full | Timeline on request detail (submit, edits, comments, decision) |
| FR46 | Daily backups | Partial | pg\_dump script on a daily schedule |
| FR47 | Keep audit logs 3 years | Full | No purge logic; retention policy documented |
| FR50 | Email on approval | Full | Sent to local mail catcher |
| FR51 | Email on rejection | Full | Sent to local mail catcher |
| FR52 | Email to manager on new request | Full | Sent to local mail catcher |
| FR53 | Notification history | Full | In-app list of sent emails |
| FR54 | Request details in emails | Full | Email template with type, dates, days, status, link |
| FR55 | HTTPS | Partial | HTTPS via local reverse proxy; real certificates in production |
| FR56 | Username/password login | Full | BCrypt-hashed passwords |
| FR57 | Lockout after 5 failures | Full | 15-minute lockout |
| FR58 | Session timeout | Full | 30-minute inactivity timeout |
| FR59 | Access logs | Full | Same as FR26 |
| FR60 | Daily automated backups | Partial | Same script as FR46 |
| FR61 | Backup and recovery | Deferred | Restore steps documented, not tested |
| FR32–FR41, FR48, FR49 | Phase 2: balance history view, carryover, reports, exports, audit export, before/after values | Deferred | Not built. Audit table keeps room for before/after values later |

## Technical requirements

The POC keeps every technical requirement that shapes the data or behaviour, and defers those that only matter at production scale or uptime.

**Required in the POC**

- Relational, ACID database: PostgreSQL (same engine as production).
- REST API with role checks on every endpoint; the UI hides features the role can't use.
- Insert-only audit and access-log tables, protected by a database trigger.
- Password hashing, lockout after 5 failures for 15 minutes, 30-minute session timeout, HttpOnly/Secure/SameSite cookies.
- Password rule: minimum 8 characters, letters and numbers.
- Responsive layout for Chrome and Edge, desktop-first.
- Basic accessibility: semantic HTML, labelled form fields, keyboard navigation on main flows.
- Database migrations from day one (Flyway).
- Configuration through environment variables, no hard-coded secrets.
- Unit and API tests for RBAC, approval rules and audit writes.

**Deferred to production (documented, not built)**

| Item | PRD target | Production approach |
| --- | --- | --- |
| Hosting | Cloud (AWS/Azure/GCP) | Containers on managed service, IaC |
| Concurrency | 20 users, scale to 500 employees | Stateless API, horizontal scaling, load test |
| Performance | Pages < 3 s, API < 500 ms, reports < 5 s | Indexing, caching layer, measured in load test |
| Encryption | TLS 1.2+, encryption at rest | Managed certificates, encrypted DB and backups |
| Backups and DR | Daily, 30-day retention, RPO/RTO 24 h | Managed DB backups, monthly restore tests |
| Monitoring | APM, error tracking, uptime alerts | Hosted APM and log aggregation |
| Delivery | CI/CD, zero-downtime deploys | Pipeline with tests, blue/green deploys |
| Security testing | OWASP Top 10 | Scan and pen test before launch |
| Accessibility | WCAG 2.1 Level A, screen readers | Automated checks in CI, manual testing |
| Email | Reliable delivery | Transactional email provider, queue with retry |

## Architecture and data model

A three-tier app: Angular front end, Java Spring Boot back end, PostgreSQL, plus a local mail catcher. Everything runs with one `docker compose up`.

**Stack**

| Layer | Choice | Why |
| --- | --- | --- |
| Front end | Angular (TypeScript), tailwind css | Structured forms, routing and guards; carries into production |
| Back end | Java 21, Spring Boot 3 (Web, Security, Data JPA, Validation, Mail), Maven | Mature RBAC and session support, typed REST API |
| Migrations | Flyway | Versioned schema from day one |
| Database | PostgreSQL 16 | Same engine as production |
| Email | Mailpit (local SMTP catcher) | See emails without a real provider |
| Tests | JUnit 5, Spring Boot Test, Testcontainers (Postgres); Playwright for 2–3 Angular flows | RBAC, balance and audit rules are the riskiest parts |

**Back-end packages** (one per domain, each with controller, service, repository, DTOs, so production can split or replace them)

1. auth — login, session, lockout, password rules (Spring Security)
2. users — accounts, roles, manager link
3. leave — requests, working-day calculation, modification, cancellation, comments
4. approvals — manager decisions and team-scope checks
5. balances — balances, reservation, ledger
6. audit — audit events, access log, History tab data
7. notifications — email sending and notification history, behind a `NotificationSender` interface so a provider can be swapped in

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

**Business rules**

- Leave days = working days Monday–Friday between start and end, inclusive. Public holidays are ignored for now. Whole days only.
- Approval chain is single level: a request goes to the requester's `manager_id`. Employees go to their manager, managers to their own manager, and top-level managers to an HR user. HR users approve their own requests; the audit event marks these as self-approved.
- Balance lifecycle: submit reserves days; edit re-reserves; approve moves reserved days to deducted; reject or cancel releases them. Every step writes a ledger row in the same transaction.
- Submission is refused if working days exceed available days. Balances can never go negative.
- Comments are saved and shown on refresh. They send no email or in-app alert.

## Acceptance criteria

The POC is accepted when these demo scenarios pass on seeded data: 1 HR admin, 1 top-level manager, 2 team managers, 8 employees.

| # | Scenario | Pass when | FRs |
| --- | --- | --- | --- |
| 1 | Employee submits vacation Thu–Tue | Days counted as 4 working days; status Pending; 4 days reserved; manager gets an email; History tab shows the submission | FR1–FR4, FR6, FR9, FR42–FR45, FR52, FR54 |
| 2 | Employee edits then cancels a pending request | Reservation updates on edit and is released on cancel; both steps in History; cancelled request stays visible | FR7, FR8, FR45 |
| 3 | Manager approves with a comment | Status Approved; reserved days become deducted; employee emailed; decision in History | FR10–FR12, FR16, FR50 |
| 4 | Manager rejects | Reject blocked without a comment; reserved days released; employee emailed with reason | FR13, FR51 |
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
| 16 | Run backup script | Dump file created with today's date | FR46, FR60 |

## Implementation plan

Four one-week milestones, each ending in a short demo. Audit and RBAC are built first because every later feature writes to them.

| Week | Milestone | Delivers | FRs |
| --- | --- | --- | --- |
| 1 | Foundation | Maven and Angular projects, Docker Compose, Flyway schema, Spring Security login, sessions, lockout, roles, manager chain, seed data, audit and access-log tables with trigger | FR18–FR26, FR43, FR44, FR56–FR59 |
| 2 | Employee flow | Submit, working-day calculation, edit, cancel, status, history, balances with reservation, History tab, email to manager | FR1–FR9, FR31, FR42, FR45, FR52, FR54 |
| 3 | Manager and HR flow | Pending dashboard, request context, approve/reject, comments (refresh to see), team view, manager-submits-to-own-manager routing, balance set/adjust and ledger, notification history | FR10–FR17, FR27–FR30, FR50, FR51, FR53 |
| 4 | Hardening and demo | Backup script, HTTPS proxy, Testcontainers and Playwright tests, demo script, README | FR46, FR47, FR55, FR60, FR61 |

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
      db/migration/                # Flyway V1__init.sql ...
      templates/email/
    src/test/java/...              # JUnit 5, Testcontainers
  frontend/                        # Angular
    src/app/
      core/                        # auth service, guards, HTTP interceptor
      features/requests/  features/approvals/
      features/team/  features/admin/  features/notifications/
      shared/
  scripts/backup.sh
  docker-compose.yml               # postgres, mailpit, backend, frontend, proxy
  README.md
```

## Path to production

The POC code is kept; production adds infrastructure and hardening around it.

1. Keep: data model, Flyway migrations, Spring Boot packages, Spring Security RBAC, audit design, Angular app.
2. Replace: Mailpit with a transactional email provider plus a retry queue; local proxy with managed TLS.
3. Add: cloud hosting with IaC, CI/CD, managed backups and DR, APM and alerting, caching, load and security testing, WCAG testing.
4. Extend with Phase 2: reports and exports, audit export, before/after values, balance history view, carryover, public holidays, comment notifications, multi-level approvals.

## Assumptions

- POC scope = the 49 MVP functional requirements; Phase 2 FRs and production-only technical items are deferred.
- Single jurisdiction, English only, one manager per user; top-level managers report to an HR user.
- Working days are Monday–Friday; public holidays are ignored; whole days only.
- Seeded demo data only; no real employee data in the POC.

## Risks

| Risk | Mitigation |
| --- | --- |
| RBAC gaps leak data across teams | Central permission layer plus tests for scenarios 6 and 7 |
| Audit or ledger writes skipped in some code path | Audit written in the service layer, inside the same DB transaction |
| Scope creep from Phase 2 items | Phase 2 FRs explicitly out of scope; new asks go to the Phase 2 list |
| POC mistaken for production-ready | Deferred list shared with the client alongside the demo |

## Decisions

Confirmed on 22 Oct 2026. These resolve the conflicts in the original PRD.

| Topic | Decision |
| --- | --- |
| Request modification (FR7) | Included, only while Pending |
| Comments (FR15) | Included, with no messaging or notification system; users refresh the page to see new comments |
| Phase 2 FRs | Not in POC; MVP FRs only |
| Leave day counting | Working days (Mon–Fri); holidays ignored for now |
| Negative balance (FR31) | Always blocked |
| Managers' own leave | Allowed; approved by their own manager; top-level managers are approved by HR |
| Half-day leave | Not needed |
| Balance on pending requests | Reserved while pending, deducted on approval, released on reject or cancel |
| Tech stack | Angular (TypeScript) front end, Java Spring Boot back end, PostgreSQL |
| HR users' own leave | HR users approve their own requests (self-approval); the audit log records it as a self-approval |
