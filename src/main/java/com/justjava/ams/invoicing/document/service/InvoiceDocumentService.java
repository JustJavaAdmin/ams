package com.justjava.ams.invoicing.document.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.ams.accountant.dto.CustomerInvoiceDTO;
import com.justjava.ams.accountant.dto.InvoiceLineItemDTO;
import com.justjava.ams.accountant.service.CustomerInvoiceService;
import com.justjava.ams.invoicing.document.dto.InvoiceDocumentDTO;
import com.justjava.ams.invoicing.document.entity.InvoiceDocument;
import com.justjava.ams.invoicing.document.repository.InvoiceDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceDocumentService {

    private final CustomerInvoiceService customerInvoiceService;
    private final InvoiceDocumentRepository invoiceDocumentRepository;
    private final ObjectMapper objectMapper;

    public InvoiceDocumentDTO generateCustomerInvoiceDocument(Long invoiceId, String format, String generatedBy) {
        CustomerInvoiceDTO invoice = customerInvoiceService.getInvoice(invoiceId);
        requireDownloadEligible(invoice);
        InvoiceDocument.DocumentFormat documentFormat = parseFormat(format);
        byte[] content = switch (documentFormat) {
            case PDF -> renderPdf(invoice);
            case JSON -> renderJson(invoice);
            case XML -> renderXml(invoice);
        };
        InvoiceDocument document = InvoiceDocument.builder()
                .invoiceId(invoice.getId())
                .invoiceType(InvoiceDocument.InvoiceType.CUSTOMER_INVOICE)
                .format(documentFormat)
                .fileName(fileName(invoice, documentFormat))
                .contentType(contentType(documentFormat))
                .checksum(sha256(content))
                .generatedBy(generatedBy)
                .sourceStatus(invoice.getStatus())
                .content(content)
                .build();
        return toDTO(invoiceDocumentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public List<InvoiceDocumentDTO> getCustomerInvoiceDocuments(Long invoiceId) {
        customerInvoiceService.getInvoice(invoiceId);
        return invoiceDocumentRepository.findByInvoiceTypeAndInvoiceIdOrderByGeneratedAtDesc(
                        InvoiceDocument.InvoiceType.CUSTOMER_INVOICE,
                        invoiceId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DownloadedInvoiceDocument getCustomerInvoiceDocumentContent(Long documentId) {
        InvoiceDocument document = invoiceDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice document not found"));
        if (!InvoiceDocument.InvoiceType.CUSTOMER_INVOICE.equals(document.getInvoiceType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document is not a customer invoice document");
        }
        return new DownloadedInvoiceDocument(
                document.getFileName(),
                document.getContentType(),
                document.getContent(),
                document.getChecksum());
    }

    public DownloadedInvoiceDocument generateCustomerInvoiceDownload(Long invoiceId, String format, String generatedBy) {
        InvoiceDocumentDTO document = generateCustomerInvoiceDocument(invoiceId, format, generatedBy);
        return getCustomerInvoiceDocumentContent(document.getId());
    }

    private byte[] renderJson(CustomerInvoiceDTO invoice) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(structuredInvoice(invoice));
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to render invoice JSON", ex);
        }
    }

    private byte[] renderXml(CustomerInvoiceDTO invoice) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<invoice>\n");
        element(xml, "invoiceId", invoice.getId());
        element(xml, "invoiceNumber", invoice.getInvoiceNumber());
        element(xml, "status", invoice.getStatus());
        element(xml, "transactionClassification", invoice.getTransactionClassification());
        element(xml, "complianceStatus", invoice.getComplianceStatus());
        element(xml, "supplierTaxId", invoice.getSupplierTaxId());
        element(xml, "buyerTaxId", invoice.getBuyerTaxId());
        element(xml, "nrsIrn", invoice.getNrsIrn());
        element(xml, "nrsQrCode", invoice.getNrsQrCode());
        element(xml, "nrsDigitalStamp", invoice.getNrsDigitalStamp());
        element(xml, "nrsReceiptReference", invoice.getNrsReceiptReference());
        element(xml, "nrsClearedAt", invoice.getNrsClearedAt());
        element(xml, "buyerReviewDeadline", invoice.getBuyerReviewDeadline());
        element(xml, "finalLockedAt", invoice.getFinalLockedAt());
        element(xml, "rejectedAt", invoice.getRejectedAt());
        element(xml, "rejectionReason", invoice.getRejectionReason());
        element(xml, "complianceBreachAt", invoice.getComplianceBreachAt());
        element(xml, "customerName", invoice.getCustomerName());
        element(xml, "customerEmail", invoice.getCustomerEmail());
        element(xml, "invoiceDate", invoice.getInvoiceDate());
        element(xml, "dueDate", invoice.getDueDate());
        element(xml, "subtotal", invoice.getSubtotal());
        element(xml, "taxCode", invoice.getTaxCode());
        element(xml, "taxRate", invoice.getTaxRate());
        element(xml, "taxAmount", invoice.getTaxAmount());
        element(xml, "totalAmount", invoice.getTotalAmount());
        element(xml, "amountPaid", invoice.getAmountPaid());
        xml.append("  <lineItems>\n");
        invoice.getLineItems().stream()
                .sorted(Comparator.comparing(InvoiceLineItemDTO::getId, Comparator.nullsLast(Long::compareTo)))
                .forEach(line -> {
                    xml.append("    <lineItem>\n");
                    element(xml, "id", line.getId(), 6);
                    element(xml, "description", line.getDescription(), 6);
                    element(xml, "quantity", line.getQuantity(), 6);
                    element(xml, "unitPrice", line.getUnitPrice(), 6);
                    element(xml, "lineTotal", line.getLineTotal(), 6);
                    element(xml, "accountCode", line.getAccountCode(), 6);
                    xml.append("    </lineItem>\n");
                });
        xml.append("  </lineItems>\n");
        xml.append("</invoice>\n");
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] renderPdf(CustomerInvoiceDTO invoice) {
        StringBuilder lines = new StringBuilder();
        lines.append("AMS Customer Invoice\\n");
        lines.append("Invoice: ").append(value(invoice.getInvoiceNumber())).append("\\n");
        lines.append("Status: ").append(value(invoice.getStatus())).append("\\n");
        lines.append("Compliance: ").append(value(invoice.getComplianceStatus())).append("\\n");
        lines.append("IRN: ").append(value(invoice.getNrsIrn())).append("\\n");
        lines.append("Customer: ").append(value(invoice.getCustomerName())).append("\\n");
        lines.append("Email: ").append(value(invoice.getCustomerEmail())).append("\\n");
        lines.append("Invoice Date: ").append(value(invoice.getInvoiceDate())).append("\\n");
        lines.append("Due Date: ").append(value(invoice.getDueDate())).append("\\n\\n");
        lines.append("Lines\\n");
        invoice.getLineItems().stream()
                .sorted(Comparator.comparing(InvoiceLineItemDTO::getId, Comparator.nullsLast(Long::compareTo)))
                .forEach(line -> lines.append("- ")
                        .append(value(line.getDescription()))
                        .append(" | Qty ").append(money(line.getQuantity()))
                        .append(" | Unit ").append(money(line.getUnitPrice()))
                        .append(" | Total ").append(money(line.getLineTotal()))
                        .append("\\n"));
        lines.append("\\nSubtotal: ").append(money(invoice.getSubtotal())).append("\\n");
        lines.append("Tax: ").append(money(invoice.getTaxAmount())).append("\\n");
        lines.append("Total: ").append(money(invoice.getTotalAmount())).append("\\n");
        lines.append("Paid: ").append(money(invoice.getAmountPaid())).append("\\n");
        lines.append("Generated: ").append(OffsetDateTime.now()).append("\\n");
        return minimalPdf(lines.toString());
    }

    private Map<String, Object> structuredInvoice(CustomerInvoiceDTO invoice) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("documentType", "CUSTOMER_INVOICE");
        root.put("invoiceId", invoice.getId());
        root.put("organizationId", invoice.getOrganizationId());
        root.put("invoiceNumber", invoice.getInvoiceNumber());
        root.put("status", invoice.getStatus());
        root.put("transactionClassification", invoice.getTransactionClassification());
        root.put("complianceStatus", invoice.getComplianceStatus());
        root.put("supplierTaxId", invoice.getSupplierTaxId());
        root.put("buyerTaxId", invoice.getBuyerTaxId());
        root.put("nrsIrn", invoice.getNrsIrn());
        root.put("nrsQrCode", invoice.getNrsQrCode());
        root.put("nrsDigitalStamp", invoice.getNrsDigitalStamp());
        root.put("nrsReceiptReference", invoice.getNrsReceiptReference());
        root.put("nrsClearedAt", stringOrNull(invoice.getNrsClearedAt()));
        root.put("buyerReviewDeadline", stringOrNull(invoice.getBuyerReviewDeadline()));
        root.put("finalLockedAt", stringOrNull(invoice.getFinalLockedAt()));
        root.put("rejectedAt", stringOrNull(invoice.getRejectedAt()));
        root.put("rejectionReason", invoice.getRejectionReason());
        root.put("complianceBreachAt", stringOrNull(invoice.getComplianceBreachAt()));
        root.put("customerId", invoice.getCustomerId());
        root.put("customerName", invoice.getCustomerName());
        root.put("customerEmail", invoice.getCustomerEmail());
        root.put("customerPhone", invoice.getCustomerPhone());
        root.put("customerAddress", invoice.getCustomerAddress());
        root.put("invoiceDate", stringOrNull(invoice.getInvoiceDate()));
        root.put("dueDate", stringOrNull(invoice.getDueDate()));
        root.put("subtotal", invoice.getSubtotal());
        root.put("taxJurisdictionId", invoice.getTaxJurisdictionId());
        root.put("taxCode", invoice.getTaxCode());
        root.put("taxRate", invoice.getTaxRate());
        root.put("taxCalculationType", invoice.getTaxCalculationType());
        root.put("taxAmount", invoice.getTaxAmount());
        root.put("totalAmount", invoice.getTotalAmount());
        root.put("amountPaid", invoice.getAmountPaid());
        root.put("notes", invoice.getNotes());
        root.put("createdAt", stringOrNull(invoice.getCreatedAt()));
        root.put("updatedAt", stringOrNull(invoice.getUpdatedAt()));
        root.put("lineItems", invoice.getLineItems().stream()
                .sorted(Comparator.comparing(InvoiceLineItemDTO::getId, Comparator.nullsLast(Long::compareTo)))
                .map(this::structuredLine)
                .collect(Collectors.toList()));
        return root;
    }

    private void requireDownloadEligible(CustomerInvoiceDTO invoice) {
        String classification = value(invoice.getTransactionClassification()).toUpperCase();
        if (!("B2B".equals(classification) || "B2G".equals(classification))) {
            return;
        }
        String complianceStatus = value(invoice.getComplianceStatus()).toUpperCase();
        if ("CLEARED".equals(complianceStatus) || "FINAL_LOCKED".equals(complianceStatus)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "B2B and B2G invoices cannot be downloaded until NRS clearance is complete");
    }

    private Map<String, Object> structuredLine(InvoiceLineItemDTO line) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", line.getId());
        row.put("chartAccountId", line.getChartAccountId());
        row.put("accountCode", line.getAccountCode());
        row.put("accountName", line.getAccountName());
        row.put("description", line.getDescription());
        row.put("quantity", line.getQuantity());
        row.put("unitPrice", line.getUnitPrice());
        row.put("lineTotal", line.getLineTotal());
        row.put("notes", line.getNotes());
        return row;
    }

    private byte[] minimalPdf(String text) {
        String[] rawLines = text.split("\\\\n");
        StringBuilder stream = new StringBuilder();
        stream.append("BT\n/F1 10 Tf\n50 790 Td\n14 TL\n");
        for (String rawLine : rawLines) {
            stream.append("(").append(escapePdf(rawLine)).append(") Tj\nT*\n");
        }
        stream.append("ET\n");
        byte[] streamBytes = stream.toString().getBytes(StandardCharsets.US_ASCII);

        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + streamBytes.length + " >>\nstream\n" + stream + "endstream"
        );

        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        int[] offsets = new int[objects.size() + 1];
        for (int i = 0; i < objects.size(); i++) {
            offsets[i + 1] = pdf.toString().getBytes(StandardCharsets.US_ASCII).length;
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xrefOffset = pdf.toString().getBytes(StandardCharsets.US_ASCII).length;
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n");
        pdf.append("0000000000 65535 f \n");
        for (int i = 1; i <= objects.size(); i++) {
            pdf.append(String.format("%010d 00000 n \n", offsets[i]));
        }
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\n");
        pdf.append("startxref\n").append(xrefOffset).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private String escapePdf(String value) {
        return value(value)
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replaceAll("[^\\x20-\\x7E]", "?");
    }

    private void element(StringBuilder xml, String name, Object value) {
        element(xml, name, value, 2);
    }

    private void element(StringBuilder xml, String name, Object value, int spaces) {
        xml.append(" ".repeat(spaces))
                .append("<").append(name).append(">")
                .append(escapeXml(value(value)))
                .append("</").append(name).append(">\n");
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private InvoiceDocument.DocumentFormat parseFormat(String format) {
        try {
            return InvoiceDocument.DocumentFormat.valueOf(value(format).toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported invoice document format");
        }
    }

    private String fileName(CustomerInvoiceDTO invoice, InvoiceDocument.DocumentFormat format) {
        String number = value(invoice.getInvoiceNumber()).replaceAll("[^A-Za-z0-9._-]", "_");
        return "customer-invoice-" + number + "." + format.name().toLowerCase();
    }

    private String contentType(InvoiceDocument.DocumentFormat format) {
        return switch (format) {
            case PDF -> "application/pdf";
            case JSON -> "application/json";
            case XML -> "application/xml";
        };
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "SHA-256 is not available", ex);
        }
    }

    private String value(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private String stringOrNull(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private String money(BigDecimal value) {
        return value != null ? value.toPlainString() : "0.00";
    }

    private InvoiceDocumentDTO toDTO(InvoiceDocument document) {
        return InvoiceDocumentDTO.builder()
                .id(document.getId())
                .invoiceId(document.getInvoiceId())
                .invoiceType(document.getInvoiceType().name())
                .format(document.getFormat().name())
                .fileName(document.getFileName())
                .contentType(document.getContentType())
                .checksum(document.getChecksum())
                .generatedBy(document.getGeneratedBy())
                .sourceStatus(document.getSourceStatus())
                .generatedAt(document.getGeneratedAt())
                .build();
    }

    public record DownloadedInvoiceDocument(
            String fileName,
            String contentType,
            byte[] content,
            String checksum) {
    }
}
