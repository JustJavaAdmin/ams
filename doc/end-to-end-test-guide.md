# AMS End-To-End Test Guide

This is a manual tester runbook for the current working tree, including the new e-invoicing, document, delivery, external API, webhook, and system administration code. Steps 1-23 provide the core finance scenario; the supplementary suites are required regression coverage. A described expected result is a test assertion, not evidence that the application has passed.

## Before starting: environment, dates, and evidence

1. Use a disposable UAT database and a dedicated Keycloak realm/client. Obtain the deployed URL, build/commit and working-tree change identifier, PostgreSQL connection, test accounts, and approved browser from the environment owner. Java 21 is required to build. Configure database and OAuth/Keycloak settings for UAT; do not rely on the checked-in connection defaults. No self-contained test database/profile is provided.
2. Provision separate single-group users for `/financeAdmin`, `/accountant`, `/cfo`, `/auditor`, `/systemAdmin`, and `/admin`, plus an authenticated user without an AMS group. The OIDC **groups claim must contain the exact slash-prefixed group**; a similarly named realm role alone is insufficient. Keep Accountant and CFO identities distinct.
3. Record actual execution start/end timestamps and server/browser time zones. `2026-08-27` below is a fixed **business scenario date**, not today's date. Audit/security/configuration timestamps use actual execution time. Filter those screens by the execution window, even when accounting entries are backdated.
4. The customer payment UI automatically uses the first returned active bank account and the browser's current date. For the fixed August scenario, use the explicit payment API request in Step 9. Alternatively shift **all** scenario dates, fiscal boundaries, invoice references, CSVs, report dates, and promise dates together into the current open quarter before starting; keep their relative ordering. Never mix the two approaches.
5. Start from a fresh database snapshot or use a fresh organization name, registration/TIN, branch code, and unique business references per run (record a `RUN_ID`). Capture returned IDs; never assume IDs or reuse an already locked organization. Create a second organization B with distinct accounts/customer/vendor/bank/branch for isolation tests.
6. For every case record: case/step ID, build, role, organization, prerequisites, inputs, expected/actual result, PASS/FAIL/BLOCKED, timestamps, record IDs, screenshot, and relevant request/response. Redact cookies, credentials, secrets, and personal data. Preserve downloaded files. Missing setup is BLOCKED, never PASS. A success toast alone is insufficient: refresh/reopen the record and inspect persisted state and accounting effects.
7. Use browser developer tools or an API client with the authorized UAT session for API-only cases. Requests below are relative to the deployed URL; send JSON with `Content-Type: application/json`. Substitute recorded IDs. Inspect HTTP status and response body; an OAuth redirect or HTML login page is not a successful JSON result. Do not assume bearer-token support: this application configures OAuth browser login, not an OAuth resource server.
8. Run the core flow without supplementary financial postings first. Capture its checkpoint totals before using separate fixture organizations for negative, tax, budget, fixed-asset, or year-end tests. Complete supplementary suites before sign-off. Run destructive close/lock cases last in their own fixtures when possible.

### Current implementation limits that affect sign-off

- `POST` invoice send generates a PDF and records a dispatch with `SUCCESS`/202; its response states that the email transport adapter is pending. This does **not** prove inbox delivery. `PATCH` invoice send only moves the business status to `SENT`.
- NRS submission prepares a `PENDING` submission and database payload archive. There is no live NRS submission/clearance worker in this tree. Signing and NRS connection tests report configuration readiness, not a live cryptographic or network test.
- Webhook test records a signed payload with `SUCCESS`/202; outbound HTTP dispatch is pending. Job schedules, client registrations, storage/encryption/retention settings are configuration records, not proof that workers, API scope enforcement, encryption, WORM, or purge operate.
- `/api/v1/invoices/**` and `/api/integrations/webhooks/**` currently fall through to generic authenticated access. Organization ownership/scoped client authorization is not enforced there. Record this as an access-control gap against the agreed policy; do not mark isolation or least privilege as passed merely because it matches current code.
- The original nine-account setup includes multiple current-asset accounts. Invoice posting chooses the first active account of the required type/subtype, rather than a configured AR control account; VAT also uses the first current-liability account. Inspect actual GL account IDs. Wrong control-account selection is a defect, even if debits equal credits.
- Keep external-delivery, NRS clearance, buyer-review deadlines/final locking, scheduled retries, credential revocation enforcement, and archive protection acceptance **BLOCKED/not implemented** until real transport/worker/security evidence is available. Never manually set `CLEARED` and count that as NRS acceptance.

## Finding your way around AMS

After signing in, use the menu on the left side of the screen. Instructions such as "Open **Cash & Bankbook**" mean click that exact menu label. Select the named organization after opening each page; do not assume the previous selection carries over. To change roles, click **Logout**, then sign in with the account named in the next step. The Admin user-management screen uses a **Sign out** control.

| Signed-in role | Menu labels used in this guide |
| --- | --- |
| Finance Admin | **Organization Setup**, **Chart of Accounts**, **Fiscal Config**, **Module Controls** |
| Accountant | **Manual Journal**, **Purchase Invoices**, **Payables Automation**, **Customer Invoices**, **Receivables Collections**, **Cash & Bankbook**, **Fixed Assets**, **Depreciation Journals** |
| CFO | **Approvals Hub**, **Trial Balance**, **Financial Reporting**, **Budget Control** |
| Auditor | **Audit Log Explorer**, **Security Events** |
| System Admin | **Technical Setup** |
| Admin | **User Management** screen, displayed after signing in |

The slash-prefixed group names in the environment setup are for the person preparing test accounts; they are not menu labels. Sections explicitly marked API, security, or automation checks require a technical tester. Ask the test coordinator to assign those checks to that person. API request paths are retained there because they identify the exact operation to test; they are not browser navigation instructions.

## Core finance scenario

## Step 1: Finance Admin Creates The Organization

Log in as your Finance Admin user, for example `fa.greenfield`, and select **Organization Setup**.

Click **Add Parent Company**, create this organization, and click **Create Organization**:

- Organization Name: `Greenfield Foods Limited`
- Registration Number: `RC-2026-AMS-001`
- Tax ID (TIN): `TIN-24860173`
- Legal Description: `Food processing and packaged goods distribution company`
- Country: `Nigeria`
- Address: `12 Admiralty Way, Lekki Phase 1`
- City: `Lagos`
- State: `Lagos`
- Postal Code: `105102`
- Phone: `+234 700 555 0198`
- Email: `finance@greenfieldfoods.ng`
- Active: checked

Expected result: the organization appears in the organization list and becomes selectable for branch setup.

## Step 2: Finance Admin Creates One Branch

Stay on **Organization Setup**, select `Greenfield Foods Limited` as the target company. In the branch form, create this branch and save it:

- Target Company: `Greenfield Foods Limited`
- Branch Office Name: `Lagos HQ Operations`
- Branch Accounting Code: `LG-HQ`
- Address: `12 Admiralty Way, Lekki Phase 1`
- City: `Lagos`
- State: `Lagos`
- Country: `Nigeria`
- Postal Code: `105102`
- Phone: `+234 700 555 0198`
- Email: `lagos.hq@greenfieldfoods.ng`
- Active: checked

