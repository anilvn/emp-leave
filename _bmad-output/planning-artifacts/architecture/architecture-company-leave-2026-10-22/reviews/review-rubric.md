---
subject: ARCHITECTURE-SPINE.md (company-leave-backend)
reviewed_against: _bmad-output/planning-artifacts/architecture/architecture-company-leave-2026-10-22/ARCHITECTURE-SPINE.md
sources_checked:
  - _bmad-output/planning-artifacts/architecture/architecture-company-leave-2026-10-22/.memlog.md
  - _bmad-output/planning-artifacts/prds/poc-prd-emp-leave-tracker-2026-10-22/prd.md
  - backend/pom.xml
  - backend/src/main/resources/application.yml
  - backend/src/main/java/com/company/leave/CompanyLeaveApplication.java
  - backend/src/main/resources/openapi.yaml (paths only)
  - backend/src/main/resources/db/migration (currently empty — only .gitkeep)
date: 2026-10-22
---

# Architecture Spine Review — company-leave-backend

## Verdict

**Needs revision before downstream stories build on it.** The paradigm choice (layered modular monolith, no event bus, service-only cross-module access) is sound and most ADs are enforceable. But the spine makes one factual claim about the codebase that is false (modulith deps "removed" — they aren't), and it leaves the entire operational/environmental envelope (docker-compose topology, env-var delivery, where TLS terminates) essentially undecided despite that envelope directly determining whether AD-7's cookie rule can even work in local dev. Two cross-cutting interactions (password reset vs. active-session invalidation; error-code taxonomy ownership) are real divergence risks an AD should have closed and didn't.

## Findings

### 1. [MAJOR] AD-1's "already removed" claim is false — pom.xml still ships both Modulith starters

AD-1's Rule states: *"`spring-modulith-starter-core` and `spring-modulith-starter-test` are removed from `pom.xml`"* (present tense, stated as accomplished fact). The Stack table's `[ADOPTED]`-style framing and the memlog ("(decision) ... spine recommends removing both from pom.xml") both read as a done deal.

Checked `backend/pom.xml`: both dependencies are still present —
- `spring-modulith-starter-core` (lines 46–49)
- `spring-modulith-starter-test`, scope `test` (lines 129–133)
- plus the `spring-modulith-bom` import and `<spring-modulith.version>2.1.1</spring-modulith.version>` property (lines 31, 36–42) that exist only to support those two.

This is exactly the kind of divergence the spine exists to prevent, now self-inflicted: a future implementer who trusts the spine's "removed" claim will leave dead Modulith scaffolding on the classpath, and nothing stops a later contributor from reaching for `ApplicationModules.verify()` (the test starter is right there) specifically because AD-1 says no such verification runs — recreating the exact "tool-enforced in some places, convention-only in others" inconsistency AD-1 was written to prevent. The spine should either (a) phrase this as an open action item for the next story ("to be removed in Story X"), not a past-tense fact, or (b) if removal was deliberately deferred, say so in Deferred with a reason, rather than letting AD-1 assert something `pom.xml` contradicts.

### 2. [MAJOR] Operational/environmental envelope is a silent structural dimension

Checklist flags this explicitly, and it applies here. Deferred has one line — *"CI/CD and multi-environment promotion: out of scope ... this spine fixes a single-environment, `docker compose up` POC per the PRD"* — but the spine never actually decides the single environment it claims to fix:

- No docker-compose service topology (postgres / backend / frontend / proxy container relationships), no statement of how the proxy relates to the Angular and backend containers.
- No decision on how `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`/the JWT HMAC secret are actually supplied to the compose stack (`.env` file? `environment:` block? docker secrets?) — this matters because the Consistency Conventions table promises "no hard-coded secrets" but the mechanism delivering that promise is never named.
- `application.yml`'s `DB_URL` default is `jdbc:postgresql://localhost:5432/leave` (line 7) — inside docker-compose's container network this must be overridden (e.g. to a `postgres` hostname). The spine doesn't say this override is the compose file's job, so a module implementer testing locally vs. in compose has no documented expectation of which value is active where.
- FR55 (HTTPS) is in the PRD's own FR range this spine binds (FR50–FR59) and the Capability → Architecture Map lists "Security (FR55–FR59)" as governed by AD-7/AD-8/AD-9 — but none of those ADs says anything about HTTPS or the proxy. FR55 is ungoverned by any AD, and the one place it could land (the deployment envelope) is the exact dimension left silent.
- This interacts directly with AD-7's **enforceability**: AD-7 mandates a `Secure` cookie attribute, which browsers will not transmit over plain HTTP. `application.yml` has a `dev` profile (line 18-24) distinct from the proxy-fronted compose stack. If a developer runs the `dev` profile directly against `localhost:8080` (no proxy, no TLS) — which the existing profile structure invites — the `Secure` cookie silently breaks login for local dev, and nothing in the spine says the `dev` profile must still go through the local HTTPS proxy, or that the `Secure` flag is conditional per profile. Either answer is fine, but the spine picking neither leaves two builders free to resolve it incompatibly (one disables `Secure` in `dev`, contradicting AD-7; another assumes the proxy is mandatory even for `dev`).

### 3. [MODERATE] Password reset / account lockout vs. active-session revocation — a real cross-module interaction no AD closes

AD-8 covers idle timeout and logout-triggered revocation of `active_sessions` rows (owned by `auth`). AD-9 covers lockout, owned by `users`, explicitly "independent of the token model." Neither AD — nor the Capability → Architecture Map — says what happens to a user's *existing* `active_sessions` rows when HR resets their password (FR20) or when the account becomes locked (FR57) mid-session. This is squarely the kind of interaction the spine should fix: it touches two different owning modules (`users` triggers the event, `auth` owns the table that would need to change), and two independently-built units could reasonably disagree — one implementer assumes a password reset/lockout must invalidate any live session (standard security practice), another assumes `active_sessions` is untouched until natural idle-timeout. Either is defensible, but the spine doesn't pick one, so Story implementers for `users` and for `auth`/`security` have no shared answer.

### 4. [MINOR] AD-11 error-code taxonomy has no home or naming convention

AD-11 (already flagged `[ASSUMPTION]` by the spine itself) gives three example codes (`BALANCE_INSUFFICIENT`, `RBAC_DENIED`, `ACCOUNT_LOCKED`) but doesn't say where the canonical list of codes lives (a shared enum/constants class in `common`?) or what naming convention governs new ones. Without that, two modules will each invent their own casing/vocabulary for codes the Angular client is supposed to branch on — a small but real version of the same "no shared source of truth" problem AD-3 and AD-10 solve for other concerns. Low severity only because AD-11 is already self-flagged as unconfirmed with the user.

## Checklist walkthrough

- **Fixes the real divergence points, misses none.** Mostly yes (module boundaries, auth model, RBAC, audit/ledger atomicity, error envelope are all genuinely addressed and genuinely would diverge without them). Misses: session-revocation-on-reset/lockout interaction (#3), error-code ownership (#4), and the deployment envelope's effect on AD-7 (#2).
- **Every AD's Rule is enforceable and prevents its divergence.** AD-3/4/5/6/7/8/9 are enforceable as written (several backed by DB constraints/triggers, which is good discipline for a convention-only design). AD-1/AD-2/AD-10 are pure code-review conventions with no tool enforcement — consistent with the explicit choice to drop Modulith verification, and partly backstopped by the PRD's own requirement for RBAC/approval/audit tests, so acceptable at this altitude. AD-7 is undermined by the undecided deployment envelope (#2).
- **Nothing under Deferred could let two units diverge incompatibly.** Four of five Deferred bullets are clean (Phase-2 FRs, backups, real SMTP, access-log read API — all genuinely inert until picked up later). The Modulith-verification bullet is not actually inert, because the dependency it refers to is still physically in `pom.xml` (#1), which is a latent trigger for exactly this kind of incompatible divergence.
- **Named tech is verified-current against pom.xml.** Java 21, Spring Boot parent 4.1.1, `flyway-database-postgresql`, Lombok, JUnit5/Testcontainers(Postgres), Spring Mail all check out exactly against `pom.xml`. The one failure is Spring Modulith, which the spine claims is gone and isn't (#1).
- **Ratifies rather than contradicts the brownfield codebase.** `application.yml` profiles (dev/demo/test), `ddl-auto: validate`, Flyway `classpath:db/migration` location, and the UTC-pinning in `CompanyLeaveApplication` are all correctly described and ratified. The one contradiction is, again, the Modulith claim (#1).
- **Covers the PRD's capabilities.** The Capability → Architecture Map's FR ranges (FR1–9, 10–17, 18–26, 27–31, 42–45, 50–54, 55–59) exactly tile the spine's bound range (FR1-31, FR42-45, FR50-59) with no gaps or overlaps, and the ERD/table list matches the PRD's core-tables list plus the one openly-flagged addition (`active_sessions`). Good. The one weak spot is FR55 (HTTPS) nominally sitting under a row governed by ADs that don't actually address it (#2).
- **Every structural dimension at this altitude is decided/deferred/flagged.** Module boundaries, auth, authz, audit integrity, balance integrity, error shape, naming/data conventions: all decided. Logging is decided ("SLF4J/Logback to stdout; no external APM"). The operational/environmental envelope — deployment topology, env-var/secret delivery mechanism, TLS termination point — is the one dimension left genuinely silent rather than decided or properly deferred (#2), which is precisely the failure mode this checklist calls out by name.

## File

`C:\Users\1772\Documents\vs-code\company-leave\_bmad-output\planning-artifacts\architecture\architecture-company-leave-2026-10-22\reviews\review-rubric.md`
