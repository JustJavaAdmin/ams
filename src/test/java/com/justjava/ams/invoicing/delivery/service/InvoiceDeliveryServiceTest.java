package com.justjava.ams.invoicing.delivery.service;

import com.justjava.ams.accountant.dto.CustomerInvoiceDTO;
import com.justjava.ams.accountant.service.CustomerInvoiceService;
import com.justjava.ams.invoicing.delivery.entity.InvoiceDispatch;
import com.justjava.ams.invoicing.delivery.repository.InvoiceDispatchRepository;
import com.justjava.ams.invoicing.document.dto.InvoiceDocumentDTO;
import com.justjava.ams.invoicing.document.service.InvoiceDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceDeliveryServiceTest {

    @Mock
    private CustomerInvoiceService customerInvoiceService;

    @Mock
    private InvoiceDocumentService invoiceDocumentService;

    @Mock
    private InvoiceDispatchRepository invoiceDispatchRepository;

    private InvoiceDeliveryService invoiceDeliveryService;

    @BeforeEach
    void setUp() {
        invoiceDeliveryService = new InvoiceDeliveryService(
                customerInvoiceService,
                invoiceDocumentService,
                invoiceDispatchRepository);
    }

    @Test
    void recordsSuccessfulEmailDispatchWhenDocumentGenerationSucceeds() {
        when(customerInvoiceService.getInvoice(10L)).thenReturn(invoice("billing@example.com"));
        when(invoiceDispatchRepository.save(any(InvoiceDispatch.class))).thenAnswer(invocation -> {
            InvoiceDispatch dispatch = invocation.getArgument(0);
            if (dispatch.getId() == null) {
                dispatch.setId(77L);
            }
            return dispatch;
        });
        when(invoiceDocumentService.generateCustomerInvoiceDocument(10L, "pdf", "tester"))
                .thenReturn(InvoiceDocumentDTO.builder().id(30L).build());

        assertThat(invoiceDeliveryService.sendCustomerInvoice(10L, "tester"))
                .satisfies(dispatch -> {
                    assertThat(dispatch.getId()).isEqualTo(77L);
                    assertThat(dispatch.getStatus()).isEqualTo("SUCCESS");
                    assertThat(dispatch.getChannel()).isEqualTo("EMAIL");
                    assertThat(dispatch.getTarget()).isEqualTo("billing@example.com");
                    assertThat(dispatch.getDocumentId()).isEqualTo(30L);
                    assertThat(dispatch.getAttemptCount()).isEqualTo(1);
                });
    }

    @Test
    void recordsFailedDispatchWhenCustomerEmailIsMissing() {
        when(customerInvoiceService.getInvoice(10L)).thenReturn(invoice(null));
        when(invoiceDispatchRepository.save(any(InvoiceDispatch.class))).thenAnswer(invocation -> {
            InvoiceDispatch dispatch = invocation.getArgument(0);
            if (dispatch.getId() == null) {
                dispatch.setId(78L);
            }
            return dispatch;
        });

        assertThatThrownBy(() -> invoiceDeliveryService.sendCustomerInvoice(10L, "tester"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ArgumentCaptor<InvoiceDispatch> captor = ArgumentCaptor.forClass(InvoiceDispatch.class);
        verify(invoiceDispatchRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        InvoiceDispatch lastSave = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(lastSave.getStatus()).isEqualTo(InvoiceDispatch.Status.FAILED);
        assertThat(lastSave.getErrorMessage()).contains("Customer email is required");
        assertThat(lastSave.getNextRetryAt()).isNotNull();
    }

    @Test
    void recordsFailedDispatchWhenComplianceBlocksDocumentGeneration() {
        when(customerInvoiceService.getInvoice(10L)).thenReturn(invoice("billing@example.com"));
        when(invoiceDispatchRepository.save(any(InvoiceDispatch.class))).thenAnswer(invocation -> {
            InvoiceDispatch dispatch = invocation.getArgument(0);
            if (dispatch.getId() == null) {
                dispatch.setId(79L);
            }
            return dispatch;
        });
        when(invoiceDocumentService.generateCustomerInvoiceDocument(10L, "pdf", "tester"))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "B2B and B2G invoices cannot be downloaded until NRS clearance is complete"));

        assertThatThrownBy(() -> invoiceDeliveryService.sendCustomerInvoice(10L, "tester"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.CONFLICT);

        ArgumentCaptor<InvoiceDispatch> captor = ArgumentCaptor.forClass(InvoiceDispatch.class);
        verify(invoiceDispatchRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        InvoiceDispatch lastSave = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(lastSave.getStatus()).isEqualTo(InvoiceDispatch.Status.FAILED);
        assertThat(lastSave.getErrorMessage()).contains("B2B and B2G");
    }

    @Test
    void listsDispatchesForInvoice() {
        when(customerInvoiceService.getInvoice(10L)).thenReturn(invoice("billing@example.com"));
        when(invoiceDispatchRepository.findByInvoiceIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(InvoiceDispatch.builder()
                        .id(1L)
                        .invoiceId(10L)
                        .channel(InvoiceDispatch.Channel.EMAIL)
                        .target("billing@example.com")
                        .status(InvoiceDispatch.Status.SUCCESS)
                        .attemptCount(1)
                        .idempotencyKey("abc")
                        .build()));

        assertThat(invoiceDeliveryService.getCustomerInvoiceDispatches(10L))
                .singleElement()
                .satisfies(dispatch -> assertThat(dispatch.getStatus()).isEqualTo("SUCCESS"));
    }

    @Test
    void rejectsRetryForSuccessfulDispatch() {
        when(customerInvoiceService.getInvoice(10L)).thenReturn(invoice("billing@example.com"));
        when(invoiceDispatchRepository.findById(1L)).thenReturn(Optional.of(InvoiceDispatch.builder()
                .id(1L)
                .invoiceId(10L)
                .channel(InvoiceDispatch.Channel.EMAIL)
                .target("billing@example.com")
                .status(InvoiceDispatch.Status.SUCCESS)
                .attemptCount(1)
                .idempotencyKey("abc")
                .build()));

        assertThatThrownBy(() -> invoiceDeliveryService.retryCustomerInvoiceDispatch(10L, 1L, "tester"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private CustomerInvoiceDTO invoice(String email) {
        return CustomerInvoiceDTO.builder()
                .id(10L)
                .invoiceNumber("INV-10")
                .customerEmail(email)
                .build();
    }
}
