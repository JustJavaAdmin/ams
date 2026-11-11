package com.justjava.ams.systemAdmin.service;

import com.justjava.ams.systemAdmin.dto.*;
import com.justjava.ams.systemAdmin.entity.*;
import com.justjava.ams.systemAdmin.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class SystemAdminConfigService {

    private static final String MASKED = "********";

    private final NrsIntegrationConfigRepository nrsRepository;
    private final SigningKeyConfigRepository signingRepository;
    private final WebhookPlatformConfigRepository webhookRepository;
    private final StorageRetentionConfigRepository storageRepository;
    private final ApiClientRegistrationRepository apiClientRepository;
    private final BackgroundJobConfigRepository backgroundJobRepository;
    private final SystemConfigAuditLogRepository auditLogRepository;

    public NrsIntegrationConfigDTO getNrsConfig() {
        return toDTO(nrsConfig(), true);
    }

    public NrsIntegrationConfigDTO updateNrsConfig(NrsIntegrationConfigDTO request, String changedBy) {
        NrsIntegrationConfig config = nrsConfig();
        if (request.getEnvironment() != null) {
            config.setEnvironment(parseEnvironment(request.getEnvironment()));
        }
        if (request.getEnabled() != null) config.setEnabled(request.getEnabled());
        if (request.getBaseUrl() != null) config.setBaseUrl(trimToNull(request.getBaseUrl()));
        if (request.getAggregatorId() != null) config.setAggregatorId(trimToNull(request.getAggregatorId()));
        if (isSecretUpdate(request.getSecretKeyReference())) config.setSecretKeyReference(request.getSecretKeyReference().trim());
        if (request.getLinkMerchantPath() != null) config.setLinkMerchantPath(validateRelativePath(request.getLinkMerchantPath()));
        if (request.getServiceMode() != null) config.setServiceMode(parseServiceMode(request.getServiceMode()));
        if (request.getLiveSubmissionEnabled() != null) config.setLiveSubmissionEnabled(request.getLiveSubmissionEnabled());
        if (request.getTokenUrl() != null) config.setTokenUrl(trimToNull(request.getTokenUrl()));
        if (request.getAccessPointProvider() != null) config.setAccessPointProvider(trimToNull(request.getAccessPointProvider()));
        if (request.getClientId() != null) config.setClientId(trimToNull(request.getClientId()));
        if (isSecretUpdate(request.getClientSecretReference())) {
            config.setClientSecretReference(request.getClientSecretReference().trim());
        }
        if (request.getScope() != null) config.setScope(trimToNull(request.getScope()));
        if (request.getConnectTimeoutSeconds() != null) config.setConnectTimeoutSeconds(positive(request.getConnectTimeoutSeconds(), "Connect timeout must be positive"));
        if (request.getReadTimeoutSeconds() != null) config.setReadTimeoutSeconds(positive(request.getReadTimeoutSeconds(), "Read timeout must be positive"));
        config.setUpdatedBy(changedBy);
        NrsIntegrationConfig saved = nrsRepository.save(config);
        audit("NRS_INTEGRATION", saved.getId(), "UPDATE", changedBy, "Updated NRS integration settings");
        return toDTO(saved, true);
    }

    public IntegrationHealthDTO testNrsConnection() {
        NrsIntegrationConfig config = nrsConfig();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return IntegrationHealthDTO.builder()
                    .component("NRS")
                    .status("DISABLED")
                    .message("NRS integration is disabled.")
                    .build();
        }
        if (isBlank(config.getBaseUrl()) || isBlank(config.getAggregatorId())
                || isBlank(config.getSecretKeyReference())) {
            return IntegrationHealthDTO.builder()
                    .component("NRS")
                    .status("INCOMPLETE")
                    .message("Base URL, aggregator ID, and secret key reference are required before connection testing.")
                    .build();
        }
        return IntegrationHealthDTO.builder()
                .component("NRS")
                .status(Boolean.TRUE.equals(config.getLiveSubmissionEnabled()) ? "CONFIGURED" : "LIVE_DISABLED")
                .message(Boolean.TRUE.equals(config.getLiveSubmissionEnabled())
                        ? "Aggregator configuration is complete."
                        : "Configuration is complete; live aggregator submission remains disabled.")
                .build();
    }

    public SigningKeyConfigDTO getSigningConfig() {
        return toDTO(signingConfig(), true);
    }

    public SigningKeyConfigDTO updateSigningConfig(SigningKeyConfigDTO request, String changedBy) {
        SigningKeyConfig config = signingConfig();
        if (request.getActive() != null) config.setActive(request.getActive());
        if (request.getAlgorithm() != null) config.setAlgorithm(required(request.getAlgorithm(), "Signing algorithm is required"));
        if (request.getKeyProvider() != null) config.setKeyProvider(trimToNull(request.getKeyProvider()));
        if (request.getKeyAlias() != null) config.setKeyAlias(trimToNull(request.getKeyAlias()));
        if (isSecretUpdate(request.getKeyReference())) config.setKeyReference(request.getKeyReference().trim());
        if (request.getLastRotatedAt() != null) config.setLastRotatedAt(request.getLastRotatedAt());
        if (request.getNextRotationDueAt() != null) config.setNextRotationDueAt(request.getNextRotationDueAt());
        config.setUpdatedBy(changedBy);
        SigningKeyConfig saved = signingRepository.save(config);
        audit("SIGNING_KEY", saved.getId(), "UPDATE", changedBy, "Updated signing key settings");
        return toDTO(saved, true);
    }

    public IntegrationHealthDTO testSigningConfig() {
        SigningKeyConfig config = signingConfig();
        if (!Boolean.TRUE.equals(config.getActive())) {
            return IntegrationHealthDTO.builder()
                    .component("SIGNING")
                    .status("DISABLED")
                    .message("Signing is disabled.")
                    .build();
        }
        if (isBlank(config.getAlgorithm()) || isBlank(config.getKeyReference())) {
            return IntegrationHealthDTO.builder()
                    .component("SIGNING")
                    .status("INCOMPLETE")
                    .message("Signing algorithm and key reference are required.")
                    .build();
        }
        return IntegrationHealthDTO.builder()
                .component("SIGNING")
                .status("CONFIGURED")
                .message("Signing configuration is present. Live signing verification will be enabled by the signing service phase.")
                .build();
    }

    public WebhookPlatformConfigDTO getWebhookConfig() {
        return toDTO(webhookConfig());
    }

    public WebhookPlatformConfigDTO updateWebhookConfig(WebhookPlatformConfigDTO request, String changedBy) {
        WebhookPlatformConfig config = webhookConfig();
        if (request.getEnabled() != null) config.setEnabled(request.getEnabled());
        if (request.getSigningAlgorithm() != null) config.setSigningAlgorithm(required(request.getSigningAlgorithm(), "Signing algorithm is required"));
        if (request.getRetryEnabled() != null) config.setRetryEnabled(request.getRetryEnabled());
        if (request.getMaxAttempts() != null) config.setMaxAttempts(positive(request.getMaxAttempts(), "Max attempts must be positive"));
        if (request.getConnectTimeoutSeconds() != null) config.setConnectTimeoutSeconds(positive(request.getConnectTimeoutSeconds(), "Connect timeout must be positive"));
        if (request.getReadTimeoutSeconds() != null) config.setReadTimeoutSeconds(positive(request.getReadTimeoutSeconds(), "Read timeout must be positive"));
        if (request.getAllowedDomainPattern() != null) config.setAllowedDomainPattern(trimToNull(request.getAllowedDomainPattern()));
        if (request.getDeadLetterRetentionDays() != null) config.setDeadLetterRetentionDays(positive(request.getDeadLetterRetentionDays(), "Dead-letter retention must be positive"));
        config.setUpdatedBy(changedBy);
        WebhookPlatformConfig saved = webhookRepository.save(config);
        audit("WEBHOOK_PLATFORM", saved.getId(), "UPDATE", changedBy, "Updated webhook platform settings");
        return toDTO(saved);
    }

    public StorageRetentionConfigDTO getStorageConfig() {
        return toDTO(storageConfig(), true);
    }

    public StorageRetentionConfigDTO updateStorageConfig(StorageRetentionConfigDTO request, String changedBy) {
        StorageRetentionConfig config = storageConfig();
        if (request.getArtifactStorageLocation() != null) config.setArtifactStorageLocation(trimToNull(request.getArtifactStorageLocation()));
        if (request.getArchiveStorageLocation() != null) config.setArchiveStorageLocation(trimToNull(request.getArchiveStorageLocation()));
        if (request.getEncryptionEnabled() != null) config.setEncryptionEnabled(request.getEncryptionEnabled());
        if (isSecretUpdate(request.getEncryptionKeyReference())) config.setEncryptionKeyReference(request.getEncryptionKeyReference().trim());
        if (request.getRetentionYears() != null) config.setRetentionYears(positive(request.getRetentionYears(), "Retention years must be positive"));
        if (request.getWormRequired() != null) config.setWormRequired(request.getWormRequired());
        if (request.getImmutableArchiveEnabled() != null) config.setImmutableArchiveEnabled(request.getImmutableArchiveEnabled());
        config.setUpdatedBy(changedBy);
        StorageRetentionConfig saved = storageRepository.save(config);
        audit("STORAGE_RETENTION", saved.getId(), "UPDATE", changedBy, "Updated storage and retention settings");
        return toDTO(saved, true);
    }

    public List<ApiClientRegistrationDTO> getApiClients() {
        return apiClientRepository.findAll().stream()
                .map(client -> toDTO(client, true))
                .collect(Collectors.toList());
    }

    public ApiClientRegistrationDTO createApiClient(ApiClientRegistrationDTO request, String changedBy) {
        String clientId = !isBlank(request.getClientId()) ? request.getClientId().trim() : "ams-" + UUID.randomUUID();
        apiClientRepository.findByClientId(clientId).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Client ID already exists");
        });
        ApiClientRegistration client = ApiClientRegistration.builder()
                .clientName(required(request.getClientName(), "Client name is required"))
                .clientId(clientId)
                .clientSecretReference(isSecretUpdate(request.getClientSecretReference()) ? request.getClientSecretReference().trim() : null)
                .scopes(trimToNull(request.getScopes()))
                .active(request.getActive() != null ? request.getActive() : true)
                .updatedBy(changedBy)
                .build();
        ApiClientRegistration saved = apiClientRepository.save(client);
        audit("API_CLIENT", saved.getId(), "CREATE", changedBy, "Created API client " + saved.getClientId());
        return toDTO(saved, true);
    }

    public ApiClientRegistrationDTO updateApiClient(Long id, ApiClientRegistrationDTO request, String changedBy) {
        ApiClientRegistration client = apiClientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API client not found"));
        if (request.getClientName() != null) client.setClientName(required(request.getClientName(), "Client name is required"));
        if (isSecretUpdate(request.getClientSecretReference())) client.setClientSecretReference(request.getClientSecretReference().trim());
        if (request.getScopes() != null) client.setScopes(trimToNull(request.getScopes()));
        if (request.getActive() != null) client.setActive(request.getActive());
        client.setUpdatedBy(changedBy);
        ApiClientRegistration saved = apiClientRepository.save(client);
        audit("API_CLIENT", saved.getId(), "UPDATE", changedBy, "Updated API client " + saved.getClientId());
        return toDTO(saved, true);
    }

    public ApiClientRegistrationDTO revokeApiClient(Long id, String changedBy) {
        ApiClientRegistration client = apiClientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API client not found"));
        client.setActive(false);
        client.setRevokedAt(LocalDateTime.now());
        client.setUpdatedBy(changedBy);
        ApiClientRegistration saved = apiClientRepository.save(client);
        audit("API_CLIENT", saved.getId(), "REVOKE", changedBy, "Revoked API client " + saved.getClientId());
        return toDTO(saved, true);
    }

    public List<BackgroundJobConfigDTO> getJobs() {
        seedDefaultJobs();
        return backgroundJobRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public BackgroundJobConfigDTO updateJob(Long id, BackgroundJobConfigDTO request, String changedBy) {
        BackgroundJobConfig job = backgroundJobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Background job not found"));
        if (request.getEnabled() != null) job.setEnabled(request.getEnabled());
        if (request.getScheduleExpression() != null) job.setScheduleExpression(trimToNull(request.getScheduleExpression()));
        if (request.getMaxRetryAttempts() != null) job.setMaxRetryAttempts(positive(request.getMaxRetryAttempts(), "Max retry attempts must be positive"));
        job.setUpdatedBy(changedBy);
        BackgroundJobConfig saved = backgroundJobRepository.save(job);
        audit("BACKGROUND_JOB", saved.getId(), "UPDATE", changedBy, "Updated background job " + saved.getJobName());
        return toDTO(saved);
    }

    public List<SystemConfigAuditLogDTO> getAuditLogs() {
        return auditLogRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private NrsIntegrationConfig nrsConfig() {
        return nrsRepository.findTopByOrderByIdAsc()
                .orElseGet(() -> nrsRepository.save(NrsIntegrationConfig.builder()
                        .environment(NrsIntegrationConfig.Environment.SANDBOX)
                        .enabled(false)
                        .connectTimeoutSeconds(10)
                        .readTimeoutSeconds(30)
                        .build()));
    }

    private SigningKeyConfig signingConfig() {
        return signingRepository.findTopByOrderByIdAsc()
                .orElseGet(() -> signingRepository.save(SigningKeyConfig.builder()
                        .active(false)
                        .algorithm("ECDSA")
                        .build()));
    }

    private WebhookPlatformConfig webhookConfig() {
        return webhookRepository.findTopByOrderByIdAsc()
                .orElseGet(() -> webhookRepository.save(WebhookPlatformConfig.builder()
                        .enabled(false)
                        .signingAlgorithm("HMAC_SHA256")
                        .retryEnabled(true)
                        .maxAttempts(5)
                        .connectTimeoutSeconds(10)
                        .readTimeoutSeconds(30)
                        .deadLetterRetentionDays(90)
                        .build()));
    }

    private StorageRetentionConfig storageConfig() {
        return storageRepository.findTopByOrderByIdAsc()
                .orElseGet(() -> storageRepository.save(StorageRetentionConfig.builder()
                        .encryptionEnabled(true)
                        .retentionYears(6)
                        .wormRequired(true)
                        .immutableArchiveEnabled(false)
                        .build()));
    }

    private void seedDefaultJobs() {
        seedJob("B2C_NRS_SUBMISSION", "0 */15 * * * *");
        seedJob("NRS_SUBMISSION_RETRY", "0 */10 * * * *");
        seedJob("COMPLIANCE_BREACH_DETECTION", "0 0 * * * *");
        seedJob("BUYER_REVIEW_POLLING", "0 */30 * * * *");
        seedJob("WEBHOOK_RETRY", "0 */5 * * * *");
    }

    private void seedJob(String jobName, String scheduleExpression) {
        backgroundJobRepository.findByJobName(jobName)
                .orElseGet(() -> backgroundJobRepository.save(BackgroundJobConfig.builder()
                        .jobName(jobName)
                        .enabled(true)
                        .scheduleExpression(scheduleExpression)
                        .maxRetryAttempts(5)
                        .build()));
    }

    private void audit(String configType, Long configId, String action, String changedBy, String summary) {
        auditLogRepository.save(SystemConfigAuditLog.builder()
                .configType(configType)
                .configId(configId)
                .action(action)
                .changedBy(!isBlank(changedBy) ? changedBy : "system")
                .summary(summary)
                .build());
    }

    private NrsIntegrationConfigDTO toDTO(NrsIntegrationConfig config, boolean maskSecrets) {
        return NrsIntegrationConfigDTO.builder()
                .id(config.getId())
                .environment(config.getEnvironment().name())
                .enabled(config.getEnabled())
                .baseUrl(config.getBaseUrl())
                .aggregatorId(config.getAggregatorId())
                .secretKeyReference(mask(config.getSecretKeyReference(), maskSecrets))
                .linkMerchantPath(config.getLinkMerchantPath())
                .serviceMode(config.getServiceMode())
                .liveSubmissionEnabled(config.getLiveSubmissionEnabled())
                .tokenUrl(config.getTokenUrl())
                .accessPointProvider(config.getAccessPointProvider())
                .clientId(config.getClientId())
                .clientSecretReference(mask(config.getClientSecretReference(), maskSecrets))
                .scope(config.getScope())
                .connectTimeoutSeconds(config.getConnectTimeoutSeconds())
                .readTimeoutSeconds(config.getReadTimeoutSeconds())
                .updatedBy(config.getUpdatedBy())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private String validateRelativePath(String value) {
        String path = required(value, "Link merchant path is required");
        if (!path.startsWith("/api/") || path.contains("..") || path.contains("://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link merchant path must be a relative API path");
        }
        return path;
    }

    private String parseServiceMode(String value) {
        String mode = required(value, "Service mode is required").toUpperCase();
        if (!List.of("APP", "SI", "BOTH", "NONE").contains(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported NRS service mode");
        }
        return mode;
    }

    private SigningKeyConfigDTO toDTO(SigningKeyConfig config, boolean maskSecrets) {
        return SigningKeyConfigDTO.builder()
                .id(config.getId())
                .active(config.getActive())
                .algorithm(config.getAlgorithm())
                .keyProvider(config.getKeyProvider())
                .keyAlias(config.getKeyAlias())
                .keyReference(mask(config.getKeyReference(), maskSecrets))
                .lastRotatedAt(config.getLastRotatedAt())
                .nextRotationDueAt(config.getNextRotationDueAt())
                .updatedBy(config.getUpdatedBy())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private WebhookPlatformConfigDTO toDTO(WebhookPlatformConfig config) {
        return WebhookPlatformConfigDTO.builder()
                .id(config.getId())
                .enabled(config.getEnabled())
                .signingAlgorithm(config.getSigningAlgorithm())
                .retryEnabled(config.getRetryEnabled())
                .maxAttempts(config.getMaxAttempts())
                .connectTimeoutSeconds(config.getConnectTimeoutSeconds())
                .readTimeoutSeconds(config.getReadTimeoutSeconds())
                .allowedDomainPattern(config.getAllowedDomainPattern())
                .deadLetterRetentionDays(config.getDeadLetterRetentionDays())
                .updatedBy(config.getUpdatedBy())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private StorageRetentionConfigDTO toDTO(StorageRetentionConfig config, boolean maskSecrets) {
        return StorageRetentionConfigDTO.builder()
                .id(config.getId())
                .artifactStorageLocation(config.getArtifactStorageLocation())
                .archiveStorageLocation(config.getArchiveStorageLocation())
                .encryptionEnabled(config.getEncryptionEnabled())
                .encryptionKeyReference(mask(config.getEncryptionKeyReference(), maskSecrets))
                .retentionYears(config.getRetentionYears())
                .wormRequired(config.getWormRequired())
                .immutableArchiveEnabled(config.getImmutableArchiveEnabled())
                .updatedBy(config.getUpdatedBy())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private ApiClientRegistrationDTO toDTO(ApiClientRegistration client, boolean maskSecrets) {
        return ApiClientRegistrationDTO.builder()
                .id(client.getId())
                .clientName(client.getClientName())
                .clientId(client.getClientId())
                .clientSecretReference(mask(client.getClientSecretReference(), maskSecrets))
                .scopes(client.getScopes())
                .active(client.getActive())
                .revokedAt(client.getRevokedAt())
                .updatedBy(client.getUpdatedBy())
                .updatedAt(client.getUpdatedAt())
                .build();
    }

    private BackgroundJobConfigDTO toDTO(BackgroundJobConfig job) {
        return BackgroundJobConfigDTO.builder()
                .id(job.getId())
                .jobName(job.getJobName())
                .enabled(job.getEnabled())
                .scheduleExpression(job.getScheduleExpression())
                .maxRetryAttempts(job.getMaxRetryAttempts())
                .updatedBy(job.getUpdatedBy())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private SystemConfigAuditLogDTO toDTO(SystemConfigAuditLog log) {
        return SystemConfigAuditLogDTO.builder()
                .id(log.getId())
                .configType(log.getConfigType())
                .configId(log.getConfigId())
                .action(log.getAction())
                .changedBy(log.getChangedBy())
                .summary(log.getSummary())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private NrsIntegrationConfig.Environment parseEnvironment(String value) {
        try {
            return NrsIntegrationConfig.Environment.valueOf(required(value, "Environment is required").toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported NRS environment");
        }
    }

    private boolean isSecretUpdate(String value) {
        return !isBlank(value) && !MASKED.equals(value.trim());
    }

    private String mask(String value, boolean mask) {
        if (isBlank(value)) {
            return null;
        }
        return mask ? MASKED : value;
    }

    private Integer positive(Integer value, String message) {
        if (value == null || value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private String required(String value, String message) {
        if (isBlank(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
