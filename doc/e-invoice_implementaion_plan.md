# E-Invoice Implementation Plan

## 1. Purpose

This document defines the implementation plan required to close the currently identified invoice management and e-invoicing gaps in the AMS application.

The scope includes:

- Full customer invoice document generation, download, and delivery.
- API integration points for external systems to retrieve invoices.
- Outbound webhook delivery for invoice events.
- Nigeria Revenue Service (NRS) e-invoicing integration based on `E-Invoicing_Requirement_Specification.md`.
- Compliance controls for B2B, B2G, and B2C invoice flows.
- A new `System_Admin` role and UI for technical configuration that should not sit with Finance Admin or Accountant users.

## 2. Current State Summary

The application currently has partial invoice lifecycle support.

Implemented capabilities include:

- Customer invoice creation, listing, retrieval, update, marking as sent, posting, and payment recording.
- Purchase invoice creation, listing, retrieval, update, submission, approval, posting, and payment recording.
- Customer and vendor `taxId` fields.
- Configurable tax jurisdictions and invoice tax calculation.
- Accountant-facing UI for creating and posting customer and purchase invoices.

Missing capabilities include:

- Actual invoice delivery to customers or third-party targets.
- Invoice PDF/XML/JSON download.
- Structured invoice document generation and storage.
- External integration-grade invoice API with filtering, pagination, and machine-client authentication.
- Outbound webhook configuration, signing, retry, and delivery audit.
- NRS/MBS portal integration.
- UBL 3.0 or PEPPOL BIS 3.0 payload generation.
- IRN, QR code, digital stamp, and NRS receipt capture.
- B2B/B2G pre-clearance enforcement.
- B2C asynchronous submission within 24 hours.
- 72-hour buyer review lifecycle.
- ECDSA signing, encrypted payload archive, and long-term immutable retention.
- Credit note workflow for rejected/voided e-invoices.
- Technical admin UI for integration and security configuration.

## 3. Guiding Architecture

Invoice functionality should be separated into four lifecycle areas:

1. Accounting lifecycle:
   Create, approve, post, pay, and reverse invoices in the general ledger.

2. Compliance lifecycle:
   NRS clearance, buyer review, compliance breach detection, final lock, and credit note handling.

3. Delivery lifecycle:
   PDF/XML/JSON generation, download, email, webhook delivery, and delivery audit.

4. Integration lifecycle:
   External invoice pull APIs, payload archive, NRS submission history, retry queues, and API client access.

Avoid placing document generation, NRS communication, webhook dispatch, or security-key handling directly inside the existing invoice accounting services. Those services should remain focused on accounting behavior.

## 4. Proposed Package Structure

Add dedicated packages for invoice delivery and e-invoicing:

```text
com.justjava.ams.invoicing
com.justjava.ams.invoicing.document
com.justjava.ams.invoicing.delivery
com.justjava.ams.invoicing.webhook
com.justjava.ams.invoicing.api
com.justjava.ams.einvoicing
com.justjava.ams.einvoicing.nrs
com.justjava.ams.einvoicing.payload
com.justjava.ams.einvoicing.signing
com.justjava.ams.einvoicing.archive
com.justjava.ams.systemAdmin
```

The existing accountant package can continue to own invoice accounting operations while delegating delivery and compliance operations to the new modules.

## 5. Data Model Changes

### 5.1 Extend Customer Invoice Metadata

Add e-invoicing and delivery metadata to customer invoices or to a linked invoice compliance table.

Recommended fields:

- `transactionClassification`: `B2B`, `B2G`, `B2C`
- `supplierTaxId`
- `buyerTaxId`
- `complianceStatus`: `NOT_REQUIRED`, `PENDING_CLEARANCE`, `CLEARED`, `FAILED`, `BREACH`, `REJECTED_VOID`, `FINAL_LOCKED`
- `nrsIrn`
- `nrsQrCode`
- `nrsDigitalStamp`
- `nrsReceiptReference`
- `nrsClearedAt`
- `buyerReviewDeadline`
- `finalLockedAt`
- `rejectedAt`
- `rejectionReason`
- `complianceBreachAt`

