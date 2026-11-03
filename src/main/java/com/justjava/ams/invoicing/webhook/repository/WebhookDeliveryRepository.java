package com.justjava.ams.invoicing.webhook.repository;

import com.justjava.ams.invoicing.webhook.entity.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {
    List<WebhookDelivery> findByWebhookEndpointIdOrderByCreatedAtDesc(Long webhookEndpointId);
}
