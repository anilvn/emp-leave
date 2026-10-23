# Story 2.4 — See My Requests and Their Status

Runnable examples for listing and reading leave requests. Start the API locally
or run the HTTPS proxy:

```bash
docker compose up --build
BASE=https://localhost/api
```

The local proxy uses a self-signed certificate, so the examples use `curl -k`.
Obtain cookie values for the seeded roles:

```bash
login_cookie() {
  curl -sk -X POST "$BASE/auth/login" \
    -H 'Content-Type: application/json' \
    -D - -o /dev/null \
    -d "{\"username\":\"$1\",\"password\":\"Passw0rd1\"}" \
    | sed -n 's/^Set-Cookie: access_token=\([^;]*\).*/\1/ip' | tr -d '\r'
}

EMPLOYEE_TOKEN=$(login_cookie emp.jsmith)
MANAGER_TOKEN=$(login_cookie mgr.chen)
HR_TOKEN=$(login_cookie hr.admin)
```

## Employee's requests, optionally filtered by status

```bash
curl -ki "$BASE/requests?status=PENDING" \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN"
```

Expected: `200 OK`, an array containing only `emp.jsmith`'s pending requests.
The supported status values are `PENDING`, `APPROVED`, `REJECTED`, and
`CANCELLED`; a cancelled request remains in the list.

## Manager's requests

```bash
curl -ki "$BASE/requests" \
  -H "Cookie: access_token=$MANAGER_TOKEN"
```

Expected: `200 OK`, the manager's own requests and requests assigned to that
manager for direct reports.

Filter to one direct report:

```bash
curl -ki "$BASE/requests?employeeId=5&status=PENDING" \
  -H "Cookie: access_token=$MANAGER_TOKEN"
```

Expected: `200 OK`, only matching requests for employee id 5 (`emp.jsmith`).
Employee id 9 (`emp.emurphy`) reports to a different manager, so it is refused:

```bash
curl -ki "$BASE/requests?employeeId=9" \
  -H "Cookie: access_token=$MANAGER_TOKEN"
```

Expected: `403 Forbidden`, `code: "ACCESS_DENIED"`, and a denied access-log row.

## HR filters to any employee

```bash
curl -ki "$BASE/requests?employeeId=9" \
  -H "Cookie: access_token=$HR_TOKEN"
```

Expected: `200 OK`, matching requests for employee id 9, with no manager-scope
restriction.

## Request detail

Create a request on a fresh stack and capture the returned id. If you already
have a request, use its id instead.

```bash
REQUEST_ID=$(curl -skS -f -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN" \
  -d '{"leaveTypeId":1,"startDate":"2026-10-29","endDate":"2026-10-29"}' \
  | sed -n 's/.*"id":[[:space:]]*\([0-9][0-9]*\).*/\1/p')
test -n "$REQUEST_ID" || { echo "Could not capture a request id"; exit 1; }

curl -ki "$BASE/requests/$REQUEST_ID" \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN"
```

Expected: `200 OK` with the complete `LeaveRequest` fields when the caller is
the owner, the owner's direct manager, or HR. An employee who is not the owner
cannot read it:

```bash
curl -ki "$BASE/requests/$REQUEST_ID" \
  -H "Cookie: access_token=$(login_cookie emp.alee)"
```

Expected for another employee's request: `403 Forbidden`, `code:
"ACCESS_DENIED"`, and a denied access-log row. A nonexistent request id returns
`404 Not Found` with `code: "NOT_FOUND"`.

An invalid status value, such as `status=UNKNOWN`, returns `400 Bad Request`
with `code: "VALIDATION_FAILED"`.
