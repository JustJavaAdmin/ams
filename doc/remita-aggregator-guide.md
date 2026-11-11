# Remita NRS Aggregator Integration Guide

## 1. Purpose and current status

This guide explains how to configure and call the Remita NRS Aggregator integration in AMS. The integration is an outbound adapter: AMS accepts an application request, validates it, sends it to the configured Remita endpoint, and returns Remita's JSON response.

The adapter is **provisional**. It follows the supplied `Remita NRS Aggregator.docx`, which contains conflicting examples and leaves important production behavior unspecified. Outbound operations are disabled unless both the general NRS integration switch and the separate live-submission switch are enabled. Keep those switches off until sandbox credentials and the unresolved contract items in this guide have been addressed.

The current implementation does not automatically create a Remita request from an AMS customer invoice. In particular, the existing AMS NRS payload mapper produces a different schema from Remita's Process Invoice example. A caller must currently build and send a Remita-shaped request to the AMS API.

## 2. Integration flow

```text
Client application
      |
      | authenticated request to AMS
      v
AMS /api/nrs/aggregator/*
      |
      | validation, configuration gates, secretKey header
      v
Remita Aggregator API
      |
      | JSON response
      v
AMS returns the Remita response to the caller
```

AMS currently performs required-field validation, checks configured aggregator identity where supplied, applies connect/read timeouts, and maps downstream HTTP/network errors to a gateway error. It does not persist the request or response, reconcile Remita state into the AMS invoice entity, retry requests, or process Remita webhooks.

## 3. Before you start

Obtain these items from the Remita integration contact:

1. Sandbox base URL and production base URL.
2. Aggregator ID and sandbox/production `secretKey` credentials.
3. Canonical Link Merchant path and method.
4. Merchant onboarding requirements, required identifiers, and accepted service modes.
5. Current Process Invoice schema, including required fields, accepted enums, dates, currency, classifications, taxes, and amount reconciliation rules.
6. Canonical placement of `aggregatorId` for every operation.
7. IRN assignment and status lifecycle, including the status that proves NRS clearance.
8. Idempotency, timeout recovery, retry, rate-limit, and polling rules.
9. Webhook events, payloads, authentication/signing, acknowledgement, retry, replay, and deduplication rules.
10. Secret storage and rotation procedures, support contacts, and production go-live approval.

The source API document has two Link Merchant routes, inconsistent aggregator ID examples, `NG` and `NGN` country examples, and an asynchronous Process Invoice description that also returns an IRN immediately. Get written answers before relying on those behaviors.

## 4. Configure AMS

NRS settings are managed through the system-admin API. These endpoints use the application's existing security and role controls; call them with an appropriately authorized admin identity.

### Read current settings

```http
GET /api/system-admin/nrs/config
```

The route prefix may depend on the class-level mapping in the deployed version. See `SystemAdminApiController` for the exact prefix. Secret fields are masked in the returned configuration.

### Update settings

```http
PUT /api/system-admin/nrs/config
Content-Type: application/json
```

Example sandbox configuration (replace the sample values):

```json
{
  "environment": "SANDBOX",
  "enabled": true,
  "baseUrl": "https://sandbox.example.remita.net",
  "aggregatorId": "AGG123456",
  "secretKeyReference": "secret-store-reference-or-current-secret-value",
  "linkMerchantPath": "/api/v1/remita/firs/aggregator/link-merchants",
  "serviceMode": "BOTH",
  "liveSubmissionEnabled": false,
  "connectTimeoutSeconds": 10,
  "readTimeoutSeconds": 30
}
```

Configuration field notes:

