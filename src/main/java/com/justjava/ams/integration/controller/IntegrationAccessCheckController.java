package com.justjava.ams.integration.controller;

import com.justjava.ams.integration.service.ClientEntitlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * STAGE 1 SCAFFOLDING: proves the scope rule and the entitlement check work together.
 * The scope (payroll-journal:post) is enforced by the integration security chain.
 * Returns no body on success. May be reviewed or removed later.
 */
@RestController
@RequestMapping("/api/integrations/organizations")
@RequiredArgsConstructor
public class IntegrationAccessCheckController {

    private final ClientEntitlementService entitlementService;

    @GetMapping("/{organizationId}/access-check")
    public ResponseEntity<Void> accessCheck(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable Long organizationId) {
        entitlementService.assertEntitled(jwt, organizationId);
        return ResponseEntity.noContent().build();
    }
}