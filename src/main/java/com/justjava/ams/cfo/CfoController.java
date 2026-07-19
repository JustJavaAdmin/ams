package com.justjava.ams.cfo;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/cfo")
public class CfoController {



    @GetMapping("/approvalsHub")
    public String approvalsHub() {
        return "cfo/approvalsHub";
    }

    @GetMapping("/trialBalance")
    public String trialBalance() {
        return "cfo/trialBalance";
    }

    @GetMapping("/financialReports")
    public String financialReports() {
        return "cfo/financialReports";
    }

    @GetMapping("/budgetControl")
    public String budgetControl() {
        return "cfo/budgetControl";
    }
}