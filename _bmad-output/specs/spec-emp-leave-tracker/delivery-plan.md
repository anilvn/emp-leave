# Delivery plan

Four one-week milestones, each ending in a short demo. Audit and RBAC come first because every later feature writes to them. New asks are checked against the in-scope FR list before they are accepted.

| Week | Milestone | Delivers | FRs | CAPs |
| --- | --- | --- | --- | --- |
| 1 | Foundation | Maven and Angular projects, Docker Compose, Flyway schema and entities, login, sessions, lockout, roles, manager chain, audit and access-log tables with trigger. Seed data starts here (users, roles). | FR18–FR26, FR43, FR44, FR56–FR59 | CAP-9, CAP-10, CAP-12, CAP-14 |
| 2 | Employee flow | Submit, working-day calculation, edit, cancel, status, history, balances with reservation, History tab, manager notification. | FR1–FR9, FR31, FR42, FR45, FR52, FR54 | CAP-1, CAP-2, CAP-3, CAP-7, CAP-13 |
| 3 | Manager and HR flow | Pending dashboard, request context, approve/reject, comments, team view, manager-to-own-manager routing, balance set/adjust and ledger, notification history. | FR10–FR17, FR27–FR30, FR50, FR51, FR53 | CAP-4, CAP-5, CAP-6, CAP-7, CAP-8, CAP-11, CAP-13 |
| 4 | Hardening and demo | HTTPS proxy, Testcontainers and Playwright tests, demo script, README. | FR55 | CAP-14 |

**Highest risks:**
- RBAC gaps leaking data across teams.
- Audit or ledger writes skipped on some code path.

The backend spine's AD-4 and AD-10 mitigate both.
