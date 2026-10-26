# Postman Success-Path API Walkthrough

This guide exercises every API route implemented by the Employee Leave Tracking System through one realistic flow:

```text
HR admin → Sunil (manager) → PD (manager) → Anil, Jay, PG, Rajesh (employees)
```

The workflow creates test accounts, changes their temporary passwords, provisions balances, and exercises request submission, edit, comment, approval, rejection, cancellation, team leave, notifications, balance ledger, and audit. It changes local application data; use a development/test instance.

## 1. Configure Postman

Create a Postman environment with these initial values:

| Variable | Initial value |
|---|---|
| `baseUrl` | `https://localhost/api` |
| `runId` | A unique suffix for this run, e.g. `20261026a` |
| `vacationTypeId` | `1` (Vacation in a fresh database seeded by Flyway) |
| `dateA`, `dateB`, `dateC`, `dateD` | Four upcoming weekdays in `YYYY-MM-DD` format |
| `temporaryPassword` | `Temp2026` |
| `newPassword` | `Passw0rd1` |

Use the Postman cookie jar and keep SSL certificate verification disabled for the local self-signed certificate. The API issues an `HttpOnly; Secure` `access_token` cookie at login. Do not extract a token from the JSON body or add an Authorization header. Use `{{baseUrl}}` in every request URL. On Docker Compose, `https://localhost/api` is the documented proxy URL. Keep the cookie jar enabled so Postman sends the current user's cookie automatically.

Choose four distinct future weekdays for the date variables. The API counts Monday through Friday inclusively and ignores public holidays. Use one-day ranges for the simple lifecycle checks; use `dateA` and `dateB` to make the edit visibly change the dates.

When a step says to switch users, send the login request for that account first; the cookie jar then contains that user's active session. Copy each returned user or request `id` into the matching environment variable before continuing.

## 2. Authenticate HR and inspect existing accounts

1. **Login — `POST {{baseUrl}}/auth/login`**

   ```json
   { "username": "hr.admin", "password": "Passw0rd1" }
   ```

   Expect `200`, a response with role `HR_ADMIN`, and an `access_token` cookie. Save the returned user `id` as `hrId`.

2. **List users — `GET {{baseUrl}}/users`**

   Expect `200`. Confirm the seeded `hr.admin` exists. This endpoint is HR-only.

3. **List manager options — `GET {{baseUrl}}/users/managers`**

   Expect `200` and confirm HR is available as a manager for Sunil.

## 3. Create the test hierarchy

Use `POST {{baseUrl}}/users` for each account, save each returned `id`, and use it as the next account's `managerId`. `temporaryPassword` is required; each new account must change it after login. Add `{{runId}}` to usernames to avoid collisions on repeated runs.

| Username | Role | `managerId` |
|---|---|---|
| `sunil.{{runId}}` | `MANAGER` | `{{hrId}}` |
| `pd.{{runId}}` | `MANAGER` | `{{sunilId}}` |
| `anil.{{runId}}` | `EMPLOYEE` | `{{pdId}}` |
| `jay.{{runId}}` | `EMPLOYEE` | `{{pdId}}` |
| `pg.{{runId}}` | `EMPLOYEE` | `{{pdId}}` |
| `rajesh.{{runId}}` | `EMPLOYEE` | `{{pdId}}` |

Example request body (change `username`, `role`, and `managerId` for each account):

```json
{
  "username": "sunil.{{runId}}",
  "role": "MANAGER",
  "managerId": {{hrId}},
  "temporaryPassword": "{{temporaryPassword}}"
}
```

Expect `201`, `mustChangePassword: true`, and the correct `managerId`. Save returned IDs as `sunilId`, `pdId`, `anilId`, `jayId`, `pgId`, and `rajeshId`. Then repeat **`GET /users`** and **`GET /users/managers`** as HR; confirm the accounts and manager options reflect the new hierarchy.

### Set each new user's permanent password

For Sunil, PD, Anil, Jay, PG, and Rajesh, perform these steps one account at a time so the cookie jar always belongs to the user being tested. Logging in to a different account at the same host replaces the active cookie in Postman's jar.

1. **`POST {{baseUrl}}/auth/login`**

   ```json
   { "username": "<username from the table>", "password": "{{temporaryPassword}}" }
   ```

   Expect `200` and `mustChangePassword: true`.
2. **`POST {{baseUrl}}/auth/change-password`**

   ```json
   { "currentPassword": "{{temporaryPassword}}", "newPassword": "{{newPassword}}" }
   ```

   Expect `204`. `Passw0rd1` satisfies the minimum eight-character letters-and-numbers rule.
3. Login again using `{{newPassword}}`; expect `200` and `mustChangePassword: false`.

## 4. Set up and check leave balances

