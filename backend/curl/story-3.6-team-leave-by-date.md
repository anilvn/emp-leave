# Story 3.6 — Team Leave by Date

Runnable Bash examples for the `GET /api/team/leave` endpoint. Start the
HTTPS proxy with Docker Compose and set a private `JWT_SECRET` in `.env`:

```bash
docker compose up --build -d
BASE=https://localhost/api
```

The local proxy uses a self-signed certificate, so these examples pass `-k`.
Log in as a manager and an employee to demonstrate access control:

```bash
login() {
  curl -sk -X POST "$BASE/auth/login" \
    -H 'Content-Type: application/json' -D - -o /dev/null \
    -d "{\"username\":\"$1\",\"password\":\"Passw0rd1\"}" \
    | sed -n 's/^Set-Cookie: access_token=\([^;]*\).*/\1/ip' | tr -d '\r'
}

MANAGER_TOKEN=$(login mgr.chen)
HR_TOKEN=$(login hr.admin)
EMPLOYEE_TOKEN=$(login emp.jsmith)
test -n "$MANAGER_TOKEN" && test -n "$HR_TOKEN" && test -n "$EMPLOYEE_TOKEN" || {
  echo "Login did not return all access tokens"; exit 1;
}
```

## 1. All approved team leave (no date filter)

Returns every approved leave request for the manager's direct reports,
with comments and history nested inline.

```bash
curl -sk "$BASE/team/leave" \
  -H "Cookie: access_token=$MANAGER_TOKEN" | jq .
```

Expected: `200 OK`, JSON array of `TeamLeaveEntry` objects (empty array if no
approved requests exist yet).

## 2. Filter by date range

Only requests that overlap August 2026 are returned. A request ending before
August or starting after August is excluded.

```bash
curl -sk "$BASE/team/leave?from=2026-08-01&to=2026-08-31" \
  -H "Cookie: access_token=$MANAGER_TOKEN" | jq .
```

Expected: `200 OK`, entries where `startDate <= 2026-08-31` and
`endDate >= 2026-08-01`.

## 3. Only a lower bound (no end cap)

```bash
curl -sk "$BASE/team/leave?from=2026-09-01" \
  -H "Cookie: access_token=$MANAGER_TOKEN" | jq .
```

Expected: `200 OK`, all approved requests that end on or after 2026-09-01.

## 4. Only an upper bound (no start cap)

```bash
curl -sk "$BASE/team/leave?to=2026-12-31" \
  -H "Cookie: access_token=$MANAGER_TOKEN" | jq .
```

Expected: `200 OK`, all approved requests that start on or before 2026-12-31.

## 5. HR_ADMIN is refused

Only the MANAGER role may call this endpoint.

```bash
curl -sk "$BASE/team/leave" \
  -H "Cookie: access_token=$HR_TOKEN" | jq .
```

Expected: `403 Forbidden`, body `{"code":"ACCESS_DENIED",...}`.

## 6. EMPLOYEE is refused

```bash
curl -sk "$BASE/team/leave" \
  -H "Cookie: access_token=$EMPLOYEE_TOKEN" | jq .
```

Expected: `403 Forbidden`, body `{"code":"ACCESS_DENIED",...}`.

## 7. No cookie → 401

```bash
curl -sk "$BASE/team/leave" | jq .
```

Expected: `401 Unauthorized`.
