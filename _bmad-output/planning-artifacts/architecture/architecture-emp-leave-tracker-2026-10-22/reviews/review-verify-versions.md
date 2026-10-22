# Review: version and technology verification

Spine reviewed: `ARCHITECTURE-SPINE.md` (status draft, 2026-10-22). Memlog: `.memlog.md`. The spine was not edited.
Review date: 2026-10-22. Method: WebSearch and WebFetch against spring.io, docs.spring.io, the Spring Boot GitHub tag, postgresql.org, and docs.aws.amazon.com. Fetched summaries are model-extracted from the page text and should be spot-checked before a pin is committed.

## Verdict

Most of the stack checks out, but the ElastiCache "Redis 8.x" line is wrong for AWS and must change, and the Spring Boot pin should be re-decided now that 4.1.1 is out.

## Item-by-item

| Item in spine | Spine says | Verified | Result |
| --- | --- | --- | --- |
| Spring Boot | 4.1.0, "ASSUMPTION, confirm on spring.io" | spring.io blog "Spring Boot 4.1.0 available now", dated 2026-06-10, confirms 4.1.0 on Maven Central | CONFIRMED. Assumption can be removed. |
| Spring Boot latest patch | not mentioned | spring.io blog "Spring Boot 4.1.1 available now", dated 2026-08-20. Boot docs for 4.1 also point to 4.1.1 | OUT OF DATE. 4.1.0 is no longer the newest 4.1 patch. |
| Flyway | 12.4.0, managed by Boot 4.1.0, do not override | Boot `v4.1.0` tag `platform/spring-boot-dependencies/build.gradle` lists `Flyway "12.4.0"`. Maven Repository lists 12.4.0 released 2026-04-14. Newer 12.x releases exist (search results mention 12.6.0 and later) | CONFIRMED for the Boot-managed version. The "do not override" rule is correct. Newer Flyway exists but Boot decides. |
| PostgreSQL server | 18.x | postgresql.org versioning page: latest minor 18.6, PostgreSQL 18 first released 2025-09-25, EOL 2030-11-14 | CONFIRMED as "18.x". Pin the image tag to 18.6 (or the latest 18.x at implementation). RDS engine availability for 18.6 was not verified (AWS had announced RDS 18.3 in Feb 2026). |
| PostgreSQL JDBC driver | not in spine | Boot `v4.1.0` build.gradle lists `Postgresql "42.7.11"` | Add to Stack table as Boot-managed 42.7.11 (do not override). |
| Redis / ElastiCache | "Redis (ElastiCache) 8.x (ASSUMPTION: engine availability not confirmed)" | AWS ElastiCache engine-versions page: "ElastiCache Serverless caches and node-based clusters support all Redis OSS versions 7.1 and before." The newest Redis OSS on ElastiCache is 7.1. ElastiCache for Valkey is at 8.0, 8.1, 8.2, 9.0 and 9.1 (9.1 latest in the page) | WRONG AS WRITTEN. Redis 8.x is not a supported ElastiCache engine. See finding 1. |
| Testcontainers | "managed by Boot BOM, not independently verified" | Boot `v4.1.0` build.gradle lists `Testcontainers "2.0.5"` | CONFIRMED as Boot-managed 2.0.5. Testcontainers 2.x artifact names differ from 1.x (for example `testcontainers-postgresql`, `testcontainers-redis` modules); the implementer must use the Boot BOM and 2.x module names. |
| Spring Security, Data JPA, Hibernate, Nimbus JOSE | "managed by Boot" | Hibernate and Security are named in the 4.1.0 release coverage (Hibernate 7.4, Spring Security 7.1 per secondary sources). Nimbus JOSE was not found in the Boot 4.1.0 managed list | Hibernate and Security: plausible, not checked against the BOM. Nimbus: UNCONFIRMED. Verify in the BOM before relying on it. |
| ArchUnit | "pin at build start" | Not searched | UNVERIFIED. Pin is deferred, which is acceptable, but record a version and confirm it works on Java 21. |
| Java | 21 LTS | User-chosen. Not re-verified in this pass | Accepted as a user decision. Note that Java 25 is also an LTS line; if the team wants the newest LTS, that is a decision to record. |
| Container base | Eclipse Temurin 21 JRE | Not searched | UNVERIFIED tag. Pin an explicit image tag (for example `eclipse-temurin:21-jre`) and confirm it exists before first build. |
| Flyway tooling for PostgreSQL 18 | implied | Flyway 12.4.0 release notes (third-party blog) mention PostgreSQL 16 compatibility for pg_stat_statements; no PostgreSQL 18 claim was found | UNVERIFIED for PG 18 specifically. Confirm on Redgate's Flyway release notes. |

