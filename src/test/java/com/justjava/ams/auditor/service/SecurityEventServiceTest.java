package com.justjava.ams.auditor.service;

import com.justjava.ams.auditor.dto.SecurityEventDTO;
import com.justjava.ams.auditor.dto.SecurityEventFilterRequest;
import com.justjava.ams.auditor.entity.SecurityEvent;
import com.justjava.ams.auditor.repository.SecurityEventRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.entity.User;
import com.justjava.ams.common.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityEventServiceTest {

    @Mock
    private SecurityEventRepository securityEventRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private AuditContextService auditContextService;

    @InjectMocks
    private SecurityEventService securityEventService;

    @Test
    void filtersSecurityEventsByUserId() {
        Organization organization = Organization.builder().id(1L).build();
        User selectedUser = User.builder().id(10L).organization(organization).build();
        User otherUser = User.builder().id(20L).organization(organization).build();
        SecurityEvent selected = event(1L, organization, selectedUser);
        SecurityEvent other = event(2L, organization, otherUser);
        SecurityEvent system = event(3L, organization, null);

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(securityEventRepository.findByOrganizationIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(selected, other, system));

        List<SecurityEventDTO> result = securityEventService.getEvents(
                1L,
                SecurityEventFilterRequest.builder()
                        .userId(10L)
                        .build());

        assertThat(result).extracting(SecurityEventDTO::getId).containsExactly(1L);
        assertThat(result.getFirst().getUserId()).isEqualTo(10L);
    }

    @Test
    void logEventUsesResolvedCurrentUserAndRequestContext() {
        Organization organization = Organization.builder().id(1L).build();
        User user = User.builder().id(10L).organization(organization).build();

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(auditContextService.currentUser()).thenReturn(Optional.of(user));
        when(auditContextService.currentIpAddress()).thenReturn("203.0.113.5");
        when(auditContextService.currentUserAgent()).thenReturn("JUnit");
        when(securityEventRepository.save(any(SecurityEvent.class))).thenAnswer(invocation -> {
            SecurityEvent saved = invocation.getArgument(0);
            saved.setId(99L);
            saved.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
            saved.setUpdatedAt(saved.getCreatedAt());
            return saved;
        });

        SecurityEventDTO result = securityEventService.logEvent(
                1L,
                "DATA_ACCESS",
                "LOW",
                "Exported data",
                "A report was exported",
                null,
                null);

        assertThat(result.getUserId()).isEqualTo(10L);
        assertThat(result.getIpAddress()).isEqualTo("203.0.113.5");
        assertThat(result.getUserAgent()).isEqualTo("JUnit");
    }

    @Test
    void createSecurityEventHonorsExplicitUserId() {
        Organization organization = Organization.builder().id(1L).build();
        User explicitUser = User.builder().id(20L).organization(organization).build();

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(auditContextService.resolveUser(20L)).thenReturn(explicitUser);
        when(auditContextService.currentIpAddress()).thenReturn("203.0.113.5");
        when(auditContextService.currentUserAgent()).thenReturn("JUnit");
        when(securityEventRepository.save(any(SecurityEvent.class))).thenAnswer(invocation -> {
            SecurityEvent saved = invocation.getArgument(0);
            saved.setId(100L);
            saved.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
            saved.setUpdatedAt(saved.getCreatedAt());
            return saved;
        });

        SecurityEventDTO result = securityEventService.createSecurityEvent(
                1L,
                SecurityEventDTO.builder()
                        .userId(20L)
                        .eventType("DATA_ACCESS")
                        .severity("LOW")
                        .title("Explicit user")
                        .description("DTO user wins")
                        .build());

        assertThat(result.getUserId()).isEqualTo(20L);
    }

    private SecurityEvent event(Long id, Organization organization, User user) {
        SecurityEvent event = SecurityEvent.builder()
                .id(id)
                .organization(organization)
                .user(user)
                .eventType(SecurityEvent.EventType.DATA_ACCESS)
                .severity(SecurityEvent.SeverityLevel.MEDIUM)
                .title("Event " + id)
                .description("Security event")
                .acknowledged(false)
                .build();
        event.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0).plusMinutes(id));
        event.setUpdatedAt(event.getCreatedAt());
        return event;
    }
}