### 5.2 New Invoice Document Entity

Create `InvoiceDocument`.

Purpose:

- Track generated invoice artifacts.
- Support PDF, JSON, and XML versions.
- Store document checksums for integrity.
- Provide a stable audit trail for documents sent or downloaded.

Suggested fields:

- `id`
- `invoiceId`
- `invoiceType`: `CUSTOMER_INVOICE`, `PURCHASE_INVOICE`, `CREDIT_NOTE`
- `format`: `PDF`, `JSON`, `XML`
- `storageKey`
- `fileName`
- `contentType`
- `checksum`
- `generatedAt`
- `generatedBy`
- `sourceVersion`

### 5.3 New Invoice Dispatch Entity

Create `InvoiceDispatch`.

Purpose:

- Track every delivery attempt.
- Support retry and failure diagnostics.

Suggested fields:

- `id`
- `invoiceId`
- `channel`: `EMAIL`, `WEBHOOK`, `MANUAL_DOWNLOAD`, `API_PULL`
- `target`
- `status`: `PENDING`, `SUCCESS`, `FAILED`, `RETRYING`, `DEAD_LETTER`
- `attemptCount`
- `lastAttemptAt`
- `nextRetryAt`
- `responseCode`
- `responseBody`
- `errorMessage`
- `idempotencyKey`
- `createdAt`

### 5.4 New Webhook Endpoint Entity

Create `WebhookEndpoint`.

Purpose:

- Store outbound webhook targets.
- Allow Finance/Admin users to configure business event destinations while System Admin owns platform constraints.

Suggested fields:

- `id`
- `organizationId`
- `name`
- `url`
- `eventTypes`
- `authType`: `NONE`, `HMAC`, `BEARER_TOKEN`, `BASIC`
- `secretReference`
- `active`
- `createdAt`
- `updatedAt`

### 5.5 New Webhook Delivery Entity

Create `WebhookDelivery`.

Purpose:

- Store event-level webhook delivery attempts.

Suggested fields:

- `id`
- `webhookEndpointId`
- `eventType`
- `eventId`
- `payloadHash`
- `status`
- `attemptCount`
- `requestBody`
- `responseCode`
- `responseBody`
- `nextRetryAt`
- `createdAt`
- `updatedAt`

### 5.6 New E-Invoice Submission Entity

Create `EInvoiceSubmission`.

Purpose:

- Track NRS submission lifecycle.

Suggested fields:

- `id`
- `invoiceId`
- `submissionType`: `PRE_CLEARANCE`, `ASYNC_B2C`, `CREDIT_NOTE`
- `status`: `PENDING`, `SUBMITTED`, `CLEARED`, `FAILED`, `BREACH`, `REJECTED`, `FINAL_LOCKED`
- `payloadFormat`: `JSON`, `XML`
- `payloadHash`
- `signature`
- `nrsRequestId`
- `nrsIrn`
- `nrsResponseCode`
- `nrsResponseMessage`
- `submittedAt`
- `clearedAt`
- `failedAt`
- `retryCount`
- `nextRetryAt`

### 5.7 New E-Invoice Payload Archive Entity

Create `EInvoicePayloadArchive`.

Purpose:

- Preserve raw signed payloads and NRS receipts for audit.

Suggested fields:

- `id`
- `submissionId`
- `invoiceId`
- `payloadStorageKey`
- `responseStorageKey`
- `payloadHash`
- `responseHash`
- `encrypted`
- `retentionUntil`
- `immutable`
- `createdAt`

### 5.8 New Credit Note Entity

Create `CreditNote`.

Purpose:

- Support correction/reversal for rejected or voided cleared invoices.

Suggested fields:

