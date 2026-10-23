# Story 1.6 — HR Creates Accounts with a Role and a Manager

curl recipes for `POST /users`, `GET /users` and `GET /users/managers`, covering every
row of the spec's I/O & Edge-Case Matrix
(`_bmad-output/implementation-artifacts/spec-1-6-hr-creates-accounts-with-a-role-and-a-manager.md`),
plus the four fixes from its review pass.

## Base URL

Two ways to run the backend locally; pick the block that matches.

**A. Backend run directly (`dev` profile, plain HTTP, port 8080)**

```bash
cd backend
./mvnw spring-boot:run
BASE=http://localhost:8080
```

> The HR session cookie is set with the `Secure` attribute. curl will **not** replay a
> `Secure` cookie over a plain `http://` connection, so cookie-jar reuse (`-b`/`-c`)
> across requests won't work against this base URL. Either extract the token
> manually (shown below) or use the HTTPS proxy in block B.

**B. Full stack via docker-compose (nginx proxy, self-signed TLS, port 443)**

```bash
docker compose up --build
BASE=https://localhost/api
# every curl command below then needs -k (self-signed cert)
```

## Seeded credentials

All 12 demo users share the same password (`V5__seed_users.sql`).

| id | username | role | manager_id | password |
|---|---|---|---|---|
| 1 | `hr.admin` | HR_ADMIN | — | `Passw0rd1` |
| 2 | `mgr.rossi` | MANAGER | 1 | `Passw0rd1` |
| 3 | `mgr.chen` | MANAGER | 2 | `Passw0rd1` |
| 4 | `mgr.ortiz` | MANAGER | 2 | `Passw0rd1` |
| 5 | `emp.jsmith` | EMPLOYEE | 3 | `Passw0rd1` |

## Log in as HR first

Every `/users` endpoint requires an authenticated `HR_ADMIN` caller:

```bash
curl -i -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -c hr-cookies.txt \
  -d '{"username":"hr.admin","password":"Passw0rd1"}'
```

If testing over plain HTTP (block A), extract the raw token instead:

```bash
TOKEN=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -D - \
  -d '{"username":"hr.admin","password":"Passw0rd1"}' \
  -o /dev/null | grep -i '^set-cookie' | sed -E 's/.*access_token=([^;]+).*/\1/')
echo "$TOKEN"
# then reuse it with: -H "Cookie: access_token=$TOKEN"
```

Also log in as a non-HR caller for the access-denied cases below:

```bash
curl -i -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -c emp-cookies.txt \
  -d '{"username":"emp.jsmith","password":"Passw0rd1"}'
```

---

## 1. Create an EMPLOYEE with a valid manager (happy path)

```bash
curl -i -X POST "$BASE/users" \
  -b hr-cookies.txt \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.new1","role":"EMPLOYEE","managerId":3,"temporaryPassword":"Temp#2026"}'
```

Expected:
- `201 Created`
- Body: `{"id":<new id>,"username":"emp.new1","role":"EMPLOYEE","managerId":3,"mustChangePassword":true,"active":true}`
- No `temporaryPassword` field and no plaintext password anywhere in the response

## 2. Create an HR_ADMIN without a manager (happy path)

```bash
curl -i -X POST "$BASE/users" \
  -b hr-cookies.txt \
  -H "Content-Type: application/json" \
  -d '{"username":"hr.new1","role":"HR_ADMIN","temporaryPassword":"Temp#2026"}'
```

Expected: `201 Created`, `"managerId":null` (top-level HR users need no manager).

## 3. EMPLOYEE/MANAGER without `managerId` (validation)

```bash
curl -i -X POST "$BASE/users" \
  -b hr-cookies.txt \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.new2","role":"EMPLOYEE","temporaryPassword":"Temp#2026"}'
```

Expected:
- `400 Bad Request`, `application/problem+json`
- `code: "VALIDATION_FAILED"`, detail: `managerId is required when role is EMPLOYEE or MANAGER.`

## 4. `managerId` points to a non-manager

```bash
curl -i -X POST "$BASE/users" \
  -b hr-cookies.txt \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.new3","role":"EMPLOYEE","managerId":5,"temporaryPassword":"Temp#2026"}'
```

`managerId: 5` is `emp.jsmith`, an `EMPLOYEE`. Expected: `400 VALIDATION_FAILED`,
detail names the user and its (ineligible) role.

## 5. `managerId` points to a nonexistent user

```bash
curl -i -X POST "$BASE/users" \
  -b hr-cookies.txt \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.new4","role":"EMPLOYEE","managerId":9999,"temporaryPassword":"Temp#2026"}'
```

