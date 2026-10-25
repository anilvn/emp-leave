# Story 3.3 - Reject a Request with a Required Comment

Runnable Bash examples for rejecting a pending leave request. Start the HTTPS
proxy with Docker Compose and set a private `JWT_SECRET` in `.env`:

```bash
docker compose up --build -d
BASE=https://localhost/api
```

The local proxy uses a self-signed certificate, so the examples use `curl -k`.
Log in as an employee, submit a request, and log in as the assigned manager:

```bash
EMPLOYEE_TOKEN=$(curl -sk -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -D - -o /dev/null \
  -d '{"username":"emp.jsmith","password":"Passw0rd1"}' \
  | sed -n 's/^Set-Cookie: access_token=\([^;]*\).*/\1/ip' | tr -d '\r')
MANAGER_TOKEN=$(curl -sk -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -D - -o /dev/null \
  -d '{"username":"mgr.chen","password":"Passw0rd1"}' \
  | sed -n 's/^Set-Cookie: access_token=\([^;]*\).*/\1/ip' | tr -d '\r')
test -n "$EMPLOYEE_TOKEN" && test -n "$MANAGER_TOKEN" || {
  echo "Login did not return both access tokens"; exit 1;
}

REQUEST_ID=$(curl -skS -f -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN" \
  -d '{"leaveTypeId":1,"startDate":"2026-10-29","endDate":"2026-11-03","notes":"Family trip"}' \
  | sed -n 's/.*"id":[[:space:]]*\([0-9][0-9]*\).*/\1/p')
test -n "$REQUEST_ID" || { echo "Could not capture a request id"; exit 1; }
```

## Reject with a reason

```bash
curl -ki -X POST "$BASE/approvals/$REQUEST_ID/reject" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$MANAGER_TOKEN" \
  -d '{"comment":"Please resubmit after the month-end close."}'
```

Expected: `200 OK`, `"status":"REJECTED"`, the assigned manager in
`approverId`, a decision timestamp, and the supplied reason. Reserved days are
released without changing total entitlement. One `RELEASE` ledger entry and
one `LEAVE_REQUEST_REJECTED` audit event are written; a `REJECTED`
notification is attempted after commit. If notification recording fails, the
rejection remains committed and no email is sent.

```bash
REJECTED_REQUEST_ID=$REQUEST_ID
```

## Missing or blank reason (400)

Create a fresh pending request for the validation checks:

```bash
REQUEST_ID=$(curl -skS -f -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN" \
  -d '{"leaveTypeId":1,"startDate":"2026-11-04","endDate":"2026-11-09","notes":"Second family trip"}' \
  | sed -n 's/.*"id":[[:space:]]*\([0-9][0-9]*\).*/\1/p')
test -n "$REQUEST_ID" || { echo "Could not capture a request id"; exit 1; }
```

Then try the endpoint with a blank comment:

```bash
curl -ki -X POST "$BASE/approvals/$REQUEST_ID/reject" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$MANAGER_TOKEN" \
  -d '{"comment":"   "}'
```

Expected: `400 Bad Request` with `code: "COMMENT_REQUIRED"`. The request
remains pending and its reservation, balance ledger, audit history, and
notifications are unchanged. A missing body, null comment, and empty comment
return the same error.

## Invalid transition (409)

Retry rejection for the request accepted above:

```bash
curl -ki -X POST "$BASE/approvals/$REJECTED_REQUEST_ID/reject" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$MANAGER_TOKEN" \
  -d '{"comment":"Please resubmit later."}'
```

Expected: `409 Conflict` with `code: "INVALID_TRANSITION"` and no additional
balance change, ledger entry, audit event, or notification.

## Access denied (403), unauthenticated (401), and unknown request (404)

An employee cannot reject a request even when they own it:

```bash
curl -ki -X POST "$BASE/approvals/$REQUEST_ID/reject" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN" \
  -d '{"comment":"Please resubmit later."}'
```

Expected: `403 Forbidden` with `code: "ACCESS_DENIED"` and a denied access-log
row. An unauthenticated call returns `401`; an authenticated manager rejecting
an unknown id returns `404`. These failures do not change request or balance
state.
