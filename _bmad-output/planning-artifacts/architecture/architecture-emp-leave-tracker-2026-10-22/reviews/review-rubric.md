# Rubric review: ARCHITECTURE-SPINE.md (emp-leave-tracker core API)

Reviewed: `../ARCHITECTURE-SPINE.md` (22 ADs, status draft), `../.memlog.md`, PRD `prds/prd-emp-leave-tracker-2026-10-15/prd.md`.
Spine was not edited.

## Verdict

Not yet a good spine: the paradigm and layering are sound, but three load-bearing rules (AD-13 balance lock, AD-7 session rotation/inactivity, AD-15 message contract vs FR54) cannot be implemented as written, several FRs and ops/infra dimensions have no decision or owner, and the stack has at least one version claim that AWS docs do not support.

## Verification of named tech (checklist item 3)

- Spring Boot 4.1.0: confirmed as released 10 June 2026 by the official spring.io blog (search result `https://spring.io/blog/2026/06/10/spring-boot-4/`). The ASSUMPTION tag can be dropped, but the spine should cite this source. The memlog note that it was "not confirmed on spring.io" is now resolved.
- Redis 8.x on ElastiCache: NOT confirmed, and the AWS results found point the other way. The ElastiCache docs surfaced list Redis OSS up to 7.1 as the newest Redis OSS line and Valkey 8.x as the successor. The spine's stack row ("Redis (ElastiCache) 8.x") is probably wrong. Confirm on the AWS engine-versions page before the first infra build; the likely fix is Valkey 8.x (ElastiCache engine) or Redis OSS 7.1.
- PostgreSQL 18.x, Flyway 12.4.0 (via Boot BOM), Java 21, Testcontainers (via BOM): not independently verified in this review. Flyway and Testcontainers versions should come from the Boot BOM, not be pinned in the spine.

## Findings by severity

### Critical / High

**H1. AD-13 (balance lock and availability) is not implementable as written.**
- The rule says submission and approval `SELECT ... FOR UPDATE` the "employee's balance account row". No such table or row exists in the spine: the data model has only `balance_ledger` (append-only), and the ERD confirms no account table.
- "Available balance" is undefined. It is not stated whether a PENDING request reserves days, or whether the debit happens at submit or at approve. Cancellation and modification (PENDING only, AD-14) need the same answer.
- Modification changes the day count (AD-11), but the rule does not say the balance check is re-run under the lock on modify.
- Fix: name the lock target (for example a `balance_account` row per employee and leave type, owned by balance), define available = sum(ledger) minus open reservations, and state the debit/reservation event and the modify path. Add the AD-13 integration test case for modify.

**H2. AD-7 (refresh session) does not implement its own inactivity rule and its reuse detection has no mechanism.**
- Idle TTL "reset on each rotation" means the SPA can keep the session alive indefinitely by refreshing on a timer while the user is idle in a background tab. Inactivity (PRD FR58, 30 min) is therefore not enforced. Inactivity needs a last-API-activity timestamp, or refresh must be user-triggered.
- Reuse detection: Redis stores only the SHA-256 of the current token. A presented token that does not match is indistinguishable from a legitimate concurrent refresh (two tabs, retry after lost response) or from theft. The rule needs a defined grace window and/or retained prior hash, and it must say how the session id reaches the server (cookie or header). Without that, the "revoke whole session on reuse" rule will either log users out spuriously or never fire.
- Fix: define the activity source for idle expiry, the reuse-detection data and grace window, and the session id carrier.

**H3. AD-15 message contract conflicts with FR54 and cannot route manager emails.**
- FR54 requires "relevant request details in notification emails" (dates, type, etc.). AD-15 says the message carries ids only, no personal data beyond employee id. Either the worker calls back into the core API (a cross-deployable read that no AD governs), or the contract is wrong. The spine must choose and state which.
- FR52 (manager emailed on submission) needs a recipient. The contract has only `eventId`, `eventType`, `schemaVersion`, `occurredAt`, and employee id. There is no recipient manager id, and no rule for how the worker resolves an email address (identity owns it; the worker must not read identity tables per AD-2).
- Event types are named only by convention; the list of events (`LeaveRequestSubmitted`, `...Approved`, `...Rejected`, `...Cancelled`) and their payloads should be enumerated since the worker team builds against them.
- Fix: add recipient ids to the contract, specify the worker's recipient and email lookup mechanism (for example a facade call or a recipient-resolved payload), and reconcile FR54 explicitly.

**H4. AD-4 and AD-1/AD-2 have no entry-point definition and no enforcement; several ownership and naming rules are unmet.**
- AD-4 says a state change happens "in one application-service method inside one DB transaction" that also writes audit and outbox rows. Submission spans leave, balance, audit, outbox, and idempotency (AD-16). The spine does not say which module's service is the entry point, or how cross-module facade calls share a transaction. Without this, each team will pick a different transaction boundary, which is the divergence this AD exists to prevent.
- Enforcement: AD-22 requires ArchUnit only for AD-3. AD-1 (facade-only access), AD-2 (single writer), and AD-4 (no direct state mutation outside the service) have no enforcing test and no integration test. Their rules are conventions.
- Ownership gaps under AD-2 ("every table has exactly one owner"):
  - Manager reporting line (the `EMPLOYEE manager_of` relation that AD-9 scope depends on): no owner, no table name.
  - Employee master data (name, email) used by notifications and reports: owner not named (identity "users" is implied).
  - Notification history (FR53): no owner, no read path, no retention.
  - Comments (FR3, FR15): no table, no owner.
  - Idempotency table (AD-16): owner not named.
  - Reference data (leave types, holidays, AD-17): owner is an open question, but AD-2 is currently violated.
- Naming rule violations of AD-2's own convention: `users` (identity, unprefixed), `access_log` (audit, unprefixed), `EMPLOYEE` in the ERD.
- Fix: name the entry service for each mutation flow, state the transaction-sharing mechanism, add ArchUnit rules for facade-only access and no cross-module repository access, and assign each missing table an owner and a prefixed name.

**H5. Operations and infrastructure dimensions for backup, recovery, IaC and environments are undecided and not deferred.**
- PRD requires daily backups, 30-day backup retention, RPO 24h, RTO 24h, monthly restore testing, IaC, zero-downtime deploys, auto-scaling, and encryption at rest for audit logs (NFR: "Audit logs must be encrypted"). The spine decides none of backup retention, PITR, restore testing, or audit-log encryption.
- The capability map cites AD-21 for FR46, FR55, FR60, FR61, but AD-21 says nothing about backups. The Deferred entry "DR runbook beyond RDS Multi-AZ and daily backups" assumes daily backups were decided; they were not.
- Environments (dev, staging, prod) and IaC tooling are not decided and not in Deferred. Zero-downtime rollout strategy (rolling vs blue/green) is implied by AD-18 but not stated.
- Fix: add a decision (or explicit deferral with owner and date) for backups/retention/RPO/RTO, audit-log encryption, IaC, environments, and rollout strategy. The Multi-AZ RDS alone is not a backup decision.

### Medium

**M1. Stack: Redis 8.x on ElastiCache is probably not offered.** See the verification section; confirm and correct the row and the AD-17 and open-question references. Also tag Spring Boot 4.1.0 as verified with the spring.io source.

**M2. FR coverage gaps (FR1-FR31, FR42-45, FR50-54 minimum set).**
- FR18-FR20 (account creation, password reset): mapped to identity, but no reset mechanism is decided (temporary password, forced change, token expiry).
- FR26 and PRD NFR "all permission checks logged": AD-5 says "sensitive data" reads write access_log, but "sensitive" is undefined and no mechanism exists for permission-decision logging.
- FR45 History tab: no decision on who may read `audit_event` for a request, or which API serves it (AD-9 scope is not stated for audit reads).
- FR53 and FR15 covered in H4 ownership gaps.
- FR9 (submitter and timestamp): covered by audit but not stated as a request-level field.
- FR3 notes: no field or table named.

**M3. AD-5 enforcement depends on DB privileges that are not specified.** Triggers can be disabled or dropped by the table owner or a role with DDL rights. The rule should require the app role to lack DDL and trigger-disable privileges, and the migration role to be separate.

**M4. AD-9 status codes are incomplete.** 404 is specified for out-of-scope records. Role violations (for example an employee calling an HR endpoint) have no status rule, so units will return 403 or 404 inconsistently.

**M5. AD-10 list and pagination shape is undefined.** "Success bodies are plain JSON objects, no envelope" conflicts with needing a list shape and pagination metadata. Decide the list shape and page parameters.

**M6. AD-12 SameSite=Strict breaks email deep links.** Notification emails (FR50-52) link into the app. Strict cookies are not sent on the cross-site top-level navigation from a mail client, so the user is logged out on arrival. Consider Lax for the access cookie, or state the accepted UX cost. Also: the same-registrable-domain assumption depends on where the SPA is hosted, which is not decided.

**M7. AD-3 enforcement assumes JPA entities are separate from domain objects.** AD-14 uses `@Version` on `leave_request`. The rule holds only if persistence entities are distinct from domain types. State that explicitly.

**M8. AD-11 counts weekends and public holidays.** This is a business decision the PRD does not make and that should be confirmed. It also makes the holiday calendar irrelevant to the day count, which contradicts AD-17 caching it as reference data for leave arithmetic. Clarify the purpose of the holiday calendar.

**M9. AD-16 idempotency gaps.** No handling for two concurrent requests with the same key (in-flight state), no cleanup job for 24h retention, owner unassigned (see H4).

**M10. AD-15 publisher failure handling.** No poison-message or max-retry rule, and no alert on stuck pending rows.

**M11. AD-6 accepted risk and FR58/NFR session deviation.** The deviation is documented as a user decision, which is good. The PRD has not been amended yet and the risk statement should cite the PRD lines it overrides (FR58 and the logout/inactivity NFR). Keep as an open action with an owner.

**M12. Performance NFR is bound but not decided.** NFR-Performance (page load under 3s, API under 500ms, reports under 5s, 20 concurrent users) is in `binds` but has no AD: no indexing rule, no pagination rule, no connection-pool or query budget. AD-17 forbids caching leave data, so query performance rests entirely on undecided indexing.

**M13. Observability and alerting incomplete.** AD-20 decides JSON logs, Micrometer and OpenTelemetry. Not decided: log aggregation destination and retention, APM and error-tracking tool, uptime monitoring, alert routing for critical failures (PRD NFR: real-time alerts for DB down and backup failure). Observability is a structural dimension and should be decided or deferred.

**M14. Accessibility and scalability NFRs not bound.** Accessibility is out of scope with the SPA exclusion, but the scope line should say so explicitly. The 100-to-500-employee scalability NFR is implicitly served by AD-1 and should be cited.

**M15. Testing scope.** AD-22 omits the PRD QA items: performance at 20 concurrent users, OWASP security testing, and accessibility testing. Add them or defer with an owner.

**M16. Deferred items that could let units diverge.**
- "Reporting and exports ... likely a read model in the audit or leave module": a read-model location left open invites cross-module reads. Name the rule (read models are owned by the reporting consumer and read via facades only).
- "Encryption at rest" deferred while the PRD requires audit-log encryption: move audit-log encryption out of Deferred and decide it.
- "CI/CD tooling choice" deferred while AD-3 and AD-22 require a CI gate: note that a CI gate is assumed.

**M17. PRD phase labels not reconciled.** AD-5 requires before/after JSON in MVP, while PRD FR49 marks it Phase 2. The spine is stricter, which is fine, but it should say so. Similarly FR7 (modify pending requests) is in MVP per FR list but excluded in the MVP scope list; the spine follows FR7. Record the resolution.

### Low

**L1.** AD-6 "role" claim: role changes take effect only at token expiry (up to 15 min). Say so in the rule.
**L2.** AD-8: BCrypt has a 72-byte input limit. State the handling (reject or pre-hash) for long passwords.
**L3.** AD-8: password complexity (PRD: min 8 chars with letters and numbers) is not in the spine. Add to AD-8.
**L4.** AD-12: double-submit CSRF cookie must be readable by script (not HttpOnly). Say so to prevent an implementer setting HttpOnly on it.
**L5.** AD-15 and ERD naming: the rule says "notification row", the ERD says `OUTBOX_MESSAGE`. Use one name.
**L6.** Open Questions omit several decisions that are de facto open: manager reporting-line owner, cross-module transaction entry points, SPA hosting, JWT key distribution (JWKS vs static public key).
**L7.** Memlog and spine are consistent with each other on the decisions; the memlog's "Fast path" note records user-supplied decisions, which is good provenance.

## Checklist scorecard

| Criterion | Result |
| --- | --- |
| Real divergence points fixed, none missed | Partial: H1, H2, H3, H4 leave divergence points open; the paradigm, boundaries and auth shape are good. |
| Every AD Rule enforceable and preventing its divergence | Fail for AD-4, AD-7, AD-13, AD-15; partial for AD-1, AD-2, AD-3 (no enforcing test beyond AD-3), AD-5, AD-9, AD-10, AD-16. |
| Named tech verified-current | Partial: Spring Boot 4.1.0 verified; Redis 8.x on ElastiCache probably wrong; others unverified. |
| PRD FR and NFR coverage | Partial: FR15, FR18-20, FR26, FR45, FR46, FR52-54, FR60-61 under-covered; performance, backup, and audit-encryption NFRs undecided. |
| Structural dimensions decided, deferred, or open | Fail for operations (backup, runbooks, zero-downtime), infra (IaC), environments, observability (alerting). Paradigm, boundaries, data ownership, state mutation, auth, API contract decided. |

## Recommendation

Revise before build: fix H1 to H5 and M1, then re-review. Medium items can be closed in the same pass or moved to Open Questions with owners.
