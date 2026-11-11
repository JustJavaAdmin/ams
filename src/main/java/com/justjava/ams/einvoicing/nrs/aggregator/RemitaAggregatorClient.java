package com.justjava.ams.einvoicing.nrs.aggregator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.ams.einvoicing.nrs.aggregator.dto.AggregatorRequests;
import com.justjava.ams.systemAdmin.entity.NrsIntegrationConfig;
import com.justjava.ams.systemAdmin.repository.NrsIntegrationConfigRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RemitaAggregatorClient {
    private static final String PROCESS = "/api/v1/invoice/aggregator/invoice/process";
    private static final String LOOKUP = "/api/v1/invoice/aggregator/invoice/details";
    private static final String PAYMENT = "/api/v1/invoice/aggregator/payment/notify";
    private static final String UPDATE_MERCHANT = "/api/v1/invoice/aggregator/merchant/update";
    private static final String UPDATE_MODE = "/api/v1/invoice/service-mode/update";

    private final NrsIntegrationConfigRepository configRepository;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public JsonNode linkMerchant(AggregatorRequests.MerchantLink request) {
        validate(request);
        NrsIntegrationConfig config = activeConfig();
        requireAggregator(config, request.getAggregatorId());
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("aggregatorId", request.getAggregatorId());
        AggregatorRequests.Merchant merchant = request.getMerchant();
        add(form, "merchant.name", merchant.getName());
        add(form, "merchant.merchantId", merchant.getMerchantId());
        add(form, "merchant.firsBusinessId", merchant.getFirsBusinessId());
        add(form, "merchant.firsServiceId", merchant.getFirsServiceId());
        add(form, "merchant.tin", merchant.getTin());
        add(form, "merchant.email", merchant.getEmail());
        add(form, "merchant.phone", merchant.getPhone());
        add(form, "merchant.webhookUrl", merchant.getWebhookUrl());
        add(form, "merchant.serviceMode", merchant.getServiceMode());
        for (int index = 0; index < merchant.getAddresses().size(); index++) {
            AggregatorRequests.Address address = merchant.getAddresses().get(index);
            String prefix = "merchant.addresses[" + index + "].";
            add(form, prefix + "line", address.getLine());
            add(form, prefix + "city", address.getCity());
            add(form, prefix + "postalZone", address.getPostalZone());
            add(form, prefix + "country", address.getCountry());
        }
        return call(config, config.getLinkMerchantPath(), MediaType.MULTIPART_FORM_DATA, form);
    }

    public JsonNode processInvoice(AggregatorRequests.ProcessInvoice request) {
        validate(request);
        NrsIntegrationConfig config = activeConfig();
        requireAggregator(config, request.getAggregatorId());
        String paymentStatus = request.getFirsSpecific() == null ? null : String.valueOf(request.getFirsSpecific().get("paymentStatus"));
        if (paymentStatus != null && !paymentStatus.equals("null") && !SetHolder.PAYMENT_STATUSES.contains(paymentStatus)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Unsupported payment status");
        }
        return call(config, PROCESS, MediaType.APPLICATION_JSON, request);
    }

    public JsonNode lookupInvoice(AggregatorRequests.InvoiceLookup request) {
        validate(request);
        if (blank(request.getIrn()) && blank(request.getInvoiceReference())) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "IRN or invoice reference is required");
        }
        return call(activeConfig(), LOOKUP, MediaType.APPLICATION_JSON, request);
    }

    public JsonNode notifyPayment(AggregatorRequests.PaymentNotification request) {
        validate(request);
        if (!SetHolder.PAYMENT_STATUSES.contains(request.getPaymentStatus())) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Unsupported payment status");
        }
        NrsIntegrationConfig config = activeConfig();
        return call(config, PAYMENT, MediaType.APPLICATION_JSON, request,
                Map.of("aggregatorId", config.getAggregatorId()));
    }

    public JsonNode updateMerchant(AggregatorRequests.MerchantUpdate request) {
        validate(request);
        return call(activeConfig(), UPDATE_MERCHANT, MediaType.APPLICATION_JSON, request);
    }

    public JsonNode updateServiceMode(AggregatorRequests.ServiceModeUpdate request) {
        validate(request);
        NrsIntegrationConfig config = activeConfig();
        requireAggregator(config, request.getAggregatorId());
        return call(config, UPDATE_MODE, MediaType.APPLICATION_JSON, request);
    }

    private JsonNode call(NrsIntegrationConfig config, String path, MediaType contentType, Object body) {
        return call(config, path, contentType, body, Map.of());
    }

    private JsonNode call(NrsIntegrationConfig config, String path, MediaType contentType, Object body,
                          Map<String, String> queryParameters) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(config.getConnectTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(config.getReadTimeoutSeconds()));
        RestClient client = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
        try {
            return client.post()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path);
                        queryParameters.forEach(uriBuilder::queryParam);
                        return uriBuilder.build();
                    })
                    .contentType(contentType)
                    .header("secretKey", config.getSecretKeyReference())
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                                "Remita aggregator request failed with HTTP " + response.getStatusCode().value());
                    })
                    .body(JsonNode.class);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Remita aggregator service could not be reached", ex);
        }
    }

    private NrsIntegrationConfig activeConfig() {
        NrsIntegrationConfig config = configRepository.findTopByOrderByIdAsc()
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "Remita aggregator configuration is missing"));
        if (!Boolean.TRUE.equals(config.getEnabled()) || !Boolean.TRUE.equals(config.getLiveSubmissionEnabled())) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Remita aggregator operations are disabled");
        }
        if (blank(config.getBaseUrl()) || blank(config.getAggregatorId()) || blank(config.getSecretKeyReference())) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Remita aggregator configuration is incomplete");
        }
        return config;
    }

    private <T> void validate(T request) {
        if (!validator.validate(request).isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Remita aggregator request is missing required fields");
        }
    }

    private void add(MultiValueMap<String, Object> form, String name, String value) {
        if (value != null) form.add(name, value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void requireAggregator(NrsIntegrationConfig config, String requestAggregatorId) {
        if (!config.getAggregatorId().equals(requestAggregatorId)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Request aggregator ID does not match configured aggregator");
        }
    }

    private static final class SetHolder {
        private static final java.util.Set<String> PAYMENT_STATUSES = java.util.Set.of("PENDING", "PAID", "REJECTED");
    }
}
