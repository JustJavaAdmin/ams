package com.justjava.ams.integration.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
public class IntegrationApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final boolean retryable;
    private final List<Object> details;

    public IntegrationApiException(HttpStatus status, String code, String message, boolean retryable) {
        this(status, code, message, retryable, List.of());
    }

    public IntegrationApiException(HttpStatus status, String code, String message, boolean retryable, List<Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryable = retryable;
        this.details = List.copyOf(details);
    }
}
