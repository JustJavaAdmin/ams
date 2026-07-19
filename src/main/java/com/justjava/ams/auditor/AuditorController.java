package com.justjava.ams.auditor;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.access.prepost.PreAuthorize;

@Controller
@RequestMapping("/auditor")
@PreAuthorize("hasRole('ROLE_AUDITOR')")
public class AuditorController {



    @GetMapping("/auditLogExplorer")
    public String auditLogExplorer() {
        return "auditor/auditLogExplorer";
    }

    @GetMapping("/securityEvents")
    public String securityEvents() {
        return "auditor/securityEvents";
    }
}