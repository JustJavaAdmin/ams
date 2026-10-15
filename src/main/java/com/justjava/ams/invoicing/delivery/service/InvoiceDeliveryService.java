package com.justjava.ams.invoicing.delivery.service;

import com.justjava.ams.accountant.dto.CustomerInvoiceDTO;
import com.justjava.ams.accountant.service.CustomerInvoiceService;
import com.justjava.ams.invoicing.delivery.dto.InvoiceDispatchDTO;
import com.justjava.ams.invoicing.delivery.entity.InvoiceDispatch;
import com.justjava.ams.invoicing.delivery.repository.InvoiceDispatchRepository;
import com.justjava.ams.invoicing.document.dto.InvoiceDocumentDTO;
import com.justjava.ams.invoicing.document.service.InvoiceDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(noRollbackFor = ResponseStatusException.class)
public class InvoiceDeliveryService {

    private final CustomerInvoiceService customerInvoiceService;
    private final InvoiceDocumentService invoiceDocumentService;
    private final InvoiceDispatchRepository invoiceDispatchRepository;

    public InvoiceDispatchDTO sendCustomerInvoice(Long invoiceId, String requestedBy) {
        CustomerInvoiceDTO invoice = customerInvoiceService.getInvoice(invoiceId);
        InvoiceDispatch dispatch = invoiceDispatchRepository.save(InvoiceDispatch.builder()
                .invoiceId(invoice.getId())
                .channel(InvoiceDispatch.Channel.EMAIL)
                .target(trimToNull(invoice.getCustomerEmail()))
                .status(InvoiceDispatch.Status.PENDING)
                .attemptCount(0)
                .idempotencyKey(UUID.randomUUID().toString())
                .createdBy(userName(requestedBy))
                .build());
        return attemptEmailDispatch(dispatch, requestedBy);
    }

    @Transactional(readOnly = true)
    public List<InvoiceDispatchDTO> getCustomerInvoiceDispatches(Long invoiceId) {
        customerInvoiceService.getInvoice(invoiceId);
        return invoiceDispatchRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public InvoiceDispatchDTO retryCustomerInvoiceDispatch(Long invoiceId, Long dispatchId, String requestedBy) {
        customerInvoiceService.getInvoice(invoiceId);
        InvoiceDispatch dispatch = invoiceDispatchRepository.findById(dispatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice dispatch not found"));
        if (!invoiceId.equals(dispatch.getInvoiceId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dispatch does not belong to invoice");
        }
        if (InvoiceDispatch.Status.SUCCESS.equals(dispatch.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Successful dispatches cannot be retried");
        }
        dispatch.setStatus(InvoiceDispatch.Status.RETRYING);
        dispatch.setNextRetryAt(null);
        return attemptEmailDispatch(invoiceDispatchRepository.save(dispatch), requestedBy);
    }

    private InvoiceDispatchDTO attemptEmailDispatch(InvoiceDispatch dispatch, String requestedBy) {
        dispatch.setAttemptCount(dispatch.getAttemptCount() != null ? dispatch.getAttemptCount() + 1 : 1);
        dispatch.setLastAttemptAt(LocalDateTime.now());
        dispatch.setResponseCode(null);
        dispatch.setResponseBody(null);
        dispatch.setErrorMessage(null);

        try {
            requireEmailTarget(dispatch);
            InvoiceDocumentDTO document = invoiceDocumentService.generateCustomerInvoiceDocument(
                    dispatch.getInvoiceId(),
                    "pdf",
                    userName(requestedBy));
            dispatch.setDocumentId(document.getId());
            dispatch.setStatus(InvoiceDispatch.Status.SUCCESS);
            dispatch.setResponseCode(202);
            dispatch.setResponseBody("Invoice PDF generated for email delivery. Transport adapter pending.");
            dispatch.setNextRetryAt(null);
            return toDTO(invoiceDispatchRepository.save(dispatch));
        } catch (ResponseStatusException ex) {
            markFailed(dispatch, ex.getReason() != null ? ex.getReason() : ex.getMessage());
            invoiceDispatchRepository.save(dispatch);
            throw ex;
        } catch (RuntimeException ex) {
            markFailed(dispatch, ex.getMessage());
            invoiceDispatchRepository.save(dispatch);
            throw ex;
        }
    }

    private void requireEmailTarget(InvoiceDispatch dispatch) {
        if (dispatch.getTarget() == null || dispatch.getTarget().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer email is required for invoice delivery");
        }
    }

    private void markFailed(InvoiceDispatch dispatch, String message) {
        dispatch.setStatus(InvoiceDispatch.Status.FAILED);
        dispatch.setErrorMessage(message);
        dispatch.setNextRetryAt(LocalDateTime.now().plusMinutes(5));
    }

    private InvoiceDispatchDTO toDTO(InvoiceDispatch dispatch) {
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

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String userName(String value) {
        String trimmed = trimToNull(value);
        return trimmed != null ? trimmed : "system";
    }
}