Login back as HR, then use **`PUT {{baseUrl}}/balances/{{userId}}/{{vacationTypeId}}`** once for each of `sunilId`, `pdId`, `anilId`, `jayId`, `pgId`, and `rajeshId`:

```json
{ "totalDays": 20, "reason": "Postman success-path test allocation" }
```

Expect `200` with `totalDays: 20` and `reservedDays: 0`. Then exercise the adjustment route for Anil:

**`POST {{baseUrl}}/balances/{{anilId}}/{{vacationTypeId}}/adjust`**

```json
{ "changeDays": 1, "reason": "Postman ledger adjustment test" }
```

Expect `200` and Anil's Vacation total to become 21.

Now call:

- **My balances:** `GET {{baseUrl}}/balances/me` as Anil. Expect the employee's balance rows and a Vacation balance with 21 total days.
- **All balances:** `GET {{baseUrl}}/balances` as HR. Expect the organization-wide balance list to include the new accounts.
- **Balance ledger:** `GET {{baseUrl}}/balances/{{anilId}}/{{vacationTypeId}}/ledger` as HR. Expect Anil's `SET` and `ADJUST` entries in oldest-first order.

There is no leave-type catalogue route. On a fresh seeded database, `vacationTypeId=1` is Vacation; the balance response contains IDs and day counts, not leave-type names.

## 5. Submit requests and exercise each lifecycle

Create four employee requests as the named employee. Save each response `id` in the indicated environment variable. The API sets `approverId` to the employee's direct manager and initially returns `status: "PENDING"`.

