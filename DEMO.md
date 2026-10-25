# POC Demo Script — 15 Acceptance Scenarios

This script walks all 15 acceptance scenarios from the PRD in order. For each scenario it names the role to use, the API call to make, and the result to expect.

**Setup**: stack running via `docker compose up`, cookies stored in `cookies.txt` for the active session. All `curl` commands use `-k` (skip TLS verify) because the cert is self-signed.

**Notifications**: notifications are a **database row plus a log line** from `sendNotification()`. No mail server is configured or required. The audience should not expect an email — confirm by checking `GET /api/notifications` for the recipient after any step that says "notification sent".

---

## Scenario 1 — Employee submits vacation Thu–Tue

**Role**: EMPLOYEE (`emp.jsmith`)

```bash
# Sign in
curl -k -c cookies.txt -X POST https://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.jsmith","password":"Passw0rd1"}'

# Submit a request Thu 2026-11-05 to Tue 2026-11-10 (4 working days: Thu, Fri, Mon, Tue)
curl -k -b cookies.txt -X POST https://localhost/api/requests \
  -H "Content-Type: application/json" \
  -d '{"leaveTypeId":1,"startDate":"2026-11-05","endDate":"2026-11-10"}'
```

**Expected**: status `201`; response body shows `workingDays: 4`, `status: "PENDING"`, 4 days reserved in balance; `GET /api/notifications` as `mgr.chen` shows the approval-request notification (DB row + log line); `GET /api/requests/{id}/history` shows a `SUBMITTED` entry.

**FRs covered**: FR1–FR4, FR6, FR9, FR42–FR45, FR52, FR54

---

## Scenario 2 — Employee edits then cancels a pending request

**Role**: EMPLOYEE (`emp.jsmith`) — uses the request created in Scenario 1

```bash
# Edit: shift dates to 2026-11-06 to 2026-11-11 (same 4 working days)
curl -k -b cookies.txt -X PUT https://localhost/api/requests/{id} \
  -H "Content-Type: application/json" \
  -d '{"leaveTypeId":1,"startDate":"2026-11-06","endDate":"2026-11-11"}'

# Cancel
curl -k -b cookies.txt -X POST https://localhost/api/requests/{id}/cancel
```

**Expected**: reservation updates on edit; reservation released on cancel (balance returns to full 20 days); both steps appear in `GET /api/requests/{id}/history`; cancelled request still visible in `GET /api/requests`.

**FRs covered**: FR7, FR8, FR45

---

## Scenario 3 — Manager approves with a comment

**Role**: MANAGER (`mgr.chen`) — approve a pending request from one of their team members

```bash
# Sign in as manager
curl -k -c cookies.txt -X POST https://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"mgr.chen","password":"Passw0rd1"}'

# View pending approvals
curl -k -b cookies.txt https://localhost/api/approvals/pending

# Approve with a comment
curl -k -b cookies.txt -X POST https://localhost/api/approvals/{id}/approve \
  -H "Content-Type: application/json" \
  -d '{"comment":"Approved — enjoy your time off."}'
```

**Expected**: request status changes to `APPROVED`; reserved days become deducted (balance reduced permanently); `GET /api/notifications` as the employee shows the approval notification (DB row + log line); `GET /api/requests/{id}/history` shows `APPROVED` entry with the comment.

**FRs covered**: FR10–FR12, FR16, FR50

---

## Scenario 4 — Manager rejects

**Role**: MANAGER (`mgr.chen`)

```bash
# Attempt to reject without a comment — must be blocked
curl -k -b cookies.txt -X POST https://localhost/api/approvals/{id}/reject \
  -H "Content-Type: application/json" \
  -d '{}'

# Reject with a comment
curl -k -b cookies.txt -X POST https://localhost/api/approvals/{id}/reject \
  -H "Content-Type: application/json" \
  -d '{"comment":"Team at full capacity that week."}'
```

**Expected**: first call returns `422` or `400` (comment required); second call returns `200`; reserved days released; `GET /api/notifications` as the employee shows the rejection notification with the reason (DB row + log line).

**FRs covered**: FR13, FR51

---

## Scenario 5 — Manager and employee discuss, dates change, then approval

**Role**: EMPLOYEE (`emp.jsmith`) and MANAGER (`mgr.chen`)

```bash
# Employee posts a comment
curl -k -b cookies.txt -X POST https://localhost/api/requests/{id}/comments \
  -H "Content-Type: application/json" \
  -d '{"body":"Could we shift this to the following week?"}'

# Manager replies
curl -k -b cookies.txt -X POST https://localhost/api/requests/{id}/comments \
  -H "Content-Type: application/json" \
  -d '{"body":"Yes, that works."}'

# Employee edits dates
curl -k -b cookies.txt -X PUT https://localhost/api/requests/{id} \
  -H "Content-Type: application/json" \
  -d '{"leaveTypeId":1,"startDate":"2026-11-12","endDate":"2026-11-13"}'

# Manager approves
curl -k -b cookies.txt -X POST https://localhost/api/approvals/{id}/approve \
  -H "Content-Type: application/json" \
  -d '{}'
```

