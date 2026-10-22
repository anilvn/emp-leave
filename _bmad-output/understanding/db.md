# Leave Tracking System - Database Schema & Example Data (Table View)

## Overview

The database schema consists of 9 core tables, organized into three domains:

1. **User Management** – users, roles, authentication
2. **Leave Management** – leave requests, balances, comments
3. **Audit & Compliance** – audit events, access logs, notifications

All tables use surrogate keys (id), timestamps, and insert-only audit tables for immutability.

---

## Table Relationships

```
users (Employee, Manager, HR)
  ├── 1:N → leave_requests (employee_id, approver_id)
  ├── 1:N → leave_balances (user_id)
  ├── 1:N → balance_ledger (user_id, actor_id)
  ├── 1:N → request_comments (author_id)
  ├── 1:N → audit_events (actor_id)
  └── 1:N → access_log (actor_id)

leave_types
  ├── 1:N → leave_requests (leave_type_id)
  ├── 1:N → leave_balances (leave_type_id)
  └── 1:N → balance_ledger (leave_type_id)

leave_requests
  ├── 1:N → request_comments (request_id)
  ├── 1:N → balance_ledger (request_id)
  └── 1:N → notifications (request_id)
```

---

## Table Definitions & Example Data

### 1. `users`

Stores all user accounts with roles (Employee, Manager, HR).

**Schema:**

| Column | Type | Constraints | Description |
| --- | --- | --- | --- |
| id | BIGINT | PRIMARY KEY | Unique user identifier |
| username | VARCHAR(50) | UNIQUE, NOT NULL | Login username |
| password_hash | VARCHAR(255) | NOT NULL | BCrypt-hashed password |
| role | VARCHAR(20) | NOT NULL | EMPLOYEE, MANAGER, or HR |
| manager_id | BIGINT | FOREIGN KEY (users.id), NULL | Reports-to; NULL for top-level |
| must_change_password | BOOLEAN | DEFAULT FALSE | Forces password change on login |
| failed_logins | INT | DEFAULT 0 | Count of failed login attempts |
| locked_until | TIMESTAMP | NULL | Account lockout expiry (5 failed attempts) |
| active | BOOLEAN | DEFAULT TRUE | Soft delete flag |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Account creation time |

**Example Data:**

| id | username | password_hash | role | manager_id | active | created_at |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | sarah_emp | $2a$10$... | EMPLOYEE | 2 | true | 2026-10-01 08:00 |
| 2 | alex_mgr | $2a$10$... | MANAGER | 3 | true | 2026-10-01 08:00 |
| 3 | jamie_hr | $2a$10$... | HR | NULL | true | 2026-10-01 08:00 |
| 4 | john_emp | $2a$10$... | EMPLOYEE | 2 | true | 2026-10-01 08:00 |
| 5 | maria_mgr | $2a$10$... | MANAGER | 3 | true | 2026-10-01 08:00 |

**Hierarchy:**
- Jamie (HR, id=3) is at the top
- Alex (Manager, id=2) reports to Jamie
- Maria (Manager, id=5) reports to Jamie
- Sarah (Employee, id=1) reports to Alex
- John (Employee, id=4) reports to Alex

---

### 2. `leave_types`

The four leave types used in the system.

**Schema:**

| Column | Type | Constraints | Description |
| --- | --- | --- | --- |
| id | INT | PRIMARY KEY | Type identifier |
| name | VARCHAR(50) | NOT NULL | Vacation, Sick, Parental, Sabbatical |

**Example Data:**

| id | name |
| --- | --- |
| 1 | Vacation |
| 2 | Sick |
| 3 | Parental |
| 4 | Sabbatical |

---

### 3. `leave_requests`

One row per leave request. Status moves: Pending → Approved/Rejected/Cancelled.

**Schema:**

| Column | Type | Constraints | Description |
| --- | --- | --- | --- |
| id | BIGINT | PRIMARY KEY | Request identifier |
| employee_id | BIGINT | FOREIGN KEY (users.id), NOT NULL | Employee who submitted |
| approver_id | BIGINT | FOREIGN KEY (users.id), NOT NULL | Manager who will/did approve |
| leave_type_id | INT | FOREIGN KEY (leave_types.id), NOT NULL | Vacation, Sick, etc. |
| start_date | DATE | NOT NULL | First day of leave (inclusive) |
| end_date | DATE | NOT NULL | Last day of leave (inclusive) |
| working_days | INT | NOT NULL | Mon–Fri count between start and end |
| notes | TEXT | NULL | Employee's optional notes |
| status | VARCHAR(20) | NOT NULL | PENDING, APPROVED, REJECTED, CANCELLED |
| submitted_at | TIMESTAMP | NOT NULL | Submission time |
| decided_at | TIMESTAMP | NULL | Approval/rejection time |
| decision_comment | TEXT | NULL | Manager's approval/rejection reason |

