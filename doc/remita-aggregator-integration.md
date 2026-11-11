# Remita Aggregator Integration

The Remita adapter is a provisional integration based on the supplied Aggregator API document. It sends requests only when both the NRS integration and the separate live-submission switch are enabled. Both are disabled by default.

## Configuration

The existing system NRS configuration now includes:

- `baseUrl`: Remita API base URL.
- `aggregatorId`: the configured aggregator identity.
- `secretKeyReference`: the secret key value or secret reference supplied by the deployment. Store a secret reference here and resolve it securely before enabling production traffic.
- `linkMerchantPath`: configurable route; defaults to `/api/v1/remita/firs/aggregator/link-merchants`. The alternate route in the API document can be set here after confirmation.
- `serviceMode`: APP, SI, BOTH, or NONE.
- `liveSubmissionEnabled`: explicit additional gate for outbound operations.
- Existing connect and read timeout values.

The system-admin NRS configuration API masks the secret reference when returning settings. Do not enable live submission with an unresolved secret reference; the API currently sends the configured value as the `secretKey` header.

## Application endpoints

All operations are under `/api/nrs/aggregator` and are protected by the application’s normal API security configuration:

- `POST /merchants/link`
- `POST /invoices/process`
- `POST /invoices/lookup`
- `POST /payments/notify`
- `PUT /merchants`
- `PUT /service-mode`

Link Merchant sends multipart fields using the documented indexed address names. Invoice submission requires a caller-provided Remita-shaped payload; the existing internal NRS payload mapper is not reused because its schema differs from the Remita example.

## Provisional behavior

- Payment Notification places `aggregatorId` in the query string because the source document lists it under PARAMS, while its JSON example omits it. Confirm with Remita before production use.
- No operation is automatically retried. Process Invoice may create a duplicate after an uncertain timeout, and idempotency behavior is undocumented.
- Lookup is an explicit operation; no background polling is enabled until status meanings, rate limits, and retention are confirmed.
- No webhook route accepts events yet because payload signatures, event identifiers, and acknowledgement rules are undocumented.
- The adapter does not infer clearance from `transmitted`, `delivered`, or the presence of an IRN. Update local invoice compliance state only after Remita confirms which response state means legal clearance.

## Production enablement checklist

Before enabling `liveSubmissionEnabled`, confirm the canonical link route, credentials and secret resolution, required aggregator ID placement, invoice schema, country code, IRN lifecycle, clearance status, idempotency, retries, webhook authentication, and service limits with Remita. Then validate the selected mapping in sandbox and update the adapter to match the confirmed contract.
