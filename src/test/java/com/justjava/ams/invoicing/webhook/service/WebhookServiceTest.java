package com.justjava.ams.invoicing.webhook.service;

import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.invoicing.webhook.dto.WebhookEndpointDTO;
import com.justjava.ams.invoicing.webhook.entity.WebhookDelivery;
import com.justjava.ams.invoicing.webhook.entity.WebhookEndpoint;
import com.justjava.ams.invoicing.webhook.repository.WebhookDeliveryRepository;
import com.justjava.ams.invoicing.webhook.repository.WebhookEndpointRepository;
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
class WebhookServiceTest {

    @Mock
    private WebhookEndpointRepository webhookEndpointRepository;

    @Mock
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new WebhookService(webhookEndpointRepository, webhookDeliveryRepository, organizationRepository);
    }

    @Test
    void createsEndpointAndMasksSecretReference() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization()));
        when(webhookEndpointRepository.save(any(WebhookEndpoint.class))).thenAnswer(invocation -> {
            WebhookEndpoint endpoint = invocation.getArgument(0);
            endpoint.setId(10L);
            return endpoint;
        });

        WebhookEndpointDTO response = webhookService.createEndpoint(WebhookEndpointDTO.builder()
                .organizationId(1L)
                .name("ERP")
                .url("https://erp.example.com/webhooks/ams")
                .eventTypes("invoice.created,invoice.sent")
                .authType("HMAC")
                .secretReference("local-secret")
                .build());

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getSecretReference()).isEqualTo("********");
        assertThat(response.getActive()).isTrue();
    }

    @Test
    void rejectsHmacEndpointWithoutSecretReference() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization()));

        assertThatThrownBy(() -> webhookService.createEndpoint(WebhookEndpointDTO.builder()
                .organizationId(1L)
                .name("ERP")
                .url("https://erp.example.com/webhooks/ams")
                .eventTypes("invoice.created")
                .authType("HMAC")
                .build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsInsecureNonLocalUrl() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization()));

        assertThatThrownBy(() -> webhookService.createEndpoint(WebhookEndpointDTO.builder()
                .organizationId(1L)
                .name("ERP")
                .url("http://erp.example.com/webhooks/ams")
                .eventTypes("invoice.created")
                .authType("NONE")
                .build()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createsSignedTestDelivery() {
        WebhookEndpoint endpoint = endpoint();
        when(webhookEndpointRepository.findById(10L)).thenReturn(Optional.of(endpoint));
        when(webhookDeliveryRepository.save(any(WebhookDelivery.class))).thenAnswer(invocation -> {
            WebhookDelivery delivery = invocation.getArgument(0);
            delivery.setId(50L);
            return delivery;
        });

        var delivery = webhookService.testEndpoint(10L);

        assertThat(delivery.getId()).isEqualTo(50L);
        assertThat(delivery.getStatus()).isEqualTo("SUCCESS");
        assertThat(delivery.getPayloadHash()).hasSize(64);
        assertThat(delivery.getSignature()).startsWith("sha256=");
        assertThat(delivery.getRequestBody()).contains("\"eventType\":\"webhook.test\"");
    }

    @Test
    void preservesMaskedSecretOnUpdate() {
        WebhookEndpoint endpoint = endpoint();
        when(webhookEndpointRepository.findById(10L)).thenReturn(Optional.of(endpoint));
        when(webhookEndpointRepository.save(any(WebhookEndpoint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        webhookService.updateEndpoint(10L, WebhookEndpointDTO.builder()
                .name("ERP Updated")
                .secretReference("********")
                .build());

        ArgumentCaptor<WebhookEndpoint> captor = ArgumentCaptor.forClass(WebhookEndpoint.class);
        verify(webhookEndpointRepository).save(captor.capture());
        assertThat(captor.getValue().getSecretReference()).isEqualTo("local-secret");
        assertThat(captor.getValue().getName()).isEqualTo("ERP Updated");
    }

    @Test
    void listsDeliveriesForEndpoint() {
        WebhookEndpoint endpoint = endpoint();
        when(webhookEndpointRepository.findById(10L)).thenReturn(Optional.of(endpoint));
        when(webhookDeliveryRepository.findByWebhookEndpointIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(WebhookDelivery.builder()
                        .id(20L)
                        .webhookEndpoint(endpoint)
                        .eventType("invoice.sent")
                        .eventId("evt-1")
                        .payloadHash("a".repeat(64))
                        .status(WebhookDelivery.Status.SUCCESS)
                        .attemptCount(1)
                        .requestBody("{}")
                        .build()));

        assertThat(webhookService.getDeliveries(10L))
                .singleElement()
                .satisfies(delivery -> assertThat(delivery.getEventType()).isEqualTo("invoice.sent"));
    }

    private Organization organization() {
        return Organization.builder().id(1L).name("Acme").registrationNumber("RC-1").build();
    }

    private WebhookEndpoint endpoint() {
        return WebhookEndpoint.builder()
                .id(10L)
                .organization(organization())
                .name("ERP")
                .url("https://erp.example.com/webhooks/ams")
                .eventTypes("invoice.created,invoice.sent")
                .authType(WebhookEndpoint.AuthType.HMAC)
                .secretReference("local-secret")
                .active(true)
                .build();
    }
}