- `id`
- `organizationId`
- `originalInvoiceId`
- `creditNoteNumber`
- `reason`
- `status`: `DRAFT`, `PENDING_CLEARANCE`, `CLEARED`, `POSTED`, `REJECTED`, `CANCELLED`
- `amount`
- `taxAmount`
- `totalAmount`
- `nrsIrn`
- `nrsQrCode`
- `nrsDigitalStamp`
- `createdAt`
- `postedAt`

## 6. Invoice Document Generation And Download

### 6.1 Services

Add `InvoiceDocumentService`.

Responsibilities:

- Generate invoice PDF.
- Generate structured JSON.
- Generate structured XML.
- Store generated artifacts.
- Return document streams for downloads.
- Calculate and persist checksums.
- Enforce compliance locks before allowing document generation or download.

### 6.2 API Endpoints

Add endpoints:

```text
GET /api/accountant/customer-invoices/{invoiceId}/download?format=pdf
GET /api/accountant/customer-invoices/{invoiceId}/download?format=json
GET /api/accountant/customer-invoices/{invoiceId}/download?format=xml
GET /api/accountant/customer-invoices/{invoiceId}/documents
GET /api/accountant/customer-invoices/{invoiceId}/documents/{documentId}
```

Future purchase invoice download endpoints can follow the same pattern if required.

### 6.3 Rules

- B2B and B2G invoices cannot be downloaded until NRS clearance is complete.
- B2C invoices can be downloaded immediately, but must enter the async NRS submission queue.
- Final-locked invoices must generate documents only from the locked invoice snapshot.
- Download actions should be audited.

## 7. Real Invoice Sending

### 7.1 Current Gap

The current customer invoice `send` endpoint only moves the invoice to `SENT`. It does not perform email, webhook, or external delivery.

### 7.2 Services

Add `InvoiceDeliveryService`.

Responsibilities:

- Resolve eligible delivery channels.
- Generate or retrieve invoice document artifacts.
- Send email where configured.
- Trigger webhook event delivery.
- Record every attempt in `InvoiceDispatch`.
- Enforce compliance locks.
- Retry failed deliveries where appropriate.

### 7.3 API Endpoints

Add endpoints:

```text
POST /api/accountant/customer-invoices/{invoiceId}/send
GET /api/accountant/customer-invoices/{invoiceId}/dispatches
POST /api/accountant/customer-invoices/{invoiceId}/dispatches/{dispatchId}/retry
```

The existing `PATCH /api/accountant/customer-invoices/{invoiceId}/send` should either be preserved as a backwards-compatible wrapper or deprecated in favor of the new explicit `POST` endpoint.

### 7.4 Rules

- B2B and B2G dispatch requires NRS clearance.
- B2C dispatch can happen immediately.
- Every dispatch result must be stored.
- Delivery should not mutate accounting status unexpectedly.
- Delivery status should be separate from posting/payment status.

## 8. External Invoice Pull API

The existing invoice endpoints are useful internally, but integration consumers need stable, versioned APIs.

### 8.1 API Endpoints

Add:

```text
GET /api/v1/invoices
GET /api/v1/invoices/{invoiceId}
GET /api/v1/invoices/{invoiceId}/documents
GET /api/v1/invoices/{invoiceId}/events
GET /api/v1/invoices/{invoiceId}/dispatches
```

### 8.2 Query Capabilities

Support filters:

- `organizationId`
- `status`
- `complianceStatus`
- `customerTaxId`
- `transactionClassification`
- `fromDate`
- `toDate`
- `updatedSince`
- `page`
- `size`
- `sort`

### 8.3 Security

Add machine-client authentication:

- API client registration.
- Client credentials.
- Scoped access.
- Token expiry.
- Revocation.
- Request audit logging.
- Rate limiting.

## 9. Outbound Webhook Delivery

### 9.1 Webhook Events

Support events:

