# Remita NRS Aggregator Contract Clarifications

**Purpose** — Use this question set to confirm the commercial, technical and operational terms for integrating our platform with Remita’s NRS e-Invoicing Aggregator API. Please provide written answers, the authoritative API specification and any applicable contract or onboarding schedules. Where a question is not applicable, please state why.

**Integration model** — Our platform will operate as an aggregator for multiple merchants. We need to understand account onboarding, merchant linking, invoice clearance, payment reporting, status notifications, support and service obligations before finalizing implementation.

Prepared from the Aggregator API documentation provided for review. API names and fields below refer to the examples in that document and should be confirmed against the current production contract.

## 1. Commercial relationship and responsibility

- Please confirm that our proposed platform use case qualifies for the Aggregator model, and identify the contracting parties, applicable service terms and any separate NRS or Remita onboarding agreements.
- Which party is responsible for each step: linking and validating merchants, invoice data quality, NRS submission, clearance decisions, QR/IRN presentation, invoice delivery, payment reporting, corrections and customer support?
- What is Remita’s role in the NRS four-corner model for each service mode (APP, SI, BOTH or NONE)? Which mode should we select, and what approvals or accreditation are prerequisites?
- Who bears responsibility for regulatory compliance, rejected or delayed submissions, duplicate invoices, incorrect merchant data, service outages and customer claims? Please identify any indemnities, liability caps, exclusions or insurance requirements.
- What are the fees and billing rules (onboarding, per merchant, per invoice, successful clearance, payment notification, support or other charges)? Please state taxes, currency, invoicing frequency, payment terms, minimums and any volume tiers.
- Are there transaction, merchant, industry, volume, territory or exclusivity restrictions? Can we suspend or remove a merchant, and what happens to outstanding invoices and records after termination?

## 2. Onboarding, identity and credentials

- What onboarding information, due diligence and documents are required from the aggregator and each merchant? Who verifies the merchant’s NRS status before linking?
- How and when are aggregatorId, merchant merchantId, secretKey and any NRS identifiers issued? Are identifiers scoped to one legal entity, environment or account?
- Is the secret key shared by the aggregator or unique per environment/tenant? What are the rotation, revocation, expiry and emergency compromise procedures? Are IP allowlists, key overlap during rotation or separate credentials for read/write operations supported?
- Please provide sandbox and production base URLs, test credentials, access prerequisites, test cases, certification/UAT steps, go-live approval process and expected lead times.
- The specification names a merchant field tin alongside firsBusinessId and firsServiceId. NRS terminology now uses Tax ID. What exact value and format should be sent in tin, and which identifiers are mandatory for each merchant type?

## 3. Merchant linking and profile changes

- Please confirm the authoritative Link Merchant URL and HTTP method. The document shows both /api/v1/remita/firs/aggregator/link-merchants and /api/v1/invoice/aggregator/merchants/link, with different base-variable names.
- Confirm the canonical multipart field names, required/optional fields, size and format limits for logo uploads, allowed serviceMode values and validation rules for merchant identifiers and contacts.
- Address.country is described as ISO 3166 alpha-2, but the sample table contains NGN. Should the value be NG? Can a merchant have multiple addresses, and what is the exact multipart indexing syntax?
- What response indicates the merchant is fully linked and eligible to submit invoices? Is linking synchronous, subject to NRS approval, or later confirmed by a status lookup? How should duplicate or invalid linking requests be handled?
- For Update Merchant Details, which fields can be changed, omitted or cleared? Are address and logo changes supported? Does an update require aggregatorId, and does it affect pending or already-cleared invoices?
- Can merchants be unlinked or deactivated through the API? What happens to historic invoice lookup, payment notifications, webhooks and legal retention if a merchant is unlinked?

## 4. Invoice submission and NRS clearance

- Please supply a complete, versioned schema for Process Invoice, including a field-by-field requiredness table, data types, maximum lengths, accepted enums and validation examples for B2B, B2C and B2G invoices.
- Confirm whether aggregatorId and merchantId are required in the request body for every aggregator invoice, and how the API verifies that the merchant is linked to that aggregator.
- Clarify the buyer and seller identification requirements, Tax ID format, address requirements, currency support, date/time zone format, decimal precision, rounding rules and maximum invoice/line-item sizes.
- Please document line-item product/service classification codes and how unit price, quantity, discounts, fees, tax categories, VAT/withholding or exemptions, subtotal and total must reconcile. Which side performs calculations and which values are authoritative?
- Which invoice kinds, invoice type codes and status values are supported? How should credit notes, debit notes, cancellations, replacements, corrections, advance payments, partial payments and foreign-currency invoices be represented?
- The document says Process Invoice persists the request and queues asynchronous transmission, while describing the IRN as NRS-issued for cleared invoices and returning an IRN immediately. Is the returned IRN provisional or final? At what exact status is it assigned, and may it change?
- Define the meanings and lifecycle of transmitted, delivered, cleared, rejected, pending and any other states. Which status confirms legal NRS clearance, and what rejection details/corrective actions are returned?
- What guarantees exist for invoice persistence, processing time, ordering, duplicate prevention, and recovery after a timeout where our platform cannot tell whether a request succeeded? Is an idempotency key supported, and what are its scope and retention period?
- Does Remita create/sign/submit the NRS-compliant payload and generate the QR code, or must we provide any NRS-specific signature or data? Please provide QR rendering requirements, expiry/verification behavior and document-placement guidance.

