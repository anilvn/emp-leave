# Story 4.5 — The HR Audit Report, One Entry per Request

Curl examples for `GET /api/audit`. All requests require an HR_ADMIN session cookie.

## Setup

All requests require an authenticated HR_ADMIN session. Obtain a cookie via `POST /api/auth/login` as the `hr.admin` user from the seeded demo cast (see the seed migration for the temporary password, then change it via `POST /api/auth/change-password`).

```bash
# Log in as HR admin and save the cookie
curl -c cookies.txt -s -X POST https://localhost/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"hr.admin","password":"<password-from-seed>"}' | jq .
```

---

## 1. Get the full audit report (happy path)

Returns one `AuditRequestReport` per leave request, any status, newest submission first.
Each entry carries the request's full comment thread and History timeline.

```bash
curl -b cookies.txt -s https://localhost/api/audit | jq .
```

**Expected response (200):**
```json
[
  {
    "requestId": 101,
    "employeeId": 5,
    "employeeUsername": "emp.jsmith",
    "leaveTypeId": 1,
    "leaveTypeName": "Vacation",
    "startDate": "2026-10-29",
    "endDate": "2026-11-03",
    "workingDays": 4,
    "status": "APPROVED",
    "comments": [
      {
        "id": 1,
        "requestId": 101,
        "authorId": 3,
        "authorUsername": "mgr.johnson",
        "body": "Approved, enjoy your break.",
        "createdAt": "2026-10-22T10:00:00Z"
      }
    ],
    "history": [
      {
        "type": "SUBMITTED",
        "actorId": 5,
        "actorUsername": "emp.jsmith",
        "occurredAt": "2026-10-20T09:00:00Z",
        "detail": "Submitted Vacation, 2026-10-29 to 2026-11-03 (4 working days)."
      },
      {
        "type": "APPROVED",
        "actorId": 3,
        "actorUsername": "mgr.johnson",
        "occurredAt": "2026-10-22T10:00:00Z",
        "detail": "Approved, enjoy your break."
      },
      {
        "type": "COMMENT",
        "actorId": 3,
        "actorUsername": "mgr.johnson",
        "occurredAt": "2026-10-22T10:00:00Z",
        "detail": "Approved, enjoy your break."
      }
    ]
  }
]
```

---

## 2. No requests exist — empty array

```bash
curl -b cookies.txt -s https://localhost/api/audit | jq 'length'
```

**Expected:** `0`

---

## 3. Access denied — EMPLOYEE caller

```bash
# Log in as employee (use seed password and complete change-password flow first)
curl -c emp_cookies.txt -s -X POST https://localhost/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"emp.jsmith","password":"<password-from-seed>"}' | jq .

curl -b emp_cookies.txt -s https://localhost/api/audit | jq .
```

**Expected response (403):**
```json
{
  "type": "about:blank",
  "title": "Access denied",
  "status": 403,
  "detail": "Only HR can view the audit trail.",
  "instance": "/api/audit",
  "code": "ACCESS_DENIED"
}
```

---

## 4. Access denied — MANAGER caller

```bash
# Log in as manager (use seed password and complete change-password flow first)
curl -c mgr_cookies.txt -s -X POST https://localhost/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"mgr.johnson","password":"<password-from-seed>"}' | jq .

curl -b mgr_cookies.txt -s https://localhost/api/audit | jq .
```

**Expected:** 403 `ACCESS_DENIED` (same shape as above)

---

*Note: The local stack uses a self-signed certificate; add `-k` to skip TLS verification during local testing.*
