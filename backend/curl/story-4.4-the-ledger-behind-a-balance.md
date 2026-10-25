# Story 4.4 — The Ledger Behind a Balance

All requests require an authenticated HR admin session. Obtain a cookie via `POST /api/auth/login` as user `hr_admin` (seeded demo cast) before running these examples.

Assumes user id `5` (an employee) and leave type id `1` (Vacation).

## Success — read the ledger after set and adjust

First set and adjust the balance so the ledger has entries:

```bash
curl -s -X PUT https://localhost/api/balances/5/1 \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"totalDays": 25, "reason": "Annual allocation 2027"}' | jq .

curl -s -X POST https://localhost/api/balances/5/1/adjust \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"changeDays": 3, "reason": "Carryover from 2026"}' | jq .
```

Then read the ledger:

```bash
curl -s https://localhost/api/balances/5/1/ledger \
  -b cookies.txt | jq .
```

Expected: 200 — two entries oldest first

```json
[
  {
    "id": 1,
    "userId": 5,
    "leaveTypeId": 1,
    "changeDays": 5,
    "kind": "SET",
    "reason": "Annual allocation 2027",
    "actorId": 1,
    "requestId": null,
    "createdAt": "2026-10-25T10:00:00Z"
  },
  {
    "id": 2,
    "userId": 5,
    "leaveTypeId": 1,
    "changeDays": 3,
    "kind": "ADJUST",
    "reason": "Carryover from 2026",
    "actorId": 1,
    "requestId": null,
    "createdAt": "2026-10-25T10:00:01Z"
  }
]
```

---

## Empty history — no rows yet (200 with empty array)

```bash
curl -s https://localhost/api/balances/5/3/ledger \
  -b cookies.txt | jq .
```

Expected: 200 `[]`

---

## Access denied — employee or manager caller (403)

```bash
curl -s https://localhost/api/balances/5/1/ledger \
  -b employee_cookies.txt | jq .
```

Expected: 403

```json
{
  "status": 403,
  "code": "ACCESS_DENIED"
}
```

---

## Demo scenario 12 — HR sets, adjusts, reads ledger; below-zero refused

This sequence closes demo scenario 12 via the API rather than a direct database check.

```bash
# Set vacation balance to 15 days
curl -s -X PUT https://localhost/api/balances/5/1 \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"totalDays": 15, "reason": "Revised annual entitlement"}' | jq .

# Adjust down by 3
curl -s -X POST https://localhost/api/balances/5/1/adjust \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"changeDays": -3, "reason": "Days taken outside system in November"}' | jq .

# Attempt to adjust below zero (should fail)
curl -s -X POST https://localhost/api/balances/5/1/adjust \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"changeDays": -20, "reason": "Would take balance below zero"}' | jq .
# Expected: 409 INSUFFICIENT_BALANCE

# Read the ledger — shows SET and ADJUST rows with actor and timestamp
curl -s https://localhost/api/balances/5/1/ledger \
  -b cookies.txt | jq .
# Expected: 200 with SET row and one ADJUST row (second attempt not written)
```