Expected result: `Lagos HQ Operations (LG-HQ)` appears under the organization branches.

## Step 3: Finance Admin Creates The Chart Of Accounts

Open **Chart of Accounts**, select `Greenfield Foods Limited`, and create these accounts one after another with **Create Account**:

1. Account Code `1200`, Account Name `Accounts Receivable`, Account Type `ASSET`, Account Subtype `CURRENT_ASSET`, Normal Balance `DEBIT`, Description `Customer invoice control account`.
2. Account Code `1000`, Account Name `GTBank Operating Bank`, Account Type `ASSET`, Account Subtype `CURRENT_ASSET`, Normal Balance `DEBIT`, Description `Main NGN bank account`.
3. Account Code `1590`, Account Name `Accumulated Depreciation`, Account Type `ASSET`, Account Subtype `FIXED_ASSET`, Normal Balance `CREDIT`, Description `Contra asset depreciation reserve`.
4. Account Code `2000`, Account Name `Accounts Payable`, Account Type `LIABILITY`, Account Subtype `CURRENT_LIABILITY`, Normal Balance `CREDIT`, Description `Supplier invoice control account`.
5. Account Code `3000`, Account Name `Retained Earnings`, Account Type `EQUITY`, Account Subtype `RETAINED_EARNINGS`, Normal Balance `CREDIT`, Description `Equity and retained earnings account`.
6. Account Code `4000`, Account Name `Consulting Revenue`, Account Type `REVENUE`, Account Subtype `REVENUE`, Normal Balance `CREDIT`, Description `Service and consulting revenue`.
7. Account Code `6000`, Account Name `Office Supplies Expense`, Account Type `EXPENSE`, Account Subtype `OPERATING_EXPENSE`, Normal Balance `DEBIT`, Description `Office consumables and supplies`.
8. Account Code `7010`, Account Name `Bank Charges Expense`, Account Type `EXPENSE`, Account Subtype `OPERATING_EXPENSE`, Normal Balance `DEBIT`, Description `Bank fees and transfer charges`.
9. Account Code `7100`, Account Name `Depreciation Expense`, Account Type `EXPENSE`, Account Subtype `OPERATING_EXPENSE`, Normal Balance `DEBIT`, Description `Monthly depreciation expense`.

Expected result: all nine accounts appear as active accounts for the organization.

## Step 4: Finance Admin Opens The Fiscal Period And VAT Rule

Open **Fiscal Config**, select `Greenfield Foods Limited`, and save these fiscal settings:

- Base Currency: `NGN`
- Accounting Method: `Accrual`
- Approval Levels: `1`
- Start Month: `1`
- End Month: `12`
- Quarters: `4`
- Multi Currency Enabled: unchecked
- Require Approval For Transactions: checked
- Allow Negative Inventory: unchecked
- Notes: `Q3 2026 UAT configuration saved on 2026-08-27`

In **Create Fiscal Period**, create the open Q3 period:

- Year: `2026`
- Quarter: `Q3 - Jul to Sep`
- Start Date: `2026-07-01`
- End Date: `2026-09-30`

In **Create Tax Rule**, create the VAT rule:

- Code: `NG-VAT-75`
- Name: `Nigeria VAT 7.5%`
- Type: `VAT`
- Rate %: `7.5`
- Description: `Standard Nigeria VAT rate for taxable local supplies`

Expected result: fiscal settings save successfully, Q3 2026 appears as `OPEN`, and the VAT rule appears in the tax table.

## Step 5: Finance Admin Initializes Module Controls And Approval Rule

Open **Module Controls**, select `Greenfield Foods Limited`, and click **Initialize Defaults** if no modules exist.

Confirm these module controls are enabled: `GENERAL_LEDGER`, `ACCOUNTS_RECEIVABLE`, `ACCOUNTS_PAYABLE`, `PAYMENTS`, `REPORTING`, `BUDGETING`, `BANKING`, `FIXED_ASSETS`, `APPROVALS`, and `AUDIT`. If any of them is disabled, enable it and enter this reason when asked:

`Required for Greenfield Foods Q3 2026 end-to-end finance test.`

Create this approval rule:

- Rule Name: `CFO approval for manual journals over NGN 1`
- Module: `GENERAL_LEDGER`
- Transaction Type: `MANUAL_JOURNAL`
- Min Amount: `1`
- Max Amount: leave blank
- Account Type: `Any account`
- Department: leave blank
- Required Approvals: `1`
- Priority: `10`
- Approver Role: `CFO`
- Notes: `All manual journals in this UAT flow should reach CFO approval.`

Expected result: required module controls are active and the approval rule appears in the approval rules table. Default initialization does not create every enum module (notably PAYMENTS). If a required control is absent, record the missing row and have the UAT owner provision it through the supported fixture/service setup; do not assume initialization enabled it.

For deterministic core workflow, set `requiresApproval=true` on GENERAL_LEDGER, ACCOUNTS_PAYABLE, PAYMENTS, REPORTING, and BUDGETING where present; retain audit trails and a transaction limit above 1,500,000. Verify the saved settings. If the UI cannot expose a setting, use the existing module update API with its recorded module ID or mark setup BLOCKED. Do not assume the fiscal configuration checkbox alone governs every module.

## Step 6: Accountant Creates The Bank Account

Log out, then log in as your Accountant user, for example `acct.greenfield`, and select **Cash & Bankbook**.

Select `Greenfield Foods Limited`, click **New Bank Account**, create this bank account, and click **Save Bank Account**:

- Bank name: `GTBank`
- Account number: `0123456789`
- Account holder: `Greenfield Foods Limited`
- Branch code: `LG-HQ`
- Currency: `NGN`
- Opening balance: `1000000`
- Linked cash/bank GL account: `1000 - GTBank Operating Bank`
- Notes: `Main operating account for Lagos HQ`

Expected result: `GTBank - 0123456789` becomes available in the Bank Account dropdown.

## Step 7: Accountant Posts A Purchase Invoice

Open **Purchase Invoices**, select `Greenfield Foods Limited`, then click **New Vendor** and save this vendor:

- Vendor code: `VEN-LAG-001`
- Legal name: `Lagos Office Supplies Ltd`
- Email: `accounts@lagosofficesupplies.ng`
- Phone: `+234 803 555 0144`
- Tax ID: `TIN-77440125`
- Payment terms: `Net 15`
- Billing address: `18 Allen Avenue, Ikeja, Lagos`

Create the purchase invoice with these details and click **Submit, Approve & Post**:

- Vendor Account: `VEN-LAG-001 - Lagos Office Supplies Ltd`
- Invoice Reference Number: `PI-2026-08-001`
- Issue Date: `2026-08-20`
- Due Date: `2026-09-04`
- Tax Code Selector: `No tax`
- Line item description: `Printer paper and pantry supplies`
- Expense account: `6000 - Office Supplies Expense`
- Quantity: `1`
- Unit price: `185000`

Expected result: purchase invoice `PI-2026-08-001` is posted, and an aged payable/payment schedule is created for `NGN 185,000`.

## Step 8: Accountant Approves The Payable Schedule And Executes A Payment Run

Open **Payables Automation**, select `Greenfield Foods Limited`, and stay on **Schedules & Forecast**.

Set the schedule filters to:

- From: `2026-08-01`
- To: `2026-09-30`
- Status: `All statuses`

Click **Refresh**, find the schedule for `PI-2026-08-001`, and click **Approve**. This first action on a PLANNED schedule submits it for evaluation. If it becomes `READY_FOR_APPROVAL`, approve again; proceed only once it is `APPROVED`.

Go to the **Payment Runs** tab and create a payment run:

- Bank account: `GTBank - 0123456789`
- Run date: `2026-08-27`
- Cutoff date: `2026-09-04`
- Explicitly select only the recorded schedule for `PI-2026-08-001`. If selection is unavailable, confirm that exactly one eligible schedule exists before creating the run; otherwise stop and isolate the fixture.

Click **Create payment run**, open the run detail, and verify one item totaling 185,000 before **Submit**. Expect `READY_FOR_APPROVAL`, then **Approve** and **Execute**. If submission automatically becomes APPROVED because approval configuration differs, record the configuration mismatch; do not invent an extra approval step.

Expected result: the run is `EXECUTED`, its sole item and schedule are `PAID`, invoice amountPaid is 185,000 and outstanding is zero. Payment GL is Dr AP 185,000 / Cr bank 185,000. Refresh using status PAID explicitly: the date-range/all-status schedule query can exclude terminal schedules. Execution records accounting payment; verify external bank settlement separately if required.

## Step 9: Accountant Creates And Part-Pays A Customer Invoice

Open **Customer Invoices**, select `Greenfield Foods Limited`, then click **New Customer** and save this customer:

- Customer code: `CUS-ABJ-001`
- Legal name: `Abuja Retail Mart Ltd`
- Email: `payables@abujaretailmart.ng`
- Phone: `+234 809 555 0112`
- Tax ID: `TIN-55900218`
- Payment terms: `Net 10`
- Credit limit: `1000000`
- Billing address: `Plot 42 Aminu Kano Crescent, Wuse 2, Abuja`

Create the customer invoice and click **Generate & Post Customer Invoice**:

- Customer Account Selector: `CUS-ABJ-001 - Abuja Retail Mart Ltd`
- Payment Terms: `Net 10 Days`
- Invoice Date: `2026-08-01`
- Due Date: `2026-08-10`
- Applied Tax Rules: `No tax`
- Line service description: `August packaged food supply contract`
- Revenue account: `4000 - Consulting Revenue`
- Quantity: `1`
- Unit price: `450000`

Record the generated `AR-...` invoice number and invoice ID. For the fixed-date scenario, as Accountant call:

```http
PATCH /api/accountant/customer-invoices/{invoiceId}/payments
Content-Type: application/json

{"amount":200000,"bankAccountId":BANK_ID,"paymentDate":"2026-08-27","notes":"UAT partial receipt"}
```

Replace `BANK_ID` with the numeric ID from Step 6. Expect HTTP 200. For a fully shifted current-date scenario only, use **Record Payment**, enter `200000`, and verify the request uses the intended bank ID and date (the UI has no bank/date selection).

Expected result: status `PARTIALLY_PAID`, total 450,000, amountPaid 200,000, outstanding 250,000; 17 days overdue as of 2026-08-27 (bucket 1-30). Receipt GL is Dr bank 200,000 / Cr AR 200,000. The initial invoice GL must be Dr AR 450,000 / Cr revenue 450,000. This core invoice has no explicit transaction classification and is not an NRS test fixture.

## Step 10: Accountant Generates Collections And Customer Statement

Open **Receivables Collections**, select `Greenfield Foods Limited`, and set **As Of Date** to `2026-08-27`.

Click **Generate**, open the collection case for `Abuja Retail Mart Ltd`, and perform these actions:

1. Click **Assign**, enter Collector `acct.greenfield`, Notes `Assigned for same-day follow-up on overdue Abuja invoice.`, then Apply.
2. Click **Activity**, choose Activity Type `CALL`, Subject `Payment follow-up`, Notes `Customer confirmed balance and requested 7 days to clear the outstanding amount.`, then Apply.
3. Click **Promise**, enter Promised Amount `250000`, Promised Date `2026-09-03`, Notes `Customer promised full balance by bank transfer.`, then Apply.
4. Click **Dunning** to record a formal reminder.

Switch to **Customer Statements**, generate a statement for:

- Customer: `CUS-ABJ-001 - Abuja Retail Mart Ltd`
- Statement date: `2026-08-27`
- Start date: `2026-08-01`
- End date: `2026-08-27`

Open the generated statement and click **Send**.

Expected result: one case shows assignment, call activity, promise of 250,000 dated 2026-09-03, and dunning activity; the statement has closing balance 250,000 and SENT status. Inspect its opening balance and invoice/payment lines. SENT and dunning activity records do not establish actual email delivery.

## Step 11: Accountant Creates A Manual Journal For Bank Charges

Open **Manual Journal**, select `Greenfield Foods Limited`, and click **New Journal**.

Create this journal:

- Entry Description: `GTBank August account maintenance charge`
- Transaction Date: `2026-08-27`
- Branch: `Lagos HQ Operations (LG-HQ)`

Add two lines:

1. Account `7010 - Bank Charges Expense`, Debit `12500`, Credit `0`, Narration `August account maintenance charge`, Department `FIN`, Project `OPS`.
2. Account `1000 - GTBank Operating Bank`, Debit `0`, Credit `12500`, Narration `Bank deduction for monthly account charge`, Department `FIN`, Project `OPS`.

Confirm total debit and total credit are both `NGN 12,500`, click **Save Draft**, then click **Submit to CFO**.

Expected result: the journal status changes to `SUBMITTED` and it appears in the CFO approval queue.

## Step 12: CFO Approves The Manual Journal

Log out, then log in as your CFO user, for example `cfo.greenfield`, and select **Approvals Hub**.

Select `Greenfield Foods Limited`, find the journal `GTBank August account maintenance charge`, and select **Review**. Confirm:

- Date: `2026-08-27`
- Debit total: `NGN 12,500`
- Credit total: `NGN 12,500`
- Debit account: `7010 - Bank Charges Expense`
- Credit account: `1000 - GTBank Operating Bank`

Click **Approve**, enter Approval Note `Approved for August 2026 bank fee posting.`, and confirm.

Expected result: the journal leaves the pending queue and becomes available for posting by the Accountant.

## Step 13: Accountant Posts The Approved Journal To GL

Log out, then log back in as `acct.greenfield`, open **Manual Journal**, and select `Greenfield Foods Limited`.

Open the approved journal `GTBank August account maintenance charge`, click **Post to GL**, then confirm **Post Journal**.

Expected result: the journal status becomes `POSTED`, and the General Ledger entries modal shows the two posted rows.

## Step 14: Accountant Reconciles The Bank Charge

Create a small CSV file on your machine named `gtbank-2026-08-27.csv` with this exact content:

```csv
date,amount,description,reference
2026-08-27,-12500,GTBank August account maintenance charge,BANK-FEE-AUG-2026
```

