# Epic 1 Context: Secure Sign-In & User Administration

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Stand up the Spring Boot backend foundation (modular layout, Postgres, Mailpit, shared error contract), an immutable audit trail, a bootstrapped first HR admin, secure cookie-based JWT login with lockout and forced password change, RBAC with access logging, and HR user administration. Everything later (balances, leave, approvals, email) builds on these boundaries and security rules.

## Stories

- Story 1.1: Modular backend foundation
- Story 1.2: Immutable audit trail
- Story 1.3: Bootstrap the first HR admin
- Story 1.4: Login, logout and current user
- Story 1.5: Password change, lockout and session timeout
- Story 1.6 to 1.10: see epics file (RBAC, user admin, access log, demo seed)

## Requirements & Constraints

- Backend only (Spring Boot API); Angular SPA is planned separately.
- PostgreSQL 16 only; tests use Testcontainers, never H2.
- All config and secrets come from environment variables; `application.yml` holds non-secret defaults only. Profiles: `dev` (default), `demo`, `test`.
- Passwords: BCrypt, at least 8 characters with letters and numbers.
- Logs never contain passwords, tokens or request notes.
- Audit, access-log and ledger tables are insert-only, enforced by DB trigger; audit retention 3 years, no purge path.

## Technical Decisions

- Modular monolith under `com.company.leave`: modules `common`, `auth`, `users`, `leave`, `approvals`, `balances`, `audit`, `notifications`. Root package is the public API (one `*Service`, DTO records, events); `internal/` and `web/` are private. Each module has `package-info.java` with `@ApplicationModule(allowedDependencies=...)`; Modulith `ApplicationModules.verify()` test enforces it.
- Allowed edges: approvals→leave; leave→balances,users,audit; balances→users,audit; users→audit; auth→users,audit; notifications→leave,users. `common` depends on nothing and is usable by all.
- One owner per table; no cross-module SQL; entities/repositories never leave `internal/`.
- `@Transactional` only on public service methods; `open-in-view=false`.
- Flyway only (`V<yyyyMMddHHmm>__<module>_<desc>.sql`, never edit applied); Hibernate `ddl-auto=validate`; `flyway.out-of-order=true` in dev only.
- Errors: `ApiException(HttpStatus, code)` + one `@RestControllerAdvice` in `common`, RFC 9457 `ProblemDetail` with `code`.
- `common` provides `Clock`, `CurrentUser`, `PasswordPolicy`, BCrypt `PasswordEncoder`, working-days function.
- Stack: Java 21, Spring Boot 4.1.1, Spring Modulith BOM 2.1.1 (core + test only), Postgres 16, Mailpit v1.31, Maven wrapper. Scaffold moves into `backend/`.
- Auth: HS256 JWT from `JWT_SECRET` (>=32 bytes) in HttpOnly/Secure/SameSite=Strict cookie; CSRF via cookie repository and `X-XSRF-TOKEN`; explicit CORS allow-list.

## Cross-Story Dependencies

- 1.1 is the base for every other story (module layout, error contract, test base).
- 1.2 audit is used by 1.3 (USER_CREATED) and later stories.
- 1.3 `UsersService` is used by auth (1.4+).