- `invoice.created`
- `invoice.updated`
- `invoice.cleared`
- `invoice.sent`
- `invoice.posted`
- `invoice.paid`
- `invoice.rejected`
- `invoice.final_locked`
- `invoice.compliance_breach`
- `credit_note.created`
- `credit_note.cleared`
- `credit_note.posted`

### 9.2 Webhook Payload

Each webhook should include:

- `eventId`
- `eventType`
- `occurredAt`
- `organizationId`
- `invoiceId`
- `invoiceNumber`
- `idempotencyKey`
- `payloadVersion`
- `data`

### 9.3 Webhook Signing

Use HMAC signing.

Recommended headers:

```text
X-AMS-Event-Id
X-AMS-Timestamp
X-AMS-Signature
X-AMS-Delivery-Id
```

### 9.4 Webhook API Endpoints

Add:

```text
POST /api/integrations/webhooks
GET /api/integrations/webhooks
GET /api/integrations/webhooks/{id}
PUT /api/integrations/webhooks/{id}
DELETE /api/integrations/webhooks/{id}
POST /api/integrations/webhooks/{id}/test
GET /api/integrations/webhooks/{id}/deliveries
POST /api/integrations/webhooks/{id}/deliveries/{deliveryId}/retry
```

### 9.5 Retry Policy

Default retry schedule:

- 1 minute
- 5 minutes
- 15 minutes
- 1 hour
- 6 hours

After maximum attempts, move delivery to `DEAD_LETTER`.

## 10. NRS E-Invoicing Integration

### 10.1 Required Components

Add:

- `NrsEInvoiceService`
- `NrsPayloadMapper`
- `NrsClient`
- `NrsAuthService`
- `EInvoiceSigningService`
- `EInvoiceComplianceService`
- `EInvoiceSubmissionWorker`
- `BuyerReviewStatusWorker`
- `EInvoiceArchiveService`

### 10.2 NRS Authentication

Implement OAuth2 client credentials.

Capabilities:

- Token request.
- Token caching.
- Token expiry detection.
- Automatic token refresh.
- Failure logging.
- Test connection action.

Configuration should be owned by `System_Admin`.

### 10.3 Payload Mapping

Implement JSON or XML mapping compatible with the documented NRS target requirements.

Include mandatory fiscal fields:

- Supplier Tax ID.
- Buyer Tax ID.
- Invoice type code.
- Issue date/time.
- Tax category code.
- VAT percentage.
- VAT amount.
- Line item IDs.
- NRS unit of measurement.
- Quantity.
- Unit price.
- Line extension amount.
- Total amount.

### 10.4 UBL And PEPPOL Alignment

Add a payload abstraction that supports:

- UBL 3.0 field naming and structure.
- PEPPOL BIS Invoicing 3.0 semantics.
- Nigerian VAT/tax-code extensions.

Validate payloads before submission.

### 10.5 Unit Of Measurement Mapping

Add `NrsUnitOfMeasurementMapping`.

Responsibilities:

- Map local line item units to NRS standard codes.
- Block invoice clearance if any line has an unmapped or invalid unit.
- Let Finance Admin maintain business mappings if they are accounting/business-owned.
- Let System Admin upload or refresh official technical lookup tables.

### 10.6 VAT Enforcement

The specification requires standard VAT of 7.5 percent except where an authorized exemption or zero-rated code applies.

Implementation:

- Add a validation rule in the e-invoice compliance layer.
- Ensure NRS-bound invoice payloads use 7.5 percent for standard VAT.
- Support explicit exemption/zero-rated codes.
- Block NRS submission if tax configuration conflicts with compliance rules.

### 10.7 B2B And B2G Pre-Clearance

For B2B/B2G invoices:

1. Accountant creates invoice.
2. System classifies transaction as B2B or B2G.
3. Invoice moves to `PENDING_CLEARANCE`.
4. Local payload is generated.
5. Payload is signed.
6. Payload is submitted synchronously to NRS.
7. System waits for NRS response.
8. If successful, store IRN, QR code, digital stamp, receipt, and response payload.
9. Invoice moves to `CLEARED`.
10. Download, email, webhook dispatch, and issue-to-customer actions become available.

Hard lock:

- No B2B/B2G invoice may be printed, downloaded, emailed, webhook-delivered, or dispatched before clearance.

### 10.8 B2C Async Submission

For B2C invoices:

1. Accountant creates invoice.
2. Invoice can be issued immediately.
3. System creates an async NRS submission job.
4. Worker submits payload to NRS within 24 hours.
5. If submission exceeds the 24-hour window, mark `BREACH`.
6. Compliance breach event is logged and emitted through webhook.

### 10.9 72-Hour Buyer Review Lifecycle

For B2B/B2G invoices:

1. After NRS clearance, set `buyerReviewDeadline = clearedAt + 72 hours`.
2. Start polling or listening for buyer rejection status.
3. If buyer rejects before deadline:
   - Mark invoice `REJECTED_VOID`.
   - Lock invoice against normal posting or delivery.
   - Prompt credit note workflow.
   - Emit `invoice.rejected`.
4. If no rejection occurs before deadline:
   - Mark invoice `FINAL_LOCKED`.
   - Prevent direct modification or local reversal.
   - Require credit note for correction.
   - Emit `invoice.final_locked`.

## 11. Local Signing And Security

### 11.1 Signing

Add `EInvoiceSigningService`.

Requirements:

- Sign raw payloads before transmission.
- Use ECDSA.
- Store signature metadata.
- Store payload hash.
- Use key references, not raw private keys, in application tables.

### 11.2 Key Storage

Support:

- Enterprise key vault integration.
- HSM-backed key references where available.
- Local development fallback with explicit non-production flag.

### 11.3 Transport Security

Use a configured HTTP client for NRS integration.

Requirements:

- Enforce modern TLS.
- Reject insecure endpoints.
- Apply timeouts.
- Apply retry policy only where safe.
- Log sanitized request/response metadata.

### 11.4 At-Rest Protection

For archived signed payloads and NRS responses:

- Encrypt stored content.
- Store checksums.
- Store retention metadata.
- Restrict read access.
- Ensure secrets are never returned through APIs.

## 12. Archive And Retention

The specification requires raw structured payloads and NRS receipts to be retained for at least 6 years.

Implementation:

- Store raw signed JSON/XML payload.
- Store NRS response body.
- Store IRN, QR code, digital stamp, and receipt references.
- Store payload hash and response hash.
- Set `retentionUntil = createdAt + 6 years`.
- Use immutable or append-only storage in production.
- Treat PDF alone as insufficient for legal retention.

## 13. Credit Note Workflow

Add credit note support for rejected or corrected e-invoices.

### 13.1 API Endpoints

Add:

```text
POST /api/accountant/customer-invoices/{invoiceId}/credit-notes
GET /api/accountant/credit-notes/{creditNoteId}
GET /api/accountant/customer-invoices/{invoiceId}/credit-notes
POST /api/accountant/credit-notes/{creditNoteId}/submit-clearance
POST /api/accountant/credit-notes/{creditNoteId}/post
```

### 13.2 Rules

- Cleared or final-locked invoices cannot be edited directly.
- Corrections require credit notes.
- Credit notes should follow NRS clearance where required.
- Credit notes must post correctly into the general ledger.
- Credit note events must be available through webhook delivery.

## 14. System_Admin Role

### 14.1 Purpose

Add a `System_Admin` role for technical and security-sensitive system configuration that should not be managed by Finance Admin or Accountant users.

### 14.2 Role Boundary

`System_Admin` should manage platform and integration settings.

`System_Admin` should not normally:

- Create invoices.
- Post invoices.
- Record invoice payments.
- Approve purchase invoices.
- Maintain chart of accounts.
- Maintain fiscal periods.
- Maintain vendors or customers.
- Override compliance locks without an audited recovery workflow.

Finance Admin should continue to own:

- Chart of accounts.
- Tax jurisdictions.
- Fiscal configuration.
- Approval rules.
- Module controls.
- Business accounting setup.

### 14.3 Role Implementation

Update role model:

- Add `SYSTEM_ADMIN` to `Role.RoleType`.
- Seed the role in startup/bootstrap data if applicable.
- Update security config to protect `/system-admin/**` and `/api/system-admin/**`.
- Ensure navigation only appears for users with `SYSTEM_ADMIN`.

### 14.4 System Admin UI

Add UI route:

```text
/system-admin
```

Add pages:

- Integration Settings.
- NRS/MBS Configuration.
- Security Keys.
- Webhook Platform Settings.
- API Clients.
- Background Jobs.
- Storage And Retention.
- System Health.
- Audit And Recovery.

### 14.5 System Admin API

Add controller:

```text
SystemAdminApiController
```

Suggested endpoints:

```text
GET /api/system-admin/nrs/config
PUT /api/system-admin/nrs/config
POST /api/system-admin/nrs/test-connection

GET /api/system-admin/signing/config
PUT /api/system-admin/signing/config
POST /api/system-admin/signing/test

GET /api/system-admin/webhook-platform/config
PUT /api/system-admin/webhook-platform/config

GET /api/system-admin/api-clients
POST /api/system-admin/api-clients
PUT /api/system-admin/api-clients/{id}
POST /api/system-admin/api-clients/{id}/revoke

GET /api/system-admin/jobs
GET /api/system-admin/jobs/{jobName}
POST /api/system-admin/jobs/{jobName}/pause
POST /api/system-admin/jobs/{jobName}/resume
POST /api/system-admin/jobs/{jobName}/retry-failed

GET /api/system-admin/storage-retention/config
PUT /api/system-admin/storage-retention/config

GET /api/system-admin/health/integrations
GET /api/system-admin/audit/config-changes
```

### 14.6 System Admin Configuration Entities

Add:

- `NrsIntegrationConfig`
- `SigningKeyConfig`
- `WebhookPlatformConfig`
- `ApiClientRegistration`
- `BackgroundJobConfig`
- `StorageRetentionConfig`
- `SystemConfigAuditLog`

### 14.7 Secret Handling

Rules:

- Secrets are write-only through the UI.
- APIs should return masked values only.
- Use secret references where possible.
- Every secret update must be audited.
- Test connection actions should report status without exposing credentials.

## 15. UI Updates

### 15.1 Accountant UI

Update customer invoice UI:

- Display compliance status.
- Display NRS clearance status.
- Display IRN once available.
- Display QR code once available.
- Display delivery status.
- Add download buttons for PDF, JSON, and XML.
- Disable download and send actions until eligible.
- Show NRS submission failures.
- Show compliance breach warnings.
- Show credit note action when invoice is rejected or final-locked.

### 15.2 Finance Admin UI

Update Finance Admin where business-owned configuration is needed:

- Tax code mapping.
- Unit-of-measure business mapping.
- Invoice numbering policy if implemented.
- Organization-level e-invoice enablement.
- Webhook endpoint configuration if this is considered business-owned.

### 15.3 System Admin UI

Create new System Admin area for technical controls:

- NRS credentials and endpoint.
- OAuth2 client settings.
- Signing key references.
- Webhook platform defaults.
- API client access.
- Background job controls.
- Storage and archive settings.
- Integration health.

## 16. Authorization And Audit

### 16.1 Authorization

Add fine-grained permission checks:

- Accountant can create and process invoices.
- Finance Admin can manage business accounting setup.
- System Admin can manage technical/integration setup.
- External API clients can access only scoped invoice APIs.

### 16.2 Audit

Audit:

- Invoice download.
- Invoice send.
- Webhook delivery.
- NRS submission.
- NRS clearance response.
- Buyer rejection.
- Compliance breach.
- Final lock.
- Credit note creation.
- System Admin config changes.
- API client creation/revocation.
- Secret/key configuration changes.

