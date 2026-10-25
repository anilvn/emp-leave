# Story 4.2 — HR Sets a Balance with a Required Reason

All requests require an authenticated HR admin session. Obtain a cookie via `POST /api/auth/login` as user `hr_admin` (seeded demo cast) before running these examples.

## Success — set vacation balance to 25 days

```bash
curl -s -X PUT https://localhost/api/balances/5/1 \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"totalDays": 25, "reason": "Annual Vacation allotment for 2027"}' | jq .
```

Expected: 200

```json
{
  "userId": 5,
  "leaveTypeId": 1,
  "totalDays": 25,
  "reservedDays": 0,
  "availableDays": 25
}
```

Verify ledger row written:

```bash
# Direct DB check (inside postgres container)
docker compose exec postgres psql -U company_leave -c \
  "SELECT change_days, kind, reason, actor_id, request_id FROM balance_ledger WHERE user_id = 5 AND kind = 'SET';"
```

Expected: one row with `change_days = 5`, `kind = SET`, reason as supplied, `request_id = NULL`.

---

## Validation error — blank reason (400)

```bash
curl -s -X PUT https://localhost/api/balances/5/1 \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"totalDays": 20, "reason": ""}' | jq .
```

Expected: 400

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "code": "VALIDATION_FAILED"
}
```

---

## Conflict — totalDays below reserved days (409)

Set up: user 5 has 4 reserved days from a pending request.

```bash
curl -s -X PUT https://localhost/api/balances/5/1 \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"totalDays": 2, "reason": "Budget cut"}' | jq .
```

Expected: 409

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Cannot set total to 2 days; 4 days are already reserved for pending requests.",
  "code": "INSUFFICIENT_BALANCE"
}
```

---

## Access denied — non-HR caller (403)

Log in as an employee (`employee1`) and attempt the same request:

```bash
curl -s -X PUT https://localhost/api/balances/5/1 \
  -H "Content-Type: application/json" \
  -b employee_cookies.txt \
  -d '{"totalDays": 25, "reason": "Employee trying to set own balance"}' | jq .
```

Expected: 403

```json
{
  "status": 403,
  "code": "ACCESS_DENIED"
}
```
