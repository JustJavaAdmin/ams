package com.justjava.ams.accountant.repository;

import com.justjava.ams.accountant.entity.InvoiceTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvoiceTemplateRepository extends JpaRepository<InvoiceTemplate, Long> {
    Optional<InvoiceTemplate> findByOrganizationIdAndTemplateName(Long organizationId, String templateName);
    Optional<InvoiceTemplate> findFirstByOrganizationIdOrderByUpdatedAtDesc(Long organizationId);
}
