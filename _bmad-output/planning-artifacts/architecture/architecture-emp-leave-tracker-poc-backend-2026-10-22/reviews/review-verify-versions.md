---
review: verify-versions
target: ARCHITECTURE-SPINE.md (emp-leave-tracker POC backend)
date: 2026-10-22
method: live web checks (spring.io, docs.spring.io, Maven Central metadata, GitHub releases, Docker Hub API, postgresql.org, framework source on GitHub)
verdict: PASS on versions, FAIL on one security default (CSRF), plus one build-breaking packaging conflict
---

# Version and reality check: POC backend spine

## Verdict

The named versions hold up. Spring Boot 4.1.1, Spring Modulith 2.1.1, the Boot 4 resource-server starter name, PostgreSQL 16 and Caddy 2.11 are all current and confirmed from primary sources. The Mailpit pin is one minor line behind.

Two problems need fixing before build:

- **CRITICAL:** The live defaults the spine relies on make AD-10 fail silently. Spring Security's resource server **turns off CSRF for every request where its BearerTokenResolver finds a token**. Once the JWT is read from a cookie, that covers every authenticated request.
- **HIGH:** The Structural Seed's `dto/` subpackage clashes with Spring Modulith's default rule that subpackages are internal, so `verify()` will fail the build.

## Findings

