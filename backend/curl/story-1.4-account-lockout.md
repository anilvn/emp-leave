# Story 1.4 — Account Lockout After Five Failed Attempts

Manual `curl` recipes for checking the five-attempt lockout, recovery, and session
revocation behavior at `POST /auth/login`.

## Start the application

Run the backend directly:

```bash
cd backend
./mvnw spring-boot:run
BASE=http://localhost:8080
```

Or run the HTTPS-fronted compose stack:

```bash
docker compose up --build
BASE=https://localhost/api
```

For the compose URL, add `-k` to each `curl` command to accept the local
self-signed certificate. If running directly over HTTP, remember curl does not
send the login's `Secure` cookie automatically; use the manual token extraction
shown in the Story 1.3 curl guide, or use HTTPS.

## Test account and database access

The seeded `emp.jsmith` account uses password `Passw0rd1`. The compose database
defaults to database/user/password `leave`; if `DB_USERNAME` is set, use that
value for `-U` below.

These tests change login counters, create sessions, and intentionally lock the
account. Prefer a disposable local database. To reset this test account and
remove any existing sessions before testing:

```bash
docker compose exec -T postgres psql -U "${DB_USERNAME:-leave}" -d leave \
  -c "UPDATE users SET failed_logins = 0, locked_until = NULL WHERE username = 'emp.jsmith'; DELETE FROM active_sessions WHERE user_id = (SELECT id FROM users WHERE username = 'emp.jsmith');"
```

Do not run the reset command on an account/database whose session revocations you
need to preserve.

## 1. Four wrong passwords do not lock the account

Start from the reset state above:

```bash
for attempt in 1 2 3 4; do
  echo "== failed attempt $attempt: expect 401 =="
  curl -i -X POST "$BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"emp.jsmith","password":"wrong"}'
done
```

Each response should be `401 Unauthorized`, `Content-Type:
application/problem+json`, with `code: "INVALID_CREDENTIALS"` and no
`Set-Cookie` header. Check the stored count:

```bash
docker compose exec -T postgres psql -U "${DB_USERNAME:-leave}" -d leave \
  -c "SELECT failed_logins, locked_until FROM users WHERE username = 'emp.jsmith';"
```

Expected: `failed_logins = 4`, `locked_until` is `NULL`.

## 2. Fifth consecutive wrong password locks the account

```bash
curl -i -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.jsmith","password":"wrong"}'
```

Expected:

- `423 Locked`, `Content-Type: application/problem+json`
- Problem detail `code: "ACCOUNT_LOCKED"`
- No `Set-Cookie` header
- `failed_logins = 5`; `locked_until` is approximately 15 minutes in the future

Verify the database state:

```bash
docker compose exec -T postgres psql -U "${DB_USERNAME:-leave}" -d leave \
  -c "SELECT failed_logins, locked_until, locked_until > now() AS is_locked FROM users WHERE username = 'emp.jsmith';"
```

## 3. Correct and incorrect passwords are both rejected while locked

Try a correct password:

```bash
curl -i -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.jsmith","password":"Passw0rd1"}'
```

Try another wrong password:

```bash
curl -i -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.jsmith","password":"wrong"}'
```

Both responses should be `423 Locked` with `code: "ACCOUNT_LOCKED"` and no
`Set-Cookie`. The account remains locked until its original `locked_until`; the
additional attempts must not extend the lock or issue a session.

## 4. A successful login clears earlier failures

After resetting the test account, make one or more (but fewer than five) wrong
attempts, then log in successfully:

```bash
curl -i -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.jsmith","password":"wrong"}'

curl -i -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.jsmith","password":"Passw0rd1"}'
```

Expected: the first response is `401 INVALID_CREDENTIALS`; the successful
response is `200 OK` with the normal user body and a session cookie. The
database counter should be reset to zero and `locked_until` cleared:

```bash
docker compose exec -T postgres psql -U "${DB_USERNAME:-leave}" -d leave \
  -c "SELECT failed_logins, locked_until FROM users WHERE username = 'emp.jsmith';"
```

## 5. Login succeeds after the lock window expires

To avoid waiting 15 minutes, simulate an expired lock in the local test database:

```bash
docker compose exec -T postgres psql -U "${DB_USERNAME:-leave}" -d leave \
  -c "UPDATE users SET failed_logins = 5, locked_until = now() - interval '1 minute' WHERE username = 'emp.jsmith';"
```

Then send the correct password:

```bash
curl -i -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.jsmith","password":"Passw0rd1"}'
```

Expected: `200 OK`, a normal login cookie, and `failed_logins = 0` with
`locked_until = NULL`. Alternatively, wait until the 15-minute lock naturally
expires and repeat the login without changing the database.

## 6. A new lockout revokes existing sessions

Start from the reset state, log in successfully and save the cookie:

```bash
curl -i -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{"username":"emp.jsmith","password":"Passw0rd1"}'
```

Then make five consecutive wrong-password requests as in sections 1 and 2.
The fifth should return `423 ACCOUNT_LOCKED` and revoke the account's active
sessions. Reuse the saved cookie on the authenticated logout route:

```bash
curl -i -X POST "$BASE/auth/logout" -b cookies.txt
```

Expected: `401 Unauthorized`, because the lockout removed the active session.
The cookie jar itself may still contain the token; it is the server-side session
record that has been revoked.

## Acceptance checklist

- [ ] Attempts one through four return `401 INVALID_CREDENTIALS`.
- [ ] Attempt five sets `failed_logins` to five, sets a 15-minute `locked_until`,
      and returns `423 ACCOUNT_LOCKED`.
- [ ] Correct and incorrect credentials during the lock window both return
      `423 ACCOUNT_LOCKED`, without issuing a cookie or extending the lock.
- [ ] Successful login resets the failure count and clears the lock timestamp.
- [ ] Correct login succeeds after the lock timestamp has passed and resets both
      lockout fields.
- [ ] Starting a new lockout revokes all active sessions for the account.
