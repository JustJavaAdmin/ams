package com.justjava.ams.systemAdmin;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/system-admin")
public class SystemAdminController {

    @GetMapping({"", "/"})
    public String dashboard(Model model) {
        model.addAttribute("userName", currentUserName());
        return "systemAdmin/dashboard";
    }

    private String currentUserName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "System Admin";
    }
}
