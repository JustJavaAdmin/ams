package com.justjava.ams.invoicing.webhook;

import com.justjava.ams.invoicing.webhook.dto.WebhookDeliveryDTO;
import com.justjava.ams.invoicing.webhook.dto.WebhookEndpointDTO;
import com.justjava.ams.invoicing.webhook.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/integrations/webhooks")
@RequiredArgsConstructor
public class WebhookIntegrationController {

    private final WebhookService webhookService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WebhookEndpointDTO createEndpoint(@RequestBody WebhookEndpointDTO request) {
        return webhookService.createEndpoint(request);
    }

    @GetMapping
    public List<WebhookEndpointDTO> getEndpoints(@RequestParam Long organizationId) {
        return webhookService.getEndpoints(organizationId);
    }

    @GetMapping("/{id}")
    public WebhookEndpointDTO getEndpoint(@PathVariable Long id) {
        return webhookService.getEndpoint(id);
    }

    @PutMapping("/{id}")
    public WebhookEndpointDTO updateEndpoint(@PathVariable Long id, @RequestBody WebhookEndpointDTO request) {
        return webhookService.updateEndpoint(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEndpoint(@PathVariable Long id) {
        webhookService.deleteEndpoint(id);
    }

    @PostMapping("/{id}/test")
    @ResponseStatus(HttpStatus.CREATED)
    public WebhookDeliveryDTO testEndpoint(@PathVariable Long id) {
        return webhookService.testEndpoint(id);
    }

    @GetMapping("/{id}/deliveries")
    public List<WebhookDeliveryDTO> getDeliveries(@PathVariable Long id) {
        return webhookService.getDeliveries(id);
    }
}
