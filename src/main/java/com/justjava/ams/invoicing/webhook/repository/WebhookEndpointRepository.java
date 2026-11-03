package com.justjava.ams.invoicing.webhook.repository;

import com.justjava.ams.invoicing.webhook.entity.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, Long> {
    List<WebhookEndpoint> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
}
