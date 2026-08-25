package com.justjava.ams.auditor.service;

import com.justjava.ams.auditor.dto.SecurityEventDTO;
import com.justjava.ams.auditor.entity.SecurityEvent;
import com.justjava.ams.auditor.repository.SecurityEventRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.entity.User;
import com.justjava.ams.common.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SecurityEventService {

    private final SecurityEventRepository securityEventRepository;
    private final OrganizationRepository organizationRepository;
    private final AuditLogService auditLogService;
    private final AuditContextService auditContextService;

    public SecurityEventDTO createSecurityEvent(Long organizationId, SecurityEventDTO dto) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        // validate event type and severity
        SecurityEvent.EventType eventType;
        SecurityEvent.SeverityLevel severity;
        try {
            eventType = SecurityEvent.EventType.valueOf(dto.getEventType());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event type");
        }
        try {
            severity = SecurityEvent.SeverityLevel.valueOf(dto.getSeverity());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid severity level");
        }

        SecurityEvent event = SecurityEvent.builder()
                .organization(organization)
                .user(resolveUser(dto.getUserId()))
                .eventType(eventType)
                .severity(severity)
                .title(dto.getTitle())
                .description(dto.getDescription())
                .ipAddress(firstNonBlank(dto.getIpAddress(), auditContextService.currentIpAddress()))
                .userAgent(firstNonBlank(dto.getUserAgent(), auditContextService.currentUserAgent()))
                // explicitly set acknowledged defaults
                .acknowledged(false)
                .build();

        return mapToDTO(securityEventRepository.save(event));
    }

    public SecurityEventDTO getSecurityEvent(Long eventId) {
        SecurityEvent event = securityEventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Security event not found"));
        return mapToDTO(event);
    }

    public List<SecurityEventDTO> getEventsBySeverity(Long organizationId, String severity) {
        // validate org
        findOrganization(organizationId);
        SecurityEvent.SeverityLevel lvl;
        try {
            lvl = SecurityEvent.SeverityLevel.valueOf(severity);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid severity level");
        }

        return securityEventRepository.findByOrganizationIdAndSeverity(organizationId, lvl)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<SecurityEventDTO> getEventsByType(Long organizationId, String eventType) {
        findOrganization(organizationId);
        SecurityEvent.EventType et;
        try {
            et = SecurityEvent.EventType.valueOf(eventType);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event type");
        }

        return securityEventRepository.findByOrganizationIdAndEventType(organizationId, et)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<SecurityEventDTO> getUnacknowledgedEvents(Long organizationId) {
        findOrganization(organizationId);
        return securityEventRepository.findByOrganizationIdAndAcknowledgedFalseOrderByCreatedAtDesc(organizationId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<SecurityEventDTO> getEventsByDateRange(Long organizationId, LocalDateTime startDate, LocalDateTime endDate) {
        findOrganization(organizationId);
        if ((startDate == null) != (endDate == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date and end date are required");
        }
        if (startDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must be before end date");
        }

        return securityEventRepository.findByOrganizationIdAndCreatedAtBetweenOrderByCreatedAtDesc(organizationId, startDate, endDate)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public SecurityEventDTO acknowledgeEvent(Long eventId, String acknowledgedBy) {
        SecurityEvent event = securityEventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Security event not found"));

        if (Boolean.TRUE.equals(event.getAcknowledged())) {
            // idempotent - do not overwrite acknowledgedAt
            return mapToDTO(event);
        }

        event.setAcknowledged(true);
        event.setAcknowledgedAt(LocalDateTime.now());
        event.setAcknowledgedBy(acknowledgedBy);
        SecurityEvent saved = securityEventRepository.save(event);

        // Create audit log for the acknowledgment
        try {
            auditLogService.log(saved.getOrganization().getId(), "SecurityEvent", saved.getId(), "UPDATE",
                    "acknowledged=false", "acknowledged=true",
                    "Security event acknowledged by " + (acknowledgedBy != null ? acknowledgedBy : "system"));
        } catch (Exception ex) {
            // preserve existing behavior: do not prevent acknowledgment due to audit failures
        }

        return mapToDTO(saved);
    }

    public List<SecurityEventDTO> getAllEvents(Long organizationId) {
        return securityEventRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // New API: list events with filtering (newest first)
    @Transactional(readOnly = true)
    public List<SecurityEventDTO> getEvents(Long organizationId, com.justjava.ams.auditor.dto.SecurityEventFilterRequest filter) {
        if (filter == null) {
            return getAllEvents(organizationId).stream().collect(Collectors.toList());
        }

        findOrganization(organizationId);

        LocalDateTime startDate = filter.getStartDate();
        LocalDateTime endDate = filter.getEndDate();
        if ((startDate == null) != (endDate == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date and end date are required");
        }
        if (startDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must be before end date");
        }

        SecurityEvent.EventType eventType = null;
        if (filter.getEventType() != null && !filter.getEventType().trim().isEmpty()) {
            try {
                eventType = SecurityEvent.EventType.valueOf(filter.getEventType());
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event type");
            }
        }

        SecurityEvent.SeverityLevel severity = null;
        if (filter.getSeverity() != null && !filter.getSeverity().trim().isEmpty()) {
            try {
                severity = SecurityEvent.SeverityLevel.valueOf(filter.getSeverity());
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid severity level");
            }
        }

        final SecurityEvent.EventType selectedEventType = eventType;
        final SecurityEvent.SeverityLevel selectedSeverity = severity;

        return securityEventRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                .stream()
                .filter(e -> selectedEventType == null || selectedEventType.equals(e.getEventType()))
                .filter(e -> selectedSeverity == null || selectedSeverity.equals(e.getSeverity()))
                .filter(e -> filter.getUserId() == null
                        || (e.getUser() != null && filter.getUserId().equals(e.getUser().getId())))
                .filter(e -> filter.getAcknowledged() == null || filter.getAcknowledged().equals(e.getAcknowledged()))
                .filter(e -> startDate == null || !e.getCreatedAt().isBefore(startDate))
                .filter(e -> endDate == null || !e.getCreatedAt().isAfter(endDate))
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // New API: create a security event programmatically (no controller exposure here)
    public SecurityEventDTO logEvent(Long organizationId, String eventType, String severity, String title, String description, String ipAddress, String userAgent) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        SecurityEvent.EventType et;
        SecurityEvent.SeverityLevel sev;
        try {
            et = SecurityEvent.EventType.valueOf(eventType);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event type");
        }
        try {
            sev = SecurityEvent.SeverityLevel.valueOf(severity);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid severity level");
        }

        SecurityEvent event = SecurityEvent.builder()
                .organization(organization)
                .user(auditContextService.currentUser().orElse(null))
                .eventType(et)
                .severity(sev)
                .title(title)
                .description(description)
                .ipAddress(firstNonBlank(ipAddress, auditContextService.currentIpAddress()))
                .userAgent(firstNonBlank(userAgent, auditContextService.currentUserAgent()))
                .acknowledged(false)
                .build();

        return mapToDTO(securityEventRepository.save(event));
    }

    private Organization findOrganization(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
    }

    private User resolveUser(Long userId) {
        return userId != null
                ? auditContextService.resolveUser(userId)
                : auditContextService.currentUser().orElse(null);
    }

    private String firstNonBlank(String preferred, String fallback) {
        String normalized = trimToNull(preferred);
        return normalized != null ? normalized : trimToNull(fallback);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private SecurityEventDTO mapToDTO(SecurityEvent event) {
        return SecurityEventDTO.builder()
                .id(event.getId())
                .organizationId(event.getOrganization().getId())
                .userId(event.getUser() != null ? event.getUser().getId() : null)
                .eventType(event.getEventType().toString())
                .severity(event.getSeverity().toString())
                .title(event.getTitle())
                .description(event.getDescription())
                .ipAddress(event.getIpAddress())
                .userAgent(event.getUserAgent())
                .acknowledged(event.getAcknowledged())
                .acknowledgedAt(event.getAcknowledgedAt())
                .acknowledgedBy(event.getAcknowledgedBy())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}