Open **Cash & Bankbook**, select:

- Organization: `Greenfield Foods Limited`
- Bank Account: `GTBank - 0123456789`

In **Import Statement**, enter:

- Statement Date: `2026-08-27`
- Opening: `1000000`
- Closing Balance: `987500`
- Statement File: upload `gtbank-2026-08-27.csv`

Click **Process Statement Import**. If the row does not auto-match, load candidates on the unmatched line, select the posted GL entry for `NGN -12,500`, and click **Match**. Then click **Complete**.

Expected result: imported lines `1`, matched lines `1`, cleared amount `-12500`, unresolved difference `0.00`, and status `COMPLETED`. This is an isolated bank-fee matching test, not a full bank reconciliation: the CSV omits supplier payment and customer receipt, and the bank master opening balance does not itself post opening GL. Do not use 987,500 as the final GL bank balance.

## Step 15: Accountant Imports A Depreciation Journal

Create a small CSV file named `depreciation-2026-08.csv` with this exact content:

```csv
accountCode,debitAmount,creditAmount,description,referenceNumber,branchCode,departmentCode,projectCode,assetCode
7100,30000,0,August depreciation for cold-room equipment,DEP-AUG-2026,LG-HQ,OPS,FA,COLD-ROOM-01
1590,0,30000,Accumulated depreciation for cold-room equipment,DEP-AUG-2026,LG-HQ,OPS,FA,COLD-ROOM-01
```

Open **Depreciation Journals**, select `Greenfield Foods Limited`, and enter:

- External System: `FixedAssetApp`
- External Batch ID: `DEP-2026-08-GFL`
- Journal Date: `2026-08-27`
- Description: `August 2026 depreciation import`
- Branch Code: `LG-HQ`
- CSV or XLSX File: upload `depreciation-2026-08.csv`
- Submit for approval after import: checked

Click **Import Depreciation Journal**.

Expected result: import history shows batch `DEP-2026-08-GFL`, total debit `NGN 30,000`, total credit `NGN 30,000`, and a linked manual journal in `SUBMITTED` status.

## Step 16: CFO Approves The Depreciation Journal

Log in as `cfo.greenfield`, open **Approvals Hub**, and select `Greenfield Foods Limited`.

Open the submitted depreciation journal from batch `DEP-2026-08-GFL`, confirm it has:

- Debit `7100 - Depreciation Expense` for `NGN 30,000`
- Credit `1590 - Accumulated Depreciation` for `NGN 30,000`

Click **Approve**, enter Approval Note `Approved monthly depreciation import from FixedAssetApp.`, and confirm.

Expected result: the depreciation journal is approved and ready for Accountant posting.

## Step 17: Accountant Posts The Depreciation Journal

Log in as `acct.greenfield`, open **Manual Journal**, select `Greenfield Foods Limited`, and open the approved depreciation journal.

Click **Post to GL**, then confirm **Post Journal**.

Expected result: the depreciation journal status becomes `POSTED` and the GL modal shows two posted depreciation rows.

## Step 18: CFO Creates And Activates A Budget

Log in as `cfo.greenfield`, open **Budget Control**, select `Greenfield Foods Limited`, and set the year selector to `2026`.

Create this budget:

- Budget code: `BUD-OPS-2026`  
- Budget name: `2026 Operations Expense Budget`
- Year: `2026`
- Total budget: `1500000`
- Start date: `2026-01-01`
- End date: `2026-12-31`
- Department: `Operations`

Add this budget line:

- Budget: `BUD-OPS-2026 - 2026 Operations Expense Budget`
- Account: `6000 - Office Supplies Expense`
- Allocated amount: `600000`
- Department code: `OPS`
- Project code: `GENERAL`
- Branch code: `LG-HQ`

Use the budget workflow buttons to click **Submit**, then **Approve**, then **Activate**.

Expected result: the budget status becomes active and the dashboard shows the operations expense budget line.

## Step 19: CFO Generates Trial Balance And Financial Reports

Open **Trial Balance**, select `Greenfield Foods Limited`, enter **As Of Date** `2026-08-27`, and click **Generate Report**.

Confirm total debits equal total credits, then click **Save Snapshot** and **Confirm Save**.

Open **Financial Reporting**, select `Greenfield Foods Limited`, and set:

- From date: `2026-08-01`
- To date: `2026-08-27`

Generate these reports one by one:

1. Click **Generate Income Statement**, review revenue and expense lines, click **Submit**, then click **Approve**.
2. Click **Generate Balance Sheet**, review assets, liabilities, equity, and variance, then export the report CSV.
3. Click **Generate Budget Variance**, confirm the budget line and zero recorded consumption: this budget was activated after the purchase posting; no retroactive consumption is expected. Test actual enforcement separately in Suite B.
4. Submit and approve the Balance Sheet and Budget Variance reports too. Do not leave DRAFT/PENDING_REVIEW reports; they block fiscal close. If submission automatically approves a report, record the configured workflow and verify APPROVED.

Expected result: trial balance snapshot saves, the income statement is approved, report history contains generated reports, and CSV export downloads successfully.

## Step 20: Auditor Reviews Audit Logs

Log out, then log in as your Auditor user, for example `audit.greenfield`, and select **Audit Log Explorer**.

Select `Greenfield Foods Limited`, filter:

- Start Date: actual execution start date
- End Date: actual execution end date
- Entity Type: `Manual Journal`
- Action: `APPROVE`

Confirm the approval records for the bank charge journal and depreciation journal are visible. Then change:

- Entity Type: `General Ledger`
- Action: `POST`

Confirm posted GL activity is visible, then click **Export Audit Log** for each filter set separately (or clear entity/action filters and export the execution window). A filtered General Ledger export alone cannot prove ManualJournal approval records.

Expected result: exported audit log includes the approval and posting evidence from the test flow.

## Step 21: Auditor Reviews And Acknowledges Security Events

Open **Security Events**, select `Greenfield Foods Limited`, and filter:

- Status: `Unacknowledged`
- Start Date: actual execution start date
- End Date: actual execution end date

Use the module initialization/change event from Step 5 as the deterministic fixture. Open each event created by module control changes or report generation, click **View**, confirm the event type, severity, timestamp, description, IP address, and user agent are populated where available, then click **Acknowledge** and confirm.

After acknowledgement, filter:

- Status: `Acknowledged`

Click **Export Security Events**.

Expected result: acknowledged events show the auditor user and acknowledgement timestamp, and CSV export downloads successfully.

## Step 22: Finance Admin Closes And Locks The Period

Log back in as `fa.greenfield`, open **Fiscal Config**, and select `Greenfield Foods Limited`.

Before closing, ensure no DRAFT/SUBMITTED/APPROVED unposted journals; no DRAFT/APPROVED/SENT unposted customer invoices; no DRAFT/SUBMITTED/APPROVED unposted purchases; no DRAFT reconciliations; no DRAFT/READY_FOR_APPROVAL/APPROVED/PARTIALLY_EXECUTED runs; no pending approvals; and no overlapping DRAFT/PENDING_REVIEW financial reports remain. Resolve legitimate work; do not bypass the guards. Supplementary test fixtures must use separate organizations or be resolved first.

