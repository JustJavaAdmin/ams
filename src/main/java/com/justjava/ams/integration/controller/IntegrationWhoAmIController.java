package com.justjava.ams.integration.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * STAGE 1 SCAFFOLDING: proves the integration security chain works.
 * Returns only the caller's client id and scopes. May be reviewed or removed later.
 */
@RestController
@RequestMapping("/api/integrations")
public class IntegrationWhoAmIController {

    @GetMapping("/whoami")
    public ResponseEntity<Map<String, Object>> whoAmI(@AuthenticationPrincipal Jwt jwt) {
        String scopeClaim = jwt.getClaimAsString("scope");
        List<String> scopes = scopeClaim == null || scopeClaim.isBlank()
                ? List.of()
                : Arrays.asList(scopeClaim.split(" "));

        return ResponseEntity.ok(Map.of(
                "clientId", String.valueOf(jwt.getClaimAsString("azp")),
                "scopes", scopes
        ));
    }
}