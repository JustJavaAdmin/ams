package com.justjava.ams.einvoicing.nrs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.ams.accountant.entity.CustomerInvoice;
import com.justjava.ams.accountant.repository.CustomerInvoiceRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.einvoicing.archive.entity.EInvoicePayloadArchive;
import com.justjava.ams.einvoicing.archive.repository.EInvoicePayloadArchiveRepository;
import com.justjava.ams.einvoicing.nrs.entity.EInvoiceSubmission;
import com.justjava.ams.einvoicing.nrs.repository.EInvoiceSubmissionRepository;
import com.justjava.ams.einvoicing.payload.dto.NrsInvoicePayloadDTO;
import com.justjava.ams.einvoicing.payload.service.NrsPayloadMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EInvoiceSubmissionServiceTest {

    @Mock
    private EInvoiceSubmissionRepository submissionRepository;

    @Mock
    private EInvoicePayloadArchiveRepository archiveRepository;

    @Mock
    private CustomerInvoiceRepository customerInvoiceRepository;

    @Mock
    private NrsPayloadMapper nrsPayloadMapper;

    private EInvoiceSubmissionService submissionService;

    @BeforeEach
    void setUp() {
        submissionService = new EInvoiceSubmissionService(
                submissionRepository,
                archiveRepository,
                customerInvoiceRepository,
                nrsPayloadMapper,
                new ObjectMapper());
    }

    @Test
    void preparesPreClearanceSubmissionAndArchive() {
        CustomerInvoice invoice = invoice(CustomerInvoice.TransactionClassification.B2B);
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(invoice));
        when(nrsPayloadMapper.mapCustomerInvoice(10L)).thenReturn(payload());
        when(submissionRepository.save(any(EInvoiceSubmission.class))).thenAnswer(invocation -> {
            EInvoiceSubmission submission = invocation.getArgument(0);
            submission.setId(100L);
            return submission;
        });
        when(archiveRepository.save(any(EInvoicePayloadArchive.class))).thenAnswer(invocation -> {
            EInvoicePayloadArchive archive = invocation.getArgument(0);
            archive.setId(200L);
            return archive;
        });

        var response = submissionService.prepareCustomerInvoiceSubmission(10L, null, "tester");

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getSubmissionType()).isEqualTo("PRE_CLEARANCE");
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getPayloadFormat()).isEqualTo("JSON");
        assertThat(response.getPayloadHash()).hasSize(64);
        assertThat(response.getArchive().getId()).isEqualTo(200L);
        assertThat(response.getArchive().getPayloadHash()).isEqualTo(response.getPayloadHash());
        assertThat(response.getArchive().getRetentionUntil()).isAfter(LocalDateTime.now().plusYears(5));
        assertThat(response.getArchive().getImmutable()).isTrue();
        assertThat(invoice.getComplianceStatus()).isEqualTo(CustomerInvoice.ComplianceStatus.PENDING_CLEARANCE);
        verify(customerInvoiceRepository).save(invoice);
    }

    @Test
    void defaultsB2CInvoicesToAsyncSubmission() {
        CustomerInvoice invoice = invoice(CustomerInvoice.TransactionClassification.B2C);
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(invoice));
        when(nrsPayloadMapper.mapCustomerInvoice(10L)).thenReturn(payload());
        when(submissionRepository.save(any(EInvoiceSubmission.class))).thenAnswer(invocation -> {
            EInvoiceSubmission submission = invocation.getArgument(0);
            submission.setId(101L);
            return submission;
        });
        when(archiveRepository.save(any(EInvoicePayloadArchive.class))).thenAnswer(invocation -> {
            EInvoicePayloadArchive archive = invocation.getArgument(0);
            archive.setId(201L);
            return archive;
        });

        var response = submissionService.prepareCustomerInvoiceSubmission(10L, null, "tester");

        assertThat(response.getSubmissionType()).isEqualTo("ASYNC_B2C");
        assertThat(invoice.getComplianceStatus()).isEqualTo(CustomerInvoice.ComplianceStatus.NOT_REQUIRED);
    }

    @Test
    void rejectsUnsupportedSubmissionType() {
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(invoice(CustomerInvoice.TransactionClassification.B2B)));

        assertThatThrownBy(() -> submissionService.prepareCustomerInvoiceSubmission(10L, "bad", "tester"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void storesPayloadContentInArchive() {
        CustomerInvoice invoice = invoice(CustomerInvoice.TransactionClassification.B2B);
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(invoice));
        when(nrsPayloadMapper.mapCustomerInvoice(10L)).thenReturn(payload());
        when(submissionRepository.save(any(EInvoiceSubmission.class))).thenAnswer(invocation -> {
            EInvoiceSubmission submission = invocation.getArgument(0);
            submission.setId(100L);
            return submission;
        });
        when(archiveRepository.save(any(EInvoicePayloadArchive.class))).thenAnswer(invocation -> invocation.getArgument(0));

        submissionService.prepareCustomerInvoiceSubmission(10L, "PRE_CLEARANCE", "tester");

        ArgumentCaptor<EInvoicePayloadArchive> captor = ArgumentCaptor.forClass(EInvoicePayloadArchive.class);
        verify(archiveRepository).save(captor.capture());
        assertThat(captor.getValue().getPayloadContent()).contains("\"invoiceNumber\" : \"INV-10\"");
        assertThat(captor.getValue().getPayloadStorageKey()).isEqualTo("db://e-invoice-payload-archives/payload/100");
    }

    @Test
    void listsSubmissionsWithArchiveMetadata() {
        EInvoiceSubmission submission = submission();
        EInvoicePayloadArchive archive = archive(submission);
        when(customerInvoiceRepository.findById(10L)).thenReturn(Optional.of(invoice(CustomerInvoice.TransactionClassification.B2B)));
        when(submissionRepository.findByInvoiceIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(submission));
        when(archiveRepository.findBySubmissionId(100L)).thenReturn(Optional.of(archive));

        assertThat(submissionService.getCustomerInvoiceSubmissions(10L))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getId()).isEqualTo(100L);
                    assertThat(row.getArchive().getPayloadStorageKey()).isEqualTo("db://payload");
                });
    }

    private CustomerInvoice invoice(CustomerInvoice.TransactionClassification classification) {
        return CustomerInvoice.builder()
                .id(10L)
                .organization(Organization.builder().id(1L).build())
                .invoiceNumber("INV-10")
                .transactionClassification(classification)
                .complianceStatus(CustomerInvoice.ComplianceStatus.NOT_REQUIRED)
                .build();
    }

    private NrsInvoicePayloadDTO payload() {
        return NrsInvoicePayloadDTO.builder()
                .invoiceHeader(NrsInvoicePayloadDTO.InvoiceHeader.builder()
                        .invoiceId(10L)
                        .organizationId(1L)
                        .invoiceNumber("INV-10")
                        .supplierTaxID("NG-123")
                        .buyerTaxID("NG-456")
                        .invoiceTypeCode("380")
                        .issueDateTime("2026-09-16T00:00:00")
                        .currencyCode("NGN")
                        .build())
                .taxTotal(NrsInvoicePayloadDTO.TaxTotal.builder()
                        .taxAmount(new BigDecimal("7.50"))
                        .taxCategoryCode("S")
                        .taxPercentage(new BigDecimal("7.50"))
                        .build())
                .monetaryTotal(NrsInvoicePayloadDTO.MonetaryTotal.builder()
                        .lineExtensionAmount(new BigDecimal("100.00"))
                        .taxExclusiveAmount(new BigDecimal("100.00"))
                        .taxInclusiveAmount(new BigDecimal("107.50"))
                        .payableAmount(new BigDecimal("107.50"))
                        .build())
                .invoiceLines(List.of(NrsInvoicePayloadDTO.InvoiceLine.builder()
                        .itemLineID("1")
                        .description("Consulting")
                        .nrsUnitOfMeasurement("EA")
                        .quantity(new BigDecimal("1.00"))
                        .unitPrice(new BigDecimal("100.00"))
                        .lineExtensionAmount(new BigDecimal("100.00"))
                        .vatAmount(new BigDecimal("7.50"))
                        .build()))
                .build();
    }

    private EInvoiceSubmission submission() {
        return EInvoiceSubmission.builder()
                .id(100L)
                .invoiceId(10L)
                .submissionType(EInvoiceSubmission.SubmissionType.PRE_CLEARANCE)
                .status(EInvoiceSubmission.Status.PENDING)
                .payloadFormat(EInvoiceSubmission.PayloadFormat.JSON)
                .payloadHash("a".repeat(64))
                .retryCount(0)
                .createdBy("tester")
                .build();
    }

    private EInvoicePayloadArchive archive(EInvoiceSubmission submission) {
        return EInvoicePayloadArchive.builder()
                .id(200L)
                .submission(submission)
                .invoiceId(10L)
                .payloadStorageKey("db://payload")
                .payloadHash("a".repeat(64))
                .encrypted(false)
                .retentionUntil(LocalDateTime.now().plusYears(6))
                .immutable(true)
                .payloadContent("{}")
                .build();
    }
}