Find fiscal period `2026 Q3`, click **Close this period**, then confirm **Close Period**. After it changes to `CLOSED`, click **Lock this period**, then confirm **Lock Period**.

Expected result: Q3 2026 changes from `OPEN` to `CLOSED` to `LOCKED`, and no further posting should be allowed into `2026-07-01` through `2026-09-30`.

## Step 23: Final Negative Control Check

Log in as `acct.greenfield`, open **Manual Journal**, select `Greenfield Foods Limited`, and attempt a new journal dated `2026-08-27`:

- Entry Description: `Blocked posting test after Q3 lock`
- Branch: `Lagos HQ Operations (LG-HQ)`
- Line 1: `6000 - Office Supplies Expense`, Debit `1000`, Credit `0`, Narration `Period lock negative test`, Department `OPS`, Project `GENERAL`
- Line 2: `1000 - GTBank Operating Bank`, Debit `0`, Credit `1000`, Narration `Period lock negative test`, Department `OPS`, Project `GENERAL`

Attempt **Save Draft**. Current `ManualJournalService` checks for an open period at creation, so rejection can occur here. No new journal/GL should persist. If a journal unexpectedly saves, record that result and test submission/posting separately; a draft saving is not proof of successful posting.

Expected result: AMS should block posting into the locked period. Record the exact validation message shown by the app as evidence of fiscal period control.
## Core checkpoint: independent accounting oracle

Before supplementary tests, verify these movements for the core organization, assuming AR and AP actually posted to the intended accounts. Do not accept a balanced trial balance as a substitute for verifying account selection. GL opening balances are zero in this fixture; the bank master balance of 1,000,000 is metadata, not an opening journal.

| Account | Closing debit | Closing credit |
| --- | ---: | ---: |
| 1200 AR | 250,000 | 0 |
| 1000 Bank | 2,500 | 0 |
| 1590 Accumulated depreciation | 0 | 30,000 |
| 2000 AP | 0 | 0 |
| 3000 Retained earnings | 0 | 0 |
| 4000 Revenue | 0 | 450,000 |
| 6000 Supplies expense | 185,000 | 0 |
| 7010 Bank charges | 12,500 | 0 |
| 7100 Depreciation expense | 30,000 | 0 |
| **Net trial-balance totals** | **480,000** | **480,000** |

Gross posted debits and credits are each 1,062,500 (purchase 185,000 + supplier payment 185,000 + invoice 450,000 + receipt 200,000 + fee 12,500 + depreciation 30,000). A report displaying gross movements uses this total; a report displaying net account balances uses 480,000. Expected income is revenue 450,000 minus expenses 227,500 = **222,500 profit**. Net assets are 222,500 after accumulated depreciation; check how the balance sheet presents current earnings and any variance. No unexplained balancing amount is acceptable. Preserve invoice, payment run, journal, import, reconciliation, snapshot, report, and audit IDs together.

## Supplementary regression suites

Run in separate disposable fixture organizations with open periods, enabled modules, correct accounts, and recorded approval settings. Unless specified otherwise, a rejected operation must return a useful 4xx response (never 500), leave business state, GL rows, balances, and budget consumption unchanged, and remain rejected after refresh. For UI-only validation, also send the invalid request through the API to verify server enforcement. Record actual behavior against the assertion, including defects already indicated below; do not convert missing safeguards into passing tests.

### Suite A: authentication, role boundaries, and organization isolation

| ID | Procedure | Required evidence / assertion |
| --- | --- | --- |
| A01 | Open the AMS home page signed out; open each protected page/API; complete login and click **Logout**; revisit a protected page in the logged-out session. | Public landing loads; protected resources require authentication; logout invalidates access. Check responses without following redirects. |
| A02 | Log in separately with each exact group from setup; open the AMS home page. | Expected landing screens: Admin **User Management**; system admin **Technical Setup**; accountant **Manual Journal**; auditor **Audit Log Explorer**; CFO **Approvals Hub**; Finance Admin **Organization Setup**. A no-group user gets no privileged page access. |
| A03 | For every role, try each other role's page and a corresponding write API, using a valid body copied from the permitted role's request. | Wrong-role requests return 403 with no mutation. ADMIN is not an implicit superuser for all groups. Multi-group home precedence is admin, system admin, accountant, auditor, CFO, Finance Admin; check separately if supported. |
| A04 | As non-Finance-Admin, GET organizations/branches, then try POST/PUT organization/branch. | Authenticated reads are currently allowed; writes require Finance Admin. Do not infer per-organization entitlement from the role alone. |
| A05 | Use organization A invoice with B customer, vendor, bank, branch, line account, or tax ID one at a time; also test inactive related records. | Reject mismatched/inactive references where applicable; verify no mixed-organization ledger or partial object. Bank creation currently lacks explicit organization/type validation of its linked GL account: test this directly and raise any acceptance as a defect. |
| A06 | Under the agreed organization entitlement policy, request A objects by their IDs as a B-only user, including journal/report/invoice/document/dispatch/submission/webhook IDs; try unfiltered external invoice search and changing organizationId. | No unauthorized disclosure or mutation. Current generic external API/webhook access and ID-based lookups need explicit security review; if entitlements are not defined, mark isolation BLOCKED pending policy rather than PASS. |
| A07 | Repeat representative write requests with an expired session; test a controlled cross-origin form submission in UAT. | No unauthenticated mutation. CSRF protection is disabled in the security configuration; record the session-cookie/CSRF risk for security review. Do not claim CSRF-token rejection. |

### Suite B: financial controls and workflow failures

| ID | Procedure | Expected result |
| --- | --- | --- |
| B01 | Repeat account/customer/vendor/tax/approval-rule codes or names within the same organization; attempt duplicate invoice references. Refresh counts. | Duplicate rules enforced with no second record; test legitimate same-code reuse across organizations separately where uniqueness is organization-scoped. Invoice draft renumbering also needs duplicate validation; report inconsistent handling. |
| B02 | Create fiscal period with missing dates, end before start, duplicate year/quarter, or overlap; test dates just before start and after end. | Invalid/overlapping periods rejected; out-of-period postings blocked. Boundary dates inside an OPEN period accepted. |
| B03 | Journal with one line, unequal totals, negative debit/credit, both debit and credit on one line, zero-only line, inactive or foreign account. Then correct it. | Invalid create/line/submit operation rejected at the applicable stage; corrected balanced two-line draft submits once. |
| B04 | Try approving DRAFT, posting SUBMITTED, editing SUBMITTED/APPROVED/POSTED lines, and approving one's own journal using a controlled multi-group user. Reject a separate submitted journal with a reason. | Invalid transitions and self-approval blocked; rejected journal has REJECTED state and reason, no GL. CFO and Accountant core identities remain separate. |
| B05 | Repost a POSTED journal/invoice; re-execute an EXECUTED run; double-click and send two concurrent identical requests on separate fixtures. | One set of source GL rows/payment effects only. Capture source type/ID, response statuses, and row counts. Retry a request with an unknown network outcome only after checking persisted state. |
| B06 | Invoice/purchase/expense: empty lines, nonpositive quantity/unit price, foreign/inactive line account, draft edit, non-draft edit, invalid transition, and attempted posted-document cancellation where an endpoint exists. | Valid draft edits persist under the same identity; invalid financial data and unauthorized changes rejected. Cancellation must not silently erase the business document while retaining unreversed GL. Current customer delete helper sets CANCELLED without a status/reversal guard; raise exposure if reachable. |
| B07 | Before payment, send zero, negative, above-balance, missing bank, inactive/foreign bank, and closed-period payment date. On a separate 450,000 invoice pay 200,000 then 250,000. | Invalid requests have no payment/GL effects; valid statuses PARTIALLY_PAID then PAID, outstanding zero; further payment rejected. Confirm AP direct payments update remaining schedules and do not duplicate run payments. |
| B08 | Hold/reschedule/cancel a payable schedule; attempt selecting HELD/unapproved/foreign/paid/already-in-run schedules; create run with no eligible items. Submit, reject/cancel a separate run; try execute before approval. | Invalid selection/execution blocked. Rescheduling returns to PLANNED; held/cancelled schedules excluded from eligible selection. Check forecasts by explicit date/status and remaining amount. |
| B09 | Activate a new 600,000 budget line for account 6000 **before** posting. Enable hard stop and warning threshold 90%. Post 185,000, then 355,000 through valid approved sources; then attempt another 60,001. | Consumption 185,000 then 540,000 (90%, warning decision); 60,001 blocked, consumption remains 540,000 and no partial GL. Exactly 60,000 may reach 600,000 on a separate fixture. Test multiple expense lines totaling more than availability to catch per-line rather than aggregate checks. |
| B10 | Disable the source module or lower its amount limit below the transaction; attempt posting, then restore it and retry. Try disabling GENERAL_LEDGER after posted entries exist. | Posting blocked with no GL/budget mutation; restored valid operation succeeds once. GENERAL_LEDGER disable rejected while posted GL exists. Do not assume every enum module has identical enforcement. |
| B11 | Place customer credit hold with a reason; try generating and posting another invoice; release hold and retry. Generate collections twice; reconcile promises before/after promised date, then pay outstanding on a separate fixture. | Hold blocks generation/posting; release permits it. No duplicate active case for same invoice. Promise/case changes reflect kept/broken/resolved state and actual balance; core outstanding fixture remains untouched. |
| B12 | Generate statements with reversed dates or another organization's customer. For valid statement compare opening + invoices - receipts = closing. Repeat report/aging at due date and +1, +30, +31, +60, +61, +90, +91 days. | Invalid request rejected; buckets CURRENT, 1-30, 31-60, 61-90, 90+ follow boundaries. Check fully paid/cancelled exclusions and future/draft invoice inclusion. Historical as-of output must not silently use later payments; current aging uses current amountPaid, so document this limitation/defect. |

### Suite C: imports, reconciliation, reports, and period close

| ID | Procedure | Expected result |
| --- | --- | --- |
| C01 | Download branch/COA import templates in organization setup/COA. Upload CSV with one valid row; validate, inspect preview, confirm once, and reload. Repeat with duplicate policy options, malformed header, empty file, wrong extension, invalid enum, invalid account/branch references, and mixed valid/invalid rows. | Validation alone creates no master data; only fully validated imports confirm. Error CSV identifies row/reason; repeated confirmation cannot create duplicate records. Bulk master import supports CSV, not XLSX. Verify each selected duplicate policy against persisted rows/counts. |
| C02 | Repeat depreciation import using the same external system/batch ID; upload unbalanced totals, negative/both-sided rows, unknown/inactive account, foreign branch, empty/malformed CSV and unsupported file. Repeat a valid XLSX equivalent. | Duplicate batch rejected; invalid imports create no partial import/journal/lines/approval. Valid CSV and XLSX produce equivalent balanced journals; posting updates linked import to POSTED once. Preserve original files and hashes. |
| C03 | Import bank statement CSV and equivalent XLSX. Try wrong-bank GL, wrong amount, unposted GL, previously matched GL, and a line belonging to another reconciliation. Unmatch/rematch a DRAFT line. | Only valid same-bank posted exact-amount GL can match; a GL row is not reused. Draft recalculates counts and cleared amount. Import retries must not silently double-match or create ambiguous evidence. |
| C04 | Complete reconciliation with no lines, unmatched lines, then all lines matched but closing-opening different from cleared amount. Finally correct and complete; attempt edit/unmatch after completion. | Completion blocked until lines exist, all match, and difference is zero; completed record immutable. Difference is closing minus opening minus matched signed amounts, not a full independent GL-to-bank balance assertion. |
| C05 | Generate reports with reversed/missing dates, empty organization data, and correct core data. Save/reopen trial snapshot, generate later postings in separate fixture, then reopen snapshot. Submit/approve/reject reports; attempt repeated finalization. | Invalid dates rejected; empty reports render without errors; snapshots remain historical; workflow transitions and approver metadata persist. All values reconcile to independently calculated GL, not just to another screen. |
| C06 | Export audit/security/report CSV for distinct filters, then open the actual file. Test commas, quotes, newlines, Unicode, and spreadsheet formula-like descriptions. | Header/row count/filter/date/amount/escaping correct; no unrelated organization's data or executable spreadsheet formulas. Confirm export is CSV, not an HTML login/error page. |
| C07 | In isolated organization, leave one blocker at a time: unposted journal, unposted customer/purchase invoice, DRAFT reconciliation, unresolved run, pending approval, DRAFT/PENDING_REVIEW report. Attempt close; resolve it through normal workflow and repeat. | Each blocker prevents close and leaves period OPEN. Once all are resolved, close succeeds. Test lock-before-close and repeated close/lock against the agreed transition policy; current services do not guard source status and can change LOCKED back to CLOSED. Record that state regression as a defect if terminal locking is required; no bypass of blockers or return to OPEN is acceptable. |
| C08 | After CLOSED, then LOCKED, attempt journal create/submit/post, invoice/purchase post, receipt/supplier payment and depreciation import at period start/end. | Every posting path rejects closed/locked dates without partial GL. A new OPEN next quarter continues to work. Keep locks last; no UI reopen path is assumed. |
| C09 | Open **Fixed Assets**, select the core organization, inspect the external-system ownership notice and import history, then follow **Depreciation Imports**. Switch to organization B. | History agrees with Step 15/17 and excludes A imports when B is selected. The page is an external-depreciation overview, not an asset creation/disposal/calculation UI. Legacy FixedAssetService and YearEndCloseService have no exposed controller operations in this tree; tester E2E coverage for those services is unavailable, not PASS. |

