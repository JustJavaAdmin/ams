package com.justjava.ams.integration.controller;

import com.justjava.ams.integration.dto.PayrollJournalRequest;
import com.justjava.ams.integration.dto.PayrollJournalResponse;
import com.justjava.ams.integration.service.ClientEntitlementService;
import com.justjava.ams.integration.service.PayrollJournalPostingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integrations/organizations/{organizationId}/payroll-journals")
@RequiredArgsConstructor
public class PayrollJournalIntegrationController {
    private final ClientEntitlementService entitlementService;
    private final PayrollJournalPostingService postingService;

    @PostMapping
    public ResponseEntity<PayrollJournalResponse> postPayrollJournal(
            @PathVariable Long organizationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PayrollJournalRequest request,
            @AuthenticationPrincipal Jwt caller) {
        entitlementService.assertEntitled(caller, organizationId);
        PayrollJournalResponse response = postingService.post(organizationId, idempotencyKey, request, caller);
        return ResponseEntity.status(response.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }

    @GetMapping("/by-key")
    public PayrollJournalResponse getByIdempotencyKey(
            @PathVariable Long organizationId,
            @RequestParam String idempotencyKey,
            @AuthenticationPrincipal Jwt caller) {
        entitlementService.assertEntitled(caller, organizationId);
        return postingService.findByKey(organizationId, idempotencyKey);
    }
}