**Example Data:**

| id | employee_id | approver_id | leave_type_id | start_date | end_date | working_days | notes | status | submitted_at | decided_at | decision_comment |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 101 | 1 (Sarah) | 2 (Alex) | 1 | 2026-11-10 | 2026-11-14 | 5 | Family vacation | APPROVED | 2026-10-22 10:30 | 2026-10-22 11:20 | Enjoy! |
| 102 | 4 (John) | 2 (Alex) | 2 | 2026-10-23 | 2026-10-23 | 1 | Doctor's appointment | PENDING | 2026-10-22 14:15 | NULL | NULL |
| 103 | 5 (Maria) | 3 (Jamie) | 1 | 2026-12-20 | 2026-12-27 | 6 | Holiday break | PENDING | 2026-10-22 09:00 | NULL | NULL |

---

### 4. `request_comments`

Threaded conversation on each request between employee, manager, and HR.

**Schema:**

| Column | Type | Constraints | Description |
| --- | --- | --- | --- |
| id | BIGINT | PRIMARY KEY | Comment identifier |
| request_id | BIGINT | FOREIGN KEY (leave_requests.id), NOT NULL | Which request |
| author_id | BIGINT | FOREIGN KEY (users.id), NOT NULL | Who wrote it |
| body | TEXT | NOT NULL | Comment text |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Comment time |

**Example Data (conversation on request 101):**

| id | request_id | author_id | body | created_at |
| --- | --- | --- | --- | --- |
| 201 | 101 | 1 (Sarah) | Need Nov 10–14 off for family trip | 2026-10-22 10:30 |
| 202 | 101 | 2 (Alex) | Can you shift to Nov 11–15? Release on Nov 10. | 2026-10-22 11:00 |
| 203 | 101 | 1 (Sarah) | Sure, moving to Nov 11–15. Updated the request. | 2026-10-22 11:15 |
| 204 | 101 | 2 (Alex) | Perfect, approved. Enjoy! | 2026-10-22 11:20 |

---

### 5. `leave_balances`

Current balance for each (user, leave_type) pair.

**Schema:**

| Column | Type | Constraints | Description |
| --- | --- | --- | --- |
| user_id | BIGINT | FOREIGN KEY (users.id), PRIMARY KEY (part 1) | Employee |
| leave_type_id | INT | FOREIGN KEY (leave_types.id), PRIMARY KEY (part 2) | Vacation, Sick, etc. |
| total_days | INT | NOT NULL | Total allocated days |
| reserved_days | INT | DEFAULT 0 | Days held by pending requests |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Last change time |

**Derived column (available days)** = `total_days - reserved_days`

**Example Data:**

| user_id | name | leave_type | total_days | reserved_days | available_days |
| --- | --- | --- | --- | --- | --- |
| 1 | Sarah | Vacation | 20 | 0 | 20 |
| 1 | Sarah | Sick | 5 | 0 | 5 |
| 4 | John | Vacation | 20 | 1 | 19 |
| 4 | John | Sick | 5 | 0 | 5 |
| 5 | Maria | Vacation | 25 | 6 | 19 |
| 5 | Maria | Sick | 5 | 0 | 5 |

---

### 6. `balance_ledger`

Insert-only audit log of every balance change.

**Schema:**

| Column | Type | Constraints | Description |
| --- | --- | --- | --- |
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Ledger entry ID |
| user_id | BIGINT | FOREIGN KEY (users.id), NOT NULL | Employee whose balance changed |
| leave_type_id | INT | FOREIGN KEY (leave_types.id), NOT NULL | Which type of leave |
| change_days | INT | NOT NULL | +/− number of days (signed) |
| kind | VARCHAR(20) | NOT NULL | SET, ADJUST, RESERVE, RELEASE, DEDUCT |
| reason | VARCHAR(255) | NOT NULL | Why: "Annual allocation", "Submit req X", etc. |
| actor_id | BIGINT | FOREIGN KEY (users.id), NOT NULL | Who made the change |
| request_id | BIGINT | FOREIGN KEY (leave_requests.id), NULL | NULL if HR manual change |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Change time (immutable) |

