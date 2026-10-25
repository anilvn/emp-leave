# Company Leave Tracker — POC

A leave-management system POC covering employee leave submission, manager approval, HR balance management, and a full immutable audit trail.

## Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (or Docker Engine + Compose plugin v2+)

## Start the stack

```bash
# 1. Copy the example env file and fill in your JWT secret
cp .env.example .env
# Edit .env and set JWT_SECRET (minimum 32 bytes)
# Generate one: openssl rand -base64 48

# 2. Bring up all services
docker compose up
```

Flyway runs automatically on startup and applies all 13 schema + seed migrations in order.

The API is available at **https://localhost/api** once the `backend` service reports healthy.

> **Self-signed certificate**: the proxy generates a self-signed certificate at build time. Your browser will show a security warning — accept it for localhost. This is the one documented gap in FR55 (HTTPS); the POC accepts a self-signed cert.

Swagger UI: `https://localhost/api/swagger-ui.html`

## Demo cast

All seeded accounts share the password **`Passw0rd1`**.

| Username | Role | Reports to |
|---|---|---|
| `hr.admin` | HR_ADMIN | — |
| `mgr.rossi` | MANAGER | hr.admin |
| `mgr.chen` | MANAGER | mgr.rossi |
| `mgr.ortiz` | MANAGER | mgr.rossi |
| `emp.jsmith` | EMPLOYEE | mgr.chen |
| `emp.alee` | EMPLOYEE | mgr.chen |
| `emp.cwong` | EMPLOYEE | mgr.chen |
| `emp.dpatel` | EMPLOYEE | mgr.chen |
| `emp.emurphy` | EMPLOYEE | mgr.ortiz |
| `emp.fkim` | EMPLOYEE | mgr.ortiz |
| `emp.gsingh` | EMPLOYEE | mgr.ortiz |
| `emp.hnguyen` | EMPLOYEE | mgr.ortiz |

### Seeded leave balances (all users)

| Leave type | Total days |
|---|---|
| Vacation | 20 |
| Sick | 10 |
| Parental | 30 |
| Sabbatical | 0 |

## Signing in

```bash
curl -k -c cookies.txt -X POST https://localhost/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.jsmith","password":"Passw0rd1"}'
# The access_token cookie is set; subsequent requests use -b cookies.txt
```

Or sign in through the frontend at **https://localhost**.

## Running the test suite

```bash
cd backend
./mvnw verify
```

Integration tests spin up a real PostgreSQL container via Testcontainers (requires Docker). The immutability trigger and the balance `CHECK` constraint are exercised against a live database (NFR10).

## Architecture notes

- No `spring-modulith` or `oauth2-resource-server` dependencies (architecture open items confirmed closed).
- No secrets are committed or baked into images — `JWT_SECRET` is required at runtime and `.env` is gitignored (NFR8).
- Notifications are a **database row plus a log line** — no mail server is configured or required.
- The `access_log` table is written by the request filter for every API call (FR26, FR59); it has no read API in this POC (Phase 2 work).

## Demo script

See [DEMO.md](DEMO.md) for the complete walkthrough of all 15 PRD acceptance scenarios.
