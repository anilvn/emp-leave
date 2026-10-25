# Story 3.2 — Approve a Request with an Optional Comment

Runnable Bash examples for approving a pending request. Start the HTTPS proxy
with Docker Compose and set a private `JWT_SECRET` in `.env`:

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

## Approve with a comment

```bash
curl -ki -X POST "$BASE/approvals/$REQUEST_ID/approve" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$MANAGER_TOKEN" \
  -d '{"comment":"Enjoy your trip!"}'
```

Expected: `200 OK`, `"status":"APPROVED"`, the assigned manager in
`approverId`, a decision timestamp, and the supplied comment. Reserved days
and total entitlement both decrease by the working-day count, preserving
available days. One `DEDUCT` ledger entry and one
`LEAVE_REQUEST_APPROVED` audit event are written, then an `APPROVED`
notification is attempted for the employee after commit. If notification
recording fails, the approval remains committed and the endpoint still
succeeds; no email is sent.

## Approve without a comment

Submit another pending request, capture its id as `REQUEST_ID`, then call the
same endpoint without a request body:

```bash
curl -ki -X POST "$BASE/approvals/$REQUEST_ID/approve" \
  -H "Cookie: access_token=$MANAGER_TOKEN"
```

Expected: `200 OK` with `"status":"APPROVED"` and `"decisionComment":null`.

## Invalid transition (409)

Retry approval for a request that was already approved:

```bash
curl -ki -X POST "$BASE/approvals/$REQUEST_ID/approve" \
  -H "Cookie: access_token=$MANAGER_TOKEN"
```

Expected: `409 Conflict` with `code: "INVALID_TRANSITION"`. No additional
balance change, ledger entry, audit event, or notification is written.

## Access denied (403), unauthenticated (401), and unknown request (404)

An employee cannot approve a request even when they own it:

```bash
curl -ki -X POST "$BASE/approvals/$REQUEST_ID/approve" \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN"
```

Expected: `403 Forbidden` with `code: "ACCESS_DENIED"` and a denied access-log
row. An unauthenticated call returns `401`; an authenticated manager approving
an unknown id returns `404`. These failures do not change request or balance
state.