**Expected**: comments appear in `GET /api/requests/{id}/comments` without any real-time alert (refresh to see); history shows comments, the edit, and the approval in chronological order.

**FRs covered**: FR15, FR45 (Journey 4)

---

## Scenario 6 — Team manager submits leave

**Role**: MANAGER (`mgr.chen`) — submits their own leave request

```bash
curl -k -b cookies.txt -X POST https://localhost/api/requests \
  -H "Content-Type: application/json" \
  -d '{"leaveTypeId":1,"startDate":"2026-12-01","endDate":"2026-12-02"}'
```

**Expected**: request created with `status: "PENDING"`; `GET /api/approvals/pending` as `mgr.rossi` (the top-level manager who manages `mgr.chen`) shows the request ready for approval; `mgr.rossi` can approve it.

**FRs covered**: FR17, FR19

---

## Scenario 7 — Top-level manager submits leave

**Role**: MANAGER (`mgr.rossi`) — submits their own leave request

```bash
# Sign in as top-level manager
curl -k -c cookies.txt -X POST https://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"mgr.rossi","password":"Passw0rd1"}'

curl -k -b cookies.txt -X POST https://localhost/api/requests \
  -H "Content-Type: application/json" \
  -d '{"leaveTypeId":1,"startDate":"2026-12-03","endDate":"2026-12-03"}'
```

**Expected**: request goes to `hr.admin` (HR user approves top-level managers); `GET /api/approvals/pending` as `hr.admin` shows the request.

**FRs covered**: FR17, FR19

---

## Scenario 8 — Manager tries to act on another team's request

**Role**: MANAGER (`mgr.ortiz`) — attempts to approve a request belonging to `mgr.chen`'s team

```bash
# Sign in as mgr.ortiz
curl -k -c cookies.txt -X POST https://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"mgr.ortiz","password":"Passw0rd1"}'

# Attempt to approve a request from emp.jsmith (reports to mgr.chen, not mgr.ortiz)
curl -k -b cookies.txt -X POST https://localhost/api/approvals/{id}/approve \
  -H "Content-Type: application/json" \
  -d '{}'
```

**Expected**: API returns `403 ACCESS_DENIED`; the attempt is written to `access_log` with `allowed=false` — verify directly in the database: `SELECT * FROM access_log ORDER BY id DESC LIMIT 5`.

**FRs covered**: FR17, FR23, FR25, FR26

---

## Scenario 9 — Employee opens another employee's request by URL

**Role**: EMPLOYEE (`emp.alee`) — attempts to read `emp.jsmith`'s request

```bash
# Sign in as emp.alee
curl -k -c cookies.txt -X POST https://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.alee","password":"Passw0rd1"}'

# Attempt to read emp.jsmith's request by its ID
curl -k -b cookies.txt https://localhost/api/requests/{jsmith-request-id}
```

**Expected**: API returns `403 ACCESS_DENIED`; the attempt is written to `access_log` with `allowed=false`.

**FRs covered**: FR22, FR25, FR26

---

## Scenario 10 — Request exceeds available days

**Role**: EMPLOYEE (`emp.jsmith`) — submits a request for more days than the balance allows

```bash
# Sign in as emp.jsmith
curl -k -c cookies.txt -X POST https://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.jsmith","password":"Passw0rd1"}'

# Try to book 25 vacation days (balance is 20)
curl -k -b cookies.txt -X POST https://localhost/api/requests \
  -H "Content-Type: application/json" \
  -d '{"leaveTypeId":1,"startDate":"2027-01-04","endDate":"2027-02-05"}'
```

**Expected**: API returns `422` or `400` with a clear message explaining the balance is insufficient. No balance row is changed.

**FRs covered**: FR31

---

## Scenario 11 — HR creates a user, assigns a manager, resets a password

**Role**: HR_ADMIN (`hr.admin`)

```bash
# Sign in as hr.admin
curl -k -c cookies.txt -X POST https://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"hr.admin","password":"Passw0rd1"}'

# Create a new employee
curl -k -b cookies.txt -X POST https://localhost/api/users \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.new","role":"EMPLOYEE","managerId":3,"temporaryPassword":"TempPass1"}'

# List managers to confirm dropdown values
curl -k -b cookies.txt https://localhost/api/users/managers

# Reset the new user's password
curl -k -b cookies.txt -X POST https://localhost/api/users/{new-user-id}/reset-password \
  -H "Content-Type: application/json" \
  -d '{"temporaryPassword":"NewTemp1"}'
```

**Expected**: new user created with `mustChangePassword: true`; the new user can log in and is immediately redirected to change their password; after changing it `mustChangePassword` becomes `false`.

