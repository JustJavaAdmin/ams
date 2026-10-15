package com.justjava.ams.invoicing.webhook.service;

import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.invoicing.webhook.dto.WebhookDeliveryDTO;
import com.justjava.ams.invoicing.webhook.dto.WebhookEndpointDTO;
import com.justjava.ams.invoicing.webhook.entity.WebhookDelivery;
import com.justjava.ams.invoicing.webhook.entity.WebhookEndpoint;
import com.justjava.ams.invoicing.webhook.repository.WebhookDeliveryRepository;
import com.justjava.ams.invoicing.webhook.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class WebhookService {

    private static final String MASKED = "********";

    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final OrganizationRepository organizationRepository;

    public WebhookEndpointDTO createEndpoint(WebhookEndpointDTO request) {
        Organization organization = organizationRepository.findById(requiredId(request.getOrganizationId(), "Organization is required"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        WebhookEndpoint endpoint = WebhookEndpoint.builder()
                .organization(organization)
                .name(required(request.getName(), "Webhook name is required"))
                .url(requiredUrl(request.getUrl()))
                .eventTypes(required(request.getEventTypes(), "Webhook event types are required"))
                .authType(parseAuthType(request.getAuthType()))
                .secretReference(trimToNull(request.getSecretReference()))
                .active(request.getActive() != null ? request.getActive() : true)
                .build();
        requireSecretIfNeeded(endpoint);
        return toDTO(webhookEndpointRepository.save(endpoint), true);
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpointDTO> getEndpoints(Long organizationId) {
        return webhookEndpointRepository.findByOrganizationIdOrderByCreatedAtDesc(
                        requiredId(organizationId, "Organization is required"))
                .stream()
                .map(endpoint -> toDTO(endpoint, true))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WebhookEndpointDTO getEndpoint(Long id) {
        return toDTO(findEndpoint(id), true);
    }

    public WebhookEndpointDTO updateEndpoint(Long id, WebhookEndpointDTO request) {
        WebhookEndpoint endpoint = findEndpoint(id);
        if (request.getName() != null) endpoint.setName(required(request.getName(), "Webhook name is required"));
        if (request.getUrl() != null) endpoint.setUrl(requiredUrl(request.getUrl()));
        if (request.getEventTypes() != null) endpoint.setEventTypes(required(request.getEventTypes(), "Webhook event types are required"));
        if (request.getAuthType() != null) endpoint.setAuthType(parseAuthType(request.getAuthType()));
        if (request.getActive() != null) endpoint.setActive(request.getActive());
        if (isSecretUpdate(request.getSecretReference())) endpoint.setSecretReference(request.getSecretReference().trim());
        requireSecretIfNeeded(endpoint);
        return toDTO(webhookEndpointRepository.save(endpoint), true);
    }

    public void deleteEndpoint(Long id) {
        webhookEndpointRepository.delete(findEndpoint(id));
    }

    public WebhookDeliveryDTO testEndpoint(Long id) {
        WebhookEndpoint endpoint = findEndpoint(id);
        String eventId = UUID.randomUUID().toString();
        String requestBody = """
                {"eventId":"%s","eventType":"webhook.test","occurredAt":"%s","organizationId":%d,"payloadVersion":"1.0","data":{"message":"Webhook configuration test"}}
                """.formatted(eventId, OffsetDateTime.now(), endpoint.getOrganization().getId()).trim();
        WebhookDelivery delivery = WebhookDelivery.builder()
                .webhookEndpoint(endpoint)
                .eventType("webhook.test")
                .eventId(eventId)
                .payloadHash(sha256(requestBody))
                .status(WebhookDelivery.Status.SUCCESS)
                .attemptCount(1)
                .requestBody(requestBody)
                .responseCode(202)
                .responseBody("Signed test payload created. Outbound HTTP dispatch will be enabled by the webhook worker phase.")
                .signature(sign(endpoint, requestBody))
                .build();
        return toDTO(webhookDeliveryRepository.save(delivery));
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryDTO> getDeliveries(Long endpointId) {
        findEndpoint(endpointId);
        return webhookDeliveryRepository.findByWebhookEndpointIdOrderByCreatedAtDesc(endpointId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private WebhookEndpoint findEndpoint(Long id) {
        return webhookEndpointRepository.findById(requiredId(id, "Webhook endpoint is required"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Webhook endpoint not found"));
    }

    private void requireSecretIfNeeded(WebhookEndpoint endpoint) {
        if (WebhookEndpoint.AuthType.HMAC.equals(endpoint.getAuthType()) && trimToNull(endpoint.getSecretReference()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HMAC webhook endpoints require a secret reference");
        }
    }

    private String sign(WebhookEndpoint endpoint, String requestBody) {
        if (!WebhookEndpoint.AuthType.HMAC.equals(endpoint.getAuthType())) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(endpoint.getSecretReference().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(requestBody.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to sign webhook payload", ex);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "SHA-256 is not available", ex);
        }
    }

    private WebhookEndpointDTO toDTO(WebhookEndpoint endpoint, boolean maskSecrets) {
        return WebhookEndpointDTO.builder()
                .id(endpoint.getId())
                .organizationId(endpoint.getOrganization().getId())
                .name(endpoint.getName())
                .url(endpoint.getUrl())
                .eventTypes(endpoint.getEventTypes())
                .authType(endpoint.getAuthType().name())
                .secretReference(maskSecrets ? mask(endpoint.getSecretReference()) : endpoint.getSecretReference())
                .active(endpoint.getActive())
                .createdAt(endpoint.getCreatedAt())
                .updatedAt(endpoint.getUpdatedAt())
                .build();
    }

    private WebhookDeliveryDTO toDTO(WebhookDelivery delivery) {
        return WebhookDeliveryDTO.builder()
                .id(delivery.getId())
                .webhookEndpointId(delivery.getWebhookEndpoint().getId())
                .eventType(delivery.getEventType())
                .eventId(delivery.getEventId())
                .payloadHash(delivery.getPayloadHash())
                .status(delivery.getStatus().name())
                .attemptCount(delivery.getAttemptCount())
                .requestBody(delivery.getRequestBody())
                .responseCode(delivery.getResponseCode())
                .responseBody(delivery.getResponseBody())
                .nextRetryAt(delivery.getNextRetryAt())
                .signature(delivery.getSignature())
                .createdAt(delivery.getCreatedAt())
                .updatedAt(delivery.getUpdatedAt())
                .build();
    }

    private WebhookEndpoint.AuthType parseAuthType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return WebhookEndpoint.AuthType.NONE;
        }
        try {
            return WebhookEndpoint.AuthType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported webhook auth type");
        }
    }

    private boolean isSecretUpdate(String value) {
        return trimToNull(value) != null && !MASKED.equals(value.trim());
    }

    private String mask(String value) {
        return trimToNull(value) == null ? null : MASKED;
    }

    private String required(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return trimmed;
    }

    private String requiredUrl(String value) {
        String url = required(value, "Webhook URL is required");
        if (!(url.startsWith("https://") || url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook URL must use HTTPS outside local development");
        }
        return url;
    }

    private Long requiredId(Long value, String message) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
