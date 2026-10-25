# Data model

Core tables and key fields. Table ownership and integrity rules are in the backend spine (AD-2, AD-4–AD-6, AD-8); API shapes are in `openapi.yaml`.

| Table | Key fields |
| --- | --- |
| users | id, username, password_hash, role, manager_id, must_change_password, failed_logins, locked_until, active |
| leave_types | id, name |
| leave_requests | id, employee_id, approver_id, leave_type_id, start_date, end_date, working_days, notes, status, submitted_at, decided_at, decision_comment |
| request_comments | id, request_id, author_id, body, created_at |
| leave_balances | user_id, leave_type_id, total_days, reserved_days (available = total − reserved) |
| balance_ledger | id, user_id, leave_type_id, change_days, kind (set / adjust / reserve / release / deduct), reason, actor_id, request_id, created_at |
| audit_events | id, entity, entity_id, action, actor_id, details_json, created_at (insert-only) |
| access_log | id, actor_id, resource, resource_id, allowed, created_at (insert-only) |
| notifications | id, user_id, request_id, kind, subject, sent_at |
| active_sessions | token_id, user_id, last_seen_at, expires_at (backend spine AD-8) |

- **Leave types:** Vacation, Sick, Parental, Sabbatical.
- **Roles:** EMPLOYEE, MANAGER, HR_ADMIN.
- **Request status:** PENDING → APPROVED | REJECTED | CANCELLED. A request is never deleted.
- **Temporary passwords:** both creation (HR-typed) and reset (system-generated) end as `password_hash` plus `must_change_password = true`. The plaintext is never stored or logged.
