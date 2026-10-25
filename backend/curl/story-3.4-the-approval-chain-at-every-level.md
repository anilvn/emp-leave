# Story 3.4 - The Approval Chain at Every Level

Runnable Bash examples for the seeded employee → team manager → top-level
manager → HR chain, including HR self-approval. Start the HTTPS proxy with
Docker Compose and set a private `JWT_SECRET` in `.env`:

```bash
docker compose up --build -d
BASE=https://localhost/api
```

The local proxy uses a self-signed certificate, so these examples pass `-k`.
Log in as the four seeded users. The seeded password is `Passw0rd1`:

```bash
login() {
  curl -sk -X POST "$BASE/auth/login" \
    -H 'Content-Type: application/json' -D - -o /dev/null \
    -d "{\"username\":\"$1\",\"password\":\"Passw0rd1\"}" \
    | sed -n 's/^Set-Cookie: access_token=\([^;]*\).*/\1/ip' | tr -d '\r'
}

EMPLOYEE_TOKEN=$(login emp.jsmith)
TEAM_MANAGER_TOKEN=$(login mgr.chen)
TOP_MANAGER_TOKEN=$(login mgr.rossi)
HR_TOKEN=$(login hr.admin)
OTHER_MANAGER_TOKEN=$(login mgr.ortiz)
test -n "$EMPLOYEE_TOKEN" && test -n "$TEAM_MANAGER_TOKEN" \
  && test -n "$TOP_MANAGER_TOKEN" && test -n "$HR_TOKEN" \
  && test -n "$OTHER_MANAGER_TOKEN" || {
    echo "Login did not return all access tokens"; exit 1;
  }
```

## Employee → team manager

```bash
EMPLOYEE_REQUEST=$(curl -skS -f -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN" \
  -d '{"leaveTypeId":1,"startDate":"2026-12-01","endDate":"2026-12-02","notes":"Employee leave"}')
EMPLOYEE_REQUEST_ID=$(printf '%s' "$EMPLOYEE_REQUEST" \
  | sed -n 's/.*"id":[[:space:]]*\([0-9][0-9]*\).*/\1/p')
printf '%s\n' "$EMPLOYEE_REQUEST"
```

Expected: `201 Created`, `status: "PENDING"`, `employeeId: 5`, and
`approverId: 3` (`mgr.chen`). The assigned manager can see it in the pending
queue and decide it:

```bash
curl -skS -f "$BASE/approvals/pending" \
  -H "Cookie: access_token=$TEAM_MANAGER_TOKEN"
```

Expected: the pending response contains this request and approval returns
`200 OK` with `status: "APPROVED"` when the assigned manager decides it.

## Cross-team decision and read denials

Before the assigned manager approves the employee request, a manager from
another team cannot decide it:

```bash
curl -ki -X POST "$BASE/approvals/$EMPLOYEE_REQUEST_ID/approve" \
  -H "Cookie: access_token=$OTHER_MANAGER_TOKEN"
curl -ki -X POST "$BASE/approvals/$EMPLOYEE_REQUEST_ID/reject" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$OTHER_MANAGER_TOKEN" \
  -d '{"comment":"Unauthorized decision"}'
```

Expected for both: `403 Forbidden`, `code: "ACCESS_DENIED"`. The request stays
pending; reservation, balance, ledger, audit decision, and notification state
are unchanged. Both refusals are recorded with `allowed = false` in
`access_log`.

The same manager cannot read request detail or history:

```bash
curl -ki "$BASE/requests/$EMPLOYEE_REQUEST_ID" \
  -H "Cookie: access_token=$OTHER_MANAGER_TOKEN"
curl -ki "$BASE/requests/$EMPLOYEE_REQUEST_ID/history" \
  -H "Cookie: access_token=$OTHER_MANAGER_TOKEN"
```

Expected for both: `403 Forbidden`, `code: "ACCESS_DENIED"`, no request data,
and a denied `access_log` row. After these refusal examples, the assigned
manager can approve the still-pending request:

```bash
curl -skS -f -X POST "$BASE/approvals/$EMPLOYEE_REQUEST_ID/approve" \
  -H "Cookie: access_token=$TEAM_MANAGER_TOKEN"
```

## Team manager → top-level manager → HR

The next requests cover the remaining manager levels:

```bash
TEAM_REQUEST=$(curl -skS -f -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$TEAM_MANAGER_TOKEN" \
  -d '{"leaveTypeId":1,"startDate":"2026-12-08","endDate":"2026-12-09","notes":"Manager leave"}')
TEAM_REQUEST_ID=$(printf '%s' "$TEAM_REQUEST" \
  | sed -n 's/.*"id":[[:space:]]*\([0-9][0-9]*\).*/\1/p')
printf '%s\n' "$TEAM_REQUEST"
```

Expected: `201 Created`, `employeeId: 3`, and `approverId: 2`. `mgr.rossi`
retrieves it from `/approvals/pending` and approves it:

```bash
curl -skS -f "$BASE/approvals/pending" \
  -H "Cookie: access_token=$TOP_MANAGER_TOKEN"
curl -skS -f -X POST "$BASE/approvals/$TEAM_REQUEST_ID/approve" \
  -H "Cookie: access_token=$TOP_MANAGER_TOKEN"
```

Then submit as the top-level manager:

```bash
TOP_REQUEST=$(curl -skS -f -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$TOP_MANAGER_TOKEN" \
  -d '{"leaveTypeId":1,"startDate":"2026-12-15","endDate":"2026-12-16","notes":"Top-level manager leave"}')
TOP_REQUEST_ID=$(printf '%s' "$TOP_REQUEST" \
  | sed -n 's/.*"id":[[:space:]]*\([0-9][0-9]*\).*/\1/p')
printf '%s\n' "$TOP_REQUEST"
```

Expected: `201 Created`, `employeeId: 2`, and `approverId: 1` (`hr.admin`).
HR sees and approves this request from the pending queue:

```bash
curl -skS -f "$BASE/approvals/pending" -H "Cookie: access_token=$HR_TOKEN"
curl -skS -f -X POST "$BASE/approvals/$TOP_REQUEST_ID/approve" \
  -H "Cookie: access_token=$HR_TOKEN"
```

Each approval returns `200 OK` with `status: "APPROVED"`.

## HR self-approval

HR has no configured manager, so HR's own request is assigned to HR:

```bash
HR_REQUEST=$(curl -skS -f -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$HR_TOKEN" \
  -d '{"leaveTypeId":1,"startDate":"2026-12-22","endDate":"2026-12-23","notes":"HR leave"}')
HR_REQUEST_ID=$(printf '%s' "$HR_REQUEST" \
  | sed -n 's/.*"id":[[:space:]]*\([0-9][0-9]*\).*/\1/p')
printf '%s\n' "$HR_REQUEST"
curl -skS -f "$BASE/approvals/pending" -H "Cookie: access_token=$HR_TOKEN"
curl -skS -f -X POST "$BASE/approvals/$HR_REQUEST_ID/approve" \
  -H "Cookie: access_token=$HR_TOKEN"
curl -skS -f "$BASE/requests/$HR_REQUEST_ID/history" \
  -H "Cookie: access_token=$HR_TOKEN"
```

Expected: `201 Created` with `employeeId` and `approverId` both `1`; approval
returns `200 OK` and the request history still includes the `APPROVED` entry
with its original `detail`. The immutable `LEAVE_REQUEST_APPROVED` audit
event's `details_json` also contains the JSON boolean `"selfApproval": true`.
Ordinary approvals retain their existing `{"detail":"..."}` payload.
