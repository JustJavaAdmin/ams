package com.justjava.ams.accountant;

import com.justjava.ams.accountant.service.*;
import com.justjava.ams.common.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/accountant")
@RequiredArgsConstructor
public class AccountantController {

    private final OrganizationRepository organizationRepository;
    private final ManualJournalService manualJournalService;
    private final PurchaseInvoiceService purchaseInvoiceService;
    private final CustomerInvoiceService customerInvoiceService;
    private final BankAccountService bankAccountService;
    private final FixedAssetService fixedAssetService;
    private final ChartOfAccountsService chartOfAccountsService;



    @GetMapping("/manualJournal")
    public String manualJournal(Model model) {
        model.addAttribute("organizations", organizationRepository.findAll());
        return "accountant/manualJournal";
    }

    @GetMapping("/purchaseInvoice")
    public String purchaseInvoice(Model model) {
        model.addAttribute("organizations", organizationRepository.findAll());
        return "accountant/purchaseInvoice";
    }

    @GetMapping("/customerInvoicing")
    public String customerInvoicing(Model model) {
        model.addAttribute("organizations", organizationRepository.findAll());
        return "accountant/customerInvoicing";
    }

    @GetMapping("/cashAndBank")
    public String cashAndBank(Model model) {
        model.addAttribute("organizations", organizationRepository.findAll());
        return "accountant/cashAndBank";
    }

    @GetMapping("/fixedAssets")
    public String fixedAssets(Model model) {
        model.addAttribute("organizations", organizationRepository.findAll());
        return "accountant/fixedAssets";
    }
}