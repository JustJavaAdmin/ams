package com.justjava.ams.invoicing.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.ams.accountant.dto.CustomerInvoiceDTO;
import com.justjava.ams.accountant.dto.InvoiceLineItemDTO;
import com.justjava.ams.accountant.service.CustomerInvoiceService;
import com.justjava.ams.invoicing.document.entity.InvoiceDocument;
import com.justjava.ams.invoicing.document.repository.InvoiceDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceDocumentServiceTest {

    @Mock
    private CustomerInvoiceService customerInvoiceService;

    @Mock
    private InvoiceDocumentRepository invoiceDocumentRepository;

    private InvoiceDocumentService invoiceDocumentService;

    @BeforeEach
    void setUp() {
        invoiceDocumentService = new InvoiceDocumentService(
                customerInvoiceService,
                invoiceDocumentRepository,
                new ObjectMapper());
    }

    @Test
    void blocksB2BDownloadBeforeNrsClearance() {
        when(customerInvoiceService.getInvoice(10L)).thenReturn(invoice("B2B", "PENDING_CLEARANCE"));

        assertThatThrownBy(() -> invoiceDocumentService.generateCustomerInvoiceDocument(10L, "json", "tester"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.CONFLICT);
        verify(invoiceDocumentRepository, never()).save(any());
    }

    @Test
    void allowsB2BDownloadAfterNrsClearance() {
        when(customerInvoiceService.getInvoice(10L)).thenReturn(invoice("B2B", "CLEARED"));
        when(invoiceDocumentRepository.save(any(InvoiceDocument.class))).thenAnswer(invocation -> {
            InvoiceDocument document = invocation.getArgument(0);
            document.setId(99L);
            return document;
        });

        assertThat(invoiceDocumentService.generateCustomerInvoiceDocument(10L, "json", "tester"))
                .satisfies(document -> {
                    assertThat(document.getId()).isEqualTo(99L);
                    assertThat(document.getFormat()).isEqualTo("JSON");
                    assertThat(document.getChecksum()).hasSize(64);
                });
    }

    @Test
    void allowsB2CDownloadBeforeNrsClearance() {
        CustomerInvoiceDTO invoice = invoice("B2C", "PENDING_CLEARANCE");
        when(customerInvoiceService.getInvoice(10L)).thenReturn(invoice);
        when(invoiceDocumentRepository.save(any(InvoiceDocument.class))).thenAnswer(invocation -> {
            InvoiceDocument document = invocation.getArgument(0);
            document.setId(100L);
            return document;
        });

        assertThat(invoiceDocumentService.generateCustomerInvoiceDocument(10L, "xml", "tester").getFormat())
                .isEqualTo("XML");
    }

    @Test
    void downloadsPersistedDocumentContent() {
        InvoiceDocument stored = InvoiceDocument.builder()
                .id(42L)
                .invoiceId(10L)
                .invoiceType(InvoiceDocument.InvoiceType.CUSTOMER_INVOICE)
                .format(InvoiceDocument.DocumentFormat.JSON)
                .fileName("customer-invoice-INV-10.json")
                .contentType("application/json")
                .checksum("abc123")
                .content("{}".getBytes())
                .build();
        when(invoiceDocumentRepository.findById(42L)).thenReturn(Optional.of(stored));

        InvoiceDocumentService.DownloadedInvoiceDocument document =
                invoiceDocumentService.getCustomerInvoiceDocumentContent(42L);

        assertThat(document.fileName()).isEqualTo("customer-invoice-INV-10.json");
        assertThat(document.contentType()).isEqualTo("application/json");
        assertThat(document.content()).isEqualTo("{}".getBytes());
    }

    private CustomerInvoiceDTO invoice(String classification, String complianceStatus) {
        return CustomerInvoiceDTO.builder()
                .id(10L)
                .organizationId(1L)
                .invoiceNumber("INV-10")
                .status("DRAFT")
                .transactionClassification(classification)
                .complianceStatus(complianceStatus)
                .supplierTaxId("SUP-123")
                .buyerTaxId("BUY-456")
                .customerName("Acme Limited")
                .customerEmail("billing@example.com")
                .subtotal(new BigDecimal("100.00"))
                .taxAmount(new BigDecimal("7.50"))
                .totalAmount(new BigDecimal("107.50"))
                .amountPaid(BigDecimal.ZERO)
                .lineItems(Set.of(InvoiceLineItemDTO.builder()
                        .id(1L)
                        .description("Implementation")
                        .quantity(BigDecimal.ONE)
                        .unitPrice(new BigDecimal("100.00"))
                        .lineTotal(new BigDecimal("100.00"))
                        .build()))
                .build();
    }
}