## 5. Lookup, payment notification and webhooks

### Lookup Invoice

- Confirm the authoritative lookup endpoint, method and request body. The general rule says every aggregator request includes aggregatorId, but the lookup example/table omits it; should it be supplied?
- Can lookup use IRN or invoiceReference independently? If both are present and conflict, which takes precedence? Please provide the complete response schema and status history/last-updated timestamps.
- What are the polling limits, recommended interval, data-retention window and behavior when an invoice is unknown, not yet transmitted, rejected or temporarily unavailable?

### Payment Notification

- Confirm where aggregatorId is sent. It appears under “PARAMS” in the document but is absent from the sample JSON body; please identify whether it is a query parameter, header or body field.
- Are PENDING, PAID and REJECTED the only accepted values? Can we report partial, multiple, reversed or refunded payments? Are amount, currency, payment date, method and payment reference required or supported?
- What are the duplicate-notification and state-transition rules? Is notification idempotent? How do we correct an earlier notification, and when is the update forwarded to NRS?
- Does the response confirm receipt only or successful NRS update? Please provide processing states, error handling and retry guidance.

### Webhooks

- Please provide the webhook event catalogue, exact payload schema, event identifier, signature/authentication scheme, timestamp/replay protection and secret rotation procedure.
- Which invoice and merchant events are delivered, including acceptance, transmission, clearance, rejection, delivery and payment status? Are events delivered once or at least once, ordered, and uniquely identifiable for deduplication?
- What are the timeout, acknowledgement, retry and backoff rules? Which HTTP response codes count as success? How can we replay or recover missed events, and is there a delivery log?
- How are webhook URLs validated and secured (TLS, IP ranges, allowlisting, redirects)? Can aggregator-level and merchant-level webhook URLs both be configured, and which takes precedence?

## 6. API operation and security controls

- Please provide the current API version and complete OpenAPI/Postman collection, including canonical base URLs, methods, headers, schemas, status codes and examples. Which document controls if the API collection and contract differ?
- What are rate limits, concurrency limits, payload limits and quotas per aggregator/merchant? How are throttling responses identified, and are limits adjustable?
- What are connection/read timeouts and recommended retry rules? Which failures are safe to retry, and how does Remita prevent duplicate processing?
- Confirm TLS requirements, supported authentication/security controls, data encryption at rest, tenant isolation, audit logging, penetration/security evidence and incident notification timelines.
- What invoice and merchant data does Remita store, where is it hosted, which subprocessors can access it, how long is it retained, and how are export, deletion and legal preservation requests handled?
- What are the incident escalation channels and response targets for production issues, suspected key exposure, data incidents, NRS outages and invoice clearance delays?

## 7. Availability, support and contract exit

- Please state the service availability target, measurement method, maintenance windows, notice period, planned/unplanned outage communications and exclusions.
- Define service targets for API response, queue acceptance, NRS transmission, clearance/status updates and webhook delivery. What remedies or service credits apply when targets are missed?
- What support hours, severity definitions, response/resolution targets and escalation contacts apply during onboarding, UAT and production?
- How are NRS specification or API changes announced? What notice, backward-compatibility period, version support and migration assistance will be provided?
- At expiry or termination, can we export merchant, invoice, IRN, QR, payment and status-history records in a usable format? How long will API lookup and webhook delivery remain available, and what deletion certification is provided?

## 8. Documented inconsistencies to resolve

- Link Merchant route: two different paths and base-variable names are shown. Please confirm one production and sandbox endpoint.
- Aggregator identity: the introduction says each aggregator call carries aggregatorId, but Lookup Invoice and Update Merchant examples omit it. Payment Notification lists it as a parameter but not in the body.
- Country code: field guidance says ISO alpha-2 while sample says NGN. Confirm the exact value.
- IRN timing: the IRN is described as assigned by NRS to cleared invoices but returned at initial asynchronous acceptance. Explain whether it is final or provisional.
- Status semantics: define transmitted/delivered and their relationship to NRS receipt, clearance, buyer delivery and rejection.
- Service mode: the general description includes NONE, while the service-mode update example lists APP/SI/BOTH. Confirm supported values, scope and who may change them.

## 9. Requested materials and decisions

- Authoritative, versioned API specification or OpenAPI file and current Postman collection.
- Aggregator and merchant onboarding checklist, service-mode guidance and UAT/certification plan.
- Commercial schedule covering fees, service levels, liability, support, data handling and termination/exit.
- Webhook security/event guide, error catalogue, retry/idempotency policy and status lifecycle diagram.
- Written confirmation of the six inconsistencies listed above, plus named Remita technical and commercial contacts for follow-up.

**Meeting outcome to record:** For each question, capture Remita’s answer, any supporting document or contract clause, the owner, and the action/date required to close the item. Treat unresolved items affecting identity, idempotency, clearance state, payment reporting, security or fees as implementation blockers.
