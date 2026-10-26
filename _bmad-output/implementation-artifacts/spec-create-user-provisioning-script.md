---
title: 'Create User Provisioning Bash Script'
type: 'feature'
created: '2026-10-26'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '4f69bcf162a2c9d9ab485bb69fce3a6f2e07d72a'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** There is no single runnable Bash script to create the requested demo reporting hierarchy through the HR user API.

**Approach:** Create `curl-req-bash/` at the repository root and add `create-user.sh` there. The script logs in as HR, obtains user IDs from API responses, and creates Sunil as manager of the HR account, PD as Sunil's report, then Anil, Jay, PG, and Rajesh as employees reporting to PD.

**Always:** Use the existing HTTP API, create users sequentially, stop and report on failure, allow base URL and credentials to be configured, and do not hard-code generated database IDs.

**Never:** Modify application code or existing curl/Postman artifacts, create accounts automatically while authoring, or silently skip duplicate usernames.

**Destination decision:** Create the missing `curl-req-bash/` directory at the repository root, matching the requested folder name.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Successful creation | HR credentials and temporary password; all six usernames available | Create Sunil → PD → Anil → Jay → PG → Rajesh and print each response | Stop on any failed API request |
| Duplicate or invalid account | Any requested username already exists or payload is rejected | Do not proceed to later users | Print response and HTTP status; exit nonzero |
| Login failure | HR credentials are invalid | No users are created | Print login response and exit nonzero |

</frozen-after-approval>

## Code Map

- `backend/curl/story-1.6-hr-create-accounts.md` — documents HR login, secure-cookie handling, account-create JSON, expected responses, and manager requirements.
- `backend/src/main/resources/openapi.yaml` — defines `POST /auth/login`, HR-only `GET /users/managers`, and `POST /users`; a new MANAGER may report to HR_ADMIN, and employees/managers require managerId.
- `README.md` — documents seeded HR login (`hr.admin` / `Passw0rd1`) and the local HTTPS proxy URL `https://localhost/api`.
- `postman/collections/emp-leave/create-a-new-user-hr-flow.request.yaml` — existing create-user request example; leave it unchanged.

## Tasks & Acceptance

**Execution:**
- [x] `curl-req-bash/create-user.sh` — implement sequential, checked curl calls that create the six requested accounts using IDs returned by API responses.

**Acceptance Criteria:**
- Given a reachable API and valid HR credentials, when the script runs with unused requested usernames, then it creates Sunil as MANAGER reporting to HR, PD as MANAGER reporting to Sunil, and Anil, Jay, PG, and Rajesh as EMPLOYEE reporting to PD, in that order.
- Given login or account creation fails, when the script receives a non-success response, then it prints the failing response and stops without attempting subsequent account creations.
- Given a shell without a required dependency, when the script starts, then it reports the missing dependency before making API calls.

## Implementation Notes

## Verification

**Commands:**
- `bash -n <script-path>` — expected: no syntax errors.
- `<script-path> --help` — expected: usage, configuration, and dependency guidance without making API calls.