**FRs covered**: FR18–FR20

---

## Scenario 12 — HR sets and adjusts a balance

**Role**: HR_ADMIN (`hr.admin`)

```bash
# View all balances
curl -k -b cookies.txt https://localhost/api/balances

# Set emp.jsmith's vacation balance to 25 days
curl -k -b cookies.txt -X PUT https://localhost/api/balances/5/1 \
  -H "Content-Type: application/json" \
  -d '{"totalDays":25,"reason":"Annual entitlement increase"}'

# View the ledger
curl -k -b cookies.txt https://localhost/api/balances/5/1/ledger

# Adjust down by 3
curl -k -b cookies.txt -X POST https://localhost/api/balances/5/1/adjust \
  -H "Content-Type: application/json" \
  -d '{"delta":-3,"reason":"Correction"}'

# Attempt an adjustment that would make the balance negative — must be refused
curl -k -b cookies.txt -X POST https://localhost/api/balances/5/1/adjust \
  -H "Content-Type: application/json" \
  -d '{"delta":-100,"reason":"Bad adjustment"}'
```

**Expected**: ledger shows each row with amount, reason, actor (`hr.admin`) and timestamp; the last adjustment is refused with an error indicating balance cannot go negative.

**FRs covered**: FR27–FR31

---

## Scenario 13 — Someone tries to change an audit row in the database

**Role**: direct database access (simulating a DBA or compromised app)

```bash
# Connect to the running Postgres container
docker compose exec postgres psql -U leave -d leave

# Attempt an UPDATE on audit_events
UPDATE audit_events SET action = 'TAMPERED' WHERE id = 1;

# Attempt a DELETE
DELETE FROM audit_events WHERE id = 1;
```

**Expected**: both statements fail with `ERROR: audit_events rows are immutable` (from the Flyway V2 trigger `reject_audit_mutation()`). The same trigger applies to `access_log`.

**FRs covered**: FR44

---

## Scenario 14 — 5 wrong passwords locks the account

**Role**: EMPLOYEE (`emp.jsmith`)

```bash
for i in 1 2 3 4 5; do
  curl -k -X POST https://localhost/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"emp.jsmith","password":"WRONG"}'
done

# 6th attempt (correct password) should still fail due to lockout
curl -k -X POST https://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.jsmith","password":"Passw0rd1"}'
```

**Expected**: after the 5th failure the account is locked for 15 minutes; the 6th attempt returns `401` with an `ACCOUNT_LOCKED` code even with the correct password.

**FRs covered**: FR57

---

## Scenario 15 — Idle 30 minutes expires the session

**Role**: any user (e.g. `emp.jsmith`)

```bash
# Sign in and note the Set-Cookie expiry
curl -k -c cookies.txt -X POST https://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.jsmith","password":"Passw0rd1"}'

# Wait 31 minutes (or advance the system clock), then call any protected endpoint
curl -k -b cookies.txt https://localhost/api/requests
```

**Expected**: the response is `401 SESSION_EXPIRED` (or `INVALID_TOKEN`); the client must log in again.

**FRs covered**: FR58

---

## Outcome record

| # | Scenario | Outcome | Notes |
|---|---|---|---|
| 1 | Employee submits vacation Thu–Tue | PASS | |
| 2 | Employee edits then cancels | PASS | |
| 3 | Manager approves with a comment | PASS | |
| 4 | Manager rejects | PASS | |
| 5 | Discussion, date change, approval | PASS | |
| 6 | Team manager submits leave | PASS | |
| 7 | Top-level manager submits leave | PASS | |
| 8 | Manager acts on another team's request | PASS | |
| 9 | Employee opens another employee's request | PASS | |
| 10 | Request exceeds available days | PASS | |
| 11 | HR creates user, assigns manager, resets password | PASS | |
| 12 | HR sets and adjusts a balance | PASS | |
| 13 | Audit row mutation attempt | PASS | |
| 14 | 5 wrong passwords locks account | PASS | |
| 15 | Idle 30 minutes expires session | PASS | |

**FR55 (HTTPS)**: PARTIAL — HTTPS is active; the certificate is self-signed (generated at proxy build time). The PRD accepts this as the one documented gap.

---

## Architecture confirmation

| Item | Status |
|---|---|
| No `spring-modulith` in `pom.xml` | CONFIRMED (grep returns no match) |
| No `oauth2-resource-server` in `pom.xml` | CONFIRMED (grep returns no match) |
| No secret committed or baked into an image | CONFIRMED — `JWT_SECRET` is required at runtime; `.env` is gitignored (NFR8) |

## Out-of-scope confirmation

Nothing was built for: FR32–FR41 (bulk operations, export, reporting), FR46–FR47 (calendar integrations), FR48–FR49 (audit export), FR60–FR61 (data retention / GDPR deletion).

No scope creep was identified.
