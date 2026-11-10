package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.InvoiceTemplateDTO;
import com.justjava.ams.accountant.entity.InvoiceTemplate;
import com.justjava.ams.accountant.repository.InvoiceTemplateRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceTemplateService {

    private final InvoiceTemplateRepository invoiceTemplateRepository;
    private final OrganizationRepository organizationRepository;

    public InvoiceTemplateDTO getTemplate(Long organizationId, String templateName) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        String name = templateName == null || templateName.trim().isEmpty() ? "default" : templateName.trim();
        InvoiceTemplate template = invoiceTemplateRepository.findByOrganizationIdAndTemplateName(organization.getId(), name)
                .orElseGet(() -> invoiceTemplateRepository.findFirstByOrganizationIdOrderByUpdatedAtDesc(organization.getId())
                        .orElse(null));

        if (template == null) {
            return InvoiceTemplateDTO.builder()
                    .organizationId(organization.getId())
                    .templateName(name)
                    .blocks("[]")
                    .theme("{\"primary\":\"#111827\",\"secondary\":\"#505f76\",\"accent\":\"#2f5bea\",\"text\":\"#111827\",\"background\":\"#f7f9fb\"}")
                    .build();
        }

        return mapToDTO(template);
    }

    public InvoiceTemplateDTO getActiveTemplate(Long organizationId) {
        return getTemplate(organizationId, "default");
    }

    public InvoiceTemplateDTO upsertTemplate(Long organizationId, InvoiceTemplateDTO dto) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        String name = dto != null && dto.getTemplateName() != null && !dto.getTemplateName().trim().isEmpty()
                ? dto.getTemplateName().trim() : "default";
        String blocks = dto != null && dto.getBlocks() != null ? dto.getBlocks() : "[]";
        String theme = dto != null && dto.getTheme() != null ? dto.getTheme() : "{\"primary\":\"#111827\",\"secondary\":\"#505f76\",\"accent\":\"#2f5bea\",\"text\":\"#111827\",\"background\":\"#f7f9fb\"}";

        InvoiceTemplate template = invoiceTemplateRepository.findByOrganizationIdAndTemplateName(organizationId, name)
                .orElseGet(() -> InvoiceTemplate.builder()
                        .organization(organization)
                        .templateName(name)
                        .blocksJson(blocks)
                        .themeJson(theme)
                        .build());

        template.setOrganization(organization);
        template.setTemplateName(name);
        template.setBlocksJson(blocks);
        template.setThemeJson(theme);
        return mapToDTO(invoiceTemplateRepository.save(template));
    }

    private InvoiceTemplateDTO mapToDTO(InvoiceTemplate template) {
        return InvoiceTemplateDTO.builder()
                .id(template.getId())
                .organizationId(template.getOrganization() != null ? template.getOrganization().getId() : null)
                .templateName(template.getTemplateName())
                .blocks(template.getBlocksJson())
                .theme(template.getThemeJson())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }
}