| Field | Meaning | Guidance |
| --- | --- | --- |
| `environment` | `SANDBOX` or `PRODUCTION` label | The label does not select a URL automatically. Set `baseUrl` explicitly. |
| `enabled` | General NRS integration switch | Must be `true` for adapter requests. |
| `baseUrl` | Remita API host/base path | Use the exact host Remita supplies. Do not include an operation path. |
| `aggregatorId` | Configured aggregator identity | Requests that carry an ID are checked against this value. |
| `secretKeyReference` | Credential input | Current code sends this configured value directly in the `secretKey` header. A reference is not yet resolved from a secret manager. |
| `linkMerchantPath` | Link Merchant route | Defaults to `/api/v1/remita/firs/aggregator/link-merchants`; can be set to the alternate documented route once confirmed. |
| `serviceMode` | Configured account mode | Accepted configuration values: `APP`, `SI`, `BOTH`, or `NONE`. |
| `liveSubmissionEnabled` | Additional outbound-call gate | Keep `false` while configuring or testing settings. Both this and `enabled` must be `true` to send a request. |
| `connectTimeoutSeconds` | Connection timeout | Positive integer; default is 10 seconds. |
| `readTimeoutSeconds` | Response timeout | Positive integer; default is 30 seconds. |

The secret field is masked when read back. To retain the current stored secret while updating other fields, omit `secretKeyReference` from the update or send the mask value (`********`); the config service ignores the mask. A blank value does not clear the existing secret.

### Test configuration status

```http
POST /api/system-admin/nrs/test-connection
```

Despite its name, this endpoint does not make a network request to Remita. It reports whether required settings are present and whether outbound calls are enabled. A `CONFIGURED` result is not proof that DNS, credentials, endpoint routing, or Remita service health have been verified.

### Recommended enablement sequence

1. Set the environment label and sandbox `baseUrl`.
2. Set `aggregatorId`, the `secretKey` credential, the confirmed Link Merchant path, and timeouts.
3. Leave `enabled` false while reviewing the values; then enable general NRS configuration for sandbox use.
4. Call the config-status endpoint and confirm the displayed values, noting that secrets are masked.
5. Keep `liveSubmissionEnabled` false until credentials and expected requests are reviewed. Requests will be rejected while this gate is false.
6. When ready for a controlled sandbox call, enable `liveSubmissionEnabled` and use a sandbox merchant and test invoice only.
7. Keep production configuration disabled until Remita approves UAT and all production blockers below are resolved.

## 5. AMS API operations

All adapter endpoints are rooted at `/api/nrs/aggregator`. They require the application's normal authentication and authorization. The adapter returns the JSON body received from Remita; it does not wrap it in a separate AMS response object.

### Link a merchant

```http
POST /api/nrs/aggregator/merchants/link
Content-Type: application/json
```

Example:

```json
{
  "aggregatorId": "AGG123456",
  "merchant": {
    "name": "Example Merchant Ltd",
    "merchantId": "MERCHANT-001",
    "firsBusinessId": "6dj03c76-1d83-4a39-a4de-51bd70547aef",
    "firsServiceId": "94ND90NR",
    "tin": "TIN-0099990001",
    "email": "billing@example.test",
    "phone": "+2348012345678",
    "webhookUrl": "https://example.test/remita-events",
    "serviceMode": "BOTH",
    "addresses": [
      {
        "line": "1 Example Road",
        "city": "Lagos",
        "postalZone": "100001",
        "country": "NG"
      }
    ]
  }
}
```

AMS validates required fields, verifies that the request's `aggregatorId` equals the configured ID, then converts the merchant object to Remita's multipart field names. Address fields are emitted as `merchant.addresses[0].line`, `merchant.addresses[0].city`, and so on. For multiple addresses, indexes increase from zero. Logo upload is not supported by the AMS endpoint yet.

Use the country code Remita confirms. The source document says ISO 3166-1 alpha-2 and shows `NG` in one example but `NGN` in another.

### Process an invoice

```http
POST /api/nrs/aggregator/invoices/process
Content-Type: application/json
```

Example payload based on the documented fields:

```json
{
  "invoiceRef": "INV-2026-0001",
  "aggregatorId": "AGG123456",
  "merchantId": "MERCHANT-001",
  "currency": "NGN",
  "issueDate": "11-11-2026",
  "dueDate": "11-12-2026",
  "status": "issued",
  "orderReference": "ORDER-9001",
  "customer": {
    "customerId": "BUYER-009",
    "name": "Example Buyer Ltd",
    "email": "accounts@example.test",
    "phone": "+2348098765432",
    "identifiers": {
      "tin": "24988238-0001"
    },
    "address": {
      "line": "9 Market Road",
      "city": "Ibadan",
      "country": "NG",
      "postalZone": "200001"
    }
  },
  "lineItems": [
    {
      "name": "Consulting service",
      "description": "Monthly consulting",
      "sellersItemIdentification": "CONSULT-MONTH",
      "isicCode": "CC-002",
      "serviceCategory": "Professional services",
      "unitPrice": 100000,
      "quantity": 1,
      "totalAmount": 107500,
      "taxCode": "STANDARD_VAT",
      "taxRate": 7.5,
      "taxAmount": 7500
    }
  ],
  "totals": {
    "totalLineAmount": 107500,
    "totalTax": 7500,
    "grandTotal": 107500
  },
  "firsSpecific": {
    "invoiceTypeCode": "396",
    "paymentStatus": "PENDING",
    "invoiceKind": "B2B"
  }
}
```

This is an illustrative schema, not a guaranteed accepted invoice. Remita's example includes ambiguous calculation conventions: in some examples `totalAmount` appears to include tax while the aggregate totals add tax again. Confirm whether line totals and invoice totals are tax-exclusive or inclusive before using production amounts. Use valid JSON; do not copy commented JSON from the supplied Word document.

AMS checks the listed required fields and that `aggregatorId` matches configured settings. If `firsSpecific.paymentStatus` is supplied, it must be `PENDING`, `PAID`, or `REJECTED`. AMS does not currently validate the full Remita schema, calculate invoice totals, or map AMS customer invoices into this payload.

### Look up an invoice

```http
POST /api/nrs/aggregator/invoices/lookup
Content-Type: application/json
```

```json
{
  "merchantId": "MERCHANT-001",
  "irn": "1966926849272-BBCCC154-20260424"
}
```

Supply `merchantId` and at least one of `irn` or `invoiceReference`. The adapter currently accepts both identifiers if both are present; it does not detect whether they conflict. Ask Remita which identifier takes precedence if both are sent.

### Notify payment status

```http
POST /api/nrs/aggregator/payments/notify
Content-Type: application/json
```

```json
{
  "merchantId": "MERCHANT-001",
  "irn": "1966926849272-BBCCC154-20260424",
  "paymentStatus": "PAID"
}
```

Allowed statuses currently enforced by AMS are `PENDING`, `PAID`, and `REJECTED`. Based on the source document's PARAMS table, AMS currently sends the configured `aggregatorId` as a query parameter on the Remita request. The source example omits it from the JSON body. Confirm this placement with Remita before using payment notification in production.

### Update merchant details

```http
PUT /api/nrs/aggregator/merchants
Content-Type: application/json
```

```json
{
  "merchantId": "MERCHANT-001",
  "name": "Example Merchant Ltd",
  "firsBusinessId": "6dj03c76-1d83-4a39-a4de-51bd70547aef",
  "firsServiceId": "94ND90NR",
  "tin": "TIN-0099990001",
  "email": "billing@example.test",
  "phone": "+2348012345678",
  "webhookUrl": "https://example.test/remita-events"
}
```

Only `merchantId` is required by the current AMS request model. Confirm which fields Remita permits to be omitted, cleared, or changed and whether updates affect existing invoices.

### Update aggregator service mode

```http
PUT /api/nrs/aggregator/service-mode
Content-Type: application/json
```

```json
{
  "aggregatorId": "AGG123456",
  "serviceMode": "BOTH"
}
```

The `aggregatorId` must equal the configured value. The upstream document lists APP, SI, and BOTH for this operation, while another section includes NONE. Confirm whether NONE can be set through the update operation before using it.

## 6. Validation, responses, and errors

The client forwards Remita's JSON response body. A typical successful response in the source document has `status`, `message`, and `data`; invoice processing may include `issueDate`, `dueDate`, `paymentStatus`, `transmitted`, `delivered`, `irn`, and `qrString`. Treat the exact shape as variable until Remita provides a versioned schema.

