# Acceptance scenarios

The POC is accepted when all of these pass on seeded data: 1 HR admin, 1 top-level manager, 2 team managers, 8 employees.

| # | Scenario | Pass when | FRs | CAPs |
| --- | --- | --- | --- | --- |
| 1 | Employee submits vacation Thu–Tue | Counted as 4 working days. Status Pending; 4 days reserved. The manager's notification is a DB row plus a log line. History shows the submission. | FR1–FR4, FR6, FR9, FR42–FR45, FR52, FR54 | CAP-1, CAP-2, CAP-7, CAP-12, CAP-13 |
| 2 | Employee edits, then cancels, a pending request | The reservation updates on edit and is released on cancel. Both steps appear in History; the cancelled request stays visible. | FR7, FR8, FR45 | CAP-3, CAP-7 |
| 3 | Manager approves with a comment | Status Approved; reserved days become deducted. The employee's notification is a DB row plus a log line. The decision appears in History. | FR10–FR12, FR16, FR50 | CAP-4, CAP-5, CAP-13 |
| 4 | Manager rejects | Reject is blocked without a comment. Reserved days are released. The employee's notification, including the reason, is a DB row plus a log line. | FR13, FR51 | CAP-5, CAP-13 |
| 5 | Manager and employee discuss, dates change, then approval | Comments appear after a page refresh, with no alert. The date change and the decision appear in History in order. | FR15, FR45 | CAP-7, CAP-14 |
| 6 | Team manager submits leave | The request goes to the top-level manager, who can approve it. | FR17, FR19 | CAP-6 |
| 7 | Top-level manager submits leave | The request goes to the HR user, who can approve it. | FR17, FR19 | CAP-6 |
| 8 | Manager tries to act on another team's request | The API returns 403 and the attempt is logged. | FR17, FR23, FR25, FR26 | CAP-6, CAP-10 |
| 9 | Employee opens another employee's request by URL | The API returns 403 and the attempt is logged. | FR22, FR25, FR26 | CAP-10, CAP-14 |
| 10 | Request exceeds available days (pending reservations included) | Submission is blocked with a clear message. | FR31 | CAP-1 |
| 11 | HR creates a user, assigns a manager, resets the password | The user can log in and must change the temporary password. | FR18–FR20 | CAP-9 |
| 12 | HR sets and adjusts a balance | The ledger shows amount, reason, actor and time. An adjustment below zero is refused. | FR27–FR31 | CAP-11 |
| 13 | Someone tries to change an audit row in the database | The update or delete fails. | FR44 | CAP-12 |
| 14 | 5 wrong passwords | The account is locked for 15 minutes. | FR57 | CAP-14 |
| 15 | Idle 30 minutes | The session expires; the next action asks for login. | FR58 | CAP-14 |

Scenarios 8, 9 and 13 are verified against the database: the `access_log` row, and a trigger rejecting the change.
