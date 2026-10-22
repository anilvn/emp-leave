---
title: 'Story 1.3: Password Login with an Idle-Expiring Session'
type: 'feature'
created: '2026-10-23'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '336230d24ab4f1df7abf8194d251e9b940a7a106'
context: ['{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Nobody can log in yet — there is no `users` table, no password check, no JWT, and no session concept — so no later story (lockout, RBAC, HR account admin, every protected endpoint) has an identity to check.

**Approach:** Add the `users`/`active_sessions` schema plus a separate seed migration for the 12-user demo cast; implement `security.JwtService` (sign/parse a 15-min HMAC JWT) and `security.SessionRegistry` (the `active_sessions`-backed idle/revocation registry, AD-8) in `security`; implement `auth.AuthController` (`POST /auth/login`, `POST /auth/logout`) and `users.UserService.authenticate(...)` in `auth`/`users`; wire a `security.JwtAuthenticationFilter` and `security.SecurityConfig` into the filter chain (AD-7). New entities/DTOs use Lombok (`@Getter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Value`) instead of hand-written boilerplate.

## Boundaries & Constraints

**Always:**
- `users`/`security`'s `@Entity`/`@Repository` classes stay package-private; only `users.UserService` and `security.SessionRegistry`/`security.JwtService` are called from outside their package (AD-2).
- `security` never imports a `users` type (AD-1 diagram: no domain-specific edge out of `security`) — the JWT `role` claim and `JwtService`/`JwtAuthenticationFilter`'s parsed result are plain `String`, never `users.Role`; `auth` (which depends on both) converts.
- `UserService.authenticate(username, rawPassword)` is the only way to check a password — `passwordHash` never leaves `users` (no DTO carries it out).
- `security.SessionRegistry` owns `active_sessions` exclusively; `auth` only calls `createSession`/`touchOrReject` (via the filter)/`revokeAllForUser`, never touches the table (AD-8).
- `SecurityConfig` explicitly `permitAll`s `/actuator/health` — Boot's auto-config fallback for that path is disabled the moment a custom `SecurityFilterChain` bean exists, and the docker-compose healthcheck depends on it.
- New `@Entity`/DTO classes use Lombok (`@Getter`/`@Builder`/`@NoArgsConstructor`/`@AllArgsConstructor` on entities, `@Value` on immutable cross-module/request DTOs) per this story's explicit instruction, diverging intentionally from `audit`'s hand-written style (which predates this instruction and is not being retrofitted).

**Never:**
- No lockout logic (incrementing `failed_logins`, checking `locked_until`) — that is Story 1.4; this story only creates the columns.
- No `/auth/change-password` — that is Story 1.7.
- No `AccessPolicy`/role-based authorization — that is Story 1.5; this story only requires authentication (`anyRequest().authenticated()`).
- No CSRF token scheme — out of scope for this POC; `SameSite=Strict` is the accepted mitigation (same reasoning as the architecture's reverse-proxy same-origin setup).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Login success | Seeded username + correct password | 200, `CurrentUser` body, `Set-Cookie: access_token=...; HttpOnly; Secure; SameSite=Strict`, `active_sessions` row created, `audit_events` row written | N/A |
| Wrong password | Seeded username + wrong password | 401 problem+json `INVALID_CREDENTIALS`, no cookie set | Same response for unknown username (no existence leak) |
| Idle timeout | Valid cookie, `active_sessions.last_seen_at` > 30 min old | Next authenticated request: 401, SecurityContext stays empty | No `audit_events` write (nothing to attribute) |
| Active use | Valid cookie, last request < 30 min ago | Request proceeds authenticated, `last_seen_at` bumped to now | N/A |
| Logout | Authenticated request to `/auth/logout` | 204, all of that user's `active_sessions` rows deleted, cookie cleared, `audit_events` row written | A later request with the old (still-unexpired) JWT gets 401 |

</frozen-after-approval>

## Code Map

- `backend/src/main/resources/db/migration/` -- add `V3__create_users_table.sql` (`users` per AC columns, `role` CHECK, self-referencing `manager_id` FK), `V4__create_active_sessions_table.sql` (`active_sessions(token_id PK, user_id FK, last_seen_at, expires_at)`), `V5__seed_users.sql` (separate seed migration — 12 users, BCrypt hash `$2a$10$494KRTD7NLmmrKANwVPvQ.xFiOxOpaw9eS4p3hWmzs8GFT2ssDIri` = `Passw0rd1`, generated via `BCryptPasswordEncoder` so it verifies at runtime). Roster mirrors `openapi.yaml`'s own examples exactly: id1 `hr.admin` (manager_id NULL), id2 `mgr.rossi` (→1), id3 `mgr.chen`/id4 `mgr.ortiz` (→2), id5 `emp.jsmith`/id6 `emp.alee`/id7 `emp.cwong`/id8 `emp.dpatel` (→3), id9 `emp.emurphy`/id10 `emp.fkim`/id11 `emp.gsingh`/id12 `emp.hnguyen` (→4).
- `backend/src/main/java/com/company/leave/users/` -- add `Role.java` (public enum EMPLOYEE/MANAGER/HR_ADMIN, matches `openapi.yaml` `Role` schema), `User.java` (package-private `@Entity`, Lombok `@Getter @NoArgsConstructor(PROTECTED) @AllArgsConstructor(PACKAGE) @Builder`), `UserRepository.java` (package-private, extends plain `Repository<User, Long>`, declares only `save`/`findById`/`Optional<User> findByUsername(String)`), `UserAccount.java` (public Lombok `@Value @Builder` DTO: `id, username, role, mustChangePassword` — exactly `CurrentUser`'s shape, reused directly as the login response body), `UserService.java` (public `@Service`; `Optional<UserAccount> authenticate(String username, String rawPassword)` — looks up by username, filters `active`, checks `passwordEncoder.matches`, maps to `UserAccount`; never returns `passwordHash`).
- `backend/src/main/java/com/company/leave/security/` -- add `ActiveSession.java` (package-private `@Entity`, Lombok `@Getter @NoArgsConstructor(PROTECTED) @AllArgsConstructor(PACKAGE)`, plus a hand-written `void touch(Instant now)` mutator), `ActiveSessionRepository.java` (package-private, extends plain `Repository`, declares `save`/`findById`/`void deleteByUserId(long)`), `SessionRegistry.java` (public `@Service`; `String createSession(long userId)`, `boolean touchOrReject(String tokenId)` — false/missing or idle > 30 min, else bumps `last_seen_at` and saves, `void revokeAllForUser(long userId)`), `JwtService.java` (public `@Service`; builds a `SecretKey` from `${security.jwt.secret}` at construction via `Keys.hmacShaKeyFor`; `String issue(long userId, String role, String tokenId)` fixed at 15 min, package-private overload taking an explicit `Duration` for test control; `ParsedToken parseAndValidate(String token)` returns a `record ParsedToken(long userId, String role, String tokenId)`, throwing `JwtException` on bad signature/expiry), `JwtAuthenticationFilter.java` (public `OncePerRequestFilter`; reads the `access_token` cookie, on success calls `SessionRegistry.touchOrReject`, populates `SecurityContextHolder` with `UsernamePasswordAuthenticationToken(userId, null, [SimpleGrantedAuthority("ROLE_"+role)])`; any failure just leaves the context empty and continues the chain — no response-writing here), `SecurityConfig.java` (public `@Configuration @EnableWebSecurity`; `PasswordEncoder` bean = `BCryptPasswordEncoder`; `SecurityFilterChain` — stateless, CSRF disabled, `permitAll` on `POST /auth/login` and `/actuator/health`, `anyRequest().authenticated()`, 401 `AuthenticationEntryPoint` via `HttpStatusEntryPoint`, `addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)`).
- `backend/src/main/java/com/company/leave/auth/` -- add `LoginRequest.java` (public Lombok `@Value` DTO: `username, password`), `AuthController.java` (public `@RestController @RequestMapping("/auth")`; `POST /login` calls `UserService.authenticate`, on empty throws `ApiException(401, INVALID_CREDENTIALS, ...)`, else `SessionRegistry.createSession` → `JwtService.issue` → sets the `Set-Cookie` via `ResponseCookie` (httpOnly/secure/sameSite Strict/maxAge 15m) → `AuditService.recordEvent("AUTH", null, "LOGIN", userId, null)` → returns the `UserAccount`; `POST /logout` reads `Authentication.getPrincipal()` (the `Long` userId set by the filter), calls `SessionRegistry.revokeAllForUser`, clears the cookie (`maxAge(0)`), `AuditService.recordEvent("AUTH", null, "LOGOUT", userId, null)`, returns 204).
- `backend/src/main/resources/application.yml` -- add `security.jwt.secret: ${JWT_SECRET:...dev-only fallback...}` under the existing `dev` profile block only (prod/demo must supply `JWT_SECRET`; tests inherit this since `spring.profiles.default: dev` applies when no profile is active, so no test file needs an explicit `@ActiveProfiles`).
- `backend/src/test/java/com/company/leave/CompanyLeaveApplicationTests.java` -- update table-list assertion to add `users`, `active_sessions`.
- `backend/src/main/java/com/company/leave/common/ApiException.java`, `ErrorCode.java`, `GlobalExceptionHandler.java` -- reuse as-is for the 401 `INVALID_CREDENTIALS` path; no changes.
- `backend/src/main/java/com/company/leave/audit/AuditService.java` -- reuse as-is (`recordEvent`); no changes.

## Tasks & Acceptance

**Execution:**
- [x] `backend/src/main/resources/db/migration/V3__create_users_table.sql` -- add -- `users` table per AC
- [x] `backend/src/main/resources/db/migration/V4__create_active_sessions_table.sql` -- add -- `active_sessions` table per AC, owned by `security`
- [x] `backend/src/main/resources/db/migration/V5__seed_users.sql` -- add -- 12-user demo cast, BCrypt-hashed, kept separate from schema migrations
- [x] `backend/src/main/java/com/company/leave/users/Role.java`, `User.java`, `UserRepository.java`, `UserAccount.java`, `UserService.java` -- add -- per Code Map
- [x] `backend/src/main/java/com/company/leave/security/ActiveSession.java`, `ActiveSessionRepository.java`, `SessionRegistry.java`, `JwtService.java`, `JwtAuthenticationFilter.java`, `SecurityConfig.java` -- add -- per Code Map
- [x] `backend/src/main/java/com/company/leave/auth/LoginRequest.java`, `AuthController.java` -- add -- per Code Map
- [x] `backend/src/main/resources/application.yml` -- update -- add `security.jwt.secret` dev fallback
- [x] `backend/src/test/java/com/company/leave/CompanyLeaveApplicationTests.java` -- update -- table list includes `users`, `active_sessions`
- [x] `backend/src/test/java/com/company/leave/security/SessionRegistryTest.java` -- add -- `@SpringBootTest` + Testcontainers: create/touch/idle-reject/revoke, per I/O Matrix
- [x] `backend/src/test/java/com/company/leave/security/JwtServiceTest.java` -- add -- round-trip issue/parse, tampered signature rejected, expired token rejected (via the package-private `Duration` overload)
- [x] `backend/src/test/java/com/company/leave/auth/AuthControllerIntegrationTest.java` -- add -- `@SpringBootTest` + `@AutoConfigureMockMvc` + Testcontainers: login success/failure (incl. unknown username) per I/O Matrix, logout revokes + clears cookie, old-token-after-logout is 401, idle timeout via direct `last_seen_at` DB manipulation is 401, `audit_events` rows written on login and logout

**Acceptance Criteria:**
- Given the Flyway migrations, when applied, then `users` and `active_sessions` exist with the exact shape in the Story's AC, and a separate seed migration inserts the 12-user cast with BCrypt-hashed passwords
- Given a seeded user and the correct password, when `POST /auth/login` is called, then 200 + `CurrentUser` body + the exact `Set-Cookie` attributes + a 15-min jjwt-signed JWT carrying `sub`/`role`/`token_id`
- Given a wrong password, when `POST /auth/login` is called, then 401 `INVALID_CREDENTIALS`, no cookie
- Given a successful login, then `auth` obtains `token_id` only via `SessionRegistry.createSession` and never touches `active_sessions` directly
- Given a valid `access_token` cookie, when a request reaches `JwtAuthenticationFilter`, then signature/expiry is validated via `JwtService`, `touchOrReject` is called, and `SecurityContextHolder` is populated from the `role` claim only through this one path
- Given idle > 30 minutes, the next request is 401; given activity within 30 minutes, `last_seen_at` bumps and the request is not interrupted
- Given `POST /auth/logout`, then 204, all that user's sessions are deleted, cookie cleared, and a later request with the old (unexpired) JWT is 401
- Given a successful login or logout, an `audit_events` row is written with the actor's id and timestamp

## Implementation Notes

## Spec Change Log

## Design Notes

Jwts.builder()...signWith(key).compact() / Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload() is the correct jjwt 0.12.x/0.13.0 API (confirmed against current Javadoc) — do not use the pre-0.12 setSigningKey/parseClaimsJws names. HS256 requires a >=256-bit (32-byte) key; `Keys.hmacShaKeyFor` throws `WeakKeyException` at startup if `security.jwt.secret` resolves too short — this is fail-fast by design.

## Verification

**Commands:**
- `cd backend && ./mvnw -DskipTests=false test` -- expected: all tests pass, including the three new test classes and the updated `CompanyLeaveApplicationTests`
- `cd backend && ./mvnw -DskipTests compile` -- expected: compiles clean

## Review Triage Log

| Verdict | Route | Finding and evidence |
|---|---|---|
| medium | patch | `JwtAuthenticationFilter` was a servlet-component bean as well as a filter-chain member, risking duplicate registration and authentication loss in a deployed server; it is now instantiated only into the security chain. |
| false | reject | The 15-minute JWT lifetime and 30-minute idle cutoff are both explicit constraints in the frozen intent and epic architecture; the code implements each limit rather than contradicting either. |
| medium | patch | Missing/blank login fields reached `PasswordEncoder.matches` without request validation; `@NotBlank` plus `@Valid` now returns `VALIDATION_FAILED` without issuing a cookie, covered by integration tests. |
| medium | patch | Missing-password validation was absent from the integration tests; cases for omitted and blank passwords now verify 400, canonical error code, and no cookie. |
| medium | patch | Unknown usernames skipped BCrypt and could be distinguished by response timing; `UserService` now performs a BCrypt match against a per-process dummy hash for unknown usernames. |
| medium | patch | The JWT filter swallowed all runtime failures, including session-store failures, as anonymous requests; it now catches only `JwtException`, allowing infrastructure errors to surface. |
| medium | patch | Expired `active_sessions` rows were never reclaimed; session creation now deletes expired rows first, verified by the session-registry test. |
| medium | defer | Cookie-authenticated endpoints lack CSRF/origin checks, and SameSite does not block an untrusted same-site sibling origin; the frozen intent excludes a CSRF-token scheme and accepts SameSite for this POC, so trusted-origin hardening is recorded in deferred work. |
| false | reject | No current endpoint or service mutates `User.active`; session behavior after a future account-deactivation feature is not reachable in this story's implementation. |
| medium | patch | The non-dev JWT property existed only in the `dev` profile, so a configured `JWT_SECRET` could not satisfy `JwtService` under `demo`; a base environment placeholder was added and a demo-profile context test passed. |
| low | patch | The tamper test changed the final Base64URL signature character, whose unused trailing bits can leave the decoded signature unchanged; it now changes the first signature character. |
| medium | patch | Unknown-user timing equalization was also independently reported as a response-enumeration gap; fixed by the same dummy-hash comparison recorded above. |
| medium | patch | Null-password handling was independently reported as an uncontrolled BCrypt failure; the same request validation fix recorded above addresses it. |