## 17. Background Jobs

Add scheduled/background processing for:

- B2C NRS submission queue.
- Failed NRS submission retry.
- 24-hour compliance breach detection.
- 72-hour buyer review polling.
- Final lock application.
- Webhook retry queue.
- Dead-letter cleanup/reporting.
- Token refresh health check.
- Archive retention validation.

## 18. Error Handling And Operational Recovery

Add standardized error handling for:

- NRS unavailable.
- NRS authentication failure.
- Payload validation failure.
- Invalid Tax ID.
- Missing UoM mapping.
- Invalid VAT configuration.
- Signing failure.
- Storage/archive failure.
- Webhook endpoint timeout.
- Webhook authentication failure.

Operational recovery actions:

- Retry failed NRS submission.
- Replay webhook delivery.
- Rebuild invoice document from locked snapshot.
- Re-run buyer review status check.
- Mark failed queue item as acknowledged with reason.

All recovery actions must be audited.

## 19. Testing Plan

### 19.1 Unit Tests

Add tests for:

- Invoice document generation.
- Download eligibility rules.
- Webhook payload generation.
- Webhook HMAC signing.
- Webhook retry decisions.
- NRS payload mapping.
- VAT enforcement.
- Unit-of-measure mapping.
- ECDSA signing abstraction.
- Compliance status transitions.
- 72-hour final lock calculation.
- 24-hour breach detection.

### 19.2 Integration Tests

Add tests for:

- Customer invoice create to PDF download.
- Customer invoice create to webhook dispatch.
- B2B invoice blocked before NRS clearance.
- B2B invoice cleared then downloadable.
- B2C invoice issued immediately and queued.
- Failed webhook delivery moves to retry.
- Dead-letter after max retries.
- API client can pull scoped invoice data.
- System Admin config write masks secrets on read.

### 19.3 Contract Tests

Add contract tests for:

- External invoice API schema.
- Webhook event schema.
- NRS request schema.
- NRS response schema.

### 19.4 Acceptance Tests

Add end-to-end tests for:

- B2B pre-clearance flow.
- B2G pre-clearance flow.
- B2C 24-hour async flow.
- Buyer rejection within 72 hours.
- Final lock after 72 hours.
- Credit note flow.
- System Admin NRS setup and connection test.
- Invoice webhook delivery and retry.

## 20. Migration Plan

### 20.1 Database Migration

Add migrations for:

- Invoice compliance fields or linked compliance table.
- Invoice documents.
- Invoice dispatches.
- Webhook endpoints.
- Webhook deliveries.
- E-invoice submissions.
- E-invoice archives.
- Credit notes.
- System Admin configuration tables.
- API client registrations.
- System config audit logs.

### 20.2 Backfill

For existing invoices:

- Set `transactionClassification` to null or `UNKNOWN`.
- Set `complianceStatus` to `NOT_REQUIRED` until organization enables e-invoicing.
- Do not retroactively submit old invoices unless explicitly required by a separate migration rule.

### 20.3 Feature Flags

Introduce feature flags:

- `einvoicing.enabled`
- `einvoicing.nrs.enabled`
- `invoice.documents.enabled`
- `invoice.webhooks.enabled`
- `external.invoice.api.enabled`
- `systemAdmin.enabled`

Allow phased rollout by environment and organization.

## 21. Delivery Phases

### Phase 1: System Admin Foundation

Deliver:

- `SYSTEM_ADMIN` role.
- System Admin UI shell.
- System Admin route security.
- NRS config screen with masked secrets.
- Signing config screen.
- Storage/retention config screen.
- API client config foundation.
- Audit logging for config changes.

### Phase 2: Invoice Documents And Download

Deliver:

- Invoice document entity.
- PDF generation.
- JSON/XML invoice document generation.
- Download endpoints.
- Download audit.
- UI download actions.
- Basic compliance gating.

### Phase 3: External Invoice API

Deliver:

