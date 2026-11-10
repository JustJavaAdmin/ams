# Fixed Assets User Guide

The Fixed Assets area in the Accountant workspace connects AMS to an external fixed asset system. The external system owns the asset records, additions, disposals, valuations, and depreciation calculations. AMS receives the accounting output as depreciation journal batches, then routes those journals through the usual review, approval, and posting workflow.

This guide explains the combined Fixed Assets workspace, including depreciation journal imports and their history.

## 1. Goals and workflow

The module helps accountants:

- Import depreciation journals calculated by an external fixed asset system.
- Submit imported journals for approval and posting in AMS.
- Trace an imported batch to its source system and linked manual journal.
- Review depreciation import history by organization.

The usual workflow is:

**Calculate depreciation externally → Export a journal file → Import into AMS → Review or submit for approval → Approve and post through the journal workflow → Review import history**

## 2. Review depreciation import history

Open **Fixed Assets** in the Accountant workspace. The page includes the depreciation journal upload form and import history. Select an organization to load its imports. Click **Refresh** to reload the list.

| Field | Meaning |
| --- | --- |
| **Batch** | Identifier for the batch in the external system |
| **External System** | Source system recorded during import |
| **Journal Date** | Date assigned to the depreciation journal |
| **Debit Total** | Total debit amount in the imported journal |
| **Status** | Current import status |
| **Journal link** | Opens the related manual journal for review and processing |

The history is empty until an organization is selected and imports exist. Open a linked journal in **Manual Journal** to review its lines and processing status. Existing bookmarks to `/accountant/depreciationJournals` redirect to **Fixed Assets**.

## 3. Prepare a depreciation journal file

Calculate depreciation in the external fixed asset system and export a journal file as `.CSV`, `.TXT`, or `.XLSX`. Include one journal line per row, with columns in this order:

| Column | Required? | Description |
| --- | --- | --- |
| Account code | Yes | Active account code in the selected organization's chart of accounts |
| Debit amount | One side of each line | Non-negative amount; use zero for a credit line |
| Credit amount | One side of each line | Non-negative amount; use zero for a debit line |
| Description | Yes | Line description |
| Reference number | No | Supporting reference |
| Branch code | No | Branch identifier |
| Department code | No | Department identifier |
| Project code | No | Project identifier |
| Asset code | No | Related asset identifier |

The first row may be a header containing terms such as `account`, `debit`, or `credit`. For `.XLSX` files, the importer reads the first worksheet.

Each row must have either a debit or a credit amount, but not both. Amounts cannot be negative. The journal must contain at least two lines, and total debits must equal total credits. Account codes must exist and be active in the selected organization.

If you provide a batch-level branch code during import, it must belong to the selected organization. Line-level branch, department, project, reference, and asset values are retained as journal details; they do not drive depreciation calculations in AMS.

## 4. Import the journal

From **Fixed Assets**, use the **Depreciation Journals** upload section. Then:

1. Select the organization.
2. Enter the **External System** name. It defaults to `FixedAssetApp`.
3. Enter a unique **External Batch ID**, such as `DEP-2026-08`.
4. Enter the **Journal Date** and a clear description.
5. Optionally enter a batch-level branch code.
6. Select the `.CSV`, `.TXT`, or `.XLSX` journal file.
7. Choose whether to select **Submit for approval after import**.
8. Click **Import Depreciation Journal**.

AMS validates the file and creates a manual journal. The import history records the source system, batch identifier, journal date, line count, debit and credit totals, importing user, and linked manual journal.

The external batch ID must be unique within the organization. Use a stable, meaningful identifier so the batch can be traced to the external system later.

## 5. Understand import and journal statuses

Import status and manual journal status describe related but different parts of the workflow:

| Import status | Meaning |
| --- | --- |
| **IMPORTED** | AMS accepted the batch and created a manual journal. If auto-submit was not selected, the journal remains a draft for review. |
| **SUBMITTED** | The imported journal was submitted into the approval workflow. Continue with the normal approval process. |
| **POSTED** | The related depreciation journal has been posted. |
| **FAILED** | Import processing failed. |

Selecting **Submit for approval after import** submits the journal automatically; it does not approve or post it. Continue through the normal journal approval process. The import history includes the linked manual journal number so you can open and inspect it in the **Manual Journal** workspace.

## 6. Review the imported journal

Before submitting or approving, check that:

- The organization and external source are correct.
- The batch ID and journal date agree with the external system's records.
- Each line uses the intended account code and debit or credit side.
- Total debits equal total credits.
- Descriptions and optional references, branch, department, project, and asset codes support review and audit.
- The linked manual journal represents the intended batch.

If an import is incorrect, use the organization's normal journal correction process. Avoid importing the same batch again under another ID as a workaround; that can create duplicate depreciation journals.

## 7. What this module does not do

The Fixed Assets workspace provides depreciation journal import and history. AMS does not use it to:

- Create or maintain a full asset register.
- Record asset additions, disposals, or revaluations.
- Calculate depreciation schedules or depreciation amounts.
- Generate depreciation batches automatically on a schedule.
- Approve or post a journal simply because it was imported.

Perform asset maintenance and depreciation calculations in the external fixed asset system. Use AMS's separate journal workflow for review, approval, and posting.

## 8. Use the module effectively

- Finish asset maintenance and depreciation calculations in the external system before exporting a batch.
- Reconcile the external batch totals and account codes before import.
- Use a unique, traceable batch ID for each period or run.
- Confirm the organization, journal date, accounts, and branch before importing.
- Keep each file balanced and provide exactly one debit or credit amount per line.
- Decide deliberately whether to auto-submit; review the journal before approval and posting.
- Check import history after processing and follow the linked manual journal through approval and posting.
- Coordinate journal dates with the organization's accounting calendar and approval controls.
