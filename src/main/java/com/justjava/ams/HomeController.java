package com.justjava.ams;

import com.justjava.ams.core.config.AuthenticationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    @Autowired
    AuthenticationManager authenticationManager;

    @GetMapping("/")
    public String home(Model model) {

        if (!authenticationManager.isAuthenticated()) {
            model.addAttribute("title", "Accounting Management System");
            model.addAttribute("subTitle", "Enterprise Financial Governance & Oversight");
            return "index";
        }

        if (authenticationManager.isAdmin()) {
            return "redirect:/admin/users";
        } else if (authenticationManager.isAccountant()) {
            return "redirect:/accountant/manualJournal";
        } else if (authenticationManager.isAuditor()) {
            return "redirect:/auditor/auditLogExplorer";
        } else if (authenticationManager.isCfo()) {
            return "redirect:/cfo/approvalsHub";
        } else if (authenticationManager.isFinanceAdmin()) {
            return "redirect:/financeAdmin/organizationSetup";
        }

        model.addAttribute("title", "Accounting Management System");
        model.addAttribute("subTitle", "Enterprise Financial Governance & Oversight");
        return "index";
    }

}