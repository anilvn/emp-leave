---
title: 'Story 1.1: Corrected Dependencies and an HTTPS-Fronted Stack'
type: 'feature'
created: '2026-10-22'
status: 'in-progress'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '53fb8538cb709e6ac334d5d49067dcb6d545a48e'
context: ['{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The committed backend scaffold still carries two closed-but-undone dependency corrections (Spring Modulith present despite AD-1; `docker-compose.yml` pins Postgres 16, exposes it to the host, runs an unused Mailpit, and has no backend/frontend/proxy services), has no package skeleton, and has no shared error envelope — so nothing later in Epic 1 has ground to build on.

**Approach:** Correct `pom.xml` and `docker-compose.yml` to match the Architecture Spine exactly (remove Spring Modulith; verify the already-committed jjwt/OAuth2 change; pin Postgres 18; add `backend`, `frontend`, and an HTTPS-terminating `proxy` service; drop Mailpit and the host Postgres port), lay down the 9-package skeleton with empty Flyway baseline, and add the one shared `ProblemDetail` error envelope (`common.ErrorCode`, `common.ApiException`, `common.GlobalExceptionHandler`) that every later story's controllers will throw into.

## Boundaries & Constraints

**Always:**
- `common` holds only the error envelope, config, and (later) the working-day util — no `@Entity`, `@Repository`, or business-rule `@Service` (AD-3).
- Every cross-domain edge drawn in the Architecture Spine's dependency graph is respected by the package skeleton even though nothing calls across it yet.
- Secrets (DB credentials, JWT signing secret) come only from `docker-compose.yml` environment variables using in-network hostnames; `application.yml` `localhost` defaults stay for running outside compose.
- `common.ErrorCode` has exactly the 10 values `openapi.yaml`'s `ProblemDetail.code` enum declares — no extras, none missing.

**Never:**
- Do not build any domain table, entity, or business endpoint (auth, users, leave, etc.) — that starts in Stories 1.2+ and later epics.
- Do not scaffold a real Angular app — `frontend` in this story is a placeholder container only, satisfying AD-13's compose envelope so `docker compose up` succeeds; it is not frontend feature work.
- Do not commit a certificate's private key or any DB/JWT secret value to the repo.

</frozen-after-approval>

## Code Map

- `backend/pom.xml` -- remove `spring-modulith.version` property, the `spring-modulith-bom` import, and the `spring-modulith-starter-core`/`spring-modulith-starter-test` dependencies (AD-1 open item). `jjwt-*` 0.13.0 and the absence of `spring-boot-starter-security-oauth2-resource-server` are already committed (verified via `git diff HEAD` — no changes needed there, just leave as-is).
- `docker-compose.yml` -- currently only `postgres` (pinned `16`, port `5432:5432` published, no healthcheck beyond what's there) and `mailpit`. Rewrite to: `postgres:18` with no host port mapping; add `backend` (build from `backend/Dockerfile`, env `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`/`JWT_SECRET` pointing at in-network `postgres`, `depends_on: postgres: condition: service_healthy`, healthcheck via actuator); add `frontend` (build from `frontend/Dockerfile`, static placeholder, no business logic); add `proxy` (build from `proxy/Dockerfile`, nginx terminating TLS with a build-time self-signed cert, the only service publishing a host port — `443:443`); remove `mailpit` entirely.
- `backend/src/main/resources/application.yml` -- already reads `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` from env with `localhost` defaults; add `JWT_SECRET` env-backed property (even though `auth`/`security` don't exist yet, AD-13 names it as compose-supplied) and `spring.mvc.problemdetails.enabled: true` so framework-level failures (404/405/bad-JSON) already emit RFC 7807 shape before `GlobalExceptionHandler` adds `code`. Add `management.endpoints.web.exposure.include: health` for the compose healthcheck.
- `backend/src/main/java/com/company/leave/CompanyLeaveApplication.java` -- `TimeZone.setDefault(UTC)` already present before `SpringApplication.run`; confirm only, no duplicate elsewhere.
- `backend/src/main/resources/openapi.yaml` -- source of truth for the exact 10 `ErrorCode` values and their example HTTP statuses (lines ~2403-2443); do not edit, only read from.
- New: one `package-info.java` per package (`auth`, `users`, `leave`, `approvals`, `balances`, `audit`, `notifications`, `security`, `common`) under `backend/src/main/java/com/company/leave/`, each with a one-line Javadoc naming the package's responsibility from the Architecture Spine's Structural Seed — no other content.
- New: `backend/src/main/java/com/company/leave/common/ErrorCode.java`, `ApiException.java` (carries an explicit `HttpStatus` + `ErrorCode` + message — the contract maps the same code to different statuses in different endpoints, e.g. `INSUFFICIENT_BALANCE` appears at both 403 and 409, so the status is not inferable from the code alone), `GlobalExceptionHandler.java` (`@RestControllerAdvice`, handles `ApiException`, `AccessDeniedException` → 403 `ACCESS_DENIED`, `MethodArgumentNotValidException`/`ConstraintViolationException`/`HttpMessageNotReadableException` → 400 `VALIDATION_FAILED`; anything else falls through to Spring's built-in `ProblemDetail` without a custom `code` — no enum value fits a genuinely unclassified failure, and inventing one would violate AD-11's "no extra values").
- New: `backend/Dockerfile` -- multi-stage (`maven`/temurin builder running `./mvnw -DskipTests package`, then `eclipse-temurin:21-jre` runtime copying the jar).
- New: `frontend/Dockerfile` + `frontend/index.html` -- `nginx:1.27-alpine` serving one static placeholder page; replaced wholesale when real Angular work starts (not this epic).
- New: `proxy/Dockerfile` + `proxy/nginx.conf` -- `nginx:1.27-alpine`; `Dockerfile` runs `openssl req -x509 -nodes -newkey rsa:2048 -days 365 -subj "/CN=localhost" ...` at build time to produce a local self-signed cert (not a secret — regenerated on every build, never reused in production); `nginx.conf` terminates TLS on 443, proxies `/api/` to `backend:8080` and `/` to `frontend:80`.
- New: `backend/src/test/java/com/company/leave/common/GlobalExceptionHandlerTest.java` + a minimal `@SpringBootTest` context-load test (Testcontainers Postgres) asserting Flyway runs and creates no domain tables.

## Tasks & Acceptance

**Execution:**
- [ ] `backend/pom.xml` -- remove the three Spring Modulith references and the version property -- closes AD-1's open item; project must still compile and the context must still start
- [ ] `docker-compose.yml` -- rewrite per Code Map (postgres 18/no host port, add backend/frontend/proxy, drop mailpit) -- closes C-1/C-2/C-3/C-4
- [ ] `backend/Dockerfile`, `frontend/Dockerfile` + `index.html`, `proxy/Dockerfile` + `nginx.conf` -- add -- gives compose something to build for each new service
- [ ] `backend/src/main/resources/application.yml` -- add `JWT_SECRET` property, `spring.mvc.problemdetails.enabled: true`, actuator health exposure -- supports the healthcheck and the error envelope
- [ ] 9x `package-info.java` -- add -- lays down the Structural Seed package skeleton
- [ ] `common/ErrorCode.java`, `common/ApiException.java`, `common/GlobalExceptionHandler.java` -- add -- the one shared error envelope (AD-11)
- [ ] `backend/src/test/java/.../GlobalExceptionHandlerTest.java` -- add -- proves a validation failure and an access-denied failure both come back as `application/problem+json` with the right `code`
- [ ] `backend/src/test/java/.../CompanyLeaveApplicationTests.java` (or similar) -- add/extend -- Testcontainers-backed context load proving Flyway creates `flyway_schema_history` and no domain tables

**Acceptance Criteria:**
- Given `backend/pom.xml`, when this story is complete, then no Spring Modulith artifact remains and `./mvnw test` compiles and runs a passing Spring context
- Given the corrected `docker-compose.yml`, when `docker compose up --build` runs from a clean checkout, then `postgres`, `backend`, and `proxy` all reach a healthy state, `postgres` publishes no host port, and `https://localhost/...` is reachable through `proxy` with the self-signed certificate
- Given any request that fails for any reason, when the response is produced, then it is `application/problem+json` with a `code` drawn from `common.ErrorCode`, and that enum has exactly the 10 values `openapi.yaml` declares
- Given an empty database, when the backend starts, then Flyway creates `flyway_schema_history` and this story adds no domain table

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Design Notes

**Frontend placeholder, not a decision point worth asking about:** the epic breakdown explicitly requires a `frontend` compose service to close AD-13's envelope (C-2), but no Angular code exists yet (out of this epic's storied scope). A static `nginx` container serving one placeholder page satisfies the compose contract without inventing product code, and is wholesale-replaceable when real frontend work starts.

**`ApiException` carries status explicitly:** cross-checking `openapi.yaml`'s examples shows the same `code` used at different HTTP statuses in different endpoints (`INSUFFICIENT_BALANCE` at both 403 and 409, `NOT_FOUND` at both 403 and 404). So `ApiException(HttpStatus, ErrorCode, String)` takes both explicitly — later modules decide the status per call site, `GlobalExceptionHandler` never guesses it from the code.

**Self-signed cert generated at image build, not committed:** `proxy/Dockerfile` runs `openssl req` during `docker build`, so `docker compose up --build` is still the one command the AC asks for, and no key material ever lands in git.

## Verification

**Commands:**
- `cd backend && ./mvnw -DskipTests=false test` -- expected: all tests pass, including the new `GlobalExceptionHandlerTest` and the Testcontainers context-load test
- `docker compose up --build -d` then `docker compose ps` -- expected: `postgres`, `backend`, `proxy` all `healthy`
- `curl -k https://localhost/` -- expected: TLS handshake succeeds (self-signed, `-k` to ignore trust), placeholder frontend page returns
- `docker compose down -v` -- tear down after manual verification
