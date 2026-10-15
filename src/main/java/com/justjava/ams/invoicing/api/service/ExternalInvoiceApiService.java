package com.justjava.ams.invoicing.api.service;

import com.justjava.ams.accountant.entity.CustomerInvoice;
import com.justjava.ams.accountant.repository.CustomerInvoiceRepository;
import com.justjava.ams.invoicing.api.dto.ExternalInvoiceDTO;
import com.justjava.ams.invoicing.api.dto.InvoiceEventDTO;
import com.justjava.ams.invoicing.api.dto.InvoiceSearchRequest;
import com.justjava.ams.invoicing.api.dto.PagedResponse;
import com.justjava.ams.invoicing.delivery.dto.InvoiceDispatchDTO;
import com.justjava.ams.invoicing.delivery.entity.InvoiceDispatch;
import com.justjava.ams.invoicing.delivery.repository.InvoiceDispatchRepository;
import com.justjava.ams.invoicing.document.dto.InvoiceDocumentDTO;
import com.justjava.ams.invoicing.document.entity.InvoiceDocument;
import com.justjava.ams.invoicing.document.repository.InvoiceDocumentRepository;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExternalInvoiceApiService {

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final InvoiceDocumentRepository invoiceDocumentRepository;
    private final InvoiceDispatchRepository invoiceDispatchRepository;

    public PagedResponse<ExternalInvoiceDTO> searchInvoices(InvoiceSearchRequest request) {
        validateFilters(request);
        Page<CustomerInvoice> page = customerInvoiceRepository.findAll(specification(request), pageable(request));
        return PagedResponse.<ExternalInvoiceDTO>builder()
                .content(page.getContent().stream().map(this::toDTO).collect(Collectors.toList()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    public ExternalInvoiceDTO getInvoice(Long invoiceId) {
        return toDTO(findInvoice(invoiceId));
    }

    public List<InvoiceDocumentDTO> getInvoiceDocuments(Long invoiceId) {
        findInvoice(invoiceId);
        return invoiceDocumentRepository.findByInvoiceTypeAndInvoiceIdOrderByGeneratedAtDesc(
                        InvoiceDocument.InvoiceType.CUSTOMER_INVOICE,
                        invoiceId)
                .stream()
                .map(this::toDocumentDTO)
                .collect(Collectors.toList());
    }

    public List<InvoiceDispatchDTO> getInvoiceDispatches(Long invoiceId) {
        findInvoice(invoiceId);
        return invoiceDispatchRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId)
                .stream()
                .map(this::toDispatchDTO)
                .collect(Collectors.toList());
    }

    public List<InvoiceEventDTO> getInvoiceEvents(Long invoiceId) {
        CustomerInvoice invoice = findInvoice(invoiceId);
        List<InvoiceEventDTO> events = new ArrayList<>();
        events.add(event("invoice.created", invoiceId, invoice.getCreatedAt(), Map.of(
                "invoiceNumber", invoice.getInvoiceNumber(),
                "status", invoice.getStatus().name())));
        events.add(event("invoice.updated", invoiceId, invoice.getUpdatedAt(), Map.of(
                "status", invoice.getStatus().name(),
                "complianceStatus", complianceStatus(invoice))));
        getInvoiceDocuments(invoiceId).forEach(document -> events.add(event("invoice.document_generated", invoiceId, document.getGeneratedAt(), Map.of(
                "documentId", document.getId(),
                "format", document.getFormat(),
                "checksum", document.getChecksum()))));
        getInvoiceDispatches(invoiceId).forEach(dispatch -> events.add(event("invoice.dispatch_" + dispatch.getStatus().toLowerCase(), invoiceId, dispatch.getLastAttemptAt(), Map.of(
                "dispatchId", dispatch.getId(),
                "channel", dispatch.getChannel(),
                "target", nullSafe(dispatch.getTarget())))));
        events.sort(Comparator.comparing(InvoiceEventDTO::getOccurredAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return events;
    }

    private Specification<CustomerInvoice> specification(InvoiceSearchRequest request) {
        return (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (request.getOrganizationId() != null) {
                predicates.add(builder.equal(root.get("organization").get("id"), request.getOrganizationId()));
            }
            if (hasText(request.getStatus())) {
                predicates.add(builder.equal(root.get("status"), parseStatus(request.getStatus())));
            }
            if (hasText(request.getComplianceStatus())) {
                predicates.add(builder.equal(root.get("complianceStatus"), parseComplianceStatus(request.getComplianceStatus())));
            }
            if (hasText(request.getTransactionClassification())) {
                predicates.add(builder.equal(root.get("transactionClassification"), parseClassification(request.getTransactionClassification())));
            }
            if (hasText(request.getCustomerTaxId())) {
                predicates.add(builder.equal(root.join("customer", JoinType.LEFT).get("taxId"), request.getCustomerTaxId().trim()));
            }
            if (request.getFromDate() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("invoiceDate"), request.getFromDate()));
            }
            if (request.getToDate() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("invoiceDate"), request.getToDate()));
            }
            if (request.getUpdatedSince() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("updatedAt"), request.getUpdatedSince()));
            }
            return builder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private void validateFilters(InvoiceSearchRequest request) {
        if (hasText(request.getStatus())) {
            parseStatus(request.getStatus());
        }
        if (hasText(request.getComplianceStatus())) {
            parseComplianceStatus(request.getComplianceStatus());
        }
        if (hasText(request.getTransactionClassification())) {
            parseClassification(request.getTransactionClassification());
        }
    }

    private Pageable pageable(InvoiceSearchRequest request) {
        int page = request.getPage() != null && request.getPage() >= 0 ? request.getPage() : 0;
        int size = request.getSize() != null && request.getSize() > 0 ? Math.min(request.getSize(), MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        return PageRequest.of(page, size, sort(request.getSort()));
    }

    private Sort sort(String requestedSort) {
        if (!hasText(requestedSort)) {
            return Sort.by(Sort.Direction.DESC, "updatedAt");
        }
        String[] parts = requestedSort.trim().split(",", 2);
        String property = switch (parts[0]) {
            case "invoiceDate", "dueDate", "createdAt", "updatedAt", "invoiceNumber", "status" -> parts[0];
            default -> "updatedAt";
        };
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }

    private CustomerInvoice findInvoice(Long invoiceId) {
        return customerInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
    }

    private ExternalInvoiceDTO toDTO(CustomerInvoice invoice) {
        return ExternalInvoiceDTO.builder()
                .id(invoice.getId())
                .organizationId(invoice.getOrganization().getId())
                .customerId(invoice.getCustomer() != null ? invoice.getCustomer().getId() : null)
                .invoiceNumber(invoice.getInvoiceNumber())
                .status(invoice.getStatus().name())
                .complianceStatus(complianceStatus(invoice))
                .transactionClassification(invoice.getTransactionClassification() != null ? invoice.getTransactionClassification().name() : null)
                .customerName(invoice.getCustomerName())
                .customerEmail(invoice.getCustomerEmail())
                .customerTaxId(invoice.getCustomer() != null ? invoice.getCustomer().getTaxId() : null)
                .supplierTaxId(invoice.getSupplierTaxId())
                .buyerTaxId(invoice.getBuyerTaxId())
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(invoice.getDueDate())
                .subtotal(invoice.getSubtotal())
                .taxAmount(invoice.getTaxAmount())
                .totalAmount(invoice.getTotalAmount())
                .amountPaid(invoice.getAmountPaid())
                .nrsIrn(invoice.getNrsIrn())
                .nrsQrCode(invoice.getNrsQrCode())
                .nrsDigitalStamp(invoice.getNrsDigitalStamp())
                .nrsReceiptReference(invoice.getNrsReceiptReference())
                .nrsClearedAt(invoice.getNrsClearedAt())
                .buyerReviewDeadline(invoice.getBuyerReviewDeadline())
                .finalLockedAt(invoice.getFinalLockedAt())
                .rejectedAt(invoice.getRejectedAt())
                .rejectionReason(invoice.getRejectionReason())
                .complianceBreachAt(invoice.getComplianceBreachAt())
                .createdAt(invoice.getCreatedAt())
                .updatedAt(invoice.getUpdatedAt())
                .build();
    }

    private InvoiceDocumentDTO toDocumentDTO(InvoiceDocument document) {
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

    private InvoiceDispatchDTO toDispatchDTO(InvoiceDispatch dispatch) {
        return InvoiceDispatchDTO.builder()
                .id(dispatch.getId())
                .invoiceId(dispatch.getInvoiceId())
                .channel(dispatch.getChannel().name())
                .target(dispatch.getTarget())
                .status(dispatch.getStatus().name())
                .attemptCount(dispatch.getAttemptCount())
                .lastAttemptAt(dispatch.getLastAttemptAt())
                .nextRetryAt(dispatch.getNextRetryAt())
                .responseCode(dispatch.getResponseCode())
                .responseBody(dispatch.getResponseBody())
                .errorMessage(dispatch.getErrorMessage())
                .idempotencyKey(dispatch.getIdempotencyKey())
                .documentId(dispatch.getDocumentId())
                .createdBy(dispatch.getCreatedBy())
                .createdAt(dispatch.getCreatedAt())
                .build();
    }

    private InvoiceEventDTO event(String eventType, Long invoiceId, LocalDateTime occurredAt, Map<String, Object> data) {
        return InvoiceEventDTO.builder()
                .eventType(eventType)
                .invoiceId(invoiceId)
                .occurredAt(occurredAt)
                .data(data)
                .build();
    }

    private CustomerInvoice.InvoiceStatus parseStatus(String value) {
        try {
            return CustomerInvoice.InvoiceStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported invoice status");
        }
    }

    private CustomerInvoice.ComplianceStatus parseComplianceStatus(String value) {
        try {
            return CustomerInvoice.ComplianceStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported compliance status");
        }
    }

    private CustomerInvoice.TransactionClassification parseClassification(String value) {
        try {
            return CustomerInvoice.TransactionClassification.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported transaction classification");
        }
    }

    private String complianceStatus(CustomerInvoice invoice) {
        return invoice.getComplianceStatus() != null
                ? invoice.getComplianceStatus().name()
                : CustomerInvoice.ComplianceStatus.NOT_REQUIRED.name();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
