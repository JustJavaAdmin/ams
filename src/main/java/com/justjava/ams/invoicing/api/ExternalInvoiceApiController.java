package com.justjava.ams.invoicing.api;

import com.justjava.ams.invoicing.api.dto.ExternalInvoiceDTO;
import com.justjava.ams.invoicing.api.dto.InvoiceEventDTO;
import com.justjava.ams.invoicing.api.dto.InvoiceSearchRequest;
import com.justjava.ams.invoicing.api.dto.PagedResponse;
import com.justjava.ams.invoicing.api.service.ExternalInvoiceApiService;
import com.justjava.ams.invoicing.delivery.dto.InvoiceDispatchDTO;
import com.justjava.ams.invoicing.document.dto.InvoiceDocumentDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class ExternalInvoiceApiController {

    private final ExternalInvoiceApiService externalInvoiceApiService;

    @GetMapping
    public PagedResponse<ExternalInvoiceDTO> searchInvoices(
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String complianceStatus,
            @RequestParam(required = false) String customerTaxId,
            @RequestParam(required = false) String transactionClassification,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedSince,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return externalInvoiceApiService.searchInvoices(InvoiceSearchRequest.builder()
                .organizationId(organizationId)
                .status(status)
                .complianceStatus(complianceStatus)
                .customerTaxId(customerTaxId)
                .transactionClassification(transactionClassification)
                .fromDate(fromDate)
                .toDate(toDate)
                .updatedSince(updatedSince)
                .page(page)
                .size(size)
                .sort(sort)
                .build());
    }

    @GetMapping("/{invoiceId}")
    public ExternalInvoiceDTO getInvoice(@PathVariable Long invoiceId) {
        return externalInvoiceApiService.getInvoice(invoiceId);
    }

    @GetMapping("/{invoiceId}/documents")
    public List<InvoiceDocumentDTO> getInvoiceDocuments(@PathVariable Long invoiceId) {
        return externalInvoiceApiService.getInvoiceDocuments(invoiceId);
    }

    @GetMapping("/{invoiceId}/events")
    public List<InvoiceEventDTO> getInvoiceEvents(@PathVariable Long invoiceId) {
        return externalInvoiceApiService.getInvoiceEvents(invoiceId);
    }

    @GetMapping("/{invoiceId}/dispatches")
    public List<InvoiceDispatchDTO> getInvoiceDispatches(@PathVariable Long invoiceId) {
        return externalInvoiceApiService.getInvoiceDispatches(invoiceId);
    }
}
