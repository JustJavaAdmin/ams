package com.justjava.ams.core.exception;

import com.justjava.ams.integration.dto.IntegrationApiError;
import com.justjava.ams.integration.exception.IntegrationApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatusException(ResponseStatusException ex, WebRequest request) {
        ResponseEntity<?> integrationResponse = integrationResponse(
                request, ex.getStatusCode(), errorCodeFor(ex.getStatusCode()),
                integrationSafeMessage(ex.getStatusCode()), false);
        if (integrationResponse != null) {
            return integrationResponse;
        }
        HttpStatusCode statusCode = ex.getStatusCode();
        String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();

        return new ResponseEntity<>(
                buildError(message, errorCodeFor(statusCode), statusCode.value(), request, null),
                statusCode);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(MethodArgumentNotValidException ex, WebRequest request) {
        ResponseEntity<?> integrationResponse = integrationResponse(
                request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", false);
        if (integrationResponse != null) {
            return integrationResponse;
        }
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.badRequest()
                .body(buildError("Validation failed", "VALIDATION_ERROR",
                        HttpStatus.BAD_REQUEST.value(), request, fieldErrors));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        ResponseEntity<?> integrationResponse = integrationResponse(
                request, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", false);
        if (integrationResponse != null) {
            return integrationResponse;
        }
        return new ResponseEntity<>(
                buildError("Access denied", "FORBIDDEN", HttpStatus.FORBIDDEN.value(), request, null),
                HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex,
            WebRequest request) {
        ResponseEntity<?> integrationResponse = integrationResponse(
                request, HttpStatus.CONFLICT, "DATA_CONFLICT", "The request conflicts with stored data", false);
        if (integrationResponse != null) {
            return integrationResponse;
        }
        String message = resolveDataIntegrityMessage(ex);
        return new ResponseEntity<>(
                buildError(message, "CONFLICT", HttpStatus.CONFLICT.value(), request, null),
                HttpStatus.CONFLICT);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        ResponseEntity<?> integrationResponse = integrationResponse(
                request, HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Request is invalid", false);
        if (integrationResponse != null) {
            return integrationResponse;
        }
        return ResponseEntity.badRequest()
                .body(buildError(ex.getMessage(), "BAD_REQUEST", HttpStatus.BAD_REQUEST.value(), request, null));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException ex, WebRequest request) {
        if (ex instanceof IntegrationApiException integrationApiException) {
            ResponseEntity<?> integrationResponse = integrationResponse(
                    request, integrationApiException.getStatus(), integrationApiException.getCode(),
                    integrationApiException.getMessage(), integrationApiException.isRetryable(),
                    integrationApiException.getDetails());
            if (integrationResponse != null) {
                return integrationResponse;
            }
        } else {
            ResponseEntity<?> integrationResponse = integrationResponse(
                    request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                    "An unexpected error occurred", false);
            if (integrationResponse != null) {
                return integrationResponse;
            }
        }
        HttpStatus status = statusForRuntimeException(ex);

        return new ResponseEntity<>(
                buildError(ex.getMessage(), errorCodeFor(status), status.value(), request, null),
                status);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class
    })
    public ResponseEntity<?> handleMalformedRequest(Exception ex, WebRequest request) {
        ResponseEntity<?> integrationResponse = integrationResponse(
                request, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request is malformed or incomplete", false);
        if (integrationResponse != null) {
            return integrationResponse;
        }
        return new ResponseEntity<>(
                buildError("An unexpected error occurred", "INTERNAL_SERVER_ERROR",
                        HttpStatus.INTERNAL_SERVER_ERROR.value(), request, null),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneralException(Exception ex, WebRequest request) {
        if (isMalformedRequestException(ex)) {
            ResponseEntity<?> integrationResponse = integrationResponse(
                    request, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                    "Request is malformed or incomplete", false);
            if (integrationResponse != null) {
                return integrationResponse;
            }
        } else {
            ResponseEntity<?> integrationResponse = integrationResponse(
                    request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                    "An unexpected error occurred", false);
            if (integrationResponse != null) {
                return integrationResponse;
            }
        }
        return new ResponseEntity<>(
                buildError("An unexpected error occurred", "INTERNAL_SERVER_ERROR",
                        HttpStatus.INTERNAL_SERVER_ERROR.value(), request, null),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private boolean isMalformedRequestException(Exception exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpMessageNotReadableException
                    || cause instanceof MissingRequestHeaderException
                    || cause instanceof HttpMediaTypeNotSupportedException
                    || cause instanceof MethodArgumentTypeMismatchException
                    || cause instanceof MissingServletRequestParameterException) {
                return true;
            }
        }
        return false;
    }

    private String integrationSafeMessage(HttpStatusCode status) {
        if (status.isSameCodeAs(HttpStatus.FORBIDDEN)) {
            return "Access to the requested organization is not permitted";
        }
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return "Requested resource was not found";
        }
        if (status.isSameCodeAs(HttpStatus.CONFLICT)) {
            return "Request conflicts with current state";
        }
        if (status.isSameCodeAs(HttpStatus.BAD_REQUEST)) {
            return "Request is invalid";
        }
        return "Request could not be completed";
    }

    private ResponseEntity<?> integrationResponse(
            WebRequest request,
            HttpStatusCode status,
            String code,
            String message,
            boolean retryable) {
        return integrationResponse(request, status, code, message, retryable, java.util.List.of());
    }

    private ResponseEntity<?> integrationResponse(
            WebRequest request,
            HttpStatusCode status,
            String code,
            String message,
            boolean retryable,
            java.util.List<Object> details) {
        if (!(request instanceof ServletWebRequest servletWebRequest)) {
            return null;
        }
        String path = servletWebRequest.getRequest().getRequestURI();
        if (!path.matches("/api/integrations/organizations/[^/]+/payroll-journals(/by-key)?")) {
            return null;
        }
        String safeCode = code == null ? "INTEGRATION_ERROR" : code;
        String safeMessage = message == null || status.is5xxServerError()
                ? (status.is5xxServerError() ? "An unexpected error occurred" : "Request failed")
                : message;
        return ResponseEntity.status(status)
                .body(new IntegrationApiError(safeCode, safeMessage, retryable, details));
    }

    private ApiErrorResponse buildError(
            String message,
            String errorCode,
            Integer status,
            WebRequest request,
            Map<String, String> fieldErrors) {
        return ApiErrorResponse.builder()
                .message(message != null ? message : "An unexpected error occurred")
                .errorCode(errorCode)
                .status(status)
                .path(resolvePath(request))
                .timestamp(LocalDateTime.now())
                .fieldErrors(fieldErrors)
                .build();
    }

    private HttpStatus statusForRuntimeException(RuntimeException ex) {
        String message = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (message.contains("not found")) {
            return HttpStatus.NOT_FOUND;
        }
        if (message.contains("already exists")
                || message.contains("duplicate")
                || message.contains("overlap")) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String resolveDataIntegrityMessage(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Data integrity violation";
        }
        return message;
    }

    private String errorCodeFor(HttpStatusCode statusCode) {
        if (statusCode.isSameCodeAs(HttpStatus.BAD_REQUEST)) {
            return "BAD_REQUEST";
        }
        if (statusCode.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return "NOT_FOUND";
        }
        if (statusCode.isSameCodeAs(HttpStatus.CONFLICT)) {
            return "CONFLICT";
        }
        if (statusCode.isSameCodeAs(HttpStatus.FORBIDDEN)) {
            return "FORBIDDEN";
        }
        return statusCode.is5xxServerError() ? "INTERNAL_SERVER_ERROR" : "API_ERROR";
    }

    private String resolvePath(WebRequest request) {
        if (request == null) {
            return null;
        }
        String description = request.getDescription(false);
        return description != null && description.startsWith("uri=")
                ? description.substring(4)
                : description;
    }
}
