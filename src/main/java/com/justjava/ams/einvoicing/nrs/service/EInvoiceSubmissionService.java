package com.justjava.ams.einvoicing.nrs.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.ams.accountant.entity.CustomerInvoice;
import com.justjava.ams.accountant.repository.CustomerInvoiceRepository;
import com.justjava.ams.einvoicing.archive.entity.EInvoicePayloadArchive;
import com.justjava.ams.einvoicing.archive.repository.EInvoicePayloadArchiveRepository;
import com.justjava.ams.einvoicing.nrs.dto.EInvoicePayloadArchiveDTO;
import com.justjava.ams.einvoicing.nrs.dto.EInvoiceSubmissionDTO;
import com.justjava.ams.einvoicing.nrs.entity.EInvoiceSubmission;
import com.justjava.ams.einvoicing.nrs.repository.EInvoiceSubmissionRepository;
import com.justjava.ams.einvoicing.payload.dto.NrsInvoicePayloadDTO;
import com.justjava.ams.einvoicing.payload.service.NrsPayloadMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class EInvoiceSubmissionService {

    private final EInvoiceSubmissionRepository submissionRepository;
    private final EInvoicePayloadArchiveRepository archiveRepository;
    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final NrsPayloadMapper nrsPayloadMapper;
    private final ObjectMapper objectMapper;

    public EInvoiceSubmissionDTO prepareCustomerInvoiceSubmission(Long invoiceId, String submissionType, String createdBy) {
        CustomerInvoice invoice = customerInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        EInvoiceSubmission.SubmissionType type = parseSubmissionType(submissionType, invoice);
        NrsInvoicePayloadDTO payload = nrsPayloadMapper.mapCustomerInvoice(invoiceId);
        String payloadJson = serialize(payload);
        String payloadHash = sha256(payloadJson);

        EInvoiceSubmission submission = submissionRepository.save(EInvoiceSubmission.builder()
                .invoiceId(invoiceId)
                .submissionType(type)
                .status(EInvoiceSubmission.Status.PENDING)
                .payloadFormat(EInvoiceSubmission.PayloadFormat.JSON)
                .payloadHash(payloadHash)
                .retryCount(0)
                .createdBy(userName(createdBy))
                .build());

        EInvoicePayloadArchive archive = archiveRepository.save(EInvoicePayloadArchive.builder()
                .submission(submission)
                .invoiceId(invoiceId)
                .payloadStorageKey("db://e-invoice-payload-archives/payload/" + submission.getId())
                .payloadHash(payloadHash)
                .encrypted(false)
                .retentionUntil(LocalDateTime.now().plusYears(6))
                .immutable(true)
                .payloadContent(payloadJson)
                .build());

        applyInitialComplianceStatus(invoice, type);
        customerInvoiceRepository.save(invoice);
        return toDTO(submission, archive);
    }

    @Transactional(readOnly = true)
    public List<EInvoiceSubmissionDTO> getCustomerInvoiceSubmissions(Long invoiceId) {
        customerInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        return submissionRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId).stream()
                .map(submission -> toDTO(submission, archiveRepository.findBySubmissionId(submission.getId()).orElse(null)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EInvoiceSubmissionDTO getSubmission(Long submissionId) {
        EInvoiceSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "E-invoice submission not found"));
        return toDTO(submission, archiveRepository.findBySubmissionId(submission.getId()).orElse(null));
    }

    private EInvoiceSubmission.SubmissionType parseSubmissionType(String requestedType, CustomerInvoice invoice) {
        if (requestedType == null || requestedType.trim().isEmpty()) {
            return defaultSubmissionType(invoice);
        }
        try {
            return EInvoiceSubmission.SubmissionType.valueOf(requestedType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported e-invoice submission type");
        }
    }

    private EInvoiceSubmission.SubmissionType defaultSubmissionType(CustomerInvoice invoice) {
        if (CustomerInvoice.TransactionClassification.B2C.equals(invoice.getTransactionClassification())) {
            return EInvoiceSubmission.SubmissionType.ASYNC_B2C;
        }
        return EInvoiceSubmission.SubmissionType.PRE_CLEARANCE;
    }

    private void applyInitialComplianceStatus(CustomerInvoice invoice, EInvoiceSubmission.SubmissionType type) {
        if (EInvoiceSubmission.SubmissionType.PRE_CLEARANCE.equals(type)) {
            invoice.setComplianceStatus(CustomerInvoice.ComplianceStatus.PENDING_CLEARANCE);
        }
    }

    private String serialize(NrsInvoicePayloadDTO payload) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize NRS payload", ex);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "SHA-256 is not available", ex);
        }
    }

    private EInvoiceSubmissionDTO toDTO(EInvoiceSubmission submission, EInvoicePayloadArchive archive) {
        return EInvoiceSubmissionDTO.builder()
                .id(submission.getId())
                .invoiceId(submission.getInvoiceId())
                .submissionType(submission.getSubmissionType().name())
                .status(submission.getStatus().name())
                .payloadFormat(submission.getPayloadFormat().name())
                .payloadHash(submission.getPayloadHash())
                .signature(submission.getSignature())
                .nrsRequestId(submission.getNrsRequestId())
                .nrsIrn(submission.getNrsIrn())
                .nrsResponseCode(submission.getNrsResponseCode())
                .nrsResponseMessage(submission.getNrsResponseMessage())
                .submittedAt(submission.getSubmittedAt())
                .clearedAt(submission.getClearedAt())
                .failedAt(submission.getFailedAt())
                .retryCount(submission.getRetryCount())
                .nextRetryAt(submission.getNextRetryAt())
                .createdBy(submission.getCreatedBy())
                .createdAt(submission.getCreatedAt())
                .updatedAt(submission.getUpdatedAt())
                .archive(archive != null ? toDTO(archive) : null)
                .build();
    }

    private EInvoicePayloadArchiveDTO toDTO(EInvoicePayloadArchive archive) {
        return EInvoicePayloadArchiveDTO.builder()
                .id(archive.getId())
                .submissionId(archive.getSubmission().getId())
                .invoiceId(archive.getInvoiceId())
                .payloadStorageKey(archive.getPayloadStorageKey())
                .responseStorageKey(archive.getResponseStorageKey())
                .payloadHash(archive.getPayloadHash())
                .responseHash(archive.getResponseHash())
                .encrypted(archive.getEncrypted())
                .retentionUntil(archive.getRetentionUntil())
                .immutable(archive.getImmutable())
                .createdAt(archive.getCreatedAt())
                .build();
    }

    private String userName(String value) {
        return value == null || value.trim().isEmpty() ? "system" : value.trim();
    }
}
