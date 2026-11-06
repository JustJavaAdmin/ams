package com.justjava.ams.integration.exception;

import com.justjava.ams.integration.controller.PayrollJournalIntegrationController;
import com.justjava.ams.integration.dto.IntegrationApiError;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestControllerAdvice(assignableTypes = PayrollJournalIntegrationController.class)
@Order(-1)
public class IntegrationApiExceptionHandler {

    @ExceptionHandler(IntegrationApiException.class)
    public ResponseEntity<IntegrationApiError> handleIntegrationApiException(IntegrationApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new IntegrationApiError(ex.getCode(), ex.getMessage(), ex.isRetryable(), ex.getDetails()));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class,
            HttpMediaTypeNotSupportedException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<IntegrationApiError> handleMalformedRequest(Exception ex) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request is malformed or incomplete", false);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<IntegrationApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return response(HttpStatus.CONFLICT, "DATA_CONFLICT", "The request conflicts with stored data", false);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<IntegrationApiError> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String code = status == HttpStatus.FORBIDDEN ? "FORBIDDEN"
                : status == HttpStatus.NOT_FOUND ? "NOT_FOUND"
                : status == HttpStatus.CONFLICT ? "CONFLICT"
                : status == HttpStatus.BAD_REQUEST ? "BAD_REQUEST" : "INTEGRATION_ERROR";
        String message = status.is5xxServerError() ? "Integration request failed" : safeReason(status);
        return response(status, code, message, false);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<IntegrationApiError> handleUnexpectedException(Exception ex) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred", false);
    }

    private ResponseEntity<IntegrationApiError> response(
            HttpStatus status, String code, String message, boolean retryable) {
        return ResponseEntity.status(status)
                .body(new IntegrationApiError(code, message, retryable, List.of()));
    }

    private String safeReason(HttpStatus status) {
        return switch (status) {
            case FORBIDDEN -> "Access to the requested organization is not permitted";
            case NOT_FOUND -> "Requested resource was not found";
            case CONFLICT -> "Request conflicts with current state";
            case BAD_REQUEST -> "Request is invalid";
            default -> "Integration request failed";
        };
    }
}
