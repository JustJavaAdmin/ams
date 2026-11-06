package com.justjava.ams.integration.dto;

import java.util.List;

public record IntegrationApiError(String code, String message, boolean retryable, List<Object> details) {
}