AMS behavior:

- Missing required fields or request/configured aggregator ID mismatch: HTTP 400.
- Missing configuration, disabled integration, or disabled live submission: HTTP 503.
- Remita HTTP errors: AMS returns HTTP 502 with an error message containing the upstream HTTP status code. The upstream error body is not returned.
- Network/connect/read failures: AMS returns HTTP 502 with a generic reachability message.
- The client does not log request bodies or secrets.

Do not interpret a successful HTTP response, a returned IRN, `transmitted: true`, or `delivered: true` as proof of NRS clearance. The definitive clearance state must be confirmed from Remita's status contract.

## 7. What is not implemented yet

| Missing capability | Current impact | What to do once Remita supplies details |
| --- | --- | --- |
| Secret-manager resolution | `secretKeyReference` is sent as-is in the `secretKey` header. | Add a credential resolver for the approved vault/provider, store only a secret identifier in DB, add rotation behavior, and test that secret values never appear in API output or logs. |
| Invoice mapping from AMS | Caller must build Remita's JSON; AMS's existing NRS payload shape differs. | Obtain the official Remita schema, map AMS invoice/customer/line/tax data to it, and define handling for unsupported fields and validation failures. |
| Persistent outbound attempt and response archive | Adapter currently returns the response without recording it. | Add a submission entity linked to the AMS invoice and merchant, store request hash and redacted/appropriately protected request/response payloads, and define retention/access controls. |
| Local invoice lifecycle updates | Remita status is not written back to `CustomerInvoice` or `EInvoiceSubmission`. | Map confirmed Remita statuses to AMS compliance states, preserving provisional and cleared states separately. Only mark cleared on the documented legal clearance state. |
| Merchant-to-aggregator registry | AMS forwards merchant IDs but does not maintain a dedicated link state. | Persist merchant onboarding results and identifiers, prevent invoice submission for merchants not confirmed linked, and support disabled/unlinked records if Remita documents the behavior. |
| Idempotency and safe retry | No calls are automatically retried; a timeout may leave the result uncertain. | Use Remita's idempotency key if available. Otherwise implement lookup-based recovery and explicit retry policies per operation; never blindly retry invoice creation. |
| Background lookup/polling | Lookup is caller initiated; status refresh is not scheduled. | Configure polling interval, max age, per-merchant rate limit, and backoff based on Remita limits. Persist each status change and last-checked time. |
| Webhook receiver | No route accepts or authenticates Remita events. | Implement the supplied event catalogue, signature verification, timestamp/replay defense, event deduplication, durable queueing, acknowledgement codes, retry/replay support, and audit trail. |
| Logo upload on merchant link | AMS request has no logo field or file upload support. | Add a multipart-capable AMS endpoint and enforce accepted file type, size, storage, and malware scanning rules provided by Remita and the application. |
| Full API contract validation | Validation currently checks basic required fields only. | Add schema constraints, supported enum checks, date formats, currency rules, maximum sizes, arithmetic reconciliation, and sandbox contract tests from the official specification. |
| Production health check | The admin test-connection route checks settings only, not live connectivity. | Implement a documented low-risk health call or sandbox operation, with secret-safe diagnostics and no side effects. |
| Contract controls and service operations | Rate limits, support SLA, audit, retention, and incident rules are not represented in this adapter. | Configure quotas, operational alerts, data retention, support escalation, and environment-specific endpoint/credential ownership once contractual values are known. |

## 8. Safe sandbox procedure

1. Obtain a sandbox account and verify the endpoint and credentials with Remita.
2. Configure sandbox values with `liveSubmissionEnabled` false.
3. Confirm the admin response masks the secret and reports settings as complete. Remember that this is a configuration check only.
4. Prepare a test merchant with values approved by Remita. Confirm the country code and service mode.
5. Enable `liveSubmissionEnabled` only for the sandbox window, then call Link Merchant once.
6. Record the returned response securely. Confirm whether the merchant is active immediately or requires separate approval/status lookup.
7. Submit a non-production test invoice only after the schema and totals are confirmed. Record the returned status and IRN without interpreting them as final clearance.
8. Use invoice lookup to observe status transitions according to Remita's sandbox instructions.
9. Test payment notification only after Remita confirms state-transition and `aggregatorId` rules.
10. Disable `liveSubmissionEnabled` after the test window and retain test evidence according to internal policy.

