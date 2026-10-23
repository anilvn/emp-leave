# Story 2.5 — Edit a Pending Request

Runnable examples for editing a pending leave request. Start the API locally
or run the HTTPS proxy:

```bash
cp .env.example .env
# Set a private JWT_SECRET value in .env; do not commit it.
docker compose up --build
BASE=https://localhost/api
```

The local proxy uses a self-signed certificate, so the examples use `curl -k`.
Log in and keep the employee's auth cookie:

```bash
TOKEN=$(curl -sk -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -D - -o /dev/null \
  -d '{"username":"emp.jsmith","password":"Passw0rd1"}' \
  | sed -n 's/^Set-Cookie: access_token=\([^;]*\).*/\1/ip' | tr -d '\r')

create_pending() {
  curl -skS -f -X POST "$BASE/requests" \
    -H 'Content-Type: application/json' \
    -H "Cookie: access_token=$TOKEN" \
    -d '{"leaveTypeId":1,"startDate":"2026-10-29","endDate":"2026-11-03","notes":"Original"}' \
    | sed -n 's/.*"id":[[:space:]]*\([0-9][0-9]*\).*/\1/p'
}
```

## Successful edit

Create a pending request and capture its id:

```bash
REQUEST_ID=$(create_pending)
test -n "$REQUEST_ID" || { echo "Could not capture a request id"; exit 1; }
```

Shorten the request by one working day:

```bash
curl -ki -X PUT "$BASE/requests/$REQUEST_ID" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$TOKEN" \
  -d '{"leaveTypeId":1,"startDate":"2026-10-30","endDate":"2026-11-03","notes":"Trip shortened by a day"}'
```

Expected: `200 OK`, the same request id and owner, `workingDays: 3`,
`status: "PENDING"`, updated dates/notes, and unchanged submission/decision
fields. The reservation becomes three days and the ledger records releasing
the old reservation and reserving the new amount.

## Invalid transition (409)

For a local demonstration, change a request to a terminal status in the
database, then attempt to edit it:

```bash
REQUEST_ID=$(create_pending)
echo "Set this request's status to APPROVED in PostgreSQL: $REQUEST_ID"
```

In the local PostgreSQL database, set that id to a terminal status:

```sql
UPDATE leave_requests SET status = 'APPROVED' WHERE id = <request-id>;
```

Then:

```bash
curl -ki -X PUT "$BASE/requests/$REQUEST_ID" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$TOKEN" \
  -d '{"leaveTypeId":1,"startDate":"2026-10-30","endDate":"2026-11-03","notes":"Changed"}'
```

Expected: `409 Conflict` with `code: "INVALID_TRANSITION"` and no request or
balance change.

## Insufficient balance (409)

Submit a short pending request, then try to extend it beyond the employee's
remaining Vacation balance:

```bash
REQUEST_ID=$(create_pending)
curl -ki -X PUT "$BASE/requests/$REQUEST_ID" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$TOKEN" \
  -d '{"leaveTypeId":1,"startDate":"2026-10-05","endDate":"2026-11-13","notes":"Too many days"}'
```

Expected: `409 Conflict` with `code: "INSUFFICIENT_BALANCE"`. The existing
dates, notes and reservation remain unchanged.

## Access denied (403)

The request owner is the only user who may edit it. For example, another
employee, the owner's manager, or HR cannot edit this request:

```bash
REQUEST_ID=$(create_pending)
OTHER_TOKEN=$(curl -sk -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -D - -o /dev/null \
  -d '{"username":"emp.alee","password":"Passw0rd1"}' \
  | sed -n 's/^Set-Cookie: access_token=\([^;]*\).*/\1/ip' | tr -d '\r')
curl -ki -X PUT "$BASE/requests/$REQUEST_ID" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$OTHER_TOKEN" \
  -d '{"leaveTypeId":1,"startDate":"2026-10-30","endDate":"2026-11-03","notes":"Not mine"}'
```

Expected: `403 Forbidden` with `code: "ACCESS_DENIED"` and a denied access-log
row. Changing `leaveTypeId` also returns `400` with
`code: "LEAVE_TYPE_CHANGE_NOT_ALLOWED"`; an end date before the start or a
weekend-only date range returns `400` with `code: "VALIDATION_FAILED"`.
