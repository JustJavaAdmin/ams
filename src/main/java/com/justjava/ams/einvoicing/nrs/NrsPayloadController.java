package com.justjava.ams.einvoicing.nrs;

import com.justjava.ams.einvoicing.payload.dto.NrsInvoicePayloadDTO;
import com.justjava.ams.einvoicing.payload.service.NrsPayloadMapper;
import com.justjava.ams.einvoicing.nrs.dto.EInvoiceSubmissionDTO;
import com.justjava.ams.einvoicing.nrs.service.EInvoiceSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/accountant/customer-invoices")
@RequiredArgsConstructor
public class NrsPayloadController {

    private final NrsPayloadMapper nrsPayloadMapper;
    private final EInvoiceSubmissionService eInvoiceSubmissionService;

    @GetMapping("/{invoiceId}/nrs-payload")
    public NrsInvoicePayloadDTO getNrsPayload(@PathVariable Long invoiceId) {
        return nrsPayloadMapper.mapCustomerInvoice(invoiceId);
    }

    @PostMapping("/{invoiceId}/nrs-submissions")
    public EInvoiceSubmissionDTO prepareNrsSubmission(
            @PathVariable Long invoiceId,
            @RequestParam(required = false) String submissionType,
            Principal principal) {
        return eInvoiceSubmissionService.prepareCustomerInvoiceSubmission(invoiceId, submissionType, userName(principal));
    }

    @GetMapping("/{invoiceId}/nrs-submissions")
    public List<EInvoiceSubmissionDTO> getNrsSubmissions(@PathVariable Long invoiceId) {
        return eInvoiceSubmissionService.getCustomerInvoiceSubmissions(invoiceId);
    }

    @GetMapping("/nrs-submissions/{submissionId}")
    public EInvoiceSubmissionDTO getNrsSubmission(@PathVariable Long submissionId) {
        return eInvoiceSubmissionService.getSubmission(submissionId);
    }

    private String userName(Principal principal) {
        return principal != null ? principal.getName() : "system";
    }
}