Expected: `400 VALIDATION_FAILED` — same code as case 4, not `404` (reserved for
Story 1.7's reset-password path).

## 5b. `managerId` for an `HR_ADMIN` creator is still validated (review fix)

The initial implementation skipped this check for `HR_ADMIN`; it is now validated the
same as cases 4 and 5 whenever `managerId` is supplied, regardless of the new
account's own role:

```bash
curl -i -X POST "$BASE/users" \
  -b hr-cookies.txt \
  -H "Content-Type: application/json" \
  -d '{"username":"hr.new2","role":"HR_ADMIN","managerId":9999,"temporaryPassword":"Temp#2026"}'
```

Expected: `400 VALIDATION_FAILED` (not a 500 from an unhandled foreign-key violation).

## 6. Duplicate username

```bash
curl -i -X POST "$BASE/users" \
  -b hr-cookies.txt \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.jsmith","role":"EMPLOYEE","managerId":3,"temporaryPassword":"Temp#2026"}'
```

Expected: `400 VALIDATION_FAILED`; no second `emp.jsmith` row is created.

## 6b. Username with leading/trailing whitespace is trimmed (review fix)

```bash
curl -i -X POST "$BASE/users" \
  -b hr-cookies.txt \
  -H "Content-Type: application/json" \
  -d '{"username":"  emp.jsmith  ","role":"EMPLOYEE","managerId":3,"temporaryPassword":"Temp#2026"}'
```

Expected: `400 VALIDATION_FAILED` — treated as the same existing `emp.jsmith`, not a
distinct new account.

## 6c. Over-length username (review fix)

```bash
curl -i -X POST "$BASE/users" \
  -b hr-cookies.txt \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$(printf 'a%.0s' {1..101})\",\"role\":\"EMPLOYEE\",\"managerId\":3,\"temporaryPassword\":\"Temp#2026\"}"
```

Expected: `400 VALIDATION_FAILED` (rejected by `@Size(max = 100)` before it ever
reaches the database).

## 7. Non-HR caller is denied on all three endpoints

```bash
echo "== POST /users as EMPLOYEE: expect 403 ==" 
curl -i -X POST "$BASE/users" \
  -b emp-cookies.txt \
  -H "Content-Type: application/json" \
  -d '{"username":"blocked.create","role":"EMPLOYEE","managerId":3,"temporaryPassword":"Temp#2026"}'

echo "== GET /users as EMPLOYEE: expect 403 =="
curl -i "$BASE/users" -b emp-cookies.txt

echo "== GET /users/managers as EMPLOYEE: expect 403 =="
curl -i "$BASE/users/managers" -b emp-cookies.txt
```

Expected (all three): `403 Forbidden`, `code: "ACCESS_DENIED"`, and an `access_log`
row is written with `allowed = false` for each.

## 8. List every account (HR only, no scope filter)

```bash
curl -i "$BASE/users" -b hr-cookies.txt
```

Expected: `200 OK`, a JSON array with every seeded and newly created account — no
filtering by caller, manager, or role (`FR24`).

## 9. List manager options for the create-account dropdown

```bash
curl -i "$BASE/users/managers" -b hr-cookies.txt
```

Expected: `200 OK`, every `MANAGER` and every `HR_ADMIN` user as `{"id","username","role"}`
rows (`mgr.rossi`, `mgr.chen`, `mgr.ortiz`, `hr.admin`, plus any created in case 2).

## Database checks

Verify the password was hashed, not stored or logged in plaintext:

```bash
docker compose exec -T postgres psql -U "${DB_USERNAME:-leave}" -d leave \
  -c "SELECT username, password_hash, must_change_password, active FROM users WHERE username = 'emp.new1';"
```

Expected: `password_hash` starts with `$2` (BCrypt); `must_change_password = true`;
`active = true`. `Temp#2026` never appears anywhere in the row.

Verify the audit row from case 1:

```bash
docker compose exec -T postgres psql -U "${DB_USERNAME:-leave}" -d leave \
  -c "SELECT entity, action, actor_id, entity_id, created_at FROM audit_events WHERE entity = 'USER' AND action = 'CREATE' ORDER BY created_at DESC LIMIT 1;"
```

Expected: one row with `actor_id = 1` (`hr.admin`'s id) and `entity_id` matching the
new user's id.

## Acceptance checklist

- [ ] HR can create an `EMPLOYEE`/`MANAGER` with a valid `managerId`, or an `HR_ADMIN`
      with no `managerId`; the response never carries the plaintext temporary password.
- [ ] `EMPLOYEE`/`MANAGER` without `managerId` returns `400 VALIDATION_FAILED`.
- [ ] A `managerId` pointing to a non-manager, a nonexistent user, or an invalid id on
      an `HR_ADMIN` creation request all return `400 VALIDATION_FAILED`, never a 500.
- [ ] Duplicate usernames — exact or after trimming whitespace — are rejected with no
      second row created.
- [ ] An over-length username is rejected before it reaches the database.
- [ ] `EMPLOYEE`/`MANAGER` callers get `403 ACCESS_DENIED` on all three endpoints, each
      logged to `access_log` with `allowed = false`.
- [ ] `GET /users` returns every account with no scope filter.
- [ ] `GET /users/managers` returns every `MANAGER` and `HR_ADMIN`.
- [ ] Every successful creation writes exactly one `audit_events` row with the acting
      HR user's id.