Do not use production customer data for sandbox tests. Avoid repeat Process Invoice calls after timeouts until you first resolve the outcome through lookup or Remita support.

## 9. Production readiness checklist

Before enabling production outbound calls, confirm all of the following:

- The current OpenAPI specification/Postman collection and canonical production base URL are available and versioned.
- The Link Merchant path is confirmed and the configured `linkMerchantPath` matches it.
- `aggregatorId` location is confirmed for every operation, especially lookup, payment notification, and merchant update.
- The secret is resolved via an approved secret store and rotation/revocation has been tested.
- Tax ID, merchant identifiers, country code, address syntax, and service-mode rules are confirmed.
- The official invoice schema and totals calculation rules are implemented and validated.
- Idempotency, timeout recovery, retry, polling limits, and rate limits are implemented.
- The legal clearance status and IRN lifecycle are confirmed and mapped to AMS state safely.
- Webhook authentication, events, acknowledgements, retries, ordering, deduplication, and replay handling are implemented if webhooks will be used.
- Request/response persistence, access control, redaction/encryption, and retention are reviewed.
- Sandbox UAT is complete, Remita has approved go-live, support/escalation contacts are recorded, and operational monitoring is ready.

After confirmation, set `environment` to `PRODUCTION`, set the production `baseUrl` and credential, verify the configuration, then enable `enabled` and `liveSubmissionEnabled` under change control. These flags are independent: both must be true for outbound calls.

## 10. Operational troubleshooting

| Symptom | Likely cause | Check |
| --- | --- | --- |
| HTTP 503 before any upstream response | One or both feature gates are off, or configuration is incomplete. | Check `enabled`, `liveSubmissionEnabled`, `baseUrl`, `aggregatorId`, and `secretKeyReference` in the admin config. |
| HTTP 400 on an aggregator call | Required request field missing or request aggregator ID differs from configured ID. | Compare the request to the examples and verify the configured aggregator ID. |
| HTTP 502 with upstream status | Remita rejected the request or returned an error HTTP status. | Use Remita's error catalogue and support process; current AMS response does not expose the upstream response body. |
| HTTP 502 reachability message | DNS, TLS, network policy, timeout, or upstream outage. | Verify the base URL, outbound network access, TLS trust, and timeout settings; check Remita status/support channel. |
| Remita accepts request but AMS caller times out | Read timeout may be shorter than processing; request outcome is unknown. | Do not resubmit Process Invoice automatically. Query by invoice reference/IRN if available or ask Remita to confirm processing. |
| Admin health says configured but calls fail | The endpoint is a settings check, not a network check. | Validate sandbox connectivity with a controlled operation and inspect operational/network logs without exposing credentials. |

## 11. Related files

- `doc/Remita NRS Aggregator.docx`: supplied Remita API reference.
- `doc/Remita_NRS_Aggregator_Contract_Clarifications.md`: contract questions and documented inconsistencies.
- `doc/remita-aggregator-integration.md`: concise implementation notes.
- `src/main/java/com/justjava/ams/einvoicing/nrs/aggregator/RemitaAggregatorController.java`: AMS adapter routes.
- `src/main/java/com/justjava/ams/einvoicing/nrs/aggregator/RemitaAggregatorClient.java`: outbound HTTP behavior and feature gates.
- `src/main/java/com/justjava/ams/einvoicing/nrs/aggregator/dto/AggregatorRequests.java`: request field models.
- `src/main/java/com/justjava/ams/systemAdmin/SystemAdminApiController.java`: admin configuration routes.
- `src/main/java/com/justjava/ams/systemAdmin/service/SystemAdminConfigService.java`: settings updates and secret masking.
