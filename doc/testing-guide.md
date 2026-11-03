# AMS Testing Guide

This guide defines the full test coverage expected for the current Accounting Management System (AMS) implementation. It covers browser testing, role access, API checkpoints, file import checks, e-invoicing checks, integration administration, negative testing, and the automated regression suite.

## 1. Coverage Goal

A test cycle is complete only when all implemented roles, screens, APIs, status transitions, imports, reporting controls, audit controls, integration controls, and automated tests have been checked.

Covered roles:

| Role or group claim | Main workspace | Landing page |
| --- | --- | --- |
| `/admin` | User and group administration | `/admin/users` |
| `/systemAdmin` | Technical and integration setup | `/system-admin` |
| `/financeAdmin` | Finance setup and controls | `/financeAdmin/organizationSetup` |
| `/accountant` | Accounting transactions and operations | `/accountant/manualJournal` |
| `/cfo` | Approvals, budgets, trial balance, reports | `/cfo/approvalsHub` |
| `/auditor` | Audit log and security review | `/auditor/auditLogExplorer` |

Covered functional areas:

| Area | Screens or APIs |
| --- | --- |
| Public entry and login | `/`, OAuth2 login, `/users/logout` |
| Organization and branches | `/api/organizations`, `/api/branches` |
| Admin user management | `/admin/users`, `/admin/create-user`, group and status actions |
| System admin | `/system-admin`, `/api/system-admin/**` |
| Finance admin | Organization setup, chart of accounts, fiscal configuration, module controls |
| Bulk imports | Branch import and chart of accounts import templates, validation, confirmation, error CSV |
| Accountant | Manual journals, customer invoices, purchase invoices, payables, receivables, bank reconciliation, expenses, depreciation imports |
| CFO | Manual journal approval, budgets, trial balance, financial reports |
| Auditor | Audit logs, security events, acknowledgement |
| External invoice API | `/api/v1/invoices/**` |
| E-invoicing and NRS | NRS payload, NRS submission, payload archive |
| Webhooks | `/api/integrations/webhooks/**` |

## 2. Environment

### 2.1 Application URL

The configured local port is:

```text
http://localhost:9078
```

### 2.2 Required Services

| Dependency | Purpose | Default value |
| --- | --- | --- |
| Java | Run and test the application | Java 21 |
| PostgreSQL | Main AMS database | `jdbc:postgresql://localhost:5432/ams` |
| Keycloak | OAuth2 login and group-based access | Realm `ams` |
| Browser | Manual UI testing | Chrome, Edge, or Firefox |

### 2.3 Useful Commands

Run all automated tests:

```powershell
.\mvnw.cmd test
```

Start the application locally:

```powershell
.\mvnw.cmd spring-boot:run
```

The application uses `spring.jpa.hibernate.ddl-auto=update`, so a clean database can be used for a fresh manual test cycle.

## 3. Test Users And Access Control

Create one test user for each Keycloak group:

| User | Required group claim | Expected result after opening `/` |
| --- | --- | --- |
| Admin tester | `/admin` | Redirects to `/admin/users` |
| System admin tester | `/systemAdmin` | Redirects to `/system-admin` |
| Finance admin tester | `/financeAdmin` | Redirects to `/financeAdmin/organizationSetup` |
| Accountant tester | `/accountant` | Redirects to `/accountant/manualJournal` |
| CFO tester | `/cfo` | Redirects to `/cfo/approvalsHub` |
| Auditor tester | `/auditor` | Redirects to `/auditor/auditLogExplorer` |

Access tests:

1. Open `/` without logging in.
2. Confirm the public landing page is visible.
3. Log in as each role and confirm the correct redirect.
4. Try opening another role workspace, such as `/cfo/approvalsHub` as an Accountant.
5. Try the matching restricted API, such as `/api/cfo/manual-journals/org/1/pending` as an Accountant.
6. Confirm unauthorized pages and APIs are blocked.
7. Click logout and confirm the session ends.

Expected result:

- Public assets and the public landing page remain accessible.
- Role pages require the correct group claim.
- Role APIs require the correct group claim.
- `GET /api/organizations/**` and `GET /api/branches/**` are available to authenticated users.
- Non-read organization and branch APIs require Finance Admin access.

## 4. Baseline Test Data

Use unique names for each test cycle.

| Data | Example |
| --- | --- |
| Organization | `QA Organization 2026-10-01` |
| Branch | `QA Main Branch` |
| Currency | `NGN` |
| Bank account | `QA Operating Bank` |
| Customer | `QA Customer Limited` |
| Vendor | `QA Vendor Limited` |
| Asset account | `1000 - Bank` |
| Receivables account | `1100 - Accounts Receivable` |
| Payables account | `2000 - Accounts Payable` |
| VAT account | `2100 - VAT Payable` |
| Equity account | `3000 - Retained Earnings` |
| Revenue account | `4000 - Product Revenue` |
| Expense account | `5000 - Office Expense` |
| Accumulated depreciation account | `1500 - Accumulated Depreciation` |

## 5. Admin Test Cases

Route: `/admin/users`

### 5.1 User Listing

Steps:

1. Log in as `/admin`.
2. Open User Management.
3. Confirm existing users and groups load.
4. Select a user and load the user's groups.

Expected result:

- Users are displayed.
- Group membership is displayed for the selected user.
- Errors are shown without stack traces if Keycloak is unavailable.

### 5.2 Create And Manage User

Steps:

1. Create a user with first name, last name, username, email, and temporary password.
2. Add one or more groups.
3. Remove one group.
4. Disable the user.
5. Try logging in as the disabled user.
6. Enable the user.
7. Send reset password email.
8. Delete the test user after all checks are complete.

Expected result:

- User creation, group assignment, group removal, enable, disable, reset email, and deletion all return controlled success or error messages.
- Access changes apply on the next login.

API/form checkpoints:

- `POST /admin/create-user`
- `POST /admin/add/group`
- `POST /admin/remove/group`
- `POST /admin/enable-user`
- `POST /admin/disable-user`
- `POST /admin/reset-password-email`
- `POST /admin/delete-user`
- `GET /admin/user/{userId}/groups`

## 6. System Admin Test Cases

Route: `/system-admin`

### 6.1 NRS Integration

Steps:

1. Log in as `/systemAdmin`.
2. Open Technical Setup.
3. Review the NRS Integration panel.
4. Toggle enabled status.
5. Set environment, access point provider, base URL, token URL, client ID, scope, and timeout values.
6. Leave a masked secret unchanged during an edit.
7. Save and refresh.
8. Use Test Connection.

Expected result:

- Configuration saves and reloads.
- Masked secret references are preserved when unchanged.
- Test Connection returns a clear status.

API checkpoints:

- `GET /api/system-admin/nrs/config`
- `PUT /api/system-admin/nrs/config`
- `POST /api/system-admin/nrs/test-connection`

### 6.2 Signing Keys

Steps:

1. Enable signing.
2. Enter algorithm, provider, key alias, key reference, last rotation date, and next rotation due date.
3. Save and refresh.
4. Use Test.

Expected result:

- Signing configuration persists.
- Masked key reference is not exposed.
- Test result is clear.

API checkpoints:

- `GET /api/system-admin/signing/config`
- `PUT /api/system-admin/signing/config`
- `POST /api/system-admin/signing/test`

### 6.3 Webhook Platform

Steps:

1. Enable webhooks.
2. Set signing algorithm, max attempts, timeouts, dead letter retention, allowed domain pattern, and retry setting.
3. Save and refresh.

Expected result:

- Platform configuration persists.
- Retry and allowed-domain settings are reflected in webhook behavior.

API checkpoints:

- `GET /api/system-admin/webhook-platform/config`
- `PUT /api/system-admin/webhook-platform/config`

### 6.4 Storage And Retention

Steps:

1. Enable encryption if required.
2. Set artifact storage, archive storage, encryption key reference, retention years, WORM requirement, and immutable archive setting.
3. Save and refresh.

Expected result:

- Retention configuration persists.
- Secret/key references remain masked.

API checkpoints:

- `GET /api/system-admin/storage-retention/config`
- `PUT /api/system-admin/storage-retention/config`

### 6.5 API Clients, Jobs, Health, And Configuration Audit

Steps:

1. Create an API client with name, client ID, secret reference, and scopes.
2. Edit the client.
3. Revoke the client.
4. Review background jobs.
5. Update a job configuration.
6. Review integration health.
7. Review configuration audit entries.

Expected result:

- API clients can be created, updated, and revoked.
- Job settings persist.
- Health records are visible.
- Configuration changes appear in audit history.

API checkpoints:

- `GET /api/system-admin/api-clients`
- `POST /api/system-admin/api-clients`
- `PUT /api/system-admin/api-clients/{id}`
- `POST /api/system-admin/api-clients/{id}/revoke`
- `GET /api/system-admin/jobs`
- `PUT /api/system-admin/jobs/{id}`
- `GET /api/system-admin/health/integrations`
- `GET /api/system-admin/audit/config-changes`

## 7. Finance Admin Test Cases

### 7.1 Organization Setup

Routes:

- `/financeAdmin/organizationSetup`
- `/finance-admin/organization-setup`

Steps:

1. Create an organization.
2. Add or edit name, registration number, tax ID, country, address, city, state, postal code, phone, email, description, and active status.
3. Create a branch for the organization.
4. Edit branch details.
5. Refresh and reload the organization.

Expected result:

- Organization and branches save correctly.
- Branches are linked to the selected organization.
- Authenticated users can read organizations and branches.
- Only Finance Admin users can create or update organizations and branches.

API checkpoints:

- `GET /api/organizations`
- `POST /api/organizations`
- `GET /api/organizations/{id}`
- `PUT /api/organizations/{id}`
- `GET /api/branches?organizationId={organizationId}`
- `POST /api/branches`
- `GET /api/branches/{id}`
- `PUT /api/branches/{id}`

### 7.2 Chart Of Accounts

Route: `/financeAdmin/chartOfAccounts`

Steps:

1. Select the test organization.
2. Create accounts for bank, receivables, payables, VAT, retained earnings, revenue, expenses, and accumulated depreciation.
3. Edit an account name, subtype, normal balance, description, or active status.
4. Delete or deactivate an unused test account.
5. Try deleting or deactivating an account that has posted activity.

Expected result:

- Accounts save with correct code, name, type, subtype, and normal balance.
- Duplicate account codes are rejected.
- Used accounts are protected or deactivated in a controlled way.

API checkpoints:

- `GET /api/financeAdmin/chartOfAccounts/org/{organizationId}`
- `POST /api/financeAdmin/chartOfAccounts/org/{organizationId}`
- `GET /api/financeAdmin/chartOfAccounts/{accountId}`
- `PUT /api/financeAdmin/chartOfAccounts/{accountId}`
- `DELETE /api/financeAdmin/chartOfAccounts/{accountId}`

### 7.3 Fiscal Configuration And Periods

Route: `/financeAdmin/fiscalConfiguration`

Steps:

1. Create fiscal configuration for the organization.
2. Set fiscal year start, accounting method, currency, and retained earnings account if shown.
3. Create an open fiscal period.
4. Attempt to close a period while unresolved items exist.
5. Resolve pending approvals, payment runs, bank reconciliations, and financial reports.
6. Close the period.
7. Lock the closed period.
8. Try posting into the locked period.

Expected result:

- Fiscal configuration persists.
- Closing is blocked while close work is unresolved.
- Closed and locked periods prevent invalid posting.

API checkpoints:

- `GET /api/financeAdmin/fiscal-configuration/org/{organizationId}`
- `POST /api/financeAdmin/fiscal-configuration/org/{organizationId}`
- `PUT /api/financeAdmin/fiscal-configuration/{configId}`
- `GET /api/financeAdmin/fiscalPeriods/org/{organizationId}`
- `POST /api/financeAdmin/fiscalPeriods/org/{organizationId}`
- `PATCH /api/financeAdmin/fiscalPeriods/{periodId}/close`
- `PATCH /api/financeAdmin/fiscalPeriods/{periodId}/lock`

### 7.4 Module Controls

Route: `/financeAdmin/moduleControls`

Steps:

1. Initialize default module controls.
2. Toggle a module off and on.
3. Update approval requirement, audit trail, notifications, transaction limit, JSON configuration, and notes.
4. Review enabled modules.

Expected result:

- Defaults are created once.
- Settings persist after refresh.
- Enabled-module list matches active controls.

API checkpoints:

- `GET /api/financeAdmin/module-controls/org/{organizationId}`
- `POST /api/financeAdmin/module-controls/org/{organizationId}/defaults`
- `PUT /api/financeAdmin/module-controls/{moduleId}`
- `PATCH /api/financeAdmin/module-controls/{moduleId}/toggle`
- `GET /api/financeAdmin/module-controls/org/{organizationId}/enabled`

### 7.5 Tax Jurisdictions

Steps:

1. Create an active tax jurisdiction for standard VAT.
2. Confirm percentage calculation works.
3. Create a zero-rated or exempt scenario if the UI/API supports the fields.
4. Edit the jurisdiction.
5. Deactivate the jurisdiction.
6. Try using an inactive jurisdiction on an invoice.

Expected result:

- Active tax jurisdictions can be used by invoices.
- Inactive jurisdictions are rejected.
- Unsupported tax calculation types are rejected.

API checkpoints:

- `GET /api/financeAdmin/tax-jurisdictions/org/{organizationId}`
- `POST /api/financeAdmin/tax-jurisdictions/org/{organizationId}`
- `GET /api/financeAdmin/tax-jurisdictions/{jurisdictionId}`
- `PUT /api/financeAdmin/tax-jurisdictions/{jurisdictionId}`
- `DELETE /api/financeAdmin/tax-jurisdictions/{jurisdictionId}`

### 7.6 Approval Rules

Steps:

1. Create an approval rule for manual journals.
2. Create a non-journal approval rule, such as payment run or expense, if the fields allow it.
3. Set threshold, approver role, active status, and rule conditions.
4. Submit a transaction that should match the rule.
5. Edit and deactivate the rule.

Expected result:

- Rules are evaluated on submission.
- Pending approval records are created when required.
- Deactivated rules no longer apply.

API checkpoints:

- `GET /api/financeAdmin/approval-rules/org/{organizationId}`
- `POST /api/financeAdmin/approval-rules/org/{organizationId}`
- `GET /api/financeAdmin/approval-rules/{ruleId}`
- `PUT /api/financeAdmin/approval-rules/{ruleId}`
- `DELETE /api/financeAdmin/approval-rules/{ruleId}`

### 7.7 Bulk Imports

Steps:

1. Download the branch import template.
2. Download the chart of accounts import template.
3. Upload a valid branch CSV for validation.
4. Confirm the validated branch import.
5. Upload an invalid branch CSV and download row-level errors.
6. Repeat valid and invalid checks for chart of accounts.
7. Test `updateExisting=true` when updating existing rows.

Expected result:

- Templates download as CSV.
- Validation reports accepted and rejected rows.
- Confirmation creates or updates only valid rows.
- Error CSV explains row-level failures.

API checkpoints:

- `GET /api/financeAdmin/imports/templates/branches`
- `GET /api/financeAdmin/imports/templates/chart-of-accounts`
- `POST /api/financeAdmin/imports/branches/validate`
- `POST /api/financeAdmin/imports/chart-of-accounts/validate`
- `GET /api/financeAdmin/imports/{importId}`
- `POST /api/financeAdmin/imports/{importId}/confirm`
- `GET /api/financeAdmin/imports/{importId}/errors`

## 8. Accountant Test Cases

### 8.1 Manual Journals

Route: `/accountant/manualJournal`

Steps:

1. Create a draft manual journal.
2. Add at least two lines.
3. Confirm the journal cannot be submitted while unbalanced.
4. Balance debits and credits.
5. Submit the journal.
6. If approval is required, approve or reject as CFO.
7. Post the approved journal as Accountant.
8. Review generated general ledger lines.
9. Verify budget controls if the journal debits an expense account.

Expected result:

- Draft journals can be edited.
- Unbalanced journals are blocked.
- Submitted journals cannot be posted unless approval rules allow it.
- Approved and posted journals create balanced GL entries.
- Budget hard stops prevent GL rows from being saved.

API checkpoints:

- `GET /api/accountant/manual-journals/org/{organizationId}`
- `POST /api/accountant/manual-journals/org/{organizationId}`
- `GET /api/accountant/manual-journals/{journalId}`
- `POST /api/accountant/manual-journals/{journalId}/lines`
- `PUT /api/accountant/manual-journals/lines/{lineId}`
- `DELETE /api/accountant/manual-journals/lines/{lineId}`
- `PATCH /api/accountant/manual-journals/{journalId}/submit`
- `PATCH /api/accountant/manual-journals/{journalId}/post`
- `GET /api/accountant/general-ledger/journal/{journalId}`

### 8.2 Customer Invoicing

Route: `/accountant/customerInvoicing`

Steps:

1. Create a customer.
2. Confirm customer credit limit and credit hold fields are available where implemented.
3. Create a customer invoice with line items.
4. Select a tax jurisdiction.
5. Save draft.
6. Edit the draft.
7. Post the invoice.
8. Generate/download invoice documents in PDF, JSON, and XML where available.
9. Send the invoice.
10. Review dispatch history.
11. Retry a failed dispatch.
12. Record a full or partial payment.
13. Confirm receivables and revenue GL impact.
14. Confirm credit hold blocks sending where applicable.

Expected result:

- Customer creation works.
- Tax is calculated from active tax jurisdictions.
- Posting increases receivables and revenue.
- Payments reduce outstanding receivables.
- B2B invoice documents are blocked until NRS clearance; B2C downloads are allowed before clearance.
- Missing customer email creates a failed dispatch rather than a silent success.

API checkpoints:

- `GET /api/accountant/customers/org/{organizationId}`
- `POST /api/accountant/customers/org/{organizationId}`
- `PATCH /api/accountant/customers/{customerId}/credit-hold`
- `PATCH /api/accountant/customers/{customerId}/credit-hold/release`
- `GET /api/accountant/customer-invoices/org/{organizationId}`
- `POST /api/accountant/customer-invoices/org/{organizationId}`
- `GET /api/accountant/customer-invoices/{invoiceId}`
- `PUT /api/accountant/customer-invoices/{invoiceId}`
- `PATCH /api/accountant/customer-invoices/{invoiceId}/send`
- `POST /api/accountant/customer-invoices/{invoiceId}/send`
- `GET /api/accountant/customer-invoices/{invoiceId}/dispatches`
- `POST /api/accountant/customer-invoices/{invoiceId}/dispatches/{dispatchId}/retry`
- `PATCH /api/accountant/customer-invoices/{invoiceId}/post`
- `PATCH /api/accountant/customer-invoices/{invoiceId}/payments`
- `POST /api/accountant/customer-invoices/{invoiceId}/documents`
- `GET /api/accountant/customer-invoices/{invoiceId}/documents`
- `GET /api/accountant/customer-invoices/{invoiceId}/download`
- `GET /api/accountant/customer-invoices/documents/{documentId}/download`

### 8.3 Receivables Collections

Route: `/accountant/receivablesCollections`

Steps:

1. Create overdue customer invoices.
2. Generate collection cases.
3. Open a case.
4. Assign the case.
5. Add an activity.
6. Send dunning.
7. Create a promise to pay.
8. Change promise status.
9. Reconcile promises.
10. Escalate the case.
11. Close the case.
12. Generate and send a customer statement.
13. Review aged receivables.

Expected result:

- Overdue invoices generate collection cases.
- Promises move cases into promised status.
- Statements include correct customer lines.
- Aged receivables buckets are correct.

API checkpoints:

- `GET /api/accountant/reports/aged-receivables/org/{organizationId}`
- `GET /api/accountant/receivables/collections/org/{organizationId}`
- `POST /api/accountant/receivables/collections/org/{organizationId}/generate`
- `GET /api/accountant/receivables/collections/{caseId}`
- `PATCH /api/accountant/receivables/collections/{caseId}/assign`
- `PATCH /api/accountant/receivables/collections/{caseId}/escalate`
- `PATCH /api/accountant/receivables/collections/{caseId}/close`
- `POST /api/accountant/receivables/collections/{caseId}/activities`
- `POST /api/accountant/receivables/collections/{caseId}/dunning`
- `POST /api/accountant/receivables/collections/{caseId}/promises`
- `PATCH /api/accountant/receivables/promises/{promiseId}/status`
- `POST /api/accountant/receivables/promises/reconcile/org/{organizationId}`
- `POST /api/accountant/receivables/customer-statements/org/{organizationId}`
- `GET /api/accountant/receivables/customer-statements/org/{organizationId}`
- `GET /api/accountant/receivables/customer-statements/{statementId}`
- `PATCH /api/accountant/receivables/customer-statements/{statementId}/send`

### 8.4 Purchase Invoices

Route: `/accountant/purchaseInvoice`

Steps:

1. Create a vendor.
2. Create a purchase invoice.
3. Edit the draft.
4. Submit for approval.
5. Approve or reject as permitted.
6. Post the approved invoice.
7. Record a payment.
8. Review aged payables.

Expected result:

- Posting increases payables and the selected expense or asset account.
- Payments reduce outstanding payables.
- Rejected invoices are not posted.

API checkpoints:

- `GET /api/accountant/vendors/org/{organizationId}`
- `POST /api/accountant/vendors/org/{organizationId}`
- `GET /api/accountant/purchase-invoices/org/{organizationId}`
- `POST /api/accountant/purchase-invoices/org/{organizationId}`
- `GET /api/accountant/purchase-invoices/{invoiceId}`
- `PUT /api/accountant/purchase-invoices/{invoiceId}`
- `PATCH /api/accountant/purchase-invoices/{invoiceId}/submit`
- `PATCH /api/accountant/purchase-invoices/{invoiceId}/approve`
- `PATCH /api/accountant/purchase-invoices/{invoiceId}/reject`
- `PATCH /api/accountant/purchase-invoices/{invoiceId}/post`
- `PATCH /api/accountant/purchase-invoices/{invoiceId}/payments`
- `GET /api/accountant/reports/aged-payables/org/{organizationId}`

### 8.5 Payables Automation

Route: `/accountant/payablesAutomation`

Steps:

1. Confirm posted purchase invoices create payment schedules.
2. Review due schedules.
3. Submit, approve, reject, hold, reschedule, and cancel schedules.
4. Create a payment run from approved due schedules.
5. Submit the run.
6. Approve or reject the run.
7. Execute the approved run.
8. Cancel a draft or eligible run.
9. Review cash requirement forecast.
10. Create a supplier statement.
11. Auto-match supplier statement lines.
12. Dispute a supplier statement line.
13. Complete the supplier statement.

Expected result:

- Only eligible schedules are included in payment runs.
- Approval rule decisions are stored when a run is submitted.
- Executed runs update payment state.
- Forecast reflects due payables.
- Supplier statements show matched, disputed, and completed status.

API checkpoints:

- `GET /api/accountant/payables/schedules/org/{organizationId}`
- `GET /api/accountant/payables/due/org/{organizationId}`
- `PATCH /api/accountant/payables/schedules/{scheduleId}/approve`
- `PATCH /api/accountant/payables/schedules/{scheduleId}/submit`
- `PATCH /api/accountant/payables/schedules/{scheduleId}/reject`
- `PATCH /api/accountant/payables/schedules/{scheduleId}/hold`
- `PATCH /api/accountant/payables/schedules/{scheduleId}/reschedule`
- `PATCH /api/accountant/payables/schedules/{scheduleId}/cancel`
- `POST /api/accountant/payables/payment-runs/org/{organizationId}`
- `GET /api/accountant/payables/payment-runs/org/{organizationId}`
- `GET /api/accountant/payables/payment-runs/{runId}`
- `PATCH /api/accountant/payables/payment-runs/{runId}/submit`
- `PATCH /api/accountant/payables/payment-runs/{runId}/approve`
- `PATCH /api/accountant/payables/payment-runs/{runId}/reject`
- `PATCH /api/accountant/payables/payment-runs/{runId}/execute`
- `PATCH /api/accountant/payables/payment-runs/{runId}/cancel`
- `GET /api/accountant/payables/forecast/org/{organizationId}`
- `POST /api/accountant/payables/supplier-statements/org/{organizationId}`
- `GET /api/accountant/payables/supplier-statements/org/{organizationId}`
- `GET /api/accountant/payables/supplier-statements/{statementId}`
- `PATCH /api/accountant/payables/supplier-statements/{statementId}/auto-match`
- `PATCH /api/accountant/payables/supplier-statement-lines/{lineId}/dispute`
- `PATCH /api/accountant/payables/supplier-statements/{statementId}/complete`

### 8.6 Cash And Bank

Route: `/accountant/cashAndBank`

Steps:

1. Create a bank account linked to a chart account.
2. Create or import a bank reconciliation.
3. Upload a bank statement CSV, TXT, or XLSX file.
4. Confirm statement lines are created.
5. Review match candidates.
6. Auto-match eligible lines.
7. Manually match a line.
8. Unmatch a line.
9. Complete the reconciliation.
10. Try completing while required lines remain unmatched.

Expected result:

- Bank accounts save and reload.
- Imported statement lines retain date, reference, description, debit/credit, and balance where provided.
- Matching links bank statement lines to ledger activity.
- Completion requires reconciliation rules to pass.

API checkpoints:

- `GET /api/accountant/bank-accounts/org/{organizationId}`
- `POST /api/accountant/bank-accounts/org/{organizationId}`
- `GET /api/accountant/bank-reconciliations/org/{organizationId}/bank-account/{bankAccountId}`
- `POST /api/accountant/bank-reconciliations/org/{organizationId}`
- `POST /api/accountant/bank-reconciliations/org/{organizationId}/import`
- `GET /api/accountant/bank-reconciliations/{reconciliationId}`
- `PATCH /api/accountant/bank-reconciliations/{reconciliationId}/auto-match`
- `GET /api/accountant/bank-reconciliations/{reconciliationId}/lines/{statementLineId}/candidates`
- `PATCH /api/accountant/bank-reconciliations/{reconciliationId}/lines/{statementLineId}/match`
- `PATCH /api/accountant/bank-reconciliations/{reconciliationId}/lines/{statementLineId}/unmatch`
- `PATCH /api/accountant/bank-reconciliations/{reconciliationId}/complete`

### 8.7 Expenses

Steps:

1. Create an expense.
2. Edit the draft.
3. Submit the expense.
4. Approve or reject it.
5. Post the approved expense.
6. Record payment.
7. Test budget controls on expense posting.

Expected result:

- Approved expenses post GL rows.
- Budget hard stops block posting and prevent partial GL writes.
- Expense payments use the dedicated expense payment source type.

API checkpoints:

- `GET /api/accountant/expenses/org/{organizationId}`
- `POST /api/accountant/expenses/org/{organizationId}`
- `GET /api/accountant/expenses/{expenseId}`
- `PUT /api/accountant/expenses/{expenseId}`
- `PATCH /api/accountant/expenses/{expenseId}/submit`
- `PATCH /api/accountant/expenses/{expenseId}/approve`
- `PATCH /api/accountant/expenses/{expenseId}/reject`
- `PATCH /api/accountant/expenses/{expenseId}/post`
- `PATCH /api/accountant/expenses/{expenseId}/payments`

### 8.8 Fixed Assets And Depreciation Journals

Routes:

- `/accountant/fixedAssets`
- `/accountant/depreciationJournals`

Steps:

1. Open Fixed Assets and confirm the page loads.
2. Open Depreciation Journal Imports.
3. Create a depreciation journal import manually if fields are available.
4. Upload a depreciation import file.
5. Include at least one valid row and one invalid row.
6. Review import history.

Expected result:

- Depreciation imports validate rows.
- Valid rows can create depreciation journal activity.
- Invalid rows show clear errors.
- Import history records status, counts, and source details.

API checkpoints:

- `GET /api/accountant/depreciation-journals/org/{organizationId}/imports`
- `POST /api/accountant/depreciation-journals/org/{organizationId}/import`
- `POST /api/accountant/depreciation-journals/org/{organizationId}/import-file`

## 9. CFO Test Cases

### 9.1 Approvals Hub

Route: `/cfo/approvalsHub`

Steps:

1. Create and submit a manual journal as Accountant.
2. Open Approvals Hub as CFO.
3. Review pending journals.
4. Approve one journal.
5. Reject another journal with a reason.
6. Confirm fallback approval works for submitted journals without an approval request where applicable.

Expected result:

- Pending journals are visible.
- Approval and rejection update journal status.
- Rejected items cannot be posted.

API checkpoints:

- `GET /api/cfo/manual-journals/org/{organizationId}/pending`
- `GET /api/cfo/manual-journals/{journalId}`
- `PATCH /api/cfo/manual-journals/{journalId}/approve`
- `PATCH /api/cfo/manual-journals/{journalId}/reject`

### 9.2 Budget Control

Route: `/cfo/budgetControl`

Steps:

1. Create a budget for the organization.
2. Add budget lines for expense accounts.
3. Submit the budget.
4. Approve the budget.
5. Activate the budget.
6. Post transactions against budgeted accounts.
7. Review budget dashboard.
8. Test warning threshold.
9. Test hard stop when projected spend exceeds allowed budget.
10. Change budget status if supported.

Expected result:

- Budget lines track approved amount, consumed amount, and remaining amount.
- Warning thresholds produce a warning decision.
- Hard stops block posting.
- When no matching budget line exists, posting is allowed unless another control blocks it.

API checkpoints:

- `GET /api/cfo/budgets/org/{organizationId}`
- `POST /api/cfo/budgets/org/{organizationId}`
- `GET /api/cfo/budgets/{budgetId}`
- `POST /api/cfo/budgets/{budgetId}/lines`
- `GET /api/cfo/budgets/{budgetId}/lines`
- `PATCH /api/cfo/budgets/{budgetId}/submit`
- `PATCH /api/cfo/budgets/{budgetId}/approve`
- `PATCH /api/cfo/budgets/{budgetId}/reject`
- `PATCH /api/cfo/budgets/{budgetId}/activate`
- `PATCH /api/cfo/budgets/{budgetId}/status/{status}`
- `GET /api/cfo/budgets/org/{organizationId}/dashboard`
- `GET /api/cfo/chart-of-accounts/org/{organizationId}`

### 9.3 Trial Balance

Route: `/cfo/trialBalance`

Steps:

1. Post several balanced transactions.
2. Generate or view trial balance for the organization and date range.
3. Create a trial balance snapshot.
4. Review previous snapshots.

Expected result:

- Total debits equal total credits.
- Snapshot retains the report state.
- Out-of-balance results are clearly visible if data is invalid.

API checkpoints:

- `GET /api/cfo/trial-balance/org/{organizationId}`
- `POST /api/cfo/trial-balance/org/{organizationId}/snapshots`
- `GET /api/cfo/trial-balance/org/{organizationId}/snapshots`

### 9.4 Financial Reports

Route: `/cfo/financialReports`

Steps:

1. Generate a financial report for a date range.
2. Open report details.
3. Submit report for approval.
4. Approve a report.
5. Reject another report with a reason.
6. Confirm unresolved submitted reports block fiscal close where applicable.

Expected result:

- Reports generate from posted ledger data.
- Report lines agree with the selected accounts and date range.
- Status lifecycle is enforced.

API checkpoints:

- `POST /api/cfo/financial-reports/org/{organizationId}/generate`
- `GET /api/cfo/financial-reports/org/{organizationId}`
- `GET /api/cfo/financial-reports/{reportId}`
- `PATCH /api/cfo/financial-reports/{reportId}/submit`
- `PATCH /api/cfo/financial-reports/{reportId}/approve`
- `PATCH /api/cfo/financial-reports/{reportId}/reject`

## 10. Auditor Test Cases

### 10.1 Audit Log Explorer

Route: `/auditor/auditLogExplorer`

Steps:

1. Perform create, update, submit, approve, reject, post, and payment actions in other roles.
2. Open Audit Log Explorer.
3. Filter by organization.
4. Filter by entity.
5. Filter by date range.
6. Review user, IP address, user agent, action, entity, and timestamp fields.

Expected result:

- Business events are traceable.
- Explicit audit context is preserved where provided.
- Current user and request context are resolved when explicit values are not provided.

API checkpoints:

- `GET /api/auditor/audit-logs/org/{organizationId}`
- `GET /api/auditor/audit-logs/org/{organizationId}/entity`
- `GET /api/auditor/audit-logs/org/{organizationId}/date-range`

### 10.2 Security Events

Route: `/auditor/securityEvents`

Steps:

1. Generate or locate security events.
2. Filter by organization.
3. Filter by user ID.
4. Filter by date range.
5. Review unacknowledged events.
6. Acknowledge an event.

Expected result:

- Security events are visible and filterable.
- Acknowledgement records the change.
- Explicit user ID is preserved where provided.

API checkpoints:

- `GET /api/auditor/security-events/org/{organizationId}`
- `GET /api/auditor/security-events/org/{organizationId}/unacknowledged`
- `GET /api/auditor/security-events/org/{organizationId}/date-range`
- `PATCH /api/auditor/security-events/{eventId}/acknowledge`

## 11. External Invoice API And E-Invoicing

### 11.1 External Invoice API

Purpose: provide paged invoice search and invoice-related resources for external consumers.

Steps:

1. Create several invoices.
2. Search invoices with paging.
3. Open invoice details.
4. Fetch invoice documents.
5. Fetch invoice events.
6. Fetch invoice dispatches.
7. Try unsupported filter values.

Expected result:

- Paging metadata is correct.
- Unsupported filters are rejected.
- Documents, events, and dispatches match invoice history.

API checkpoints:

- `GET /api/v1/invoices`
- `GET /api/v1/invoices/{invoiceId}`
- `GET /api/v1/invoices/{invoiceId}/documents`
- `GET /api/v1/invoices/{invoiceId}/events`
- `GET /api/v1/invoices/{invoiceId}/dispatches`

### 11.2 NRS Payload And Submission

Steps:

1. Create a standard VAT invoice with valid supplier and buyer tax IDs.
2. Request the NRS payload.
3. Submit the invoice for NRS processing.
4. Review submission details.
5. Review all submissions for the invoice.
6. Test a B2C invoice and confirm it defaults to asynchronous submission.
7. Try unsupported submission type.
8. Try invalid VAT rate.
9. Try zero tax without exemption or zero-rated code.
10. Try missing supplier or buyer tax ID.

Expected result:

- Valid invoices map to NRS payloads.
- Payload content is archived.
- Submission records include archive metadata.
- Compliance failures block payload or document flow.

API checkpoints:

- `GET /api/accountant/customer-invoices/{invoiceId}/nrs-payload`
- `POST /api/accountant/customer-invoices/{invoiceId}/nrs-submissions`
- `GET /api/accountant/customer-invoices/{invoiceId}/nrs-submissions`
- `GET /api/accountant/customer-invoices/nrs-submissions/{submissionId}`

## 12. Webhook Integration Tests

API root: `/api/integrations/webhooks`

Steps:

1. Create a webhook endpoint with HTTPS URL, event subscriptions, and auth type.
2. Create an HMAC endpoint with a secret reference.
3. Confirm secret references are masked in responses.
4. Try creating an HMAC endpoint without a secret reference.
5. Try creating an insecure non-local URL.
6. Update an endpoint while leaving the masked secret unchanged.
7. Send a test webhook.
8. Review deliveries.
9. Delete the endpoint.

Expected result:

- Valid endpoints are created and updated.
- Invalid security settings are rejected.
- Test deliveries are signed when configured.
- Delivery history is visible.
- Deleted endpoints are no longer returned as active.

API checkpoints:

- `POST /api/integrations/webhooks`
- `GET /api/integrations/webhooks`
- `GET /api/integrations/webhooks/{id}`
- `PUT /api/integrations/webhooks/{id}`
- `DELETE /api/integrations/webhooks/{id}`
- `POST /api/integrations/webhooks/{id}/test`
- `GET /api/integrations/webhooks/{id}/deliveries`

## 13. Negative And Edge Case Testing

Run these checks across all relevant modules:

| Category | Checks |
| --- | --- |
| Required fields | Submit forms with missing organization, account, customer, vendor, amount, dates, or descriptions |
| Dates | Invalid dates, end date before start date, due date before invoice date, closed/locked fiscal periods |
| Amounts | Negative amount, zero amount where not allowed, excessive amount, unbalanced debit/credit totals |
| Duplicates | Duplicate account code, duplicate branch code, duplicate API client ID, duplicate import rows |
| Status transitions | Post draft, approve already approved item, reject posted item, pay unposted invoice, execute unapproved run |
| Files | Wrong file type, missing CSV columns, invalid row values, empty file, oversized file |
| Security | Cross-role page access, cross-role API access, unauthenticated API access, masked secret exposure |
| Integrations | Insecure webhook URL, missing HMAC secret, unsupported invoice filters, unsupported NRS submission type |

Expected result:

- Invalid actions are rejected.
- Existing state remains unchanged.
- Errors are clear and do not expose stack traces, secrets, tokens, or SQL details.

## 14. File Test Data

### 14.1 Branch Import CSV

Use the downloaded branch template as the source of truth. Include:

- Valid new branch row.
- Duplicate branch row.
- Missing required field row.
- Existing branch row for `updateExisting=true`.

### 14.2 Chart Of Accounts Import CSV

Use the downloaded chart template as the source of truth. Include:

- Valid account row.
- Duplicate account code row.
- Unsupported account type row.
- Missing normal balance row if required.
- Existing account row for `updateExisting=true`.

### 14.3 Bank Statement File

Include:

- Statement date.
- Reference.
- Description.
- Debit amount or credit amount.
- Running balance if supported.
- One line that should match a ledger transaction.
- One line that should remain unmatched.

### 14.4 Depreciation Journal Import

Include:

- External system or batch ID.
- Asset reference.
- Depreciation period.
- Depreciation amount.
- Accumulated depreciation account.
- Expense account.
- One invalid row with missing or invalid account data.

## 15. Automated Regression Suite

Run:

```powershell
.\mvnw.cmd test
```

The suite currently covers:

