# Story 2.3 — Notification on Submission and My Notification History

Runnable curl examples for submission notifications and the caller's own notification history.

## Start the backend

```bash
cd backend
./mvnw spring-boot:run
BASE=http://localhost:8080
```

Or start the full stack behind the HTTPS proxy and use `BASE=https://localhost/api`
with `-k` for its self-signed certificate.

The seeded employee `emp.jsmith` (user 5) reports to manager `mgr.chen` (user 3);
all seeded users use password `Passw0rd1`.

## Log in as the employee and manager

```bash
EMPLOYEE_TOKEN=$(curl -sS -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' -D - -o /dev/null \
  -d '{"username":"emp.jsmith","password":"Passw0rd1"}' \
  | sed -n 's/^Set-Cookie: access_token=\([^;]*\).*/\1/ip' | tr -d '\r')

MANAGER_TOKEN=$(curl -sS -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' -D - -o /dev/null \
  -d '{"username":"mgr.chen","password":"Passw0rd1"}' \
  | sed -n 's/^Set-Cookie: access_token=\([^;]*\).*/\1/ip' | tr -d '\r')
```

## Submit a request

```bash
curl -i -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN" \
  -d '{
    "leaveTypeId": 1,
    "startDate": "2026-10-29",
    "endDate": "2026-11-03",
    "notes": "Family trip"
  }'
```

Expected: `201 Created`. After the request, reservation, ledger, and audit event
commit, the manager receives one `SUBMITTED` notification. The application log
prints the rendered subject/body; no mail server is contacted.

## Read the manager's notification history

```bash
curl -i "$BASE/notifications" \
  -H "Cookie: access_token=$MANAGER_TOKEN"
```

Expected: `200 OK` with an array containing the manager's own notification rows
(`id`, `userId`, `requestId`, `kind`, `subject`, and `sentAt`). An employee or
another manager receives only their own rows; the caller cannot request a
different user's history.

## Unauthenticated history request

```bash
curl -i "$BASE/notifications"
```

Expected: `401 Unauthorized`.
