# Receivables Collections User Guide

Receivables Collections helps the accounting team follow up on overdue customer invoices, record collection work, track payment promises, manage customer credit holds, and prepare customer statements.

This guide describes the current Receivables Collections screen and its behavior.

## 1. What you can do

| Area | Purpose |
| --- | --- |
| **Collections Queue** | Generate and review cases for overdue invoices, then record collection actions. |
| **Customer Statements** | Prepare a statement for one customer and mark it as sent. |

A typical collection workflow is:

**Post customer invoice → Generate queue → Assign collector → Contact customer and record activity → Track a promise or escalate → Review payment → Resolve or close the case**

## 2. Before you begin

Make sure you have:

- Access to the **Accountant** workspace and **Receivables Collections**.
- The correct organization selected in the organization selector at the top of the page.
- Customer invoices entered with due dates and outstanding balances.
- Customer records associated with the invoices when you need to apply or release a credit hold.

The selected organization determines which customers, collection cases, and statements are shown. Choose an organization before using either tab.

## 3. Work with the Collections Queue

Open **Collections Queue**. The left panel lists cases; select a case to see its details and available actions on the right.

### Generate cases

1. Confirm the correct organization is selected.
2. Choose an **As of** date. It defaults to today.
3. Click **Generate**.
4. Select a case in the queue to review its invoice, balance, overdue days, status, activity history, and payment promises.

Generation creates or refreshes a case for each non-cancelled, unpaid invoice with a positive outstanding balance and a due date before the selected date. Invoices due on the selected date are not overdue for this purpose. Existing cases are refreshed rather than duplicated. **Refresh** reloads the existing queue without generating cases.

The queue is ordered by days overdue, with the most overdue cases first. It is not a filtered view: it can include cases in different statuses.

### Understand case details and statuses

| Status | Meaning |
| --- | --- |
| **OPEN** | Case created and awaiting collection work. |
| **IN_PROGRESS** | A collector has been assigned or collection work has started. |
| **PROMISED** | A payment promise is being tracked. |
| **ESCALATED** | The case has been referred to a manager or team. |
| **RESOLVED** | The invoice has been paid in full. |
| **CLOSED** | A user closed the case. |

Case details show the invoice total, amount paid, outstanding balance, assigned collector, next action date, promises, and activity history. A paid invoice can be marked **RESOLVED** automatically when its balance is refreshed. **CLOSED** is a manual closure and is distinct from resolution.

### Assign a collector

1. Select the case and click **Assign**.
2. Enter the collector's username.
3. Add notes if useful, then click **Apply**.

The case records the collector and assignment time. An **OPEN** case moves to **IN_PROGRESS**. The assignment is also added to the activity history.

### Record collection activity

1. Select the case and click **Activity**.
2. Choose **NOTE**, **CALL**, or **EMAIL**.
3. Enter a subject and notes describing what happened or what was agreed.
4. Click **Apply**.

Activities are saved in the case history with their type, subject, notes, creator, and timestamp. Recording an activity does not send an email or place a call; it documents work performed outside this screen.

### Record a payment promise

1. Select the case and click **Promise**.
2. Enter the amount and promised payment date.
3. Add notes if needed, then click **Apply**.

The amount must be greater than zero and cannot exceed the current outstanding balance. A promise is recorded as **ACTIVE**, the case status becomes **PROMISED**, and the promised date becomes the next action date.

When cases are generated and promises are reconciled, an active promise is marked **KEPT** if the invoice is paid in full, or **BROKEN** if its date has passed while a balance remains. A broken promise returns a **PROMISED** case to **IN_PROGRESS**. Partial payment alone does not mark a promise as kept.

### Create a dunning notice

1. Select the case and click **Dunning**.
2. Review the updated activity history and next action date.

AMS records a payment reminder based on days overdue:

| Days overdue | Notice recorded | Next action |
| --- | --- | --- |
| 0–30 | Payment reminder (level 1) | 7 days later |
| 31–60 | Overdue payment notice (level 2) | 3 days later |
| 61 or more | Final overdue payment notice (level 3) | 3 days later |

The notice is recorded as **SENT** in the case history, but this action does not itself email the customer. Use your normal communication process to send the notice, then keep the case activity accurate.

### Escalate or close a case

**Escalate**: Select the case, click **Escalate**, enter the manager or team in **Escalate to**, give the reason in notes, and click **Apply**. The case becomes **ESCALATED** and the referral is recorded in its history.

**Close**: Select the case, click **Close**, enter a clear reason, and click **Apply**. The case becomes **CLOSED**. Close a case when collection follow-up should stop; a later queue generation may refresh its balance, but it does not automatically reopen a manually closed case.

Resolved and closed cases do not show the assign, dunning, promise, activity, escalate, or close actions. A paid invoice is normally resolved automatically, so use **Close** for another documented reason.

### Manage a customer credit hold

When the selected case is linked to a customer record, its detail shows **Credit hold** and **Release hold** actions.

- To place a hold, click **Credit hold**, enter the reason in notes, and click **Apply**.
- To release a hold, click **Release hold** and confirm the action.

The hold applies to the customer, not just the selected invoice. Use your organization's credit-control policy before changing it. The hold reason and audit details are stored with the customer record.

## 4. Create and send customer statements

Open **Customer Statements**. Statements summarize a customer's invoice activity and balances for a date range.

### Generate a statement

1. Select the customer.
2. Enter the **Statement Date** (defaults to today), **Start Date**, and **End Date**. The start date defaults to 30 days before today.
3. Click **Generate statement**.
4. Select the statement to review its opening balance, invoices, payments, and closing balance.

The start date must not be after the end date. The statement includes non-cancelled invoices dated within the selected period. Its opening balance is the remaining balance on that customer's invoices dated before the start date. The period shows invoice debits and payments as credits; the closing balance is opening balance plus period invoices minus period payments.

Review the customer, period, references, and balances before sending. Statements are retained in the list for the selected organization; **Refresh** reloads that list.

### Mark a statement as sent

1. Select the statement and review its details.
2. Click **Send**.
3. Confirm that its status changes from **GENERATED** to **SENT**.

**Send** records the statement as sent in AMS. It does not deliver an email or create a downloadable statement in this screen. Send the statement through your approved customer communication channel, then mark it sent here.

## 5. Good collection practice

- Generate cases regularly using the current date so balances, overdue days, and promise statuses stay current.
- Confirm the organization, customer, invoice number, due date, and balance before contacting a customer.
- Record clear, factual notes after each call, email, or other collection action.
- Set realistic promise dates and amounts, then follow up on the next action date.
- Use escalation, credit holds, and manual closure according to your organization's approval and credit policies.
- Reconcile customer payments through the normal payment process; payment updates can resolve a case automatically.
