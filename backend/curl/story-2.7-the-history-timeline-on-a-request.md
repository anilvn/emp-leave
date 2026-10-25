# Story 2.7 — The History Timeline on a Request

Runnable examples for creating a request, changing it twice, cancelling it, and
reading its timeline. Start the API locally or run the HTTPS proxy:

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

## Create a request and generate history events

```bash
REQUEST_ID=$(curl -skS -f -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN" \
  -d '{"leaveTypeId":1,"startDate":"2026-10-29","endDate":"2026-11-03","notes":"initial"}' \
  | sed -n 's/.*"id":[[:space:]]*\([0-9][0-9]*\).*/\1/p')
test -n "$REQUEST_ID" || { echo "Could not capture a request id"; exit 1; }

curl -skS -f -X PUT "$BASE/requests/$REQUEST_ID" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN" \
  -d '{"leaveTypeId":1,"startDate":"2026-10-30","endDate":"2026-11-03","notes":"first edit"}'

curl -skS -f -X PUT "$BASE/requests/$REQUEST_ID" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN" \
  -d '{"leaveTypeId":1,"startDate":"2026-11-02","endDate":"2026-11-03","notes":"second edit"}'

curl -skS -f -X POST "$BASE/requests/$REQUEST_ID/cancel" \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN"
```

## Read the timeline

```bash
curl -ki "$BASE/requests/$REQUEST_ID/history" \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN"
```

Expected: `200 OK` with four entries oldest first: `SUBMITTED`, two `EDITED`
entries, and `CANCELLED`. Each entry includes `type`, `actorId`, `actorUsername`,
`occurredAt`, and readable `detail` describing the range and working days. The owner, the
owner's direct manager, and HR may read the same history:

```bash
curl -ki "$BASE/requests/$REQUEST_ID/history" \
  -H "Cookie: access_token=$MANAGER_TOKEN"
curl -ki "$BASE/requests/$REQUEST_ID/history" \
  -H "Cookie: access_token=$HR_TOKEN"
```

An employee who is not the owner cannot read it:

```bash
curl -ki "$BASE/requests/$REQUEST_ID/history" \
  -H "Cookie: access_token=$(login_cookie emp.alee)"
```

Expected: `403 Forbidden`, `code: "ACCESS_DENIED"`, and a denied access-log
row. A nonexistent request ID returns `404 Not Found` with `code: "NOT_FOUND"`.
History rows created before detail snapshots were recorded return the explicit
detail `Event details were not recorded.` New submission, edit, and cancellation
rows contain readable summaries captured at the time of each change.
