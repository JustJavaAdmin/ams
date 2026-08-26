# AMS Testing Guide

This guide is for testers validating the Accounting Management System (AMS) from login through setup, transaction processing, approvals, reporting, audit review, and administration.

## 1. Test Objective

Validate that AMS works as an end-to-end accounting workflow:

1. Admin creates and manages user access.
2. Finance Admin configures the organization, branches, chart of accounts, fiscal periods, modules, taxes, and approval rules.
3. Accountant records accounting activity such as journals, invoices, payables, receivables, bank reconciliation, expenses, and depreciation journal imports.
4. CFO reviews approvals, budgets, trial balance, and financial reports.
5. Auditor reviews audit logs and security events.

## 2. Environment And Access

### 2.1 Application URL

Use the configured application port:

```text
http://localhost:9078
```

The root page `/` is public. After login, the application redirects users according to their role.

### 2.2 Required External Services

Confirm these services are available before testing:

| Dependency | Purpose | Default configuration |
| --- | --- | --- |
| PostgreSQL | Main AMS database | `jdbc:postgresql://localhost:5432/ams` |
| Keycloak | OAuth2 login and role/group access | Realm: `ams` |
| Browser | UI testing | Chrome, Edge, or Firefox |

### 2.3 Required Test Users

Create or obtain one test user for each role:

| Role | Expected landing page after login |
| --- | --- |
| `ADMIN` | `/admin/users` |
| `FINANCE_ADMIN` | `/financeAdmin/organizationSetup` |
| `ACCOUNTANT` | `/accountant/manualJournal` |
| `CFO` | `/cfo/approvalsHub` |
| `AUDITOR` | `/auditor/auditLogExplorer` |

### 2.4 Browser And Session Checks

For each role:

1. Open `http://localhost:9078`.
2. Confirm the public AMS landing page is visible before login.
3. Log in through Keycloak.
4. Confirm the redirect matches the role landing page in the table above.
5. Click Logout.
6. Confirm the session ends and the public landing page is reachable again.

## 3. Test Data Setup

Use clear test naming so records are easy to identify later.

Recommended baseline data:

| Data type | Example |
| --- | --- |
| Organization | `QA Test Organization` |
| Branch | `QA Main Branch` |
| Bank account | `QA Operating Bank`, currency `NGN` |
| Customer | `QA Customer Limited` |
| Vendor | `QA Vendor Limited` |
| Revenue account | `4000 - Product Revenue` |
| Expense account | `5000 - Office Expense` |
| Bank account | `1000 - Bank` |
| Receivables account | `1100 - Accounts Receivable` |
| Payables account | `2000 - Accounts Payable` |
| Tax account | `2100 - VAT Payable` |
| Retained earnings account | `3000 - Retained Earnings` |

## 4. Full End-To-End Test Flow

Run this flow in order for a complete application walkthrough.

## 5. Admin: User Management

Route: `/admin/users`

### 5.1 Create A User

1. Log in as an `ADMIN`.
2. Open User Management.
3. Create a new test user with first name, last name, email, username, and temporary password.
4. Assign one or more role groups, such as `ACCOUNTANT` or `CFO`.
5. Save the user.

Expected result:

- The user appears in the user list.
- User details are visible after selection.
- Assigned groups/roles are shown.

### 5.2 Manage User Access

1. Select the created user.
2. Add a group.
3. Remove a group.
4. Disable the user.
5. Enable the user.
6. Send a reset password email.
7. Delete the test user only after all user management checks are complete.

Expected result:

- Each action displays a success or error message.
- Access changes take effect on the next login.
- A disabled user should not be able to access protected AMS pages.

## 6. Finance Admin: Foundation Configuration

Log in as `FINANCE_ADMIN`.

### 6.1 Organization Setup

Routes:

- `/financeAdmin/organizationSetup`
- `/finance-admin/organization-setup`

Steps:

1. Open Organization Setup.
2. Create `QA Test Organization`.
3. Add `QA Main Branch`.
4. Add or update organization details such as name, registration, currency, fiscal year settings, and contact details if available.
5. Refresh the page.

Expected result:

