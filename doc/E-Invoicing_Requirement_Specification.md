# Business and Technical Requirement Specification: E-Invoicing System

## Document Control
* **Document Version:** 1.0.0
* **Target Region:** Nigeria
* **Regulatory Compliance:** Nigeria Revenue Service (NRS) E-Invoicing Mandate
* **Official Gateway Target:** NRS Merchant Buyer Solution (MBS) Portal (`https://einvoice.nrs.gov.ng/`)
* **Status:** Draft for Technical Review

---

## 1. Executive Summary & Regulatory Context

This document defines the functional, technical, and regulatory requirements for integrating our enterprise ERP application with the **Nigeria Revenue Service (NRS)** Electronic Invoicing infrastructure. 

### 1.1 Legal Framework & Mandate
In accordance with modernised Nigerian fiscal policies, all operating businesses must route commercial invoicing transactions directly through the official **NRS Merchant Buyer Solution (MBS) Portal**. The core objectives are to eliminate manual processing, secure tax collections, and enforce transparency across B2B, B2G, and B2C value chains.

### 1.2 Core System Principles
1. **The Tax ID Transition:** The legacy Taxpayer Identification Number (TIN) is completely deprecated. The system must utilize the official, multi-digit **Tax ID** as the sole legal identifier for all automated validation queries, registrations, and transaction matching.
2. **Standardization:** All data payloads must align structurally with **Universal Business Language (UBL) 3.0** and **PEPPOL BIS Invoicing 3.0** specifications, mapped explicitly to Nigerian tax codes.

---

## 2. Functional Requirements & Transaction Lifecycles

The application must enforce strict process pathways depending on the classification of the buyer entity.

```
       [ Invoice Generated in ERP ]
                     │
         Is Transaction B2B / B2G?
         ├─── Yes ───► [ Synchronous Pre-Clearance ] ───► Send to NRS Portal ───► Wait for IRN & QR ───► [ Allow Print/Issue ]
         │
         └─── No  ───► [ Asynchronous Queue ] ──────────► Issue to Customer ───► Push to NRS (<24 hrs) ───► [ Complete ]
```

### 2.1 B2B (Business-to-Business) & B2G (Business-to-Government) Pathways
* **Synchronous Pre-Clearance Enforcement:** The system must implement a strict hard lock on invoicing modules. No B2B or B2G invoice may be printed, downloaded, emailed, or cleared for dispatch to a client until it has been explicitly validated and signed by the NRS gateway.
* **Response Elements:** The application must successfully receive and embed the official **Invoice Reference Number (IRN)**, a cryptographic digital stamp, and a compliant **QR Code** returned dynamically by the NRS portal prior to finalizing local invoice states.

### 2.2 B2C (Business-to-Consumer) Pathway
* **Asynchronous Queue Management:** B2C transactions can be issued immediately to retail consumers at the point of sale.
* **The 24-Hour Enforcement Window:** The application background queue must automatically forward all B2C transactional payloads to the NRS portal within **24 hours** from the exact timestamps of creation. The system must flag any transaction exceeding this window with a "Compliance Breach" status log.

### 2.3 The 72-Hour Buyer Review Lifecycle
* **Rejection Hook Integration:** For B2B/B2G streams, the system must open a state listener tracking the **72-hour buyer review window**.
* **Lock State:** If a buyer officially rejects an invoice on the NRS portal within 72 hours, the application must automatically mark the internal invoice as "Rejected - Void" and prompt the user to initiate a credit note workflow. 
* **Finality Lock:** After 72 hours without buyer rejection, the invoice must be structurally locked in the database against any future modifications or local reversals.

---

## 3. Data Architecture & Payload Mapping

Every invoice transaction transformed into a digital payload must map cleanly to Nigerian fiscal requirements.

### 3.1 Core Fiscal Constants
* **Standard VAT Rate:** Fixed strictly at **7.5%**. The calculation engine must programmatically enforce this rate unless a specific exemption code or zero-rated identifier is authorized.
* **NRS Unit of Measurement (UoM):** Quantities must be mapped precisely to the official NRS standardized lookup code tables (e.g., kilograms, units, liters, hours). Text-based or custom variations are rejected by the API validator.

### 3.2 Machine-Readable Structural Mapping
The application must generate data payloads natively in either **XML** or **JSON** configurations matching the PEPPOL BIS 3.0 schema framework. Below is the structural layout map required for the 55 core mandatory data fields:

```json
{
  "invoiceHeader": {
    "supplierTaxID": "NG-12345678-0001",
    "buyerTaxID": "NG-87654321-0002",
    "invoiceTypeCode": "380",
    "issueDateTime": "2026-09-16T18:00:00Z"
  },
  "taxTotal": {
    "taxAmount": 750.00,
    "taxCategoryCode": "S",
    "taxPercentage": 7.50
  },
  "invoiceLines": [
    {
      "itemLineID": "1",
      "nrsUnitOfMeasurement": "HUR",
      "quantity": 10.00,
      "unitPrice": 1000.00,
      "lineExtensionAmount": 10000.00,
      "vatAmount": 750.00
    }
  ]
}
```

---

## 4. Technical, Integration & Security Specifications

### 4.1 API & Authentication Standards
* **Protocol & Format:** RESTful APIs executing JSON or XML structural exchanges.
* **Access Point Security:** Authentication must be orchestrated using **OAuth 2.0 (Client Credentials Grant)** via verified Access Point Providers approved by the NRS.
* **Token Rotation:** The system must securely handle bearer tokens, executing automatic refresh algorithms without requiring administrative intervention during business hour batches.

### 4.2 Local Cryptographic Signing
* **Pre-Transit Signing:** In addition to transport-layer defenses, every raw transactional payload must be digitally signed locally by the ERP application before it enters the outbound integration pipe.
* **Algorithm Standard:** Enforce **ECDSA (Elliptic Curve Digital Signature Algorithm)** using private keys securely stored within an Enterprise Key Vault or Hardware Security Module (HSM).

### 4.3 Data Encryption & Network Security
* **In-Transit Protection:** Mandatory enforcement of **TLS 1.3** across all integration endpoints. Fallbacks to TLS 1.1 or 1.0 must be programmatically rejected by the local firewall rules.
* **At-Rest Protection:** Local application storage layers holding structural payload logs must use **AES-256 bit encryption**.

### 4.4 Data Retention, Compliance & Archiving
* **Legal Retention Lifecycle:** All raw, validated machine-readable payloads (the original signed XML or JSON schemas along with the corresponding NRS receipts) must be archived for a minimum statutory period of **6 years**.
* **Immutability Mandate:** Storage blocks must run on Write-Once-Read-Many (WORM) configurations. Flattened representations, such as standard PDF visualization layouts, are **not legally sufficient** on their own during official tax audits. The underlying raw structured data must be preserved.

---
*Disclaimer: This specification document is modeled strictly on the technical architectures dictated by standard e-invoicing framework transitions within sub-Saharan Africa fiscal networks.*