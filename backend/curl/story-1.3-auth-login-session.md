# Story 1.3 — Password Login with Idle-Expiring Session

curl recipes for `POST /auth/login` and `POST /auth/logout`, covering every row of the
spec's I/O & Edge-Case Matrix (`_bmad-output/implementation-artifacts/spec-1-3-password-login-idle-expiring-session.md`).

## Base URL

Two ways to run the backend locally; pick the block that matches.

**A. Backend run directly (`dev` profile, plain HTTP, port 8080)**

```bash
cd backend
./mvnw spring-boot:run
BASE=http://localhost:8080
```

> The login cookie is set with the `Secure` attribute. curl will **not** replay a
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

All 12 demo users share the same password. `BCrypt` hash is seeded in
`V5__seed_users.sql`.

| username | role | password |
|---|---|---|
| `hr.admin` | HR_ADMIN | `Passw0rd1` |
| `mgr.rossi` | MANAGER | `Passw0rd1` |
| `emp.jsmith` | EMPLOYEE | `Passw0rd1` |

(any of the other 9 seeded usernames work the same way — see `V5__seed_users.sql`)

---

## 1. Login success

```bash
curl -i -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{"username":"emp.jsmith","password":"Passw0rd1"}'
```

Expected:
- `200 OK`
- Body is the `CurrentUser` shape: `{"id":5,"username":"emp.jsmith","role":"EMPLOYEE","mustChangePassword":false}`
- `Set-Cookie: access_token=<jwt>; Path=/; Max-Age=900; HttpOnly; Secure; SameSite=Strict`
- A row is written to `active_sessions` and to `audit_events` (action `LOGIN`)

If testing over plain HTTP (block A) and you need the raw token for later requests
(since the `Secure` cookie won't be replayed by curl automatically):

```bash
TOKEN=$(curl -s -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -D - \
  -d '{"username":"emp.jsmith","password":"Passw0rd1"}' \
  -o /dev/null | grep -i '^set-cookie' | sed -E 's/.*access_token=([^;]+).*/\1/')
echo "$TOKEN"
```

Then reuse it on any authenticated request with:

```bash
-H "Cookie: access_token=$TOKEN"
```

## 2. Wrong password

```bash
curl -i -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.jsmith","password":"wrong-password"}'
```

Expected:
- `401 Unauthorized`, `Content-Type: application/problem+json`
- Body: `{"status":401,"detail":"Username or password is incorrect.","code":"INVALID_CREDENTIALS"}`
- No `Set-Cookie` header

## 3. Unknown username (same response — no existence leak)

```bash
curl -i -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"nobody.here","password":"Passw0rd1"}'
```

Expected: identical `401 INVALID_CREDENTIALS` body/shape/timing as case 2.

## 4. Missing / blank credentials (validation)

```bash
# missing password field entirely
curl -i -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.jsmith"}'

# blank password
curl -i -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.jsmith","password":""}'
```

Expected (both):
- `400 Bad Request`, `application/problem+json`
- `code: "VALIDATION_FAILED"`
- No `Set-Cookie` header

## 5. Authenticated request while session is active

Use any protected endpoint (e.g. `/actuator/health` is `permitAll`, so use a real
protected route once later stories add one — for Story 1.3 the filter itself can be
exercised against `/auth/logout`, which requires authentication):

```bash
curl -i -X POST "$BASE/auth/logout" \
  -b cookies.txt
# or, direct-HTTP mode:
curl -i -X POST "$BASE/auth/logout" \
  -H "Cookie: access_token=$TOKEN"
```

Expected while the session is still fresh (< 30 min since last request):
- `204 No Content`
- `last_seen_at` on the matching `active_sessions` row is bumped before deletion
- request is **not** rejected for idling

## 6. Idle timeout (> 30 minutes since last_seen_at)

This can't be produced by waiting in real time during a quick test; per the spec it's
verified by directly back-dating `active_sessions.last_seen_at` in the DB:

```sql
UPDATE active_sessions
SET last_seen_at = now() - interval '31 minutes'
WHERE token_id = '<token_id from the JWT token_id claim>';
```

Then:

```bash
curl -i -X POST "$BASE/auth/logout" -b cookies.txt
```

Expected:
- `401 Unauthorized`
- `SecurityContextHolder` stays empty (filter's `touchOrReject` returned `false`)
- No `audit_events` row written (nothing to attribute)

## 7. Logout

```bash
curl -i -X POST "$BASE/auth/logout" -b cookies.txt -c cookies.txt
```

Expected:
- `204 No Content`
- `Set-Cookie: access_token=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Strict` (clears the cookie)
- All `active_sessions` rows for that user are deleted
- `audit_events` row written (action `LOGOUT`)

## 8. Reuse of the old (still cryptographically valid, unexpired) JWT after logout

```bash
curl -i -X POST "$BASE/auth/logout" \
  -H "Cookie: access_token=$TOKEN"
```

Expected:
- `401 Unauthorized` — the JWT signature/expiry still check out, but
  `SessionRegistry.touchOrReject` finds no matching `active_sessions` row (deleted at
  logout) and rejects it.

---

## One-shot smoke script

```bash
#!/usr/bin/env bash
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"

echo "== login success =="
curl -sS -i -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -c /tmp/cl-cookies.txt -d '{"username":"emp.jsmith","password":"Passw0rd1"}'

echo -e "\n== wrong password =="
curl -sS -i -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d '{"username":"emp.jsmith","password":"wrong"}'

echo -e "\n== unknown username =="
curl -sS -i -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d '{"username":"nobody","password":"Passw0rd1"}'

echo -e "\n== blank password (validation) =="
curl -sS -i -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
  -d '{"username":"emp.jsmith","password":""}'

echo -e "\n== logout =="
curl -sS -i -X POST "$BASE/auth/logout" -b /tmp/cl-cookies.txt

echo -e "\n== logout again with same (revoked) cookie — expect 401 =="
curl -sS -i -X POST "$BASE/auth/logout" -b /tmp/cl-cookies.txt
```