- Organization is saved and reloads correctly.
- Branch is linked to the selected organization.
- Organization and branch are available in downstream pages.

API checkpoints:

- `GET /api/organizations`
- `GET /api/branches?organizationId={organizationId}`

### 6.2 Chart Of Accounts

Route: `/financeAdmin/chartOfAccounts`

Steps:

1. Select the QA organization.
2. Create accounts needed for the test flow:
   - Asset accounts: Bank, Accounts Receivable.
   - Liability accounts: Accounts Payable, VAT Payable.
   - Equity account: Retained Earnings.
   - Revenue account: Product Revenue.
   - Expense account: Office Expense.
3. Edit one account and confirm the update is retained.
4. Try deleting an unused test account.
5. Try deleting an account already used in a transaction.

Expected result:

- Accounts are created with correct code, name, and type.
- Edited values persist after refresh.
- Used accounts should be protected from invalid deletion or should fail gracefully.

API checkpoint:

- `GET /api/financeAdmin/chartOfAccounts/org/{organizationId}`

### 6.3 Fiscal Configuration And Fiscal Periods

Route: `/financeAdmin/fiscalConfiguration`

Steps:

1. Create or update the fiscal configuration for the QA organization.
2. Set the fiscal year start and accounting method.
3. Create an open fiscal period for the current testing month.
4. Close a period that has completed transactions.
5. Lock a closed period.
6. Attempt to post a transaction into a locked period.

Expected result:

- Fiscal configuration persists.
- Open periods accept valid postings.
- Closed or locked periods prevent invalid posting activity.

API checkpoints:

- `GET /api/financeAdmin/fiscal-configuration/org/{organizationId}`
- `GET /api/financeAdmin/fiscalPeriods/org/{organizationId}`

### 6.4 Module Controls

Route: `/financeAdmin/moduleControls`

Steps:

1. Open Module Controls.
2. Create default module controls if none exist.
3. Toggle modules on and off.
4. Update module control settings.
5. Confirm enabled modules are returned correctly.

Expected result:

- Module control settings persist after refresh.
- Disabled modules prevent or restrict the related behavior where implemented.
- Enabled modules are available to the relevant role.

API checkpoints:

- `GET /api/financeAdmin/module-controls/org/{organizationId}`
- `GET /api/financeAdmin/module-controls/org/{organizationId}/enabled`

### 6.5 Approval Rules And Tax Jurisdictions

Route: `/financeAdmin/moduleControls`

Steps:

1. Create an approval rule for journal or invoice approval.
2. Edit the rule threshold, approver role, or active status.
3. Create a tax jurisdiction and tax rate.
4. Edit the jurisdiction.
5. Delete an unused test jurisdiction.

Expected result:

- Approval rules affect approval routing where supported.
- Tax jurisdictions are available to accountant invoice screens.
- Tax values are calculated or applied consistently in downstream invoice flows.

API checkpoints:

- `GET /api/financeAdmin/approval-rules/org/{organizationId}`
- `GET /api/financeAdmin/tax-jurisdictions/org/{organizationId}`

### 6.6 Bulk Import Templates

Steps:

1. Download the branch import template.
2. Download the chart of accounts import template.
3. Upload a valid branch CSV for validation.
4. Confirm the valid import.
5. Upload an invalid CSV.
6. Download the error CSV.

Expected result:

- Template downloads return CSV files.
- Valid files preview successfully and can be confirmed.
- Invalid files show row-level validation errors.

API checkpoints:

- `GET /api/financeAdmin/imports/templates/branches`
- `GET /api/financeAdmin/imports/templates/chart-of-accounts`
- `POST /api/financeAdmin/imports/branches/validate`
- `POST /api/financeAdmin/imports/chart-of-accounts/validate`
- `POST /api/financeAdmin/imports/{importId}/confirm`

## 7. Accountant: Transaction Processing

Log in as `ACCOUNTANT`.

### 7.1 Manual Journal

Route: `/accountant/manualJournal`

Steps:

1. Select the QA organization.
2. Click New Journal.
3. Enter journal header details such as date, description, branch, and reference.
4. Add at least two journal lines:
   - Debit Office Expense.
   - Credit Bank or Accounts Payable.
5. Confirm total debits equal total credits.
6. Save as draft.
7. Reopen the journal from the journal list.
8. Submit the journal.

Expected result:

- Draft journal is saved and listed.
- Balanced journal can be submitted.
- Unbalanced journal cannot be submitted or should show validation.
- Submitted journal appears in the CFO approval queue.

Negative checks:

- Try submitting with no lines.
- Try submitting with one-sided debit/credit values.
- Try deleting a line from an already submitted journal.

API checkpoints:

- `GET /api/accountant/manual-journals/org/{organizationId}`
- `GET /api/accountant/manual-journals/{journalId}`

### 7.2 CFO Approval For Manual Journal

Switch to `CFO`.

Route: `/cfo/approvalsHub`

Steps:

1. Open Approval Hub.
2. Select the QA organization.
3. Confirm the submitted journal appears in the Authorization Queue.
4. Open the journal review modal.
5. Verify journal header and lines.
6. Approve the journal.

Expected result:

- Pending count and total value update.
- Approved journal no longer appears as pending.
- Approval decision is persisted.

Reject path:

1. Create and submit another journal as Accountant.
2. Open it as CFO.
3. Reject with a clear reason.
4. Confirm the Accountant can see the rejected status.

### 7.3 Post Approved Journal To General Ledger

Switch back to `ACCOUNTANT`.

Route: `/accountant/manualJournal`

Steps:

1. Open the approved journal.
2. Click Post.
3. Confirm posting.
4. Review the generated General Ledger entries.

Expected result:

- Journal status changes to posted.
- GL entries are created for each journal line.
- Debit and credit totals remain balanced.
- Posted journal should not allow unauthorized line changes.

API checkpoint:

- `GET /api/accountant/general-ledger/journal/{journalId}`

### 7.4 Customer Invoicing And Receivables

Route: `/accountant/customerInvoicing`

Steps:

1. Select the QA organization.
2. Create `QA Customer Limited`.
3. Create a customer invoice with at least one line item.
4. Select revenue, receivable, tax, and bank/payment accounts where required.
5. Save draft.
6. Post the invoice.
7. Refresh Open Receivables.
8. Record a partial payment.
9. Record a final payment.

Expected result:

- Customer is saved and available in the customer selector.
- Invoice totals, tax, and receivable impact are calculated correctly.
- Draft invoice can be posted.
- Posted invoice appears in receivables.
- Partial payment changes status to partially paid.
- Full payment changes status to paid.
- Aging receivables update after posting and payment.

API checkpoints:

- `GET /api/accountant/customers/org/{organizationId}`
- `GET /api/accountant/customer-invoices/org/{organizationId}`
- `GET /api/accountant/reports/aged-receivables/org/{organizationId}`

### 7.5 Receivables Collections

Route: `/accountant/receivablesCollections`

Steps:

1. Confirm overdue or open customer invoices exist.
2. Generate collection cases for the QA organization.
3. Open a collection case.
4. Assign the case to a collector.
5. Add collection activity notes.
6. Send or record a dunning activity.
7. Create a promise to pay.
8. Update promise status.
9. Escalate the case.
10. Put the customer on credit hold.
11. Release credit hold.
12. Close the case.

Expected result:

- Collection cases are generated from eligible invoices.
- Activities and promises are shown on the case.
- Escalation and close actions update status.
- Credit hold prevents or warns on new customer invoice processing where implemented.
- Customer statements can be generated and marked sent.

API checkpoints:

- `GET /api/accountant/receivables/collections/org/{organizationId}`
- `POST /api/accountant/receivables/collections/org/{organizationId}/generate`
- `POST /api/accountant/receivables/customer-statements/org/{organizationId}`

### 7.6 Purchase Invoice And Payables

Route: `/accountant/purchaseInvoice`

Steps:

1. Select the QA organization.
2. Create `QA Vendor Limited`.
3. Create a purchase invoice with one or more line items.
4. Save as draft.
5. Submit the invoice.
6. Approve the invoice.
7. Post the invoice.
8. Record a partial payment.
9. Record a final payment.
10. Review aged payables.