- Versioned invoice pull API.
- Pagination and filtering.
- API client registration.
- Scoped machine-client access.
- API audit logging.

### Phase 4: Webhook Delivery

Deliver:

- Webhook endpoint configuration.
- Webhook event model.
- HMAC signing.
- Delivery queue.
- Retry and dead-letter handling.
- Delivery audit UI.
- Invoice event emission.

### Phase 5: NRS Payload And Security

Deliver:

- NRS payload mapper.
- UBL/PEPPOL-aligned structure.
- VAT validation.
- UoM mapping.
- OAuth2 client credentials.
- ECDSA signing abstraction.
- Payload archive.
- NRS test connection.

### Phase 6: B2B/B2G Pre-Clearance

Deliver:

- Transaction classification.
- Synchronous clearance flow.
- IRN, QR, digital stamp persistence.
- Download/send hard locks before clearance.
- Cleared invoice UI state.
- Webhook event for `invoice.cleared`.

### Phase 7: B2C Async Compliance

Deliver:

- Async submission queue.
- 24-hour submission enforcement.
- Compliance breach status.
- Retry queue.
- Breach event and UI alert.

### Phase 8: Buyer Review And Final Lock

Deliver:

- 72-hour buyer review tracking.
- Buyer rejection handling.
- Rejected-void invoice status.
- Final lock.
- Modification prevention after final lock.
- Webhook events.

### Phase 9: Credit Notes

Deliver:

- Credit note entity.
- Credit note UI/API.
- NRS clearance for credit notes where required.
- GL posting for credit notes.
- Webhook events for credit notes.

### Phase 10: Hardening And Production Readiness

Deliver:

- Full test coverage.
- Integration health dashboard.
- Operational recovery actions.
- Metrics and alerts.
- Security review.
- Load/performance validation.
- Production runbook.

## 22. Acceptance Criteria

The implementation is complete when:

- Customer invoices can be generated as PDF, JSON, and XML.
- Eligible invoices can be downloaded through secured endpoints.
- B2B/B2G invoices cannot be downloaded or sent before NRS clearance.
- B2C invoices can be issued immediately and are submitted to NRS within 24 hours.
- NRS clearance stores IRN, QR code, digital stamp, and receipt payload.
- Invoice events can be delivered to configured webhook endpoints.
- Webhook deliveries are signed, retried, audited, and dead-lettered when needed.
- External systems can pull invoices through a versioned API.
- The system supports OAuth2 client credentials for integration access.
- The system signs NRS payloads before submission.
- Raw signed payloads and NRS responses are archived for at least 6 years.
- Buyer rejection within 72 hours marks invoices as rejected-void.
- Invoices are final-locked after the review window.
- Credit notes are available for corrected/rejected invoices.
- `System_Admin` can configure technical integration, signing, API, job, and storage settings.
- Finance Admin and Accountant roles are not burdened with technical security configuration.
- All sensitive configuration changes are audited.

## 23. Key Risks

- NRS API details may differ from the draft specification and require adapter changes.
- UBL/PEPPOL validation can be complex and should be isolated behind mapper/validator services.
- Hard compliance locks may affect existing user workflows and need clear UI messaging.
- Secret storage and signing key management need production-grade infrastructure.
- WORM retention may require external storage capabilities beyond the local application database.
- Backward compatibility must be managed for the existing `send` endpoint.

## 24. Recommended First Sprint

Start with the foundation that reduces future rework:

1. Add `SYSTEM_ADMIN` role and secured System Admin UI shell.
2. Add configuration/audit entities for NRS, signing, storage, and webhook platform settings.
3. Add invoice document entity and PDF/JSON generation service skeleton.
4. Add compliance status model without enforcing all locks yet.
5. Add tests for role access, secret masking, invoice document metadata, and basic download eligibility.

This creates the technical base needed for NRS integration, document download, webhook delivery, and external invoice APIs without destabilizing the existing accounting lifecycle.
