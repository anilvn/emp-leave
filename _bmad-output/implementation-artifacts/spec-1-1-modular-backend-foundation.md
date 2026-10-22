---
title: 'Story 1.1: Modular backend foundation'
type: 'feature'
created: '2026-10-22'
status: 'done'
baseline_commit: '294d5876ccfde339d03652d6a2401b4646dd608c'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/architecture/architecture-emp-leave-tracker-poc-backend-2026-10-22/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The repo holds a bare Initializr scaffold at the root on Spring Boot 4.0.8, with no module boundaries, database, mail sandbox, or shared error contract.

**Approach:** Move the scaffold into `backend/`, bump to Boot 4.1.1 with the Modulith BOM 2.1.1, create the eight module packages with enforced dependencies, add docker-compose for Postgres 16 and Mailpit, and add the `common` error contract and utilities with a shared Testcontainers test base.

## Boundaries & Constraints

**Always:** Allowed module edges exactly as the spine graph (approvals→leave; leave→balances,users,audit; balances→users,audit; users→audit; auth→users,audit; notifications→leave,users). `application.yml` holds non-secret defaults only; profiles `dev` (`flyway.out-of-order=true`), `demo`, `test`. `ddl-auto=validate`, `open-in-view=false`. Config from `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Java 21, group `com.company.leave`. Decision: Docker Desktop is installed and its engine is running, so all ACs, including Testcontainers and compose, are verified for real (docker.exe is at `C:\Users\1772\AppData\Local\Programs\DockerDesktop\resources\bin`, not on the bash PATH).

**Never:** No H2. No Spring Modulith event-publication starters. No business endpoints, entities or migrations beyond what this story lists. Do not touch `_bmad*` or `.claude`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| ApiException | module throws `ApiException(status, code)` | RFC 9457 ProblemDetail with `code` | N/A |
| Bean validation | invalid request record | 400 ProblemDetail `code=VALIDATION_FAILED` | N/A |
| Password policy | "abc12345" / "short1" / "abcdefgh" | accept / reject (<8) / reject (no digit) | returns invalid |
| Module graph | undeclared edge | `ModularityTests` fails | build fails |

</frozen-after-approval>

## Code Map

- `pom.xml` (repo root) -- scaffold to move to `backend/pom.xml`; parent 4.0.8 → 4.1.1; add Modulith BOM, `spring-modulith-starter-core`, `spring-modulith-starter-test`, OAuth2 resource server, Testcontainers (postgresql, junit-jupiter)
- `mvnw`, `mvnw.cmd`, `.mvn/` -- move to `backend/`
- `src/main/java/com/company/leave/CompanyLeaveApplication.java` -- move to `backend/src/main/java/...`
- `src/main/resources/application.properties` -- replace with `application.yml`
- `src/test/java/com/company/leave/CompanyLeaveApplicationTests.java` -- move; make it extend the shared Postgres base
- `.gitignore` -- keep at root; add `backups/`, `.env` if needed
- `HELP.md` -- leave alone

## Tasks & Acceptance

**Execution:**
- [x] Move scaffold into `backend/` with `git mv`; bump Boot to 4.1.1; add dependencies and Modulith BOM 2.1.1
- [x] `backend/src/main/java/com/company/leave/{common,auth,users,leave,approvals,balances,audit,notifications}/package-info.java` -- `@ApplicationModule(allowedDependencies=...)`
- [x] `common`: `ApiException`, `ErrorCode`-style codes, `GlobalExceptionHandler` (`@RestControllerAdvice`), `PasswordPolicy`, `PasswordEncoder` bean (BCrypt), `Clock` bean
- [x] `backend/src/main/resources/application.yml` -- defaults and profiles
- [x] `docker-compose.yml` (repo root) -- postgres:16 with `pg_isready` healthcheck, axllent/mailpit:v1.31
- [x] Tests: `ModularityTests`, `PasswordPolicyTest`, error-handler test, `AbstractPostgresIT` base (Testcontainers Postgres 16)

**Acceptance Criteria:**
- Given the scaffold, when complete, then `backend/` builds on Boot 4.1.1 and `./mvnw verify` passes there
- Given the module packages, when `ModularityTests` runs, then `verify()` passes
- Given `docker compose up postgres mailpit`, when started, then both services run and `spring-boot:run` (dev) connects
- Given integration tests, when run, then they share one Testcontainers Postgres 16 base

## Implementation Notes

- Integration tests are named `*Tests` (not `*IT`) so surefire runs them; `AbstractPostgresIT` is the shared base.
- JVM is pinned to UTC (main() and surefire `-Duser.timezone=UTC`): Postgres rejects the legacy `Asia/Calcutta` zone id.
- `common` is an OPEN Modulith module; other modules list it in `allowedDependencies`.
- `./mvnw verify`: 11 tests pass. `docker compose up postgres mailpit` healthy; `spring-boot:run` (dev) connects with `DB_*` env vars.
- `DB_PASSWORD` has no default in `application.yml`; compose defaults to `leave`.

## Spec Change Log

## Review Triage Log

Review ran inline (no review subagents were authorized in this session).

| Finding | Verdict | Evidence |
|---|---|---|
| Tests run outside surefire (IDE) fail on non-UTC machines: Postgres rejects `Asia/Calcutta` | medium | Reproduced as a context-load failure; patched by pinning UTC in `AbstractPostgresIT` |
| `*IT` test class was skipped by surefire | medium | Not in surefire report; renamed to `*Tests`, now runs |
| Empty `internal/` and `web/` package dirs are untracked by git | low | Harmless; they appear when the first class is added |

## Verification

**Commands:**
- `cd backend && ./mvnw verify` -- expected: BUILD SUCCESS
- `docker compose up -d postgres mailpit` -- expected: postgres healthy