Expected result:

- Vendor is saved and available in the vendor selector.
- Purchase invoice follows status progression from draft to submitted, approved, posted, partially paid, and paid where supported.
- Aged payables update after invoice posting and payment.
- Invalid approval or posting actions fail gracefully.

API checkpoints:

- `GET /api/accountant/vendors/org/{organizationId}`
- `GET /api/accountant/purchase-invoices/org/{organizationId}`
- `GET /api/accountant/reports/aged-payables/org/{organizationId}`

### 7.7 Payables Automation

Route: `/accountant/payablesAutomation`

Steps:

1. Confirm one or more approved or posted purchase invoices are due.
2. Open Payables Automation.
3. Review payment schedules.
4. Submit a payment schedule.
5. Approve, reject, hold, reschedule, and cancel separate schedules as applicable.
6. Create a payment run using due schedules.
7. Submit the payment run.
8. Approve the payment run.
9. Execute the payment run.
10. Review payables forecast.
11. Generate a supplier statement.
12. Auto-match supplier statement lines.
13. Mark unmatched or disputed lines as disputed.
14. Complete supplier statement reconciliation.

Expected result:

- Due invoices appear in schedules or due payables.
- Schedule status changes are persisted.
- Payment run totals equal included schedule amounts.
- Executed payment run updates schedule/payment statuses.
- Forecast reflects upcoming cash requirements.
- Supplier statement workflow identifies matched, unmatched, and disputed items.

API checkpoints:

- `GET /api/accountant/payables/schedules/org/{organizationId}`
- `GET /api/accountant/payables/due/org/{organizationId}`
- `GET /api/accountant/payables/payment-runs/org/{organizationId}`
- `GET /api/accountant/payables/forecast/org/{organizationId}`
- `GET /api/accountant/payables/supplier-statements/org/{organizationId}`

### 7.8 Expenses

The expense workflow is exposed by API and should be tested if the current UI build includes an expenses surface or if API-level testing is in scope.

Steps:

1. Create an expense for the QA organization.
2. Add category, description, amount, vendor/payee, branch, and accounting details.
3. Submit the expense.
4. Approve the expense.
5. Reject a separate expense and confirm the rejection reason.
6. Post an approved expense.
7. Record payment against a posted expense.

Expected result:

- Expense status follows draft, submitted, approved/rejected, posted, and paid behavior.
- Expense posting creates appropriate accounting impact.
- Payment source and payment status are tracked.

API checkpoints:

- `GET /api/accountant/expenses/org/{organizationId}`
- `POST /api/accountant/expenses/org/{organizationId}`
- `PATCH /api/accountant/expenses/{expenseId}/submit`
- `PATCH /api/accountant/expenses/{expenseId}/approve`
- `PATCH /api/accountant/expenses/{expenseId}/post`
- `PATCH /api/accountant/expenses/{expenseId}/payments`

### 7.9 Cash And Bank Reconciliation

Route: `/accountant/cashAndBank`

Steps:

1. Create `QA Operating Bank`.
2. Select the bank account.
3. Import a bank statement CSV.
4. Confirm a reconciliation record is created.
5. Review the Verification Queue.
6. Run Auto Match.
7. Manually match at least one unmatched bank statement line to a candidate ledger entry.
8. Unmatch a line.
9. Re-match it.
10. Complete the reconciliation.

Expected result:

- Bank account is saved and selectable.
- Statement import creates statement lines.
- Auto-match matches eligible lines by date/amount/reference logic where supported.
- Manual match and unmatch update line status.
- Completed reconciliation is no longer editable except through allowed review actions.

API checkpoints:

- `GET /api/accountant/bank-accounts/org/{organizationId}`
- `GET /api/accountant/bank-reconciliations/org/{organizationId}/bank-account/{bankAccountId}`
- `GET /api/accountant/bank-reconciliations/{reconciliationId}`

### 7.10 Fixed Assets And Depreciation Journals

Routes:

- `/accountant/fixedAssets`
- `/accountant/depreciationJournals`

Steps:

1. Open Fixed Assets.
2. Confirm the page communicates that fixed assets are maintained outside AMS.
3. Refresh the depreciation import summary.
4. Open Depreciation Journals.
5. Upload a valid depreciation journal import file.
6. Upload an invalid file.
7. Refresh Import History.

Expected result:

- External fixed asset summary loads without error.
- Valid depreciation journal imports are accepted and listed.
- Invalid imports fail with useful validation feedback.
- Import history persists after refresh.

API checkpoints:

- `GET /api/accountant/depreciation-journals/org/{organizationId}/imports`
- `POST /api/accountant/depreciation-journals/org/{organizationId}/import-file`

## 8. CFO: Reporting And Controls

Log in as `CFO`.

### 8.1 Trial Balance

Route: `/cfo/trialBalance`

Steps:

1. Select the QA organization.
2. Generate a trial balance as of the current test date.
3. Review account-level debit and credit balances.
4. Save a trial balance snapshot.
5. Reload snapshots.

Expected result:

- Trial balance loads all active accounts with balances.
- Total debits equal total credits.
- Snapshot is saved and listed.

API checkpoints:

- `GET /api/cfo/trial-balance/org/{organizationId}`
- `POST /api/cfo/trial-balance/org/{organizationId}/snapshots`
- `GET /api/cfo/trial-balance/org/{organizationId}/snapshots`

### 8.2 Financial Reports

Route: `/cfo/financialReports`

Steps:

1. Select the QA organization.
2. Generate each supported report type:
   - Income Statement.
   - Balance Sheet.
   - Cash Flow.
   - Equity.
   - Budget Variance.
   - Custom report, if available.
3. Open report detail.
4. Submit a draft report for approval.
5. Approve one report.
6. Reject another report with comments.

Expected result:

- Reports generate from posted accounting data.
- Report totals agree with known posted transactions.
- Report status changes are persisted.
- Rejected report retains rejection reason.

API checkpoints:

- `POST /api/cfo/financial-reports/org/{organizationId}/generate`
- `GET /api/cfo/financial-reports/org/{organizationId}`
- `GET /api/cfo/financial-reports/{reportId}`

### 8.3 Budget Control

Route: `/cfo/budgetControl`

Steps:

1. Select the QA organization and budget year.
2. Create a budget.
3. Add at least one budget line for an expense account.
4. Submit the budget.
5. Approve the budget.
6. Activate the budget.
7. Post an expense or journal against the budgeted account.
8. Return to Budget Control.
9. Review allocated, spent, available, variance, historical trend, and alerts.

Expected result:

- Budget lifecycle status changes correctly.
- Budget line values persist.
- Spending against budget-controlled accounts updates the dashboard.
- Alerts appear when budget usage crosses configured thresholds.

API checkpoints:

- `GET /api/cfo/budgets/org/{organizationId}`
- `GET /api/cfo/budgets/org/{organizationId}/dashboard`
- `GET /api/cfo/budgets/{budgetId}/lines`

## 9. Auditor: Audit And Security Review

Log in as `AUDITOR`.

### 9.1 Audit Log Explorer

Route: `/auditor/auditLogExplorer`

Steps:

1. Select the QA organization.
2. Search audit logs after completing setup and transaction steps.
3. Filter by user.
4. Filter by action.
5. Filter by entity type.
6. Filter by date range.
7. Open paginated results.
8. Export audit logs.

Expected result:

- Audit records exist for key create, update, approval, posting, and security-relevant events.
- Filters return narrowed results.
- Pagination works without losing filter criteria.
- Export produces a downloadable audit record file.

API checkpoints:

- `GET /api/auditor/audit-logs/org/{organizationId}`
- `GET /api/auditor/audit-logs/org/{organizationId}/entity`
- `GET /api/auditor/audit-logs/org/{organizationId}/date-range`

### 9.2 Security Events

Route: `/auditor/securityEvents`

Steps:

1. Select the QA organization.
2. Review security event list.
3. Apply severity, type, status, and date filters.
4. Open a security event detail modal.
5. Acknowledge an unacknowledged event with comments.
6. Refresh the page.
7. Export security events.

Expected result:

- Security events load and show severity/status.
- Detail modal shows complete event data.
- Acknowledged event is marked acknowledged and records acknowledgement details.
- Export works for filtered results.

API checkpoints:

- `GET /api/auditor/security-events/org/{organizationId}`
- `GET /api/auditor/security-events/org/{organizationId}/unacknowledged`
- `PATCH /api/auditor/security-events/{eventId}/acknowledge`

## 10. Cross-Role Security Tests

Run these checks with each role.

| User role | Test | Expected result |
| --- | --- | --- |
| Unauthenticated | Open `/accountant/manualJournal` | Redirect to login |
| Accountant | Open `/cfo/approvalsHub` | Access denied or redirected |
| Accountant | Call `/api/cfo/manual-journals/org/1/pending` | Forbidden |
| CFO | Open `/accountant/manualJournal` | Access denied or redirected |
| Auditor | Open `/financeAdmin/chartOfAccounts` | Access denied or redirected |
| Finance Admin | Call `/api/accountant/manual-journals/org/1` | Forbidden |
| Admin | Open `/admin/users` | Allowed |
| Non-admin | Open `/admin/users` | Forbidden or redirected |

Also verify:

- Direct API access requires authentication.
- Role-specific API endpoints reject unauthorized roles.
- Logout invalidates the session.
- Browser Back after logout does not reopen protected pages.

## 11. End-To-End Accounting Assertions

After completing the full flow, validate these accounting outcomes:

1. Manual journal debits equal credits before submission.
2. Approved and posted manual journals create GL entries.
3. Customer invoice posting increases receivables and revenue.
4. Customer payment reduces receivables.
5. Purchase invoice posting increases expenses/assets and payables.
6. Vendor payment reduces payables.
7. Bank reconciliation can match bank statement lines to ledger activity.
8. Trial balance remains balanced after all postings.
9. Financial report totals agree with posted transactions.
10. Budget spent values update after controlled spend.
11. Audit logs show transaction creation, approval, rejection, posting, and security actions.

## 12. Negative And Edge Case Tests

### 12.1 Validation

Test these invalid inputs:

- Empty required fields.
- Invalid dates.
- Duplicate account code.
- Negative invoice amount where not allowed.
- Unbalanced journal.
- Missing organization selection.
- Invalid CSV file type.
- CSV with missing required columns.
- Unsupported tax/account combination.

Expected result:

- The application blocks invalid submission.
- Error messages are visible and meaningful.
- No partial or corrupt record is saved.

### 12.2 Status Transition Rules

Try invalid transitions:

- Post a draft journal.
- Approve an already approved item.
- Reject a posted item.
- Pay an unposted invoice.
- Complete a bank reconciliation with unmatched mandatory lines.
- Lock an open fiscal period before closing it, if not allowed by business rules.

Expected result:

- Invalid transitions are rejected.
- Existing record state is unchanged.
- Error response does not expose stack traces or sensitive details.

### 12.3 Data Persistence

For each major workflow:

1. Create or update a record.
2. Refresh the browser.
3. Log out and log in again.
4. Reopen the same page.

Expected result:

- Saved records remain available.
- Status and totals are unchanged.
- Lists and detail views show consistent data.

## 13. File Upload Test Data

### 13.1 Branch Import CSV

Use the downloaded template as the source of truth. A valid branch file should include the required branch fields expected by the template.

Checks:

- Valid CSV previews successfully.
- Invalid CSV returns row-level errors.
- Confirmed import creates branch records.

### 13.2 Chart Of Accounts Import CSV

Use the downloaded template as the source of truth.

Checks:

- Account code, account name, account type, and active status are validated.
- Duplicate account codes are rejected.
- Confirmed import creates accounts.

### 13.3 Bank Statement CSV

Use a small file with:

- Statement date.
- Reference.
- Description.
- Debit or credit amount.
- Running balance, if supported by the import format.

Checks:

- Import creates statement lines.
- Matching candidates appear for ledger transactions with similar amount/date/reference.

### 13.4 Depreciation Journal Import

Use a valid file with:

- Asset identifier or external reference.
- Depreciation period.
- Depreciation expense amount.
- Accumulated depreciation account.
- Expense account.

Checks:

