# Story 2.2 — Submit a Leave Request Against a Real Balance

Runnable curl examples for `POST /requests`. Any authenticated user can submit a
leave request. Working days are computed server-side (Mon-Fri inclusive, holidays
ignored). The requested days are reserved against the employee's balance.

## Start the backend

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

## Obtain a token

The backend marks its cookie `Secure`, so curl will not replay it over plain HTTP.
Extract the token and send it in a `Cookie` header:

```bash
TOKEN=$(curl -sS -X POST "$BASE/auth/login" \
  -H 'Content-Type: application/json' \
  -D - -o /dev/null \
  -d '{"username":"emp.jsmith","password":"Passw0rd1"}' \
  | sed -n 's/^Set-Cookie: access_token=\([^;]*\).*/\1/ip' | tr -d '\r')
```

## Successful submission (Thu to Tue = 4 working days)

```bash
curl -i -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$TOKEN" \
  -d '{
    "leaveTypeId": 1,
    "startDate": "2026-10-29",
    "endDate": "2026-11-03",
    "notes": "Family trip"
  }'
```

Expected: `201 Created` with a JSON body containing `workingDays: 4`,
`status: "PENDING"`, `approverId` set to the employee's manager (user id 3),
and `submittedAt` populated.

## Successful submission without notes

```bash
curl -i -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$TOKEN" \
  -d '{
    "leaveTypeId": 1,
    "startDate": "2026-11-09",
    "endDate": "2026-11-13"
  }'
```

Expected: `201 Created` with `notes: null` and `workingDays: 5`.

## End date before start date (400)

```bash
curl -i -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$TOKEN" \
  -d '{
    "leaveTypeId": 1,
    "startDate": "2026-11-03",
    "endDate": "2026-10-29"
  }'
```

Expected: `400 Bad Request` with `code: "VALIDATION_FAILED"` and
`detail: "endDate must be on or after startDate"`.

## Weekend-only range — zero working days (400)

```bash
curl -i -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$TOKEN" \
  -d '{
    "leaveTypeId": 1,
    "startDate": "2026-10-31",
    "endDate": "2026-11-01"
  }'
```

Expected: `400 Bad Request` with `code: "VALIDATION_FAILED"` and
`detail: "Range yields zero working days"`.

## Insufficient balance (409)

After submitting enough requests to exhaust the 20 Vacation days, submit one more:

```bash
curl -i -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$TOKEN" \
  -d '{
    "leaveTypeId": 1,
    "startDate": "2026-10-05",
    "endDate": "2026-11-06"
  }'
```

Expected: `409 Conflict` with `code: "INSUFFICIENT_BALANCE"` and a detail message
naming `requested`, `available`, `total`, and `reserved` day counts.

## Invalid leave type (400)

```bash
curl -i -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$TOKEN" \
  -d '{
    "leaveTypeId": 9999,
    "startDate": "2026-10-29",
    "endDate": "2026-11-03"
  }'
```

Expected: `400 Bad Request` with `code: "VALIDATION_FAILED"`.

## Unauthenticated (401)

```bash
curl -i -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -d '{
    "leaveTypeId": 1,
    "startDate": "2026-10-29",
    "endDate": "2026-11-03"
  }'
```

Expected: `401 Unauthorized`.

## Must-change-password (403)

Log in with an account that has `must_change_password = true`:

```sql
UPDATE users SET must_change_password = true WHERE id = 5;
```

```bash
curl -i -X POST "$BASE/requests" \
  -H 'Content-Type: application/json' \
  -H "Cookie: access_token=$TOKEN" \
  -d '{
    "leaveTypeId": 1,
    "startDate": "2026-10-29",
    "endDate": "2026-11-03"
  }'
```

Expected: `403 Forbidden` with `code: "PASSWORD_CHANGE_REQUIRED"`.

```sql
UPDATE users SET must_change_password = false WHERE id = 5;
```
