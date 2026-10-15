package com.justjava.ams.einvoicing.payload.service;

import com.justjava.ams.accountant.dto.CustomerInvoiceDTO;
import com.justjava.ams.accountant.dto.InvoiceLineItemDTO;
import com.justjava.ams.accountant.service.CustomerInvoiceService;
import com.justjava.ams.einvoicing.payload.dto.NrsInvoicePayloadDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NrsPayloadMapper {

    private static final BigDecimal STANDARD_VAT_RATE = new BigDecimal("7.50");
    private static final String DEFAULT_NRS_UNIT = "EA";
    private static final String STANDARD_TAX_CATEGORY = "S";
    private static final String EXEMPT_TAX_CATEGORY = "E";
    private static final String ZERO_RATED_TAX_CATEGORY = "Z";

    private final CustomerInvoiceService customerInvoiceService;

    public NrsInvoicePayloadDTO mapCustomerInvoice(Long invoiceId) {
        CustomerInvoiceDTO invoice = customerInvoiceService.getInvoice(invoiceId);
        validateInvoice(invoice);
        return NrsInvoicePayloadDTO.builder()
                .invoiceHeader(header(invoice))
                .taxTotal(taxTotal(invoice))
                .monetaryTotal(monetaryTotal(invoice))
                .invoiceLines(lines(invoice))
                .build();
    }

    private void validateInvoice(CustomerInvoiceDTO invoice) {
        requireText(invoice.getSupplierTaxId(), "Supplier Tax ID is required for NRS payloads");
        requireText(invoice.getBuyerTaxId(), "Buyer Tax ID is required for NRS payloads");
        requireText(invoice.getInvoiceNumber(), "Invoice number is required for NRS payloads");
        if (invoice.getInvoiceDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice date is required for NRS payloads");
        }
        if (invoice.getLineItems() == null || invoice.getLineItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one invoice line is required for NRS payloads");
        }
        validateVat(invoice);
    }

    private void validateVat(CustomerInvoiceDTO invoice) {
        BigDecimal taxAmount = valueOrZero(invoice.getTaxAmount());
        if (taxAmount.compareTo(BigDecimal.ZERO) > 0) {
            if (invoice.getTaxRate() == null || invoice.getTaxRate().compareTo(STANDARD_VAT_RATE) != 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Standard VAT invoices must use 7.5 percent VAT for NRS payloads");
            }
            return;
        }
        if (!hasExemptionOrZeroRatedCode(invoice.getTaxCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Zero VAT NRS payloads require an explicit exemption or zero-rated tax code");
        }
    }

    private NrsInvoicePayloadDTO.InvoiceHeader header(CustomerInvoiceDTO invoice) {
        return NrsInvoicePayloadDTO.InvoiceHeader.builder()
                .invoiceId(invoice.getId())
                .organizationId(invoice.getOrganizationId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .supplierTaxID(invoice.getSupplierTaxId())
                .buyerTaxID(invoice.getBuyerTaxId())
                .invoiceTypeCode("380")
                .transactionClassification(invoice.getTransactionClassification())
                .issueDateTime(invoice.getInvoiceDate().atTime(LocalTime.MIDNIGHT).toString())
                .currencyCode("NGN")
                .build();
    }

    private NrsInvoicePayloadDTO.TaxTotal taxTotal(CustomerInvoiceDTO invoice) {
        return NrsInvoicePayloadDTO.TaxTotal.builder()
                .taxAmount(scale(valueOrZero(invoice.getTaxAmount())))
                .taxCategoryCode(taxCategory(invoice))
                .taxPercentage(scale(invoice.getTaxRate() != null ? invoice.getTaxRate() : BigDecimal.ZERO))
                .build();
    }

    private NrsInvoicePayloadDTO.MonetaryTotal monetaryTotal(CustomerInvoiceDTO invoice) {
        return NrsInvoicePayloadDTO.MonetaryTotal.builder()
                .lineExtensionAmount(scale(valueOrZero(invoice.getSubtotal())))
                .taxExclusiveAmount(scale(valueOrZero(invoice.getSubtotal())))
                .taxInclusiveAmount(scale(valueOrZero(invoice.getTotalAmount())))
                .payableAmount(scale(valueOrZero(invoice.getTotalAmount())))
                .build();
    }

    private List<NrsInvoicePayloadDTO.InvoiceLine> lines(CustomerInvoiceDTO invoice) {
        Set<InvoiceLineItemDTO> lineItems = invoice.getLineItems();
        return lineItems.stream()
                .sorted(Comparator.comparing(InvoiceLineItemDTO::getId, Comparator.nullsLast(Long::compareTo)))
                .map(line -> line(invoice, line))
                .collect(Collectors.toList());
    }

    private NrsInvoicePayloadDTO.InvoiceLine line(CustomerInvoiceDTO invoice, InvoiceLineItemDTO line) {
        BigDecimal lineTotal = valueOrZero(line.getLineTotal());
        BigDecimal vatAmount = scale(lineTotal.multiply(invoice.getTaxRate() != null ? invoice.getTaxRate() : BigDecimal.ZERO)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
        return NrsInvoicePayloadDTO.InvoiceLine.builder()
                .itemLineID(line.getId() != null ? String.valueOf(line.getId()) : line.getDescription())
                .description(line.getDescription())
                .nrsUnitOfMeasurement(DEFAULT_NRS_UNIT)
                .quantity(scale(valueOrZero(line.getQuantity())))
                .unitPrice(scale(valueOrZero(line.getUnitPrice())))
                .lineExtensionAmount(scale(lineTotal))
                .vatAmount(vatAmount)
                .build();
    }

    private String taxCategory(CustomerInvoiceDTO invoice) {
        if (valueOrZero(invoice.getTaxAmount()).compareTo(BigDecimal.ZERO) > 0) {
            return STANDARD_TAX_CATEGORY;
        }
        String taxCode = normalize(invoice.getTaxCode());
        return taxCode.contains("EXEMPT") ? EXEMPT_TAX_CATEGORY : ZERO_RATED_TAX_CATEGORY;
    }

    private boolean hasExemptionOrZeroRatedCode(String taxCode) {
        String normalized = normalize(taxCode);
        return normalized.contains("EXEMPT") || normalized.contains("ZERO") || normalized.contains("0-RATED");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
