# Review Lens: Version & Reality-Check Verification

**Target:** `ARCHITECTURE-SPINE.md` (architecture-company-leave-2026-10-22)
**Lens:** Every committed decision must be web-researched or reality-checked, not asserted from training data — current library/framework versions, technology existence/fit, and brownfield codebase claims.
**Method:** Read `backend/pom.xml` and `backend/src/main/resources/application.yml` first as brownfield ground truth, then verified each version/artifact claim against Maven Central (`central.sonatype.com`, `repo1.maven.org` directory listings — the legacy `search.maven.org/solrsearch` API proved stale/unreliable for 2026 releases and was discarded as a source), spring.io blog posts, and official Spring Boot 4.x reference docs.

---

## Verdict

**Mostly sound on versions, but the spine contains one materially false claim about the current brownfield codebase (spring-modulith is NOT removed from `pom.xml`, contrary to AD-1's wording), plus one overstated/imprecise technical claim (the "web starter split" is not quite what AD/Stack table implies) and a minor staleness note (a newer patch exists). All core version numbers (Spring Boot 4.1.1, Spring Framework 7.x, PostgreSQL 18, `flyway-database-postgresql`) check out as real and correctly current.**

---

## Findings

### 1. [HIGH] AD-1's claim that spring-modulith is "removed from `pom.xml`" is false as of this review — the dependency is still present

AD-1 states: *"`spring-modulith-starter-core` and `spring-modulith-starter-test` are removed from `pom.xml` — they imply boundary verification this design explicitly does not run."*

This is phrased as a statement of current fact about the brownfield codebase. It is not true. The actual `backend/pom.xml` (read directly for this review) still contains, unmodified:

- `dependencyManagement` import of `spring-modulith-bom` (version `2.1.1`, via the `spring-modulith.version` property) — lines 33–43
- `org.springframework.modulith:spring-modulith-starter-core` as a main dependency — lines 46–49
- `org.springframework.modulith:spring-modulith-starter-test` as a test dependency — lines 129–133

So either: (a) this is a pending action item that hasn't been executed yet and the spine should say "must be removed" / "to remove," not describe it as already done, or (b) the spine's author checked a different version of `pom.xml` than the one in the repo. Either way, a builder reading AD-1 today would be wrong to assume Modulith tooling is absent — `spring-modulith-starter-test`'s presence in particular means `@ApplicationModuleTest`/verification APIs are still on the test classpath and nothing currently stops someone from writing a Modulith boundary test, which directly contradicts the design intent ("Spring Modulith's verification is deliberately not used here"). This should be corrected to an explicit action item, or the removal should actually be made, before the spine is treated as ground truth.

### 2. [MEDIUM] "Spring Boot 4 splits `spring-boot-starter-web` into `spring-boot-starter-webmvc`" is an imprecise characterization — it's a rename-and-deprecate, not a clean split into two parallel starters

The task framing (and a plausible reading of the Stack table and AD-7's "oauth2ResourceServer().jwt() (already in pom.xml)") suggested a straight split. Reality, per Spring Boot 4 reference docs and Maven Central:

- `spring-boot-starter-web` still exists in the 4.x line but is deprecated in favor of `spring-boot-starter-webmvc`.
- `spring-boot-starter-webmvc` is the new recommended starter for Spring MVC; `spring-boot-starter-webflux` is the reactive counterpart. This isn't a 1-for-1 split of one artifact into two successor artifacts with the same role — it's a rename of the servlet-stack starter plus a broader modularization where Boot 4 broke up the monolithic `spring-boot-autoconfigure` module into many feature-specific modules (one per starter), each gaining its own paired `*-test` module.
- The pom.xml's use of `spring-boot-starter-webmvc` (not the deprecated `spring-boot-starter-web`) is therefore the *correct, current* choice — this finding is about the spine's/task's shorthand description of *why* that name exists, not about the pom.xml being wrong.

Confirmed real and current on Maven Central / repo1.maven.org: `spring-boot-starter-webmvc` has a published `4.1.1` release (2026-08-20) matching the pinned parent version, with `4.1.2` already out (2026-10-22, i.e. today).

### 3. [LOW] Spring Boot 4.1.1 is real and current, but a newer patch (4.1.2) already exists as of today

Verified directly against `repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-webmvc/` (and mirrored for `spring-boot-starter-flyway` and `spring-boot-starter-security-oauth2-resource-server`): the 4.1.x line is `4.1.0` (2026-06-10) → `4.1.0-RC1`/milestones → `4.1.1` (2026-08-20) → **`4.1.2` (2026-10-22 — today's date)**. The pom.xml's pin to `4.1.1` is a real, current-ish released version and not a hallucination, but it is one patch behind as of the spine's own `created`/`updated` date (2026-10-22). Not a defect — the spine correctly treats the pinned version as reality rather than re-asserting a different number — but worth a note that a patch bump is available, since the spine's "Deferred" section already tracks the PRD/pom.xml Spring Boot 3-vs-4 mismatch and this is the same category of housekeeping.

Also confirmed via spring.io's blog ("Spring Boot 4.1.1 available now," dated 2026-08-20) and `spring.io/blog/2026/02/18/spring-framework-7-0-5-available-now` that Boot's 4.1.x line is real and actively patched, not a fabricated version string.

### 4. [LOW] Spring Framework 7.x pairing with Spring Boot 4 confirmed, with reasonable specificity

Spring Framework 7.0 and Spring Boot 4.0 were released together in November 2025 (per InfoQ's Spring team coverage and Broadcom/Spring announcements), and patch trains are paired (Framework 7.0.5 shipped alongside Boot 4.0.3 per the Feb 2026 Spring Framework blog post; a secondary source ties Boot 4.1.0 to Framework 7.0.8). The spine's Stack table entry "Spring Framework | 7.x (transitive via Boot 4)" is accurate and appropriately hedged (doesn't over-claim an exact patch number). No issue.

### 5. [LOW] PostgreSQL 18 confirmed real and current

PostgreSQL 18 was released 2025-09-25 (postgresql.org's own FAQ and release announcements) and remains the current major version as of this review (2026-10-22), with point releases (18.3/18.4 referenced in downstream vendor notes) continuing to ship. The spine's pin to "PostgreSQL 18" is a legitimate, current choice for a new POC — it is not sourced from `pom.xml`/`application.yml` (neither file pins a Postgres server version; that's an infra/docker-compose decision, not a Maven dependency), so this is a forward-looking choice rather than a brownfield-fact claim, and it checks out.

### 6. [LOW] `flyway-database-postgresql` confirmed as the correct current artifact, not a hallucinated name

Since the Flyway 10 database-module split, `flyway-core` retains only H2/SQLite support bundled in; PostgreSQL (and other production RDBMS) support moved to dedicated artifacts like `flyway-database-postgresql`. Confirmed on `repo1.maven.org/maven2/org/flywaydb/flyway-database-postgresql/`: actively published up through the 13.x line (latest seen: 13.11.1), with 12.x also present (12.11.0) — consistent with secondary reporting that Spring Boot 4.1.0's BOM manages Flyway at 12.4.0. The pom.xml's explicit `flyway-database-postgresql` dependency (alongside the new `spring-boot-starter-flyway` convenience starter, itself confirmed to exist and resolve at 4.1.1 via Maven Central) is the right call, not an invented artifact. No `flyway-core` direct dependency is declared, which matches the current recommended pattern (the starter pulls in `flyway-core` transitively; the DB-specific module is the one piece you must add yourself).

### 7. [INFO] The non-obvious `*-starter-*-test` artifact family (`spring-boot-starter-data-jpa-test`, `spring-boot-starter-flyway-test`, `spring-boot-starter-mail-test`, `spring-boot-starter-security-test`, `spring-boot-starter-validation-test`, `spring-boot-starter-webmvc-test`) all independently verified as real

These names look unusual next to Boot 3's single `spring-boot-starter-test`, and an initial pass using Spring Boot's own `test-modules.html` reference page (and the legacy `search.maven.org/solrsearch` API) suggested they might not exist, since that doc lists module names *without* "-starter-" (e.g. `spring-boot-data-jpa-test`, `spring-boot-webmvc-test`) and the legacy Solr search returned zero hits for all of them. Direct verification against `central.sonatype.com` resolved the discrepancy: every one of these `-starter-...-test` artifacts does exist as a real, currently-published `org.springframework.boot` artifact (each a thin starter wrapping the underlying `-test` module plus `spring-boot-starter-test`/`spring-boot-starter-jdbc-test`, etc., confirmed via each artifact's own POM). The legacy Solr-based search API at `search.maven.org` appears stale for 2026-era releases (it returned no 4.x results at all even for well-established artifacts like `spring-boot-starter-parent`) and should not be trusted as a source for current-version checks — `central.sonatype.com` and `repo1.maven.org` directory listings were the reliable sources here. No correction needed to the spine/pom.xml; flagged only so future reviewers don't waste time distrusting these names or re-querying the unreliable endpoint.

### 8. [INFO] `spring-boot-starter-security-oauth2-resource-server` (used in pom.xml, referenced by AD-7) confirmed as the current/renamed artifact, not a typo

A plain Boot-3-era `spring-boot-starter-oauth2-resource-server` still exists (up to 3.5.3) but is deprecated in favor of the longer `spring-boot-starter-security-oauth2-resource-server` name used in this project's `pom.xml` and consistent with Boot 4's modularization pattern (one starter per `spring-security-*` feature area, prefixed consistently). Confirmed present on Maven Central at 4.1.1 via `central.sonatype.com`. AD-7's reference to this dependency as "already in pom.xml" is accurate.

### 9. [INFO] `application.yml` claims in the spine are accurate

The spine's Consistency Conventions and AD references to `ddl-auto: validate`, Flyway `classpath:db/migration` location, JVM pinned to UTC, and the datasource pointing at Postgres via env-var-driven JDBC URL all match the actual `backend/src/main/resources/application.yml` read for this review. No discrepancy found here.

---

## Summary Table

| Claim | Verdict | Evidence |
| --- | --- | --- |
| Spring Boot 4.1.1 is a real, current release | Confirmed | repo1.maven.org dir listing (2026-08-20); spring.io blog |
| Spring Boot 4 "splits" `spring-boot-starter-web` into `spring-boot-starter-webmvc` | Partially accurate — rename + deprecation, not a clean split into two sibling starters | Spring Boot 4.x reference docs; Maven Central listings |
| PostgreSQL 18 is real/current | Confirmed | postgresql.org FAQ/release notes (2025-09-25 GA, current as of 2026-10-22) |
| `flyway-database-postgresql` is the correct DB-specific artifact (vs. bundled `flyway-core`) | Confirmed | repo1.maven.org dir listing (up to 13.11.1); Flyway 10+ split documented |
| Spring Framework 7 is the correct paired major version for Spring Boot 4 | Confirmed | InfoQ/Broadcom Nov 2025 joint release coverage; spring.io Feb 2026 Framework 7.0.5 post |
| spring-modulith removed from `pom.xml` (AD-1) | **False as of this review** — still present | `backend/pom.xml` lines 33–43, 46–49, 129–133 |
| `spring-boot-starter-security-oauth2-resource-server` and the `*-starter-*-test` family are real artifacts | Confirmed | central.sonatype.com per-artifact pages (all resolve at 4.1.1/4.2.0-RC1) |

## Sources

- https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-webmvc/ (version directory listing)
- https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-flyway/
- https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-security-oauth2-resource-server/
- https://repo1.maven.org/maven2/org/flywaydb/flyway-database-postgresql/
- https://central.sonatype.com/artifact/org.springframework.boot/spring-boot-starter-webmvc-test
- https://central.sonatype.com/artifact/org.springframework.boot/spring-boot-starter-flyway
- https://central.sonatype.com/artifact/org.springframework.boot/spring-boot-starter-flyway-test
- https://central.sonatype.com/artifact/org.springframework.boot/spring-boot-starter-mail-test
- https://central.sonatype.com/artifact/org.springframework.boot/spring-boot-starter-validation-test
- https://central.sonatype.com/artifact/org.springframework.boot/spring-boot-starter-security-test
- https://central.sonatype.com/artifact/org.springframework.boot/spring-boot-starter-security-oauth2-resource-server
- https://central.sonatype.com/artifact/org.springframework.boot/spring-boot-starter-data-jpa-test
- https://docs.spring.io/spring-boot/4.1.0-SNAPSHOT/reference/testing/test-modules.html
- https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/
- https://spring.io/blog/2026/02/18/spring-framework-7-0-5-available-now/
- https://www.postgresql.org/about/news/postgresql-18-rc-1-released-3130/ and postgresql.org FAQ
- https://www.infoq.com/news/2025/11/spring-7-spring-boot-4
- https://itnext.io/spring-boot-v4-modularization-d2ba253d59ab
- Brownfield ground truth: `backend/pom.xml`, `backend/src/main/resources/application.yml` (read directly)
