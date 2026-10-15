package com.justjava.ams.invoicing.api.service;

import com.justjava.ams.accountant.entity.Customer;
import com.justjava.ams.accountant.entity.CustomerInvoice;
import com.justjava.ams.accountant.repository.CustomerInvoiceRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.invoicing.api.dto.InvoiceSearchRequest;
import com.justjava.ams.invoicing.delivery.entity.InvoiceDispatch;
import com.justjava.ams.invoicing.delivery.repository.InvoiceDispatchRepository;
import com.justjava.ams.invoicing.document.entity.InvoiceDocument;
import com.justjava.ams.invoicing.document.repository.InvoiceDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalInvoiceApiServiceTest {

    @Mock
    private CustomerInvoiceRepository customerInvoiceRepository;

    @Mock
    private InvoiceDocumentRepository invoiceDocumentRepository;

    @Mock
    private InvoiceDispatchRepository invoiceDispatchRepository;

    private ExternalInvoiceApiService externalInvoiceApiService;

    @BeforeEach
    void setUp() {
        externalInvoiceApiService = new ExternalInvoiceApiService(
                customerInvoiceRepository,
                invoiceDocumentRepository,
                invoiceDispatchRepository);
    }

    @Test
    void searchesInvoicesWithPagingMetadata() {
        when(customerInvoiceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(invoice()), org.springframework.data.domain.PageRequest.of(0, 25), 1));

        var response = externalInvoiceApiService.searchInvoices(InvoiceSearchRequest.builder()
                .organizationId(1L)
                .status("draft")
                .complianceStatus("not_required")
                .transactionClassification("b2c")
                .build());

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent()).singleElement().satisfies(invoice -> {
            assertThat(invoice.getId()).isEqualTo(10L);
            assertThat(invoice.getOrganizationId()).isEqualTo(1L);
            assertThat(invoice.getCustomerTaxId()).isEqualTo("TIN-123");
            assertThat(invoice.getComplianceStatus()).isEqualTo("NOT_REQUIRED");
        });
    }

    @Test
    void rejectsUnsupportedFilterValues() {
        assertThatThrownBy(() -> externalInvoiceApiService.searchInvoices(InvoiceSearchRequest.builder()
                .status("unknown")
                .build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void returnsInvoiceDocuments() {
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(invoice()));
        when(invoiceDocumentRepository.findByInvoiceTypeAndInvoiceIdOrderByGeneratedAtDesc(
                InvoiceDocument.InvoiceType.CUSTOMER_INVOICE,
                10L)).thenReturn(List.of(InvoiceDocument.builder()
                .id(20L)
                .invoiceId(10L)
                .invoiceType(InvoiceDocument.InvoiceType.CUSTOMER_INVOICE)
                .format(InvoiceDocument.DocumentFormat.PDF)
                .fileName("customer-invoice-INV-10.pdf")
                .contentType("application/pdf")
                .checksum("abc")
                .generatedBy("tester")
                .sourceStatus("DRAFT")
                .build()));

        assertThat(externalInvoiceApiService.getInvoiceDocuments(10L))
                .singleElement()
                .satisfies(document -> {
                    assertThat(document.getId()).isEqualTo(20L);
                    assertThat(document.getFormat()).isEqualTo("PDF");
                });
    }

    @Test
    void returnsInvoiceDispatches() {
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(invoice()));
        when(invoiceDispatchRepository.findByInvoiceIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(InvoiceDispatch.builder()
                        .id(30L)
                        .invoiceId(10L)
                        .channel(InvoiceDispatch.Channel.EMAIL)
                        .target("billing@example.com")
                        .status(InvoiceDispatch.Status.SUCCESS)
                        .attemptCount(1)
                        .idempotencyKey("key")
                        .build()));

        assertThat(externalInvoiceApiService.getInvoiceDispatches(10L))
                .singleElement()
                .satisfies(dispatch -> {
                    assertThat(dispatch.getId()).isEqualTo(30L);
                    assertThat(dispatch.getStatus()).isEqualTo("SUCCESS");
                });
    }

    @Test
    void returnsSynthesizedEvents() {
        CustomerInvoice invoice = invoice();
        invoice.setCreatedAt(LocalDateTime.of(2026, 9, 16, 9, 0));
        invoice.setUpdatedAt(LocalDateTime.of(2026, 9, 16, 10, 0));
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(invoice));
        when(invoiceDocumentRepository.findByInvoiceTypeAndInvoiceIdOrderByGeneratedAtDesc(any(), any()))
                .thenReturn(List.of());
        when(invoiceDispatchRepository.findByInvoiceIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of());

        assertThat(externalInvoiceApiService.getInvoiceEvents(10L))
                .extracting("eventType")
                .containsExactly("invoice.created", "invoice.updated");
    }

    private CustomerInvoice invoice() {
        Organization organization = Organization.builder().id(1L).build();
        Customer customer = Customer.builder()
                .id(5L)
                .organization(organization)
                .taxId("TIN-123")
                .build();
        return CustomerInvoice.builder()
                .id(10L)
                .organization(organization)
                .customer(customer)
                .invoiceNumber("INV-10")
                .status(CustomerInvoice.InvoiceStatus.DRAFT)
                .complianceStatus(CustomerInvoice.ComplianceStatus.NOT_REQUIRED)
                .transactionClassification(CustomerInvoice.TransactionClassification.B2C)
                .customerName("Acme Limited")
                .customerEmail("billing@example.com")
                .invoiceDate(LocalDate.of(2026, 9, 16))
                .dueDate(LocalDate.of(2026, 9, 30))
                .subtotal(new BigDecimal("100.00"))
                .taxAmount(new BigDecimal("7.50"))
                .totalAmount(new BigDecimal("107.50"))
                .amountPaid(BigDecimal.ZERO)
                .build();
    }
}
