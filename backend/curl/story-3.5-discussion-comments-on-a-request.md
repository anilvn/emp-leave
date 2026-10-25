# Story 3.5 — Discussion Comments on a Request

Runnable Bash examples for adding and listing discussion comments on a leave
request. Start the HTTPS proxy with Docker Compose and set a private
`JWT_SECRET` in `.env`:

```bash
docker compose up --build -d
BASE=https://localhost/api
```

The local proxy uses a self-signed certificate, so these examples pass `-k`.
Log in as the seeded employee, their manager, HR, and an unrelated employee:

```bash
login() {
  curl -sk -X POST "$BASE/auth/login" \
    -H 'Content-Type: application/json' -D - -o /dev/null \
    -d "{\"username\":\"$1\",\"password\":\"Passw0rd1\"}" \
    | sed -n 's/^Set-Cookie: access_token=\([^;]*\).*/\1/ip' | tr -d '\r'
}

EMPLOYEE_TOKEN=$(login emp.jsmith)
MANAGER_TOKEN=$(login mgr.chen)
HR_TOKEN=$(login hr.admin)
OTHER_TOKEN=$(login emp.torres)
test -n "$EMPLOYEE_TOKEN" && test -n "$MANAGER_TOKEN" \
  && test -n "$HR_TOKEN" && test -n "$OTHER_TOKEN" || {
    echo "Login did not return all access tokens"; exit 1;
  }
```

## Setup: submit a leave request

```bash
REQUEST=$(curl -skS -f -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN" \
  -d '{"leaveTypeId":1,"startDate":"2026-12-01","endDate":"2026-12-05","notes":"Year-end break"}')
REQUEST_ID=$(printf '%s' "$REQUEST" \
  | sed -n 's/.*"id":[[:space:]]*\([0-9][0-9]*\).*/\1/p')
printf '%s\n' "$REQUEST"
```

Expected: `201 Created`, `status: "PENDING"`.

## Add a comment (authorized — manager)

```bash
curl -skS -f -X POST "$BASE/requests/$REQUEST_ID/comments" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$MANAGER_TOKEN" \
  -d '{"body":"Please arrange team coverage for the first two days."}'
```

Expected: `201 Created` with `id`, `requestId`, `authorId`, `authorUsername`,
`body`, and `createdAt` set. An `audit_events` row with
`entity = REQUEST_COMMENT`, `action = REQUEST_COMMENT_ADDED` is committed in
the same transaction.

## Add a comment (authorized — request owner)

```bash
curl -skS -f -X POST "$BASE/requests/$REQUEST_ID/comments" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN" \
  -d '{"body":"Coverage is arranged with emp.jones."}'
```

Expected: `201 Created`, `authorUsername: "emp.jsmith"`.

## List comments (authorized — oldest first)

```bash
curl -skS -f "$BASE/requests/$REQUEST_ID/comments" \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN"
```

Expected: `200 OK`, array of two comments in `createdAt ASC, id ASC` order,
each with `authorUsername` set.

## Comment appears in the request history

```bash
curl -skS -f "$BASE/requests/$REQUEST_ID/history" \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN"
```

Expected: the history timeline contains `COMMENT` entries interleaved with
`SUBMITTED` and any other events, each with `detail` equal to the comment body.

## Add and list comments (authorized — HR)

HR can read and post on any request:

```bash
curl -skS -f -X POST "$BASE/requests/$REQUEST_ID/comments" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$HR_TOKEN" \
  -d '{"body":"Noted for the audit trail."}'
```

Expected: `201 Created`, `authorUsername: "hr.admin"`.

```bash
curl -skS -f "$BASE/requests/$REQUEST_ID/comments" \
  -H "Cookie: access_token=$HR_TOKEN"
```

Expected: `200 OK` with all comments including the one just posted, each with
`authorUsername` set and results in `createdAt ASC, id ASC` order.

## Unauthorized caller is denied

An employee with no relation to this request cannot read or post comments:

```bash
curl -ki -X POST "$BASE/requests/$REQUEST_ID/comments" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$OTHER_TOKEN" \
  -d '{"body":"Unauthorized comment."}'
curl -ki "$BASE/requests/$REQUEST_ID/comments" \
  -H "Cookie: access_token=$OTHER_TOKEN"
```

Expected for both: `403 Forbidden`, `code: "ACCESS_DENIED"`, and an
`access_log` row with `allowed = false`. No comment row is written.

## Blank body is rejected

```bash
curl -ki -X POST "$BASE/requests/$REQUEST_ID/comments" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$MANAGER_TOKEN" \
  -d '{"body":"   "}'
```

Expected: `400 Bad Request`, `code: "VALIDATION_FAILED"`. No comment row is
written.

## Comments on a terminal (approved) request

Comments may be added regardless of request status. After the manager approves:

```bash
curl -skS -f -X POST "$BASE/approvals/$REQUEST_ID/approve" \
  -H "Cookie: access_token=$MANAGER_TOKEN"
curl -skS -f -X POST "$BASE/requests/$REQUEST_ID/comments" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN" \
  -d '{"body":"Thanks for approving."}'
```

Expected: `201 Created` — request status does not block comment posting.
