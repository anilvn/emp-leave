# Story 2.6 — Cancel a Pending Request

Runnable Bash examples for cancelling a pending leave request. Run them with
Bash (for example, Git Bash or WSL on Windows). Start the HTTPS proxy with
Docker Compose:

```bash
cp .env.example .env
# Set a private JWT_SECRET value in .env; do not commit it.
docker compose up --build
BASE=https://localhost/api
```

The local proxy uses a self-signed certificate, so the examples use `curl -k`.
Log in as the request owner and keep the authentication cookie:

```bash
TOKEN=$(curl -sk -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -D - -o /dev/null \
  -d '{"username":"emp.jsmith","password":"Passw0rd1"}' \
  | sed -n 's/^Set-Cookie: access_token=\([^;]*\).*/\1/ip' | tr -d '\r')
test -n "$TOKEN" || { echo "Login did not return an access token"; exit 1; }

REQUEST_ID=$(curl -skS -f -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$TOKEN" \
  -d '{"leaveTypeId":1,"startDate":"2026-10-29","endDate":"2026-11-03","notes":"Family trip"}' \
  | sed -n 's/.*"id":[[:space:]]*\([0-9][0-9]*\).*/\1/p')
test -n "$REQUEST_ID" || { echo "Could not capture a request id"; exit 1; }
```

## Successful cancellation

```bash
curl -ki -X POST "$BASE/requests/$REQUEST_ID/cancel" \
  -H "Cookie: access_token=$TOKEN"
```

Expected: `200 OK`, the response has the same request id and
`"status":"CANCELLED"`. The request remains in `GET /api/requests`; its
reserved working days are released, with a `RELEASE` ledger row and a
`LEAVE_REQUEST_CANCELLED` audit event.

## Invalid transition (409)

A second cancellation attempt uses the same now-cancelled request:

```bash
curl -ki -X POST "$BASE/requests/$REQUEST_ID/cancel" \
  -H "Cookie: access_token=$TOKEN"
```

Expected: `409 Conflict` with `code: "INVALID_TRANSITION"`. No additional
balance release, ledger entry, or cancellation audit event is written.

## Access denied (403)

Another employee cannot cancel the owner's request:

```bash
OTHER_TOKEN=$(curl -sk -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -D - -o /dev/null \
  -d '{"username":"emp.alee","password":"Passw0rd1"}' \
  | sed -n 's/^Set-Cookie: access_token=\([^;]*\).*/\1/ip' | tr -d '\r')
test -n "$OTHER_TOKEN" || { echo "Other employee login did not return an access token"; exit 1; }

curl -ki -X POST "$BASE/requests/$REQUEST_ID/cancel" \
  -H "Cookie: access_token=$OTHER_TOKEN"
```

Expected: `403 Forbidden` with `code: "ACCESS_DENIED"` and a denied access-log
row. The request and its balance are unchanged.
