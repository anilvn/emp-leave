# FR coverage

The 45 in-scope FRs, numbered as in the source PRD (Anil, 2026-10-15). Every FR is Full except FR55, which is Partial.

| FR | Requirement | CAP | How the POC meets it |
| --- | --- | --- | --- |
| FR1 | Submit request with leave type | CAP-1 | Vacation, Sick, Parental, Sabbatical; whole days only |
| FR2 | Start and end dates | CAP-1 | End ≥ start; counted as working days Mon–Fri |
| FR3 | Optional notes | CAP-1 | Free text |
| FR4 | View request status | CAP-2 | Pending / Approved / Rejected / Cancelled |
| FR5 | Leave history | CAP-2 | Filterable list of own requests |
| FR6 | Balance by type | CAP-2 | Total, reserved (pending), available |
| FR7 | Modify pending request | CAP-3 | Pending only; reservation recalculated; change logged |
| FR8 | Cancel pending request | CAP-3 | Status Cancelled; days released; never deleted |
| FR9 | Record submitter and time | CAP-1, CAP-12 | On request and audit event |
| FR10 | Manager pending dashboard | CAP-4 | Pending Approvals for direct reports |
| FR11 | Rich request context | CAP-4 | Employee, dates, type, balance, recent history |
| FR12 | Approve, optional comment | CAP-5 | Reserved → deducted |
| FR13 | Reject, required comment | CAP-5 | Reject disabled until a comment is entered; days released |
| FR14 | Team leave history | CAP-8 | Approved team leave by date, with comments and History per entry |
| FR15 | Discussion comments | CAP-7 | Thread per request; refresh to see; no notifications |
| FR16 | Record decision, time, approver | CAP-5, CAP-12 | On request and audit event |
| FR17 | Block decisions outside team | CAP-6, CAP-10 | Only the requester's manager; HR decides own requests |
| FR18 | Create user accounts | CAP-9 | HR admin page; HR types the temporary password |
| FR19 | Assign roles | CAP-6, CAP-9 | Role and manager set at creation; top-level managers report to HR |
| FR20 | Reset passwords | CAP-9 | System-generated temporary password; must change on login |
| FR21 | RBAC, three roles | CAP-10 | Role checks on every endpoint |
| FR22 | Employees see own data only | CAP-10 | Scoped by user id |
| FR23 | Managers see direct reports only | CAP-10 | Scoped by `manager_id` |
| FR24 | HR sees all | CAP-10, CAP-12 | No scope filter |
| FR25 | Block unauthorized access | CAP-10 | 403 plus an access-denied log entry |
| FR26 | Log sensitive data access | CAP-10 | `access_log` written by a request filter; no read API/UI |
| FR27 | HR views all balances | CAP-11 | Balances page |
| FR28 | HR sets balances | CAP-11 | Reason required |
| FR29 | HR adjusts balances | CAP-11 | ± days, reason required |
| FR30 | Record adjustments | CAP-11 | Ledger: time, amount, reason, actor; readable via API |
| FR31 | No negative balance | CAP-1, CAP-11 | Request days ≤ available; adjustments cannot go below zero |
| FR42 | Audit trail of transactions | CAP-12 | Audit event per action |
| FR43 | Time and user on every action | CAP-12 | Required audit columns |
| FR44 | Immutable audit records | CAP-12 | Insert-only; DB trigger blocks update/delete |
| FR45 | History tab per request | CAP-7 | Timeline: submit, edits, comments, decision |
| FR50 | Email on approval | CAP-13 | `sendNotification()`: DB row + log line |
| FR51 | Email on rejection | CAP-13 | Same as FR50 |
| FR52 | Email to manager on new request | CAP-13 | Same as FR50 |
| FR53 | Notification history | CAP-13 | In-app list of `notifications` rows |
| FR54 | Request details in emails | CAP-13 | Template: type, dates, days, status, link |
| FR55 | HTTPS | CAP-14 | **Partial:** local reverse proxy, self-signed certificate |
| FR56 | Username/password login | CAP-14 | BCrypt |
| FR57 | Lockout after 5 failures | CAP-14 | 15-minute lockout |
| FR58 | Session timeout | CAP-14 | 30-minute inactivity timeout |
| FR59 | Access logs | CAP-10 | Same as FR26 |

**Not in this POC:**
- **Phase 2:** FR32–FR41, FR48, FR49. `audit_events.details_json` already has room for before/after values.
- **Not planned:** FR46, FR47, FR60, FR61 (backups, retention).