**Example Data (Sarah's complete history):**

| id | user_id | leave_type | change_days | kind | reason | actor_id | request_id | created_at |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1001 | 1 (Sarah) | Vacation | +20 | SET | Annual allocation 2026 | 3 (Jamie HR) | NULL | 2026-01-15 08:00 |
| 1002 | 1 (Sarah) | Vacation | −5 | RESERVE | Submit request 101 (Nov 10–14) | 1 (Sarah) | 101 | 2026-10-22 10:30 |
| 1003 | 1 (Sarah) | Vacation | +5 | RELEASE | Request 101 edit | 1 (Sarah) | 101 | 2026-10-22 11:15 |
| 1004 | 1 (Sarah) | Vacation | −5 | RESERVE | Request 101 edit (Nov 11–15) | 1 (Sarah) | 101 | 2026-10-22 11:15 |
| 1005 | 1 (Sarah) | Vacation | −5 | DEDUCT | Approve request 101 | 2 (Alex) | 101 | 2026-10-22 11:20 |

---

### 7. `audit_events`

Insert-only log of all system actions for compliance and debugging.

**Schema:**

| Column | Type | Constraints | Description |
| --- | --- | --- | --- |
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Event ID |
| entity | VARCHAR(50) | NOT NULL | Table name (leave_requests, users, etc.) |
| entity_id | BIGINT | NOT NULL | ID of the record changed |
| action | VARCHAR(20) | NOT NULL | CREATE, UPDATE, APPROVE, REJECT, CANCEL |
| actor_id | BIGINT | FOREIGN KEY (users.id), NOT NULL | Who did it |
| details_json | TEXT | NULL | JSON with extra context |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Event time (immutable) |

**Example Data:**

| id | entity | entity_id | action | actor_id | details_json | created_at |
| --- | --- | --- | --- | --- | --- | --- |
| 5001 | leave_requests | 101 | CREATE | 1 (Sarah) | {"type":"Vacation","start":"2026-11-10","end":"2026-11-14","days":5} | 2026-10-22 10:30 |
| 5002 | leave_requests | 101 | UPDATE | 1 (Sarah) | {"field":"start_date","from":"2026-11-10","to":"2026-11-11"} | 2026-10-22 11:15 |
| 5003 | leave_requests | 101 | UPDATE | 1 (Sarah) | {"field":"end_date","from":"2026-11-14","to":"2026-11-15"} | 2026-10-22 11:15 |
| 5004 | leave_requests | 101 | APPROVE | 2 (Alex) | {"approver_id":2,"comment":"Perfect, enjoy!"} | 2026-10-22 11:20 |

---

### 8. `access_log`

Tracks who accessed what data and when (for privacy compliance).

**Schema:**

| Column | Type | Constraints | Description |
| --- | --- | --- | --- |
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Log entry ID |
| actor_id | BIGINT | FOREIGN KEY (users.id), NOT NULL | User who tried to access |
| resource | VARCHAR(50) | NOT NULL | What was accessed (leave_requests, users, etc.) |
| resource_id | BIGINT | NOT NULL | ID of the resource |
| allowed | BOOLEAN | NOT NULL | true if access granted, false if denied |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Access time (immutable) |

**Example Data:**

| id | actor_id | resource | resource_id | allowed | created_at |
| --- | --- | --- | --- | --- | --- |
| 6001 | 1 (Sarah) | leave_requests | 101 | true | 2026-10-22 10:30 |
| 6002 | 2 (Alex) | leave_requests | 101 | true | 2026-10-22 11:00 |
| 6003 | 4 (John) | leave_requests | 101 | false | 2026-10-22 12:00 |
| 6004 | 1 (Sarah) | leave_requests | 102 | false | 2026-10-22 12:30 |

---

### 9. `notifications`

Sent emails for tracking and resend logic.

**Schema:**

| Column | Type | Constraints | Description |
| --- | --- | --- | --- |
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | Notification ID |
| user_id | BIGINT | FOREIGN KEY (users.id), NOT NULL | Who received it |
| request_id | BIGINT | FOREIGN KEY (leave_requests.id), NOT NULL | Which request |
| kind | VARCHAR(20) | NOT NULL | SUBMITTED, APPROVED, REJECTED |
| subject | VARCHAR(255) | NOT NULL | Email subject |
| sent_at | TIMESTAMP | NOT NULL | When sent to mail catcher |

**Example Data:**

| id | user_id | request_id | kind | subject | sent_at |
| --- | --- | --- | --- | --- | --- |
| 7001 | 2 (Alex) | 101 | SUBMITTED | New leave request from Sarah (Nov 10–14) | 2026-10-22 10:30 |
| 7002 | 1 (Sarah) | 101 | APPROVED | Your leave request approved (Nov 11–15) | 2026-10-22 11:20 |
| 7003 | 2 (Alex) | 102 | SUBMITTED | New leave request from John (Oct 23) | 2026-10-22 14:15 |

---

## Complete Leave Request Lifecycle (Step by Step)

### Step 1: Sarah submits request for Nov 10–14 (Vacation)

**leave_requests** (new row):

| id | employee_id | approver_id | leave_type_id | start_date | end_date | working_days | status | submitted_at |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 101 | 1 (Sarah) | 2 (Alex) | 1 (Vacation) | 2026-11-10 | 2026-11-14 | 5 | PENDING | 2026-10-22 10:30 |

**balance_ledger** (new row - RESERVE):

| change_days | kind | reason | actor_id | request_id |
| --- | --- | --- | --- | --- |
| −5 | RESERVE | Submit request 101 | 1 (Sarah) | 101 |

**leave_balances** (updated):

| user_id | leave_type | total_days | reserved_days | available |
| --- | --- | --- | --- | --- |
| 1 (Sarah) | Vacation | 20 | **5** | **15** |

**notifications** (new row):

| user_id | request_id | kind | subject |
| --- | --- | --- | --- |
| 2 (Alex) | 101 | SUBMITTED | New leave request from Sarah (Nov 10–14) |

---

### Step 2: Sarah and Alex discuss via comments; dates shift to Nov 11–15

**request_comments** (4 new rows):

| author_id | body |
| --- | --- |
| 1 (Sarah) | Need Nov 10–14 off for family trip |
| 2 (Alex) | Can you shift to Nov 11–15? Release on Nov 10. |
| 1 (Sarah) | Sure, moving to Nov 11–15. Updated the request. |
| 2 (Alex) | Perfect, approved. Enjoy! |

**leave_requests** (updated):

| id | start_date | end_date | working_days |
| --- | --- | --- | --- |
| 101 | **2026-11-11** | **2026-11-15** | **5** |

**balance_ledger** (2 new rows - RELEASE then RESERVE):

| change_days | kind | reason | request_id |
| --- | --- | --- | --- |
| +5 | RELEASE | Request 101 edit | 101 |
| −5 | RESERVE | Request 101 edit (new dates) | 101 |

**leave_balances** (no change):

| user_id | leave_type | total_days | reserved_days | available |
| --- | --- | --- | --- | --- |
| 1 (Sarah) | Vacation | 20 | 5 | 15 |

---

### Step 3: Alex approves the request

**leave_requests** (updated):

| id | status | decided_at | decision_comment |
| --- | --- | --- | --- |
| 101 | **APPROVED** | 2026-10-22 11:20 | Enjoy! |

**balance_ledger** (1 new row - DEDUCT):

| change_days | kind | reason | actor_id | request_id |
| --- | --- | --- | --- | --- |
| −5 | DEDUCT | Approve request 101 | 2 (Alex) | 101 |

**leave_balances** (updated - reserved → deducted):

| user_id | leave_type | total_days | reserved_days | available |
| --- | --- | --- | --- | --- |
| 1 (Sarah) | Vacation | 20 | **0** | **15** |

**notifications** (1 new row):

| user_id | request_id | kind | subject |
| --- | --- | --- | --- |
| 1 (Sarah) | 101 | APPROVED | Your leave request approved (Nov 11–15) |

---

## Final Audit Trail for Sarah (Request 101)

### balance_ledger (complete history):

| sequence | change_days | kind | reason | actor_id | created_at |
| --- | --- | --- | --- | --- | --- |
| 1 | +20 | SET | Annual allocation 2026 | 3 (Jamie HR) | 2026-01-15 08:00 |
| 2 | −5 | RESERVE | Submit request 101 (Nov 10–14) | 1 (Sarah) | 2026-10-22 10:30 |
| 3 | +5 | RELEASE | Request 101 edit | 1 (Sarah) | 2026-10-22 11:15 |
| 4 | −5 | RESERVE | Request 101 edit (Nov 11–15) | 1 (Sarah) | 2026-10-22 11:15 |
| 5 | −5 | DEDUCT | Approve request 101 | 2 (Alex) | 2026-10-22 11:20 |

### Final balances:

| user_id | leave_type | total_days | reserved_days | deducted_days | available |
| --- | --- | --- | --- | --- | --- |
| 1 (Sarah) | Vacation | 20 | 0 | 5 | 15 |
| 1 (Sarah) | Sick | 5 | 0 | 0 | 5 |

---

## Key Design Principles

1. **Insert-Only Audit Tables:** `audit_events` and `access_log` can never be updated or deleted. All changes are preserved forever.

2. **Immutable Ledger:** `balance_ledger` is append-only. Every change is a new row with reason and actor. Corrections are new entries, never overwrites.

3. **Reservation Model:** Balance has two components:
   - `total_days` – allocated
   - `reserved_days` – held by pending requests
   - **Available** = `total_days - reserved_days` (calculated at runtime)

4. **Transactions:** Balance and ledger changes happen in the same database transaction, so they never drift.

5. **Soft Delete:** Users have an `active` flag; records are never deleted, just marked inactive.

6. **Normalized Relationships:**
   - Employee and approver are both `users.id`
   - Manager is a self-referential `users.manager_id`
   - All foreign keys are NOT NULL to enforce referential integrity