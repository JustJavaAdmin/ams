package com.justjava.ams.financeAdmin;

import com.justjava.ams.financeAdmin.service.FiscalConfigurationService;
import com.justjava.ams.financeAdmin.service.ModuleControlService;
import com.justjava.ams.financeAdmin.service.OrganizationSettingsService;
import com.justjava.ams.common.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/financeAdmin")
@RequiredArgsConstructor
public class FinanceAdminController {

    private final OrganizationRepository organizationRepository;
    private final FiscalConfigurationService fiscalConfigurationService;
    private final OrganizationSettingsService organizationSettingsService;
    private final ModuleControlService moduleControlService;



    @GetMapping("/organizationSetup")
    public String organizationSetup(Model model) {
        // Load all organizations and branches
        model.addAttribute("organizations", organizationRepository.findAll());
        return "financeAdmin/organizationSetup";
    }

    @GetMapping("/chartOfAccounts")
    public String chartOfAccounts(Model model) {
        // Data will be loaded via AJAX from REST API
        return "financeAdmin/chartOfAccounts";
    }

    @GetMapping("/fiscalConfiguration")
    public String fiscalConfiguration(Model model) {
        // Data will be loaded via AJAX from REST API
        return "financeAdmin/fiscalConfiguration";
    }

    @GetMapping("/moduleControls")
    public String moduleControls(Model model) {
        // Data will be loaded via AJAX from REST API
        return "financeAdmin/moduleControls";
    }
}