package com.justjava.ams.accountant;

import com.justjava.ams.accountant.dto.PostedJournalDetail;
import com.justjava.ams.accountant.dto.PostedJournalPage;
import com.justjava.ams.accountant.service.GeneralLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/posted-journals")
@RequiredArgsConstructor
public class PostedJournalController {
    private final GeneralLedgerService generalLedgerService;

    @GetMapping("/organizations/{organizationId}")
    public PostedJournalPage listPostedJournals(
            @PathVariable Long organizationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int pageSize) {
        return generalLedgerService.getPostedJournalSummaries(organizationId, fromDate, toDate, page, pageSize);
    }

    @GetMapping("/organizations/{organizationId}/batches/{postingBatchId}")
    public PostedJournalDetail getPostedJournal(
            @PathVariable Long organizationId,
            @PathVariable String postingBatchId) {
        return generalLedgerService.getPostedJournalDetail(organizationId, postingBatchId);
    }
}
