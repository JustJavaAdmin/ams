package com.justjava.ams.auditor.service;

import com.justjava.ams.auditor.dto.AuditLogDTO;
import com.justjava.ams.auditor.entity.AuditLog;
import com.justjava.ams.auditor.repository.AuditLogRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.entity.User;
import com.justjava.ams.common.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private AuditContextService auditContextService;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    void logUsesResolvedCurrentUserAndRequestContext() {
        Organization organization = Organization.builder().id(1L).build();
        User user = User.builder().id(10L).organization(organization).build();

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(auditContextService.currentUser()).thenReturn(Optional.of(user));
        when(auditContextService.currentIpAddress()).thenReturn("203.0.113.5");
        when(auditContextService.currentUserAgent()).thenReturn("JUnit");
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog saved = invocation.getArgument(0);
            saved.setId(99L);
            saved.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
            return saved;
        });

        AuditLogDTO result = auditLogService.log(
                1L,
                "ManualJournal",
                2L,
                "CREATE",
                null,
                "new",
                "Created manual journal");

        assertThat(result.getUserId()).isEqualTo(10L);
        assertThat(result.getIpAddress()).isEqualTo("203.0.113.5");
        assertThat(result.getUserAgent()).isEqualTo("JUnit");
    }

    @Test
    void createAuditLogHonorsExplicitUserAndRequestContext() {
        Organization organization = Organization.builder().id(1L).build();
        User explicitUser = User.builder().id(20L).organization(organization).build();

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(auditContextService.resolveUser(20L)).thenReturn(explicitUser);
        when(auditContextService.currentIpAddress()).thenReturn("203.0.113.5");
        when(auditContextService.currentUserAgent()).thenReturn("JUnit");
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog saved = invocation.getArgument(0);
            saved.setId(100L);
            saved.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
            return saved;
        });

        AuditLogDTO result = auditLogService.createAuditLog(
                1L,
                AuditLogDTO.builder()
                        .userId(20L)
                        .entityType("ManualJournal")
                        .entityId(2L)
                        .action("UPDATE")
                        .oldValue("old")
                        .newValue("new")
                        .description("Updated manual journal")
                        .build());

        assertThat(result.getUserId()).isEqualTo(20L);
        assertThat(result.getIpAddress()).isEqualTo("203.0.113.5");
        assertThat(result.getUserAgent()).isEqualTo("JUnit");
    }

    @Test
    void createAuditLogKeepsExplicitIpAndUserAgent() {
        Organization organization = Organization.builder().id(1L).build();

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(auditContextService.currentUser()).thenReturn(Optional.empty());
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog saved = invocation.getArgument(0);
            saved.setId(101L);
            saved.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
            return saved;
        });

        AuditLogDTO result = auditLogService.createAuditLog(
                1L,
                AuditLogDTO.builder()
                        .entityType("ManualJournal")
                        .entityId(2L)
                        .action("UPDATE")
                        .ipAddress("198.51.100.9")
                        .userAgent("Explicit Agent")
                        .description("Updated manual journal")
                        .build());

        assertThat(result.getUserId()).isNull();
        assertThat(result.getIpAddress()).isEqualTo("198.51.100.9");
        assertThat(result.getUserAgent()).isEqualTo("Explicit Agent");
    }
}
