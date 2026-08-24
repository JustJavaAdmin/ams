package com.justjava.ams.auditor.service;

import com.justjava.ams.auditor.dto.AuditLogDTO;
import com.justjava.ams.auditor.dto.AuditLogFilterRequest;
import com.justjava.ams.auditor.entity.AuditLog;
import com.justjava.ams.auditor.repository.AuditLogRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AuditLogService {
    private static final Set<String> SUPPORTED_ENTITY_TYPES = Set.of(
            "Organization",
            "Branch",
            "ChartOfAccounts",
            "FiscalPeriod",
            "ManualJournal",
            "JournalLine",
            "GeneralLedger",
            "BankReconciliation",
            "BankStatementLine",
            "ApprovalRule",
            // Stage 2 entity types
            "ModuleControl",
            "TrialBalance",
            "FinancialReport",
            "SecurityEvent"
    );

    private final AuditLogRepository auditLogRepository;
    private final OrganizationRepository organizationRepository;

    public AuditLogDTO createAuditLog(Long organizationId, AuditLogDTO dto) {
        Organization organization = findOrganization(organizationId);
        String entityType = normalizeEntityType(dto.getEntityType());

        AuditLog log = AuditLog.builder()
                .organization(organization)
                .entityType(entityType)
                .entityId(dto.getEntityId())
                .action(parseAction(dto.getAction()))
                .oldValue(dto.getOldValue())
                .newValue(dto.getNewValue())
                .ipAddress(dto.getIpAddress())
                .userAgent(dto.getUserAgent())
                .description(dto.getDescription())
                .build();

        return mapToDTO(auditLogRepository.save(log));
    }

    public AuditLogDTO log(Long organizationId, String entityType, Long entityId, String action, String oldValue, String newValue, String description) {
        AuditLogDTO dto = AuditLogDTO.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .description(description)
                .build();

        return createAuditLog(organizationId, dto);
    }

    @Transactional(readOnly = true)
    public AuditLogDTO getAuditLog(Long logId) {
        AuditLog log = auditLogRepository.findById(logId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audit log not found"));
        return mapToDTO(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> getLogsByEntity(Long organizationId, String entityType, Long entityId) {
        findOrganization(organizationId);
        String normalizedEntityType = normalizeEntityType(entityType);

        if (entityId == null) {
            return auditLogRepository.findByOrganizationIdAndEntityTypeOrderByCreatedAtDesc(organizationId, normalizedEntityType)
                    .stream()
                    .map(this::mapToDTO)
                    .collect(Collectors.toList());
        }

        return auditLogRepository.findByOrganizationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(organizationId, normalizedEntityType, entityId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> getLogsByAction(Long organizationId, String action) {
        findOrganization(organizationId);

        return auditLogRepository.findByOrganizationIdAndActionOrderByCreatedAtDesc(organizationId, parseAction(action))
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> getLogsByDateRange(Long organizationId, LocalDateTime startDate, LocalDateTime endDate) {
        findOrganization(organizationId);
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date and end date are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must be before end date");
        }

        return auditLogRepository.findByOrganizationIdAndCreatedAtBetweenOrderByCreatedAtDesc(organizationId, startDate, endDate)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> getLogsByUser(Long userId) {
        return auditLogRepository.findByUserId(userId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> getAllLogs(Long organizationId) {
        findOrganization(organizationId);

        return auditLogRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> getLogs(Long organizationId, AuditLogFilterRequest filter) {
        if (filter == null) {
            return getAllLogs(organizationId);
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

        String entityType = null;
        if (filter.getEntityType() != null && !filter.getEntityType().trim().isEmpty()) {
            entityType = normalizeEntityType(filter.getEntityType());
        }

        AuditLog.AuditAction action = null;
        if (filter.getAction() != null && !filter.getAction().trim().isEmpty()) {
            action = parseAction(filter.getAction());
        }

        final String selectedEntityType = entityType;
        final AuditLog.AuditAction selectedAction = action;
        return auditLogRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                .stream()
                .filter(log -> selectedEntityType == null || selectedEntityType.equals(log.getEntityType()))
                .filter(log -> filter.getEntityId() == null || filter.getEntityId().equals(log.getEntityId()))
                .filter(log -> selectedAction == null || selectedAction.equals(log.getAction()))
                .filter(log -> startDate == null || !log.getCreatedAt().isBefore(startDate))
                .filter(log -> endDate == null || !log.getCreatedAt().isAfter(endDate))
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<String> getSupportedEntityTypes() {
        return SUPPORTED_ENTITY_TYPES.stream()
                .sorted()
                .collect(Collectors.toList());
    }

    private Organization findOrganization(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
    }

    private String normalizeEntityType(String entityType) {
        if (entityType == null || entityType.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entity type is required");
        }

        String normalized = entityType.trim();
        if (!SUPPORTED_ENTITY_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported entity type");
        }
        return normalized;
    }

    private AuditLog.AuditAction parseAction(String action) {
        if (action == null || action.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action is required");
        }

        try {
            return AuditLog.AuditAction.valueOf(action.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported audit action");
        }
    }

    private List<AuditLogDTO> mapToDTOs(List<AuditLog> logs) {
        return logs
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private AuditLogDTO mapToDTO(AuditLog log) {
        return AuditLogDTO.builder()
                .id(log.getId())
                .organizationId(log.getOrganization().getId())
                .userId(log.getUser() != null ? log.getUser().getId() : null)
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .action(log.getAction().toString())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .description(log.getDescription())
                .createdAt(log.getCreatedAt())
                .build();
    }
}