- Valid rows import.
- Invalid rows fail validation.
- Import history records status and totals.

## 14. Automated Regression Suite

The repository includes backend tests that should be run before sign-off:

```powershell
.\mvnw.cmd test
```

Key test areas already present:

| Area | Example test class |
| --- | --- |
| Application startup | `AmsApplicationTests` |
| Manual journals | `ManualJournalServiceTest` |
| Fiscal periods | `FiscalPeriodServiceTest` |
| Customer invoices and collections | `CustomerInvoiceCollectionControlTest`, `ReceivablesCollectionServiceTest` |
| Expenses | `ExpenseServicePaymentSourceTest` |
| Payables | `PaymentScheduleServiceTest`, `PaymentRunServiceTest` |
| Tax | `TaxCalculationServiceTest` |
| Bank reconciliation | `BankReconciliationAcceptanceTest` |
| Approval rules | `ApprovalRuleServiceTest`, `ApprovalWorkflowServiceTest`, `ApprovalRulesAcceptanceTest` |
| Budget control | `BudgetControlServiceTest`, `GeneralLedgerServiceBudgetControlTest` |
| Audit and security | `AuditLogServiceTest`, `SecurityEventServiceTest` |
| Reporting controls | `Stage2ReportingControlsAcceptanceTest` |
| MVP acceptance | `MvpBackendAcceptanceCriteriaTest` |

Expected result:

- Maven test run completes successfully.
- Any failing test is reviewed before manual sign-off.

## 15. Final Sign-Off Checklist

Use this checklist before closing a test cycle:

| Area | Pass criteria |
| --- | --- |
| Authentication | Users can log in, land on correct role page, and log out |
| Authorization | Users cannot access other role workspaces or APIs |
| Organization setup | Organization and branches can be created and reloaded |
| Chart of accounts | Accounts can be created, edited, listed, and protected when used |
| Fiscal controls | Fiscal periods enforce close/lock behavior |
| Module controls | Module settings persist and affect enabled feature list |
| Approval rules | Rules can be created and used by approval workflows |
| Manual journals | Draft, submit, approve/reject, post, and GL review work |
| Customer invoices | Customer creation, invoice posting, payments, and aging work |
| Receivables collections | Cases, activities, dunning, promises, credit holds, and statements work |
| Purchase invoices | Vendor creation, invoice lifecycle, payments, and aged payables work |
| Payables automation | Schedules, payment runs, forecasts, and supplier statements work |
| Expenses | Expense lifecycle and payment tracking work |
| Bank reconciliation | Bank account, import, match, unmatch, auto-match, and complete work |
| Depreciation imports | Valid and invalid imports behave correctly |
| Trial balance | Debits and credits balance |
| Financial reports | Reports generate and follow review status lifecycle |
| Budget control | Budgets, lines, activation, spend tracking, and alerts work |
| Audit logs | Key business and security events are traceable |
| Security events | Events can be filtered, viewed, acknowledged, and exported |
| Error handling | Invalid actions show controlled errors without stack traces |

## 16. Defect Reporting Template

Use this format when logging issues:

```text
Title:
Environment:
User role:
Browser:
Route/page:
Test data used:
Steps to reproduce:
Expected result:
Actual result:
Screenshots or logs:
Severity:
Business impact:
```

## 17. Recommended Test Order Summary

1. Verify login and role redirects.
2. Configure organization, branch, fiscal period, chart of accounts, modules, taxes, and approval rules.
3. Create master data: customer, vendor, bank account.
4. Create and submit manual journal.
5. Approve or reject journal as CFO.
6. Post approved journal as Accountant.
7. Create customer invoice, post it, and record payment.
8. Generate receivables collection cases and test credit control.
9. Create purchase invoice, approve/post it, and record payment.
10. Test payment schedules, payment runs, supplier statements, and forecast.
11. Test expense lifecycle if enabled in the UI or through API.
12. Import bank statement and complete bank reconciliation.
13. Review fixed asset/depreciation import screens.
14. Generate trial balance and financial reports.
15. Create, approve, activate, and consume a budget.
16. Review audit logs and security events.
17. Run automated regression tests.
18. Complete final sign-off checklist.