| Test class | Coverage |
| --- | --- |
| `AmsApplicationTests` | Spring context startup |
| `ManualJournalServiceTest` | Approval fallback for journals without pending workflow |
| `FiscalPeriodServiceTest` | Close blockers for bank reconciliations, payment runs, approvals, financial reports, and successful close |
| `GeneralLedgerServiceBudgetControlTest` | Budget controls for manual journals and expenses, amount limits, no partial GL rows, expense payment source type |
| `CustomerInvoiceCollectionControlTest` | Credit hold blocks invoice sending |
| `ReceivablesCollectionServiceTest` | Overdue case generation and promise-to-pay status |
| `PaymentScheduleServiceTest` | Default schedules and direct payment schedule reduction |
| `PaymentRunServiceTest` | Draft run creation and approval-rule decision storage |
| `ExpenseServicePaymentSourceTest` | Dedicated expense payment posting |
| `TaxCalculationServiceTest` | Percentage tax, no jurisdiction, inactive jurisdiction, unsupported calculation type |
| `ApprovalRuleServiceTest` | Non-journal approval rule evaluation |
| `ApprovalWorkflowServiceTest` | Pending workflow rejection and approval requirement enforcement |
| `BudgetControlServiceTest` | Hard stop, warning threshold, no matching line allowance |
| `AuditLogServiceTest` | Current user/request context and explicit audit context |
| `SecurityEventServiceTest` | User filtering, resolved request context, explicit user ID |
| `ExternalInvoiceApiServiceTest` | Invoice search, paging, filter rejection, documents, dispatches, synthesized events |
| `InvoiceDocumentServiceTest` | B2B NRS clearance download rules, B2C download, persisted document content |
| `InvoiceDeliveryServiceTest` | Email dispatch success/failure, compliance block, dispatch listing, retry rejection for success |
| `WebhookServiceTest` | Endpoint creation, masked secrets, HMAC validation, URL validation, signed test delivery, update behavior, delivery listing |
| `NrsPayloadMapperTest` | NRS payload mapping, VAT validation, zero tax rules, required tax IDs |
| `EInvoiceSubmissionServiceTest` | Pre-clearance submission, archive creation, B2C async default, unsupported type, archive content, submission listing |
| `ApprovalRulesAcceptanceTest` | Approval rule applied to submitted manual journals and CFO fallback approval |
| `BankReconciliationAcceptanceTest` | Auto-match, completion, CSV import, auto-match after import |
| `MvpBackendAcceptanceCriteriaTest` | End-to-end MVP backend API flow |
| `Stage2ReportingControlsAcceptanceTest` | Reporting and control API flow |

Expected result:

- All tests pass.
- Any failure is triaged before sign-off.
- If a manual change adds behavior, add or update tests so this table remains accurate.

## 16. End-To-End Accounting Reconciliation

After completing the role workflows, verify these accounting outcomes:

1. Manual journal debits equal credits before submission.
2. Approved and posted manual journals create balanced GL entries.
3. Customer invoice posting increases receivables and revenue.
4. Customer payment reduces receivables.
5. Purchase invoice posting increases expenses or assets and payables.
6. Vendor payment reduces payables.
7. Expense posting and expense payment use the correct source types.
8. Bank reconciliation matches statement lines to ledger activity.
9. Trial balance total debits equal total credits.
10. Financial report totals agree with posted ledger data.
11. Budget dashboard reflects controlled spend.
12. Fiscal close is blocked while unresolved work exists.
13. Audit logs show creation, update, submit, approve, reject, post, payment, import, and configuration events where implemented.
14. Security events can be filtered and acknowledged.
15. E-invoice payloads and submissions preserve archive metadata.

## 17. Final Sign-Off Checklist

| Area | Pass criteria |
| --- | --- |
| Login and logout | Public page, OAuth2 login, role redirect, logout all work |
| Authorization | Cross-role pages and APIs are blocked |
| Admin | User creation, group changes, enable/disable, reset email, delete |
| System admin | NRS, signing, webhooks, storage, API clients, jobs, health, config audit |
| Organization | Organization and branch CRUD |
| Chart of accounts | Account CRUD, duplicate prevention, used-account protection |
| Fiscal controls | Configuration, period create, close blockers, close, lock |
| Module controls | Defaults, update, toggle, enabled list |
| Taxes | Active tax calculation, inactive rejection, unsupported type rejection |
| Approval rules | Create, evaluate, update, deactivate |
| Bulk import | Templates, validate, confirm, error CSV |
| Manual journals | Create, line edit, balance validation, submit, approve/reject, post, GL |
| Customer invoices | Customer, draft/edit, tax, post, payment, documents, send, dispatch retry |
| Receivables | Aging, case generation, assignment, activities, dunning, promises, statements |
| Purchase invoices | Vendor, draft/edit, submit, approve/reject, post, payment, aged payables |
| Payables automation | Schedules, due list, payment runs, forecast, supplier statements |
| Cash and bank | Bank account, import, candidates, match, unmatch, auto-match, complete |
| Expenses | Create, edit, submit, approve/reject, post, payment, budget block |
| Depreciation imports | Manual/file import, valid rows, invalid rows, history |
| CFO reports | Budgets, dashboard, trial balance, snapshots, financial report lifecycle |
| Auditor | Audit logs, security events, filters, acknowledgement |
| External APIs | Invoice search/details/documents/events/dispatches |
| E-invoicing | NRS payload, submissions, archive, clearance rules |
| Webhooks | Endpoint CRUD, validation, signed test delivery, delivery list |
| Negative tests | Invalid input and invalid status transitions are controlled |
| Automated tests | `.\mvnw.cmd test` passes |

## 18. Defect Report Template

```text
Title:
Environment:
Build or commit:
User role:
Browser:
Route or API:
Test data:
Steps to reproduce:
Expected result:
Actual result:
Screenshots or logs:
Severity:
Business impact:
Regression risk:
```

## 19. Recommended Test Order

1. Run `.\mvnw.cmd test`.
2. Verify public page, login, logout, role redirects, and access control.
3. Configure organization, branches, chart of accounts, fiscal periods, module controls, taxes, and approval rules.
4. Test bulk imports.
5. Create customer, vendor, and bank account master data.
6. Complete manual journal workflow through CFO approval and GL posting.
7. Complete customer invoice, document, NRS, dispatch, payment, and receivables collection workflows.
8. Complete purchase invoice, payment schedule, payment run, forecast, and supplier statement workflows.
9. Complete expense workflow.
10. Complete bank reconciliation workflow.
11. Complete depreciation import workflow.
12. Complete budget, trial balance, and financial report workflows.
13. Complete system admin integration, webhook, API client, job, health, and audit checks.
14. Complete auditor log and security event checks.
15. Complete external invoice API checks.
16. Run negative and edge case testing.
17. Run `.\mvnw.cmd test` again before final sign-off.