| # | Severity | Item | Finding | Fix |
|---|---|---|---|---|
| F1 | **Critical** | AD-9 + AD-10 (CSRF with a cookie JWT) | `OAuth2ResourceServerConfigurer.registerDefaultCsrfOverride` calls `csrf.ignoringRequestMatchers(this.requestMatcher)`. The `BearerTokenRequestMatcher` matches whenever the configured `BearerTokenResolver` (via `BearerTokenAuthenticationConverter`) resolves a token. With a cookie-based resolver, every request that carries the JWT cookie, which is every authenticated state-changing request, **skips CSRF**. Ignored matchers are ANDed out of `requireCsrfProtectionMatcher`, so turning CSRF back on afterwards does not undo it. Only login, which has no JWT yet, would still be protected. | Amend AD-9/AD-10 so the JWT cookie is **not** wired through `http.oauth2ResourceServer(...)`. Register a `BearerTokenAuthenticationFilter`, or a small custom filter, with the cookie `BearerTokenResolver`, a `JwtAuthenticationProvider(jwtDecoder)` and the converter yourself, so `CsrfFilter` applies normally. Add a mandatory test: a POST with a valid JWT cookie and no `X-XSRF-TOKEN` must get 403. |
| F2 | **High** | AD-1 / Structural Seed (`{Module}Service.java dto/  # public API`) | In Spring Modulith, only a module's base package is its API. **Every subpackage is internal by default**, including `dto/`. Cross-module use of `leave.dto.*` therefore fails `ApplicationModules.verify()`. The prose of the Design Paradigm and AD-1 says "DTO records and events" live in the root package, which contradicts the seed. The same applies to `common`'s subpackages (config, security, errors, working days), which every module is meant to use. | Choose one. (a) Put DTO records and events directly in the module root package and drop `dto/` from the seed. (b) Annotate `dto/package-info.java` with `@NamedInterface("dto")`. For `common`, either keep the shared types in `com.company.leave.common` itself, or mark the subpackages with `@NamedInterface`, or make it `@ApplicationModule(type = Type.OPEN)`. |
| F3 | Medium | AD-10 (CSRF token storage with a stateless JWT) | The spine doesn't name a `CsrfTokenRepository`. The default is `HttpSessionCsrfTokenRepository`, which creates an `HttpSession` and a `JSESSIONID`. That contradicts the stateless decision in AD-9. The Spring docs' `GET /csrf` → `CsrfToken` pattern is the one AD-10 uses, and it fits, but the docs note the token is cleared and must be re-fetched after authentication and logout success. | Specify `CookieCsrfTokenRepository`. It can stay HttpOnly because the SPA reads the token from the response body, not the cookie. Set SameSite via `setCookieCustomizer`. Keep the default `XorCsrfTokenRequestAttributeHandler`: `/api/auth/csrf` returns the masked token, and the Xor handler accepts it from `X-XSRF-TOKEN`. Make `/api/auth/csrf` `permitAll`. The SPA re-fetches the token after login and logout. |
| F4 | Medium | AD-8 / AD-9 (`role` claim → `hasRole(...)`) | By default `JwtGrantedAuthoritiesConverter` maps the `scope`/`scp` claims to `SCOPE_*` authorities. A `role` claim is ignored, so every `hasRole('HR_ADMIN')` would deny. Boot 4.1.1 exposes `spring.security.oauth2.resourceserver.jwt.authorities-claim-name` and `.authority-prefix`. | Add to AD-9: `authorities-claim-name=role`, `authority-prefix=ROLE_`, or one `JwtAuthenticationConverter` bean in `common`. If F1 is fixed by building the filter by hand, pass the converter to `JwtAuthenticationProvider` explicitly. |
| F5 | Medium | AD-9 (HS256 "through resource-server support") | Confirmed: `NimbusJwtDecoder.withSecretKey(key)` defaults to HS256 (source: `private JWSAlgorithm jwsAlgorithm = JWSAlgorithm.HS256;`). `NimbusJwtEncoder.withSecretKey(SecretKey)` exists **since Spring Security 7.0** and its `algorithm()` defaults to HS256. Both live in `spring-security-oauth2-jose`, which the starter brings in. However, Boot has **no property for an HMAC secret**: `jws-algorithms` defaults to `RS256` and only public-key and JWK properties exist. RFC 7518 §3.2 requires an HS256 key of at least 256 bits. | State that `common` declares the `JwtDecoder` and `JwtEncoder` beans from one `SecretKeySpec(…, "HmacSHA256")` built from the environment variable, and that startup fails if the secret is shorter than 32 bytes. |
| F6 | Low | Stack: Mailpit `v1.30` | The `axllent/mailpit:v1.30` tag has been frozen at v1.30.7 since 2026-08-08. The current line is **v1.31** (v1.31.6, 2026-10-15, marked Latest). The memlog's "v1.30.5 latest" is out of date. | Pin `axllent/mailpit:v1.31`. |
| F7 | Low | Conventions: "paged using Spring Data's `PagedModel` shape" | In Boot 4.1.1, `spring.data.web.pageable.serialization-mode` defaults to **`direct`**, so returning a bare `Page<T>` serializes `PageImpl` and logs a warning. `PagedModel` (Spring Data Commons 4.1.1) is opt-in. The Spring Data docs also say this envelope "is not guaranteed stable and we might change it at any time". | Say how it is produced: either controllers return `new PagedModel<>(page)`, or set `spring.data.web.pageable.serialization-mode=via-dto` once. Pick one. Accept the stability caveat, or define a project-owned `PageResponse<T>` record. |
| F8 | Low | AD-11 (`@TransactionalEventListener(AFTER_COMMIT)`) | Confirmed: AFTER_COMMIT is the default phase. The listener is **not invoked if no transaction is active** unless `fallbackExecution=true`. `TransactionalApplicationListenerSynchronization` invokes it from `afterCompletion(STATUS_COMMITTED)`, so exceptions are **logged, not propagated**: an SMTP failure won't 500 an approval, as AD-11 intends. It runs **synchronously on the request thread**, so SMTP latency adds to the response. Data access there still joins the finished transaction. Since Spring 6.1 (`RestrictedTransactionalEventListenerFactory`), a `@Transactional` listener method must be `REQUIRES_NEW` or `NOT_SUPPORTED`, or startup fails with `IllegalStateException`. | Add to AD-11: events are published only from inside `@Transactional` service methods. The listener catches and logs all exceptions. The listener, or the history write it calls, uses `REQUIRES_NEW` (never plain `@Transactional`). Optionally make it `@Async`. |
| F9 | Low | Stack: Spring Modulith dependency set | The Modulith Event Publication Registry tracks **all** `@TransactionalEventListener`s whenever `spring-modulith-starter-jpa` or `-jdbc` is present. It also needs its own publication table, which AD-13 (Flyway only, `ddl-auto=validate`) would have to create. That would quietly reverse the "no registry/outbox" decision in AD-11. | Pin the Modulith dependency to `spring-modulith-starter-test` (2.1.1 exists, test scope) under `spring-modulith-bom` 2.1.1. Forbid the persistence starters in the POC. |
| F10 | Low | AD-10 dev envelope (`Secure` cookie on `http://localhost`) | Chrome and Firefox accept `Secure` cookies from `http://localhost`. **Safari/WebKit rejects them** (open WebKit bugs 231035/232088), so dev login fails in Safari. Same-site across `localhost:4200` and `localhost:8080` is fine, because the port is not part of the site (MDN). | Note "dev: Chrome/Firefox only, or run dev behind Caddy TLS". |
| F11 | Info | AD-10 (SPA side) | Angular's built-in XSRF interceptor sends `X-XSRF-TOKEN` only to relative and same-origin URLs and reads the token from a cookie. It will **not** add the header for the cross-origin API. The CORS config must also allow the `X-XSRF-TOKEN` and `Content-Type` request headers along with credentials. | Tell the SPA builder to write a custom interceptor that uses the body token. List the allowed headers in the CORS config. |
| F12 | Info | Testcontainers (Conventions: "Postgres in Testcontainers") | Boot 4.1.1 manages Testcontainers **2.0.5**. The 2.x module names carry a `testcontainers-` prefix (`org.testcontainers:testcontainers-postgresql`). The scaffold `pom.xml` has neither that module nor `spring-boot-testcontainers`. | Add both at test scope with no version, so the Boot BOM manages them. |
| F13 | Low | AD-8 vs dependency graph | AD-8's "one servlet filter is the sole writer of `access_log`" conflicts with the graph if the filter lives in `common`: `common` "depends on none" of the modules, and `audit` owns `access_log` and depends on `common`, so verify() would report a cycle. | State that the access-log filter lives in `audit`. `audit.web` is fine, because Spring picks up the filter as a bean. |
| F14 | Info | Support windows | Boot 4.1 OSS support to about July 2027 comes only from third-party sources (endoflife.ai). spring.io's support page rendered without data. Boot 4.2.0-M2 and Modulith 2.2.0-M2 are already on Central, and Modulith 2.2 targets Boot 4.2. | No change. Plan a 4.2/2.2 pair bump after 4.2 GA. |

## Confirmed (no change needed)

| Claim in spine | Status | Evidence |
| --- | --- | --- |
| Spring Boot 4.1.1 is current GA | **Confirmed.** Released 2026-08-20. Central lists 4.1.1 as the newest 4.1.x, and the next releases are 4.2.0-M1/M2 (milestones). | spring.io blog, Maven Central metadata |
| Scaffold 4.0.8 → 4.1.1 bump | **Justified.** 4.0.8 is the last 4.0.x on Central. 4.0 OSS ends about 2026-12 (third-party). | Maven Central; endoflife.ai |
| Spring Modulith 2.1.x for Boot 4.1, latest patch 2.1.1 | **Confirmed.** 2.1 GA was 2026-06-11 and 2.1.1 was 2026-08-26. The 2.1 milestones tracked Boot 4.1 milestones, and 2.2 M1 "moves to Spring Boot 4.2 M1". Central's newest 2.1.x is 2.1.1. | spring.io blogs, Maven Central |
| `spring-boot-starter-security-oauth2-resource-server` | **Confirmed.** Published 4.0.0 to 4.1.1 (and 4.2.0-M2). The old `spring-boot-starter-oauth2-resource-server` is still published but deprecated in favour of it. | Maven Central; Moderne Boot 4 rename recipe |
| HS256 via `NimbusJwtDecoder.withSecretKey` | **Confirmed.** HS256 is the default. Encoding uses `NimbusJwtEncoder.withSecretKey` (since 7.0), also HS256 by default. See F5 for the Boot property gap. | Spring Security 7.1.1 reference, javadoc and source |
| A cookie JWT needs a custom `BearerTokenResolver` | **Confirmed.** The 7.1.1 package has only `DefaultBearerTokenResolver` and `HeaderBearerTokenResolver`, with no cookie resolver. **But see F1:** that same resolver exempts the request from CSRF. | javadoc package summary; bearer-tokens reference |
| Managed versions under Boot 4.1.1 | Spring Security 7.1.1, Spring Framework 7.0.9, Spring Data Commons 4.1.1, Hibernate 7.4.5.Final, Flyway 12.4.0 (plus `flyway-database-postgresql` 12.4.0, already in the scaffold), Lombok 1.18.46, Testcontainers 2.0.5 | Boot 4.1.1 dependency-versions appendix |
| `spring.jpa.open-in-view=false` | **Needed.** The Boot 4.1.1 default is still `true`, so setting it explicitly as the spine does is correct. | Boot 4.1.1 properties appendix |
| PostgreSQL 16 | **Supported** until **2028-11-09**, current minor 16.15. Latest major is 18.6, and 19 is at RC1 (2026-10-15). The `postgres:16` tag is fine. | postgresql.org versioning |
| Caddy 2.11 | **Confirmed on official GitHub** (the memlog had it unconfirmed). v2.11.7, 2026-10-03, is marked Latest, with no 2.12 pre-releases. The Docker Hub `caddy:2.11` tag exists and tracks 2.11.7, which picks up the 2.11.4+ security fixes. | GitHub releases; Docker Hub tags API |
| `@TransactionalEventListener` AFTER_COMMIT | **Confirmed**, with the nuances in F8. | Spring Framework 7.0.9 reference, `TransactionSynchronization` javadoc, framework source |
| SameSite=Strict across `app.`/`api.` and `localhost:4200`/`:8080` | **Confirmed same-site**: the registrable domain matches and the port is ignored. | MDN Glossary: Site |
| CSRF `GET /api/auth/csrf` body pattern | **Documented by Spring Security 7.1.1** (`@GetMapping("/csrf") CsrfToken csrf(CsrfToken t)`, `permitAll`, re-fetch after login and logout). See F1 and F3 for what it must be combined with. | Spring Security CSRF reference |

## Sources

- Spring Boot 4.1.1 announcement: https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/
- Spring Boot 4.1.0 announcement: https://spring.io/blog/2026/06/10/spring-boot-4/
- Spring Boot versions on Central: https://repo1.maven.org/maven2/org/springframework/boot/spring-boot/maven-metadata.xml
- Resource-server starter on Central: https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-security-oauth2-resource-server/maven-metadata.xml
- Old starter, deprecated: https://central.sonatype.com/artifact/org.springframework.boot/spring-boot-starter-oauth2-resource-server
- Boot 4 starter rename recipe: https://docs.moderne.io/user-documentation/recipes/recipe-catalog/java/spring/boot4/renamedeprecatedstartersmanagedversions
- Boot 4.1.1 managed dependency versions: https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html
- Boot 4.1.1 application properties (`serialization-mode` default `direct`, `open-in-view` default `true`, `resourceserver.jwt.*`): https://docs.spring.io/spring-boot/appendix/application-properties/index.html
- Boot support window, third-party: https://endoflife.ai/spring-boot/4.0 and https://www.herodevs.com/blog-posts/spring-boot-versions-eol-dates-and-latest-releases-april-2026
- Spring Modulith 2.1 GA: https://spring.io/blog/2026/06/11/spring-modulith-2-1-ga-2-0-7-and-1-4-12-released/
- Spring Modulith 2.1.1 and 2.2 M1 (2.2 → Boot 4.2): https://spring.io/blog/2026/08/26/spring-modulith-2-2-m1-2-1-1-2-0-8-and-1-4-13-released/
- Spring Modulith 2.1 M1 (tracks Boot 4.1): https://spring.io/blog/2026/01/23/spring-modulith-2-1-m-1-2-0-2-and-1-4-7-released/
- Modulith BOM on Central: https://repo1.maven.org/maven2/org/springframework/modulith/spring-modulith-bom/maven-metadata.xml
- Modulith test starter on Central: https://repo1.maven.org/maven2/org/springframework/modulith/spring-modulith-starter-test/maven-metadata.xml
- Modulith fundamentals (subpackages internal, `@NamedInterface`, open modules): https://docs.spring.io/spring-modulith/reference/fundamentals.html
- Modulith verification: https://docs.spring.io/spring-modulith/reference/verification.html
- Modulith events (registry tracks all `@TransactionalEventListener`): https://docs.spring.io/spring-modulith/reference/events.html
- Spring Security JWT resource server (7.1.1): https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- Bearer token resolution (7.1.1): https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/bearer-tokens.html
- Resource-server web package (no cookie resolver): https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/oauth2/server/resource/web/package-summary.html
- `NimbusJwtDecoder.SecretKeyJwtDecoderBuilder`: https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/oauth2/jwt/NimbusJwtDecoder.SecretKeyJwtDecoderBuilder.html
- `NimbusJwtDecoder` source (HS256 default): https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/oauth2/oauth2-jose/src/main/java/org/springframework/security/oauth2/jwt/NimbusJwtDecoder.java
- `NimbusJwtEncoder` and `SecretKeyJwtEncoderBuilder` (since 7.0, HS256 default): https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/oauth2/jwt/NimbusJwtEncoder.html and https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/oauth2/jwt/NimbusJwtEncoder.SecretKeyJwtEncoderBuilder.html
- **F1 evidence**, `OAuth2ResourceServerConfigurer` source (`registerDefaultCsrfOverride`, `BearerTokenRequestMatcher`): https://raw.githubusercontent.com/spring-projects/spring-security/7.1.x/config/src/main/java/org/springframework/security/config/annotation/web/configurers/oauth2/server/resource/OAuth2ResourceServerConfigurer.java
- Spring Security CSRF reference (SPA, `/csrf` endpoint, `CookieCsrfTokenRepository`, Xor handler): https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html
- RFC 7518 §3.2 (HMAC key size): https://www.rfc-editor.org/rfc/rfc7518#section-3.2
- Spring Data Commons 4.1.1 web support (`PagedModel`, `VIA_DTO`, stability caveat): https://docs.spring.io/spring-data/commons/reference/repositories/core-extensions.html
- Transaction-bound events (Framework 7.0.9): https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html
- `TransactionSynchronization` javadoc: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html
- `TransactionalApplicationListenerSynchronization` source: https://raw.githubusercontent.com/spring-projects/spring-framework/main/spring-tx/src/main/java/org/springframework/transaction/event/TransactionalApplicationListenerSynchronization.java
- `RestrictedTransactionalEventListenerFactory` source: https://raw.githubusercontent.com/spring-projects/spring-framework/main/spring-tx/src/main/java/org/springframework/transaction/annotation/RestrictedTransactionalEventListenerFactory.java
- PostgreSQL versioning policy: https://www.postgresql.org/support/versioning/
- Mailpit releases: https://github.com/axllent/mailpit/releases
- Mailpit Docker tags: https://hub.docker.com/v2/repositories/axllent/mailpit/tags?page_size=30
- Caddy releases: https://github.com/caddyserver/caddy/releases
- Caddy Docker tags: https://hub.docker.com/v2/repositories/library/caddy/tags?page_size=50&name=2.11
- Testcontainers PostgreSQL module on Central: https://repo1.maven.org/maven2/org/testcontainers/testcontainers-postgresql/maven-metadata.xml
- MDN, "Site" (port ignored): https://developer.mozilla.org/en-US/docs/Glossary/Site
- WebKit `Secure` cookies on localhost: https://bugs.webkit.org/show_bug.cgi?id=232088 and https://bugs.webkit.org/show_bug.cgi?id=231035
- Angular XSRF interceptor scope: https://angular.dev/best-practices/security