## Findings

1. **Redis 8.x on ElastiCache is not a supported engine (high severity).** AWS lists Redis OSS only through 7.1 and points new workloads to ElastiCache for Valkey. Confirmed items: Redis OSS 7.1 and earlier; Valkey 8.0 through 9.1. Recommended pin: ElastiCache for Valkey 9.1 (latest on the AWS page), or 8.2 if the team wants the older line. Change the Stack row to "ElastiCache for Valkey 9.x (or 8.2)"; rename the Redis references in AD-7, AD-17, AD-21 and the container diagram, or add a note that the client and test image are Valkey-compatible (Spring Data Redis works with Valkey). Test container should use a Valkey image to match production. Remove the ASSUMPTION tag only after the chosen engine is confirmed in the target region.

2. **Spring Boot pin is 4.1.0, but 4.1.1 is current (medium).** Confirmed: 4.1.0 is GA on 2026-06-10 and matches the spine. 4.1.1 was released 2026-08-20 with 98 fixes. Recommended pin: Spring Boot 4.1.1 (the latest 4.1 patch), or keep 4.1.0 with a recorded reason. Either way, the Boot-managed Flyway, Testcontainers and PostgreSQL JDBC versions must be re-read from the chosen release's BOM, because they can change between patches. The Flyway 12.4.0 and Testcontainers 2.0.5 values above come from 4.1.0. Remove "ASSUMPTION" from the Boot row once the pin is final.

3. **Flyway 12.4.0 is confirmed as Boot-managed, with a caveat (low).** The `v4.1.0` BOM lists Flyway 12.4.0, so the spine's claim is correct. Do not override it. Note the spine should say "managed by Boot 4.1.x; currently 12.4.0 in 4.1.0", since the value can move with the patch. Newer Flyway 12.x releases exist (the search results mention 12.6.0 and later). Only consider overriding if a needed fix or PostgreSQL 18 feature is missing, and record that decision.

4. **PostgreSQL 18 pin is "18.x" with no specific patch (low).** postgresql.org lists 18.6 as the latest minor, with EOL in November 2030. Recommended pin: PostgreSQL 18.6 for Testcontainers and local dev, and check that RDS offers 18.6 in the target region before go-live (the AWS announcement found covered 18.3). Keep "18.x" in the spine, but add the patch to the Stack table.

5. **Testcontainers and nimbus-jose-jwt are not yet fully pinned (low).** Testcontainers is confirmed as 2.0.5 in Boot 4.1.0 and should replace the "not independently verified" note. Nimbus JOSE was not found in the managed list and needs checking in the BOM. ArchUnit, Temurin image tag and the PostgreSQL JDBC driver (42.7.11, Boot-managed) also need stack-table rows. Recommended: add rows with "managed by Boot 4.1.x, BOM value X" and the ArchUnit version pinned at build start.

## Open items that remain unverified

- Nimbus JOSE version in the Boot BOM.
- ArchUnit version and Java 21 compatibility.
- Temurin 21 JRE image tag.
- RDS availability of PostgreSQL 18.6 in the target region.
- Whether the 4.1.0 vs 4.1.1 choice changes any managed value (not checked against the 4.1.1 BOM).
- Flyway 12.4.0 support for PostgreSQL 18 (the one source found was a third-party blog).

Sources used: spring.io Spring Boot 4.1.0 and 4.1.1 announcements; docs.spring.io 4.1.x dependency-versions appendix; github.com spring-projects/spring-boot `v4.1.0` `platform/spring-boot-dependencies/build.gradle`; postgresql.org versioning page; docs.aws.amazon.com ElastiCache engine-versions page; Maven Repository (Flyway 12.4.0 release date).
