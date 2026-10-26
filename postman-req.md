# Complete API Testing Plan — Employee Leave Tracking System POC

1\. Login as HR admin using username `hr.admin` and password `Passw0rd1`.

2\. Extract the authentication token from the login response and store it in an environment variable. Reuse it for all authenticated API requests. If the token expires, log in again and update the variable.

3\. Get all users and their IDs using the users API.

4\. Create Sunil as a MANAGER reporting to HR admin.

5\. Set Sunil's password to `Passw0rd1` using the password reset API if required.

6\. Create PD as a MANAGER reporting to Sunil.

7\. Set PD's password to `Passw0rd1` using the password reset API if required.

8\. Create Anil as an EMPLOYEE reporting to PD.

9\. Set Anil's password to `Passw0rd1` using the password reset API if required.

10\. Create Jay as an EMPLOYEE reporting to PD.

11\. Set Jay's password to `Passw0rd1` using the password reset API if required.

12\. Create PG as an EMPLOYEE reporting to PD.

13\. Set PG's password to `Passw0rd1` using the password reset API if required.

14\. Create Rajesh as an EMPLOYEE reporting to PD.

15\. Set Rajesh's password to `Passw0rd1` using the password reset API if required.

16\. Verify all user accounts and their reporting relationships using the users API.

17\. Get the available manager options using the managers API.

18\. Login separately as Sunil, PD, Anil, Jay, PG, and Rajesh to verify each account works. Change each user's password to `Passw0rd1` if the application requires a password change on first login. Store or update the authentication token for each user separately.

19\. Get the leave balances for each employee using the personal balance API.

20\. Get all leave balances using the HR balance API and verify that HR can view the organization-wide balances.

21\. Create a few leave requests for Anil, Jay, PG, and Rajesh. Use valid leave types, dates, and reasons supported by the API. Save every returned request ID.

22\. Create multiple requests with different date ranges and leave types so that the test data covers different leave scenarios.

23\. Get all leave requests and verify that each employee can access their own requests and that managers and HR see only the requests allowed by their roles.

24\. Get individual leave request details using the request ID.

25\. Get the history of each leave request and verify that request creation and subsequent changes are recorded.

26\. Get the comments for each leave request.

27\. Add discussion comments to selected requests using the comments API.

28\. Get pending approvals using the approvals API. Verify that PD sees the requests awaiting PD's approval and that HR sees the requests permitted by the HR role.

29\. Approve some pending requests as PD using the approval API.

30\. Reject some pending requests as PD using the rejection API. Provide the required rejection reason or decision details according to the API schema.

31\. Create additional requests where necessary so that there are enough pending requests to test both approval and rejection.

32\. Modify selected pending leave requests using the update API and verify that the changes are reflected in the request details and history.

33\. Cancel selected pending leave requests using the cancellation API.

34\. Verify that approved requests cannot be approved again, rejected requests cannot be processed as pending requests, and cancelled requests follow the API's permitted state transitions.

35\. Verify that approved requests update the appropriate leave balances and that rejected or cancelled requests follow the application's balance reservation and release rules.

36\. Get the balance ledger for selected employees and leave types. Verify that balance changes and adjustments are recorded.

37\. Adjust a selected employee's leave balance using the balance adjustment API, if HR is authorized to do so.

38\. Set a selected employee's leave balance using the balance update API, if HR is authorized to do so.

39\. Get team leave information as PD and Sunil. Verify that managers can see the approved leave of their permitted teams.

40\. Get notifications for each test user. Verify that notifications are generated for relevant events such as request submission, approval, rejection, cancellation, or comments, where supported by the implementation.

41\. Get the HR audit report using the audit API. Verify that HR can inspect leave requests across all statuses, including pending, approved, rejected, and cancelled requests.

42\. Verify that the audit information identifies the relevant request and provides the available status, actor, timestamp, and change details defined by the API.

43\. Verify request history and audit results after every important action, including creation, modification, comments, approval, rejection, and cancellation.

44\. Test password reset for a selected user using the password reset API. Confirm that the user can log in with the reset password and follow any required password-change process.

45\. Test logout for each authenticated account using the logout API. Verify that the session or authentication cookie is cleared as expected.

46\. Test authentication and authorization by attempting protected requests without a token, with an invalid token, and with a token belonging to a user who lacks the required permissions.

47\. Test invalid requests, missing fields, invalid user IDs, invalid request IDs, invalid dates, overlapping leave requests, and requests exceeding available balances. Verify that the API returns the expected error responses.

48\. Verify the complete reporting hierarchy: HR admin → Sunil → PD → Anil, Jay, PG, and Rajesh.

49\. Verify that employees cannot view or modify other employees' requests, that managers cannot access unauthorized teams, and that only HR can perform HR-specific user administration and audit operations.

50\. Run the OpenAPI endpoints at `/v3/api-docs` and use the live Swagger UI to confirm the actual request schemas, required fields, response formats, and status codes before testing each endpoint.

Important: Use the same password, `Passw0rd1`, for all accounts as requested, but keep a separate authentication token for each user. The exact password-change and password-reset payloads, leave-request fields, approval/rejection payloads, and permitted state transitions must be confirmed from the live API schemas; they are not fully specified in the endpoint list you provided.