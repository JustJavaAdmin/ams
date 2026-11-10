# Cash & Bankbook Reconciliation User Guide

Cash & Bankbook Reconciliation helps the Accountant team compare transactions on a bank statement with posted transactions in the General Ledger (GL). It helps confirm that statement activity is supported by ledger entries, identify missing or incorrect records, and complete a traceable reconciliation for a specific bank account and statement date.

This guide describes the current Cash & Bankbook Reconciliation screen and its behavior.

## 1. How the workflow fits together

**Select organization and bank account → Import statement → Review automatic matches → Resolve unmatched lines → Check the difference → Complete reconciliation**

The module accepts statement imports in `.CSV`, `.TXT`, and `.XLSX` formats. It matches each statement line to a posted entry in the GL account linked to the selected bank account.

## 2. Set up the bank account

Select the **Organization** first, then select an existing **Bank Account**. The account list shows the bank name and account number.

If no account is available:

1. Click **New Bank Account**.
2. Enter the bank name, account number, and account holder.
3. Optionally add the branch code, currency, opening balance, and notes.
4. Select the linked cash or bank GL account.
5. Click **Save Bank Account**.

The bank account must link to the correct asset account in the organization's chart of accounts. This link determines which ledger entries can be matched. The screen offers asset accounts for selection.

## 3. Prepare and import a statement

Prepare a file with one transaction per row and these columns, in order:

| Column | Required? | Description |
| --- | --- | --- |
| Date | Yes | Transaction date |
| Amount | Yes | Signed amount: deposits and increases are positive; withdrawals and charges are negative |
| Description | Yes | Transaction details |
| Reference | No | Transaction or bank reference |

The first row may contain headings such as `Date,Amount,Description,Reference`. Use a supported date format, such as `YYYY-MM-DD`, `DD/MM/YYYY`, or `MM/DD/YYYY`. Amounts may include commas and an `NGN` prefix; retain the correct sign. For `.XLSX` files, the importer reads the first worksheet.

To import:

1. Select the organization and bank account.
2. In **Import Statement**, enter the **Statement Date**, **Opening** balance, and **Closing Balance** from the bank statement.
3. Choose the statement file.
4. Click **Process Statement Import**.

AMS creates a reconciliation with **DRAFT** status, imports the lines, attempts automatic matching, and displays the new reconciliation for review. Use the actual statement balances and confirm the account and statement period before processing.

## 4. Review the reconciliation

The **Reconciliation** selector lists reconciliations for the selected organization and bank account, newest statement date first. Each entry shows the statement date, status, and matched-line count.

The **Summary** shows:

| Field | Meaning |
| --- | --- |
| **Status** | `DRAFT` while work is in progress; `COMPLETED` when finalized |
| **Imported lines** | Number of transactions imported |
| **Matched lines** | Number of statement lines linked to a GL entry |
| **Cleared amount** | Sum of the signed amounts of matched statement lines |
| **Difference** | Expected statement movement less the cleared amount |
| **Pending** | Imported lines that remain unmatched |

The expected statement movement is:

`Closing balance − Opening balance`

The system calculates:

`Difference = (Closing balance − Opening balance) − Cleared amount`

A zero difference means the matched lines' signed amounts add up to the net movement between the statement balances. It does not prove every transaction is matched; the pending count must also be zero before completion.

The **Verification Queue** lists each statement line by value date, description, amount, GL match status, and available action. Check that each amount's sign agrees with the direction of the bank transaction.

## 5. Match statement lines to the GL

### Automatic matching

Import automatically attempts matching. Click **Auto-match** to retry after ledger entries have been posted or earlier matches have been corrected.

Automatic matching considers posted GL entries for the linked bank account on the same transaction date. The signed amounts must match. If the statement line has a reference, it must match the GL reference; if either side has no reference, the reference does not prevent a match. Each GL entry can be matched to only one statement line.

Matching is exact. A different date, amount, sign, account, posting status, or conflicting reference can leave a transaction unmatched.

### Manual matching

For an unmatched statement line:

1. Focus or click **Load candidates…** in its **GL Match** selector.
2. Review candidate date, journal number, debit/credit, amount, and description.
3. Select the correct GL entry.
4. Click **Match**.

Only posted entries on the linked GL account with the exact signed amount can be matched. A GL entry already matched to another statement line cannot be reused. The candidate list also applies date, amount, and reference filters. If the expected entry is missing, check its account, posting status, transaction date, amount and sign, and reference.

Matched lines are marked `AUTO_MATCHED` or `MANUAL_MATCHED`. If a line is matched incorrectly, click **Unmatch** while the reconciliation is still a draft, then match it to the correct entry.

### Investigate unmatched transactions

For each unmatched line, check whether:

- The transaction is missing from the GL and should be entered and posted through the appropriate accounting workflow.
- A ledger transaction exists but has a different date, amount, sign, reference, or linked account.
- The bank transaction needs investigation with the bank or internal team.
- The statement file contains an incorrect or duplicate row.

After correcting or posting ledger records, retry **Auto-match** or match the line manually. A match should represent the same transaction; do not use matching simply to make the totals balance.

## 6. Complete the reconciliation

Before clicking **Complete**, confirm that:

- The organization, bank account, statement date, opening balance, and closing balance are correct.
- Every imported line is matched and **Pending** is zero.
- **Difference** is exactly `0.00`.
- The matches and imported statement lines have been reviewed.

Click **Complete** to finalize. AMS checks that the reconciliation has statement lines, every line is matched, and the unresolved difference is zero. It records the completing user and time and changes the status to `COMPLETED`.

Completed reconciliations are read-only in this screen: matching and unmatching actions are available only for drafts. Review carefully before completion.

## 7. Use the module effectively

- Reconcile each bank account regularly using the statement period's actual opening and closing balances.
- Keep each bank account linked to the correct GL asset account.
- Use consistent file columns and signed amounts; check dates, references, and duplicate rows before importing.
- Keep references consistent between bank statements and ledger transactions to improve automatic matching.
- Post transactions to the GL before reconciling, then retry auto-match.
- Investigate every unmatched line and correct the underlying accounting record through its normal workflow.
- Check both the difference and pending count; both must be zero before completion.
- Complete only after reviewing every line, since the completed record cannot be edited through this screen.

## 8. What this module does not do

The screen imports statement lines and matches them to existing posted GL entries. It does not create GL transactions for unmatched bank lines. **Complete** finalizes the reconciliation; it does not post adjusting journals.