**C10 Expense API flow:** In a separate open organization, as Accountant POST `/api/accountant/expenses/org/{organizationId}` with a unique expenseNumber, payeeName `UAT Courier`, expenseDate/dueDate in the open period, and lineItems `[{"chartAccountId":EXPENSE_ACCOUNT_ID,"description":"Delivery","quantity":1,"unitPrice":10000}]`. Expect 201/DRAFT. GET `/api/accountant/expenses/{id}`, PUT a corrected draft, then PATCH `/{id}/submit`, `/{id}/approve` if SUBMITTED, and `/{id}/post` beneath `/api/accountant/expenses`. Expect POSTED and Dr expense 10,000 / Cr AP 10,000. For a fixture whose PAYMENTS approval requirement is disabled, PATCH `/{id}/payments` with amount 10,000, bankAccountId and explicit paymentDate; expect PAID and Dr AP / Cr bank 10,000 with source type EXPENSE_PAYMENT. With payment approval required, direct payment must reject with the use-payment-run message; do not assume expense schedules exist. Repeat B06/B07 negatives and reject a separate submitted expense using `{"reason":"UAT rejection"}` at PATCH `/{id}/reject`. Verify state and GL after each operation.

### Suite D: invoice documents, NRS payloads, and dispatch records

These cases are API-led because the current invoice UI does not expose the compliance/classification fields. Use Accountant session and `/api/accountant/customer-invoices` as the base path. Prepare separate DRAFT invoices via `POST /org/{organizationId}` using fields from the core invoice's captured request: a unique invoiceNumber, customerId, invoiceDate/dueDate, lineItems with chartAccountId/description/quantity/unitPrice, and the fields below. Do not send server-returned IDs/timestamps as creation input. Resolve these drafts normally or keep them outside the core close fixture.

| ID | Request / fixture | Expected result |
| --- | --- | --- |
| D01 | Create B2C invoice with `transactionClassification=B2C`, `complianceStatus=NOT_REQUIRED`. GET `/{id}/download?format=pdf`, then json and xml. GET `/{id}/documents`. | Each download persists document metadata, correct invoice identity/values, format/content type, safe filename, nonempty bytes and SHA-256 checksum. Re-download persisted bytes via `/documents/{documentId}/download`; hash equals metadata (PowerShell `Get-FileHash -Algorithm SHA256`). A fresh PDF includes generation time, so do not require hashes of separately generated PDFs to match. |
| D02 | Generate PDF with long descriptions, enough lines for multiple pages, Unicode, quotes, XML special characters, and decimal quantities. Also request unsupported format `txt`. | Unsupported format returns 400. PDF must open with all text/totals readable and no clipped lines; JSON/XML parse and escape text correctly. Current PDF renderer is single-page ASCII; capture clipping/Unicode loss as defects, not acceptable output. |
| D03 | Repeat B2B and B2G with NOT_REQUIRED, then PENDING_CLEARANCE. Call both POST `/{id}/documents?format=pdf` and GET download for pdf/json/xml; also POST send. | Document generation/download returns 409 until CLEARED/FINAL_LOCKED. Failed send records failure evidence where transaction persistence permits; no successful dispatch. Test a separately seeded CLEARED/FINAL_LOCKED fixture for download eligibility only; seeded state is not clearance evidence. |
| D04 | Create taxable B2B fixture: supplierTaxId `TIN-UAT-SUPPLIER`, buyerTaxId `TIN-UAT-BUYER`, configured 7.5% taxJurisdictionId, quantity 2, unit price 100,000. GET `/{id}/nrs-payload`. | Payload: NGN, invoice type 380, tax category S, subtotal 200,000.00, VAT 15,000.00, total/payable 215,000.00, quantity 2.00, unit EA, correct IDs/classification and midnight invoice datetime. These are mapper conventions under test, not certification of NRS schema compliance. |
| D05 | Omit supplier then buyer tax ID; try positive tax at another rate; zero tax with no rule; then explicit active zero-rate code containing ZERO or EXEMPT. | Missing IDs/wrong standard VAT/no explicit zero code return 400; ZERO maps category Z and EXEMPT maps E. Tax rules calculate percentage HALF_UP to two decimals. Test small multi-line amounts and reconcile sum of rounded line VAT with header VAT; raise discrepancies. |
| D06 | POST `/{id}/nrs-submissions` using controller parameter `submissionType` (omit to test default); GET list and `/nrs-submissions/{submissionId}`. | B2B default PRE_CLEARANCE, submission PENDING, invoice PENDING_CLEARANCE; B2C default ASYNC_B2C. Archive links same invoice/submission, hash is 64 hex chars, immutable flag true, retention about six years, encrypted false. Do not claim actual WORM/encryption from flags. No fabricated IRN/signature/response. Invalid submissionType returns 400. |
| D07 | Repeat submission preparation and test contradictory explicit submissionType/classification or client-supplied compliance/IRN fields on draft update. | Capture new submission count: preparation currently creates another row per request. Treat duplicate submission protection and authoritative compliance-field tampering as gaps requiring resolution; client-set CLEARED must not be accepted as real NRS clearance. |
| D08 | POST `/{id}/send` for eligible invoice with email; GET `/{id}/dispatches`. Retry via POST `/{id}/dispatches/{dispatchId}/retry`. Test missing email, cross-invoice dispatch ID, and compliance-blocked invoice. | Local success has attemptCount 1, documentId, responseCode 202 and explicit pending-transport message. Successful retry rejected 409; cross-invoice ID rejected 400. Missing email returns 400; compliance gate 409. Inspect persisted FAILED attempt/error/nextRetryAt (about +5 minutes); mocks alone do not prove rollback behavior. There is no automatic transport retry proof. |

### Suite E: external invoice API and webhook preparation

Use an authenticated UAT session; test authorization separately with A06. These routes currently have no client-credential/scope enforcement.

1. **E01 Search:** GET `/api/v1/invoices?organizationId={id}&page=0&size=1&sort=invoiceNumber,asc`. Use at least three invoices with distinct numbers. Traverse pages and assert content, page, size, totalElements/totalPages, first/last. Test status, complianceStatus, transactionClassification, customerTaxId, fromDate/toDate (inclusive), and updatedSince individually and combined, empty result, page beyond end, malformed date, unknown enum, reversed range. Current size defaults to 25 and caps at 100; negative page normalizes to zero and nonpositive size defaults. Sort accepts invoiceDate, dueDate, createdAt, updatedAt, invoiceNumber, status; unknown property falls back to updatedAt. With tied timestamps test for missing/duplicate rows across pages and record unstable pagination.
2. **E02 Detail/history:** GET `/api/v1/invoices/{id}` plus `/documents`, `/events`, `/dispatches`; compare with Accountant data. Unknown numeric ID returns 404. Events are synthesized from current invoice/document/dispatch records, not an immutable event log: check order and references, but do not claim all historical transitions or original created-state values are preserved.
3. **E03 Register webhook:** POST `/api/integrations/webhooks` with `{"organizationId":ORG_ID,"name":"UAT hook","url":"https://example.test/ams-hook","eventTypes":"webhook.test","authType":"HMAC","secretReference":"uat-only-secret","active":true}`. Expect 201 and secretReference `********`. List via `?organizationId=ORG_ID`, GET `/{id}`, and PUT `/{id}` with name plus masked secret; verify secret remains masked and unchanged. HMAC without secret and insecure non-local HTTP URL return 400. Test HTTPS, localhost/127.0.0.1 local HTTP, malformed URL, unsupported auth type, missing organization/name/events.
4. **E04 Signed test:** POST `/api/integrations/webhooks/{id}/test`; GET `/{id}/deliveries`. Expect 201, unique eventId, requestBody with organization and payloadVersion, 64-character payloadHash, HMAC signature `sha256=` plus HMAC-SHA256 over exact UTF-8 requestBody using the supplied test secret. Verify independently. NONE auth has no signature. Current code uses secretReference itself as HMAC key; it does not resolve a vault reference. SUCCESS/202 records payload creation only; no receiver request is expected.
5. **E05 Lifecycle/security:** Update active=false and test again; delete endpoint via DELETE `/{id}` and inspect delivery-history behavior, unknown IDs, other-role mutations, foreign-organization IDs, and platform disabled state. Record that inactive endpoints/platform settings are not currently enforced by testEndpoint. Durable deletion with delivery children and restricted configuration access must be verified in a real database. Real event publishing, outbound retries/backoff, dead letters, receiver deduplication and secret resolution remain BLOCKED pending implementation.

