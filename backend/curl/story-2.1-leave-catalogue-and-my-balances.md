# Story 2.1 — Leave Catalogue and My Balances

Runnable curl examples for `GET /balances/me`. The endpoint accepts authenticated
employees, managers, and HR administrators and returns all four seeded leave types.

## Start the backend

Run the backend directly for local development:

```bash
cd backend
./mvnw spring-boot:run
BASE=http://localhost:8080
```

Or start the full stack behind the HTTPS proxy:

```bash
docker compose up --build
BASE=https://localhost/api
```

The examples below use the backend directly. For the proxy, add `-k` to curl
commands for the local self-signed certificate and use the cookie-jar login shown
under **HTTPS proxy**.

## Seeded callers

The users seeded by `V5__seed_users.sql` can call `/balances/me` with their assigned
role. Their shared development password is `Passw0rd1`.

| username | role |
|---|---|
| `emp.jsmith` | EMPLOYEE |
| `mgr.rossi` | MANAGER |
| `hr.admin` | HR_ADMIN |

## Successful read

The backend marks its cookie `Secure`, so curl will not replay it over plain HTTP.
Extract the token and send it in a `Cookie` header when running directly:

```bash
TOKEN=$(curl -sS -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -D - -o /dev/null \
  -d '{"username":"emp.jsmith","password":"Passw0rd1"}' \
  | sed -n 's/^Set-Cookie: access_token=\([^;]*\).*/\1/ip' | tr -d '\r')

curl -i "$BASE/balances/me" \
  -H "Cookie: access_token=$TOKEN"
```

Expected: `200 OK` and four entries, with totals of 20 Vacation days, 10 Sick days,
30 Parental days, and 0 Sabbatical days. `availableDays` equals `totalDays -
reservedDays`.

With the HTTPS proxy, curl can store and replay the secure cookie itself:

```bash
curl -k -i -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -c employee-cookies.txt \
  -d '{"username":"emp.jsmith","password":"Passw0rd1"}'

curl -k -i "$BASE/balances/me" -b employee-cookies.txt
```

Change the login username to `mgr.rossi` or `hr.admin` to read the same response
shape as a manager or HR administrator.

## Authentication failures

No cookie returns `401 Unauthorized`:

```bash
curl -i "$BASE/balances/me"
```

A logged-in user whose temporary password still needs to be changed receives
`403 Forbidden` with `code: PASSWORD_CHANGE_REQUIRED`. To exercise this locally,
set the seeded employee's flag in PostgreSQL, call the endpoint using the token
from the successful login example, then restore the flag:

```sql
UPDATE users SET must_change_password = true WHERE id = 5;
```

```bash
curl -i "$BASE/balances/me" -H "Cookie: access_token=$TOKEN"
```

```sql
UPDATE users SET must_change_password = false WHERE id = 5;
```

## Verify seeded catalogue and balances

The additive migrations are `V7__create_leave_types_table.sql`,
`V8__create_leave_balances_table.sql`, and `V9__seed_leave_types_and_balances.sql`.
This query should return four rows with 12 users per leave type:

```sql
SELECT types.name, balances.total_days, count(*) AS users
FROM leave_balances balances
JOIN leave_types types ON types.id = balances.leave_type_id
GROUP BY types.name, balances.total_days
ORDER BY types.name;
```

There should be 48 balance rows in total. The database rejects any write that
would make `total_days - reserved_days` negative.
