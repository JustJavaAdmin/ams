# AMS End-To-End Test Guide

Use this guide to test the non-admin AMS roles end to end. It assumes the four role users already exist in Keycloak and are assigned to `/financeAdmin`, `/accountant`, `/cfo`, and `/auditor`.

Today's test date is `2026-08-27`, so the test period is Q3 2026 and all operational dates are realistic for an August 2026 finance cycle.

## Step 1: Finance Admin Creates The Organization

Log in as your Finance Admin user, for example `fa.greenfield`, and open `/financeAdmin/organizationSetup`.

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

Still on `/financeAdmin/organizationSetup`, select `Greenfield Foods Limited` as the target company. In the branch form, create this branch and save it:

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

Open `/financeAdmin/chartOfAccounts`, select `Greenfield Foods Limited`, and create these accounts one after another with **Create Account**:

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

Open `/financeAdmin/fiscalConfiguration`, select `Greenfield Foods Limited`, and save these fiscal settings:

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

Open `/financeAdmin/moduleControls`, select `Greenfield Foods Limited`, and click **Initialize Defaults** if no modules exist.

Confirm these module controls are enabled: `GENERAL_LEDGER`, `ACCOUNTS_RECEIVABLE`, `ACCOUNTS_PAYABLE`, `PAYMENTS`, `REPORTING`, and `BUDGET_CONTROL`. If any of them is disabled, enable it and enter this reason when asked:

`Required for Greenfield Foods Q3 2026 end-to-end finance test.`

Create this approval rule:

- Rule Name: `CFO approval for manual journals over NGN 1`
- Module: choose the manual journal or general ledger related module available in the module dropdown
- Transaction Type: `MANUAL_JOURNAL`
- Min Amount: `1`
- Max Amount: leave blank
- Account Type: `Any account`
- Department: leave blank
- Required Approvals: `1`
- Priority: `10`
- Approver Role: `CFO`
- Notes: `All manual journals in this UAT flow should reach CFO approval.`

Expected result: module controls are active and the approval rule appears in the approval rules table.

## Step 6: Accountant Creates The Bank Account

Log out, then log in as your Accountant user, for example `acct.greenfield`, and open `/accountant/cashAndBank`.

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

Open `/accountant/purchaseInvoice`, select `Greenfield Foods Limited`, then click **New Vendor** and save this vendor:

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

Open `/accountant/payablesAutomation`, select `Greenfield Foods Limited`, and stay on **Schedules & Forecast**.

Set the schedule filters to:

- From: `2026-08-01`
- To: `2026-09-30`
- Status: `All statuses`

Click **Refresh**, find the schedule for `PI-2026-08-001`, and click **Approve**.

Go to the **Payment Runs** tab and create a payment run:

- Bank account: `GTBank - 0123456789`
- Run date: `2026-08-27`
- Cutoff date: `2026-09-04`
- Select the approved schedule for `PI-2026-08-001`; if the UI allows leaving selection empty, leave it empty and rely on the cutoff date.

Click **Create payment run**, open the run detail, then click **Submit**, **Approve**, and **Execute**.

Expected result: the run moves through submitted, approved, and executed statuses; the supplier payment is reflected against the payable.

## Step 9: Accountant Creates And Part-Pays A Customer Invoice

Open `/accountant/customerInvoicing`, select `Greenfield Foods Limited`, then click **New Customer** and save this customer:

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

In the invoice list, find the posted customer invoice for `Abuja Retail Mart Ltd`, click **Record Payment**, and enter:

- Payment amount: `200000`

Expected result: the invoice remains outstanding with `NGN 250,000` unpaid, which is overdue as of `2026-08-27`.

## Step 10: Accountant Generates Collections And Customer Statement

Open `/accountant/receivablesCollections`, select `Greenfield Foods Limited`, and set **As Of Date** to `2026-08-27`.

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

Expected result: the collection case shows assignment, call activity, promise to pay, dunning notice, and a sent customer statement.

## Step 11: Accountant Creates A Manual Journal For Bank Charges

Open `/accountant/manualJournal`, select `Greenfield Foods Limited`, and click **New Journal**.

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

Log out, then log in as your CFO user, for example `cfo.greenfield`, and open `/cfo/approvalsHub`.

Select `Greenfield Foods Limited`, find the journal `GTBank August account maintenance charge`, and open **Review**. Confirm:

- Date: `2026-08-27`
- Debit total: `NGN 12,500`
- Credit total: `NGN 12,500`
- Debit account: `7010 - Bank Charges Expense`
- Credit account: `1000 - GTBank Operating Bank`

Click **Approve**, enter Approval Note `Approved for August 2026 bank fee posting.`, and confirm.

Expected result: the journal leaves the pending queue and becomes available for posting by the Accountant.

## Step 13: Accountant Posts The Approved Journal To GL

Log out, then log back in as `acct.greenfield`, open `/accountant/manualJournal`, and select `Greenfield Foods Limited`.

Open the approved journal `GTBank August account maintenance charge`, click **Post to GL**, then confirm **Post Journal**.

Expected result: the journal status becomes `POSTED`, and the General Ledger entries modal shows the two posted rows.

## Step 14: Accountant Reconciles The Bank Charge

Create a small CSV file on your machine named `gtbank-2026-08-27.csv` with this exact content:

```csv
date,amount,description,reference
2026-08-27,-12500,GTBank August account maintenance charge,BANK-FEE-AUG-2026
```

Open `/accountant/cashAndBank`, select:

- Organization: `Greenfield Foods Limited`
- Bank Account: `GTBank - 0123456789`

In **Import Statement**, enter:

- Statement Date: `2026-08-27`
- Opening: `1000000`
- Closing Balance: `987500`
- Statement File: upload `gtbank-2026-08-27.csv`

Click **Process Statement Import**. If the row does not auto-match, load candidates on the unmatched line, select the posted GL entry for `NGN -12,500`, and click **Match**. Then click **Complete**.

Expected result: imported lines `1`, matched lines `1`, unresolved difference `0.00`, and reconciliation status `COMPLETED`.

## Step 15: Accountant Imports A Depreciation Journal

Create a small CSV file named `depreciation-2026-08.csv` with this exact content:

```csv
accountCode,debitAmount,creditAmount,description,referenceNumber,branchCode,departmentCode,projectCode,assetCode
7100,30000,0,August depreciation for cold-room equipment,DEP-AUG-2026,LG-HQ,OPS,FA,COLD-ROOM-01
1590,0,30000,Accumulated depreciation for cold-room equipment,DEP-AUG-2026,LG-HQ,OPS,FA,COLD-ROOM-01
```

Open `/accountant/depreciationJournals`, select `Greenfield Foods Limited`, and enter:

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

Log in as `cfo.greenfield`, open `/cfo/approvalsHub`, and select `Greenfield Foods Limited`.

Open the submitted depreciation journal from batch `DEP-2026-08-GFL`, confirm it has:

- Debit `7100 - Depreciation Expense` for `NGN 30,000`
- Credit `1590 - Accumulated Depreciation` for `NGN 30,000`

Click **Approve**, enter Approval Note `Approved monthly depreciation import from FixedAssetApp.`, and confirm.

Expected result: the depreciation journal is approved and ready for Accountant posting.

## Step 17: Accountant Posts The Depreciation Journal

Log in as `acct.greenfield`, open `/accountant/manualJournal`, select `Greenfield Foods Limited`, and open the approved depreciation journal.

Click **Post to GL**, then confirm **Post Journal**.

Expected result: the depreciation journal status becomes `POSTED` and the GL modal shows two posted depreciation rows.

## Step 18: CFO Creates And Activates A Budget

Log in as `cfo.greenfield`, open `/cfo/budgetControl`, select `Greenfield Foods Limited`, and set the year selector to `2026`.

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

Open `/cfo/trialBalance`, select `Greenfield Foods Limited`, enter **As Of Date** `2026-08-27`, and click **Generate Report**.

Confirm total debits equal total credits, then click **Save Snapshot** and **Confirm Save**.

Open `/cfo/financialReports`, select `Greenfield Foods Limited`, and set:

- From date: `2026-08-01`
- To date: `2026-08-27`

Generate these reports one by one:

1. Click **Generate Income Statement**, review revenue and expense lines, click **Submit**, then click **Approve**.
2. Click **Generate Balance Sheet**, review assets, liabilities, equity, and variance, then export the report CSV.
3. Click **Generate Budget Variance**, confirm the budget line appears or that the report clearly shows no posted budget consumption yet.

Expected result: trial balance snapshot saves, the income statement is approved, report history contains generated reports, and CSV export downloads successfully.

## Step 20: Auditor Reviews Audit Logs

Log out, then log in as your Auditor user, for example `audit.greenfield`, and open `/auditor/auditLogExplorer`.

Select `Greenfield Foods Limited`, filter:

- Start Date: `2026-08-27`
- End Date: `2026-08-27`
- Entity Type: `Manual Journal`
- Action: `APPROVE`

Confirm the approval records for the bank charge journal and depreciation journal are visible. Then change:

- Entity Type: `General Ledger`
- Action: `POST`

Confirm posted GL activity is visible, then click **Export Audit Log**.

Expected result: exported audit log includes the approval and posting evidence from the test flow.

## Step 21: Auditor Reviews And Acknowledges Security Events

Open `/auditor/securityEvents`, select `Greenfield Foods Limited`, and filter:

- Status: `Unacknowledged`
- Start Date: `2026-08-27`
- End Date: `2026-08-27`

Open each event created by module control changes or report generation, click **View**, confirm the event type, severity, timestamp, description, IP address, and user agent are populated where available, then click **Acknowledge** and confirm.

After acknowledgement, filter:

- Status: `Acknowledged`

Click **Export Security Events**.

Expected result: acknowledged events show the auditor user and acknowledgement timestamp, and CSV export downloads successfully.

## Step 22: Finance Admin Closes And Locks The Period

Log back in as `fa.greenfield`, open `/financeAdmin/fiscalConfiguration`, and select `Greenfield Foods Limited`.

Find fiscal period `2026 Q3`, click **Close this period**, then confirm **Close Period**. After it changes to `CLOSED`, click **Lock this period**, then confirm **Lock Period**.

Expected result: Q3 2026 changes from `OPEN` to `CLOSED` to `LOCKED`, and no further posting should be allowed into `2026-07-01` through `2026-09-30`.

## Step 23: Final Negative Control Check

Log in as `acct.greenfield`, open `/accountant/manualJournal`, select `Greenfield Foods Limited`, and attempt a new journal dated `2026-08-27`:

- Entry Description: `Blocked posting test after Q3 lock`
- Branch: `Lagos HQ Operations (LG-HQ)`
- Line 1: `6000 - Office Supplies Expense`, Debit `1000`, Credit `0`, Narration `Period lock negative test`, Department `OPS`, Project `GENERAL`
- Line 2: `1000 - GTBank Operating Bank`, Debit `0`, Credit `1000`, Narration `Period lock negative test`, Department `OPS`, Project `GENERAL`

Save the draft if the UI allows it, then try to submit or post it.

Expected result: AMS should block posting into the locked period. Record the exact validation message shown by the app as evidence of fiscal period control.