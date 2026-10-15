package com.justjava.ams.systemAdmin;

import com.justjava.ams.systemAdmin.dto.*;
import com.justjava.ams.systemAdmin.service.SystemAdminConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/system-admin")
@RequiredArgsConstructor
public class SystemAdminApiController {

    private final SystemAdminConfigService configService;

    @GetMapping("/nrs/config")
    public NrsIntegrationConfigDTO getNrsConfig() {
        return configService.getNrsConfig();
    }

    @PutMapping("/nrs/config")
    public NrsIntegrationConfigDTO updateNrsConfig(
            @RequestBody NrsIntegrationConfigDTO request,
            Principal principal) {
        return configService.updateNrsConfig(request, principalName(principal));
    }

    @PostMapping("/nrs/test-connection")
    public IntegrationHealthDTO testNrsConnection() {
        return configService.testNrsConnection();
    }

    @GetMapping("/signing/config")
    public SigningKeyConfigDTO getSigningConfig() {
        return configService.getSigningConfig();
    }

    @PutMapping("/signing/config")
    public SigningKeyConfigDTO updateSigningConfig(
            @RequestBody SigningKeyConfigDTO request,
            Principal principal) {
        return configService.updateSigningConfig(request, principalName(principal));
    }

    @PostMapping("/signing/test")
    public IntegrationHealthDTO testSigningConfig() {
        return configService.testSigningConfig();
    }

    @GetMapping("/webhook-platform/config")
    public WebhookPlatformConfigDTO getWebhookConfig() {
        return configService.getWebhookConfig();
    }

    @PutMapping("/webhook-platform/config")
    public WebhookPlatformConfigDTO updateWebhookConfig(
            @RequestBody WebhookPlatformConfigDTO request,
            Principal principal) {
        return configService.updateWebhookConfig(request, principalName(principal));
    }

    @GetMapping("/storage-retention/config")
    public StorageRetentionConfigDTO getStorageConfig() {
        return configService.getStorageConfig();
    }

    @PutMapping("/storage-retention/config")
    public StorageRetentionConfigDTO updateStorageConfig(
            @RequestBody StorageRetentionConfigDTO request,
            Principal principal) {
        return configService.updateStorageConfig(request, principalName(principal));
    }

    @GetMapping("/api-clients")
    public List<ApiClientRegistrationDTO> getApiClients() {
        return configService.getApiClients();
    }

    @PostMapping("/api-clients")
    public ApiClientRegistrationDTO createApiClient(
            @RequestBody ApiClientRegistrationDTO request,
            Principal principal) {
        return configService.createApiClient(request, principalName(principal));
    }

    @PutMapping("/api-clients/{id}")
    public ApiClientRegistrationDTO updateApiClient(
            @PathVariable Long id,
            @RequestBody ApiClientRegistrationDTO request,
            Principal principal) {
        return configService.updateApiClient(id, request, principalName(principal));
    }

    @PostMapping("/api-clients/{id}/revoke")
    public ApiClientRegistrationDTO revokeApiClient(
            @PathVariable Long id,
            Principal principal) {
        return configService.revokeApiClient(id, principalName(principal));
    }

    @GetMapping("/jobs")
    public List<BackgroundJobConfigDTO> getJobs() {
        return configService.getJobs();
    }

    @PutMapping("/jobs/{id}")
    public BackgroundJobConfigDTO updateJob(
            @PathVariable Long id,
            @RequestBody BackgroundJobConfigDTO request,
            Principal principal) {
        return configService.updateJob(id, request, principalName(principal));
    }

    @GetMapping("/health/integrations")
    public List<IntegrationHealthDTO> getIntegrationHealth() {
        return List.of(
                configService.testNrsConnection(),
                configService.testSigningConfig()
        );
    }

    @GetMapping("/audit/config-changes")
    public List<SystemConfigAuditLogDTO> getAuditLogs() {
        return configService.getAuditLogs();
    }

    private String principalName(Principal principal) {
        return principal != null ? principal.getName() : "system";
    }
}