**`POST {{baseUrl}}/requests`** (body for Anil; repeat for Jay, PG, and Rajesh using their respective IDs only for tracking—the caller's cookie identifies the employee):

```json
{
  "leaveTypeId": {{vacationTypeId}},
  "startDate": "{{dateA}}",
  "endDate": "{{dateA}}",
  "notes": "Postman success-path test"
}
```

Save IDs as `approvedRequestId` (Anil), `rejectedRequestId` (Jay), `cancelledRequestId` (PG), and `pendingRequestId` (Rajesh). For each, expect `201`, one working day, and `approverId: {{pdId}}`.

While signed in as the corresponding user, exercise:

- **List requests:** `GET {{baseUrl}}/requests` and `GET {{baseUrl}}/requests?status=PENDING`. Expect the caller's permitted requests; the filter returns pending requests.
- **Request detail:** `GET {{baseUrl}}/requests/{{approvedRequestId}}`. Expect Anil's request and PD as approver.
- **Edit pending request:** `PUT {{baseUrl}}/requests/{{approvedRequestId}}`

  ```json
  {
    "leaveTypeId": {{vacationTypeId}},
    "startDate": "{{dateB}}",
    "endDate": "{{dateB}}",
    "notes": "Updated test date while pending"
  }
  ```

  Expect `200` and the updated date. The leave type must remain unchanged.
- **Add a discussion comment:** `POST {{baseUrl}}/requests/{{approvedRequestId}}/comments`

  ```json
  { "body": "Please review this request." }
  ```

  Expect `201`.
- **Read comments:** `GET {{baseUrl}}/requests/{{approvedRequestId}}/comments`. Expect the comment in the response.
- **Read history:** `GET {{baseUrl}}/requests/{{approvedRequestId}}/history`. Expect submission, edit, and comment events.
- **Cancel PG's pending request:** as PG, call `POST {{baseUrl}}/requests/{{cancelledRequestId}}/cancel` with no body. Expect `200`, `status: "CANCELLED"`, and released reserved days.

## 6. Approve, reject, and check manager/team views

1. Login as PD. Call **`GET {{baseUrl}}/approvals/pending`**. Expect Anil's, Jay's, and Rajesh's assigned pending requests; PG's was cancelled in the previous section. PD is the direct approver of these employees.
2. As PD, approve Anil's edited request:

   **`POST {{baseUrl}}/approvals/{{approvedRequestId}}/approve`**

   ```json
   { "comment": "Approved for the requested dates." }
   ```

   Expect `200`, `status: "APPROVED"`, and a decision timestamp. Approval converts the one reserved day into a deduction.
3. As PD, reject Jay's request:

   **`POST {{baseUrl}}/approvals/{{rejectedRequestId}}/reject`**

   ```json
   { "comment": "Please coordinate coverage before resubmitting." }
   ```

   Expect `200`, `status: "REJECTED"`, and the decision comment. Rejection releases the reserved day.
4. As PD, submit a leave request for PD using the same `POST /requests` body with `dateC`. Save its ID as `pdRequestId`; expect `approverId: {{sunilId}}`.
5. As Sunil, call **`GET {{baseUrl}}/approvals/pending`**, then approve PD's request:

   **`POST {{baseUrl}}/approvals/{{pdRequestId}}/approve`**

   ```json
   { "comment": "Approved." }
   ```

   Expect `200` and `APPROVED`. Decisions are made by the direct manager assigned to the request; approval does not automatically proceed to another manager.
6. As PD, call **`GET {{baseUrl}}/team/leave?from={{dateA}}&to={{dateD}}`**. Expect approved leave for direct reports in that range, including Anil.
7. As Sunil, call the same team endpoint. Expect PD's approved leave.

## 7. Verify notifications, balances, and audit

- **Notifications:** login as PD, Sunil, Anil, and Jay in turn and call `GET {{baseUrl}}/notifications` for each. PD receives submissions from direct reports; Sunil receives PD's submission and approval; Anil and Jay receive their decision notifications. Comments and cancellations do not generate notifications in this implementation.
- **Balances after decisions:** login as Anil and call `GET {{baseUrl}}/balances/me`; the approved one-day Vacation request has been deducted. Login as PG and verify the cancelled request no longer reserves a day.
- **Ledger:** as HR, call `GET {{baseUrl}}/balances/{{anilId}}/{{vacationTypeId}}/ledger`; expect `SET`, `ADJUST`, `RESERVE`, and `DEDUCT` entries. Check Jay's and PG's ledgers for the reservation and release entries.
- **Audit report:** as HR, call `GET {{baseUrl}}/audit`. Expect one entry per request, including pending Rajesh, approved Anil and PD, rejected Jay, and cancelled PG, with each request's history and comments.
- **History after decisions:** call `GET {{baseUrl}}/requests/{{approvedRequestId}}/history` and the same route for Jay, PG, and PD as HR or the request owner. Confirm the relevant decision or cancellation event is present.
- **Updated organization balances:** as HR, call `GET {{baseUrl}}/balances` and confirm the balance totals, reservations, and deductions match the preceding actions.

## 8. Exercise password reset and logout

As HR, call **`POST {{baseUrl}}/users/{{rajeshId}}/reset-password`**. Expect `200` with a generated `temporaryPassword`; save it only in the local Postman environment as `resetPassword`. This endpoint generates the value—it does not accept a password to set. As Rajesh, log in with `resetPassword`, then call:

**`POST {{baseUrl}}/auth/change-password`**

```json
{ "currentPassword": "{{resetPassword}}", "newPassword": "{{newPassword}}" }
```

Expect `204`, then login again with `{{newPassword}}` and expect `200`.

Finally, while authenticated, call **`POST {{baseUrl}}/auth/logout`**. Expect `204` and a cleared cookie. The logout endpoint revokes the caller's active sessions.

## Implemented route checklist

This flow calls every implemented API route (26 total):

| Area | Routes exercised |
|---|---|
| Auth | `POST /auth/login`, `POST /auth/change-password`, `POST /auth/logout` |
| Users | `POST /users`, `GET /users`, `GET /users/managers`, `POST /users/{userId}/reset-password` |
| Balances | `GET /balances/me`, `GET /balances`, `PUT /balances/{userId}/{leaveTypeId}`, `POST /balances/{userId}/{leaveTypeId}/adjust`, `GET /balances/{userId}/{leaveTypeId}/ledger` |
| Requests | `GET /requests`, `POST /requests`, `GET /requests/{requestId}`, `PUT /requests/{requestId}`, `GET /requests/{requestId}/history`, `GET /requests/{requestId}/comments`, `POST /requests/{requestId}/comments`, `POST /requests/{requestId}/cancel` |
| Approvals | `GET /approvals/pending`, `POST /approvals/{requestId}/approve`, `POST /approvals/{requestId}/reject` |
| Team, notifications, audit | `GET /team/leave`, `GET /notifications`, `GET /audit` |

`GET /auth/me` appears in the checked-in OpenAPI document but has no corresponding controller route; do not count it as an implemented API. `/v3/api-docs` and Swagger UI are documentation tools, not business API routes. The access log is written internally and has no read API in this POC.

## Useful success assertions

For stronger checks without making this run failure-focused, add Postman Tests to the relevant requests:

- Assert each successful response has the expected HTTP status and content type.
- On login, assert the returned username, role, and `mustChangePassword` value; confirm the cookie jar contains `access_token`.
- On user creation, assert the role, manager ID, and `mustChangePassword: true`.
- On request creation, assert `PENDING`, expected direct `approverId`, and `workingDays`.
- On edit/decision/cancel, assert the changed dates, final status, decision comment, or cancellation status.
- Compare balance totals and reserved days before and after submit, edit, approval, rejection, and cancellation.
- In history, notifications, ledger, team leave, and audit responses, assert the exact request ID and expected event/kind/status—not only that the response is non-empty.

For the core happy-path coverage, the four employee requests plus PD's own request cover every request status and both levels of the reporting chain. Additional invalid-input and permission-denial cases can be run separately; they are not required to complete this success-path walkthrough.