### Suite F: System Admin and user administration

**F01 Configuration persistence and audit:** Log in as the System Admin user, then select **Technical Setup** in the left menu, and test each page section/API below. Save UAT-only values, refresh, verify updatedBy/updatedAt, and match entries in GET `/api/system-admin/audit/config-changes` (latest 50 only; filter by actual execution time and captured IDs). As each other single-group user, verify the page and APIs return 403. Use valid JSON from the UI's captured requests rather than guessing DTO fields.

| Section / relative API under `/api/system-admin` | Checks |
| --- | --- |
| GET/PUT `/nrs/config`, POST `/nrs/test-connection` | SANDBOX, enabled/disabled, base/token URL, clientId, test secret reference, positive connect/read timeouts; missing enabled prerequisites produce INCOMPLETE, disabled produces DISABLED, populated produces CONFIGURED only. Unsupported environment/zero timeout rejected. |
| GET/PUT `/signing/config`, POST `/signing/test` | Active toggle, algorithm/key reference, rotation fields, masked key. DISABLED/INCOMPLETE/CONFIGURED reflects configuration only. |
| GET/PUT `/webhook-platform/config` | Enabled, retry flag, signing algorithm, attempts/timeouts/domain pattern/retention. Positive numeric validation; persistence does not prove enforcement by webhook service. |
| GET/PUT `/storage-retention/config` | Storage locations, encryption/key reference, retention years, WORM/immutable settings; positive retention and masked key. Compare archive from D06 with settings; current preparation hardcodes six years and encrypted=false, so changed settings are not proof of runtime behavior. |
| GET/POST `/api-clients`; PUT `/{id}` and POST `/{id}/revoke` beneath `/api-clients` | Unique clientId, active/scopes/test secret, duplicate 409, update persists, revoke sets active=false and revokedAt. Registration/revocation is local metadata; OAuth token issuance and actual scope/revocation enforcement remain unimplemented acceptance. |
| GET `/jobs`, PUT `/jobs/{id}` | Five defaults B2C_NRS_SUBMISSION, NRS_SUBMISSION_RETRY, COMPLIANCE_BREACH_DETECTION, BUYER_REVIEW_POLLING, WEBHOOK_RETRY; repeated GET creates no duplicates. Change enabled/schedule/maxRetryAttempts; reject nonpositive attempts. Test malformed schedule and record if accepted; there is no runtime scheduling proof. |
| GET `/health/integrations` | JSON health/configuration status matches saved settings; do not interpret CONFIGURED as successful live connection. |

**F02 Secret handling:** For NRS, signing, storage and API-client settings, save a dummy secret, GET, save the returned `********`, then GET again. Outputs remain masked and the stored secret is not overwritten by the mask. Blank/masked updates currently preserve secrets; clearing/rotation needs explicit verification. Inspect rendered HTML, responses and audit summaries for accidental raw secrets; redact evidence.

**F03 Admin users:** As the Admin user, use the **User Management** screen with a disposable account to create, view groups, add/remove exact AMS group, disable/enable, request password reset email, and delete. Confirm errors for duplicate/missing inputs and wrong-role requests. Re-login after group changes to refresh OIDC claims. Verify disable/deletion/session effects with a separate browser session; do not assume an already issued session is revoked. Password-reset request success needs a controlled mailbox to prove delivery; without one mark delivery BLOCKED. Delete only the disposable user after preserving evidence.

## Execution, automation, and completion criteria

The repository contains service unit tests and MockMvc acceptance tests, not a browser E2E harness. MockMvc acceptance tests use simulated OIDC users and application infrastructure; they do not verify real Keycloak login, browser JavaScript, PDF rendering, external delivery, or transport behavior. No test profile isolates all external dependencies; application-context tests must point to UAT infrastructure and may persist data.

For a developer-supported automated check on Windows, from repository root:

```powershell
java -version
.\mvnw.cmd test
```

If the wrapper cannot initialize, use installed `mvn test` and record Maven version and error. For service checks that avoid intentionally starting application-context acceptance tests:

```powershell
mvn '-Dtest=*ServiceTest,*ControlTest,NrsPayloadMapperTest' test
```

This selection is supporting evidence only, not full coverage. Use `mvn -o` only when dependencies already exist locally. Preserve `target/surefire-reports` and exact command/summary; a dependency/compile failure means tests did not run. Existing generated reports are not evidence for the current working tree. During this documentation review on 2026-10-15, the offline service command above completed with 64 tests, zero failures/errors/skips using installed Maven; the wrapper version command failed to initialize. This result does not include live browser/Keycloak/database acceptance execution. Never run build/push/deployment goals as part of this manual runbook.

Sign-off requires every required case recorded, core arithmetic reconciled, correct accounts/organizations verified, invalid operations proven to leave no partial financial effects, and unresolved defects linked with severity and reproduction evidence. Distinguish local preparation PASS from external acceptance BLOCKED. Any unauthorized access, duplicate financial posting, partial transaction, incorrect control account, missing period enforcement, or unexplained report variance blocks financial/security sign-off pending triage. Preserve failed fixtures for investigation; restore the disposable snapshot or retire the RUN_ID after approval of evidence. Do not delete posted accounting rows to make totals agree.

### Source map for maintaining this guide

- Role/login rules: `core/config/AuthenticationManager.java`, `core/config/Oauth2SecurityConfig.java`, `HomeController.java`.
- UI routes/requests: role controllers and `src/main/resources/templates/{role}/`; API contracts: AccountantApiController, FinanceAdminApiController, CfoApiController, AuditorApiController and common organization/branch controllers.
- Financial oracles/guards: accountant services for customer/purchase invoices, GL, journals, fiscal periods, schedules/runs, collections, reconciliation/imports; CFO budget/report/trial-balance services; financeAdmin module/approval/import services.
- New integration limits: EInvoiceSubmissionService, NrsPayloadMapper, InvoiceDocumentService, InvoiceDeliveryService, ExternalInvoiceApiService, WebhookService and SystemAdminConfigService.
- Automated supporting coverage: `src/test/java/com/justjava/ams/` service tests and `mvp/*AcceptanceTest.java`. Check source and current build when expectations change; requirement/implementation-plan documents alone are not execution evidence.
