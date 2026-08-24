package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.GeneralLedgerDTO;
import com.justjava.ams.accountant.dto.YearEndCloseRequest;
import com.justjava.ams.accountant.dto.YearEndCloseResponse;
import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.accountant.entity.GeneralLedger;
import com.justjava.ams.accountant.repository.ChartOfAccountsRepository;
import com.justjava.ams.accountant.repository.GeneralLedgerRepository;
import com.justjava.ams.common.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class YearEndCloseService {
    private final OrganizationRepository organizationRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final GeneralLedgerRepository generalLedgerRepository;
    private final GeneralLedgerService generalLedgerService;

    public YearEndCloseResponse closeYear(Long organizationId, YearEndCloseRequest request, String closedBy) {
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        ChartOfAccounts retainedEarnings = chartOfAccountsRepository.findById(request.getRetainedEarningsAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Retained earnings account not found"));
        if (!retainedEarnings.getOrganization().getId().equals(organizationId)
                || !ChartOfAccounts.AccountType.EQUITY.equals(retainedEarnings.getAccountType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Retained earnings account must be an equity account for the organization");
        }

        List<GeneralLedger> entries = generalLedgerRepository.findPostedEntriesByOrganizationAndDateRange(
                organizationId, request.getFromDate(), request.getToDate());
        Map<Long, List<GeneralLedger>> byAccount = entries.stream()
                .filter(gl -> gl.getAccount().getAccountType() == ChartOfAccounts.AccountType.REVENUE
                        || gl.getAccount().getAccountType() == ChartOfAccounts.AccountType.EXPENSE)
                .collect(Collectors.groupingBy(gl -> gl.getAccount().getId()));

        List<GeneralLedger> debits = new ArrayList<>();
        List<GeneralLedger> credits = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;

        for (List<GeneralLedger> accountEntries : byAccount.values()) {
            ChartOfAccounts account = accountEntries.get(0).getAccount();
            BigDecimal debitTotal = sum(accountEntries, GeneralLedger.DebitCredit.DEBIT);
            BigDecimal creditTotal = sum(accountEntries, GeneralLedger.DebitCredit.CREDIT);
            if (account.getAccountType() == ChartOfAccounts.AccountType.REVENUE) {
                BigDecimal balance = creditTotal.subtract(debitTotal);
                if (balance.compareTo(BigDecimal.ZERO) > 0) {
                    totalRevenue = totalRevenue.add(balance);
                    debits.add(line(account, balance, "Close revenue " + account.getAccountCode()));
                }
            } else if (account.getAccountType() == ChartOfAccounts.AccountType.EXPENSE) {
                BigDecimal balance = debitTotal.subtract(creditTotal);
                if (balance.compareTo(BigDecimal.ZERO) > 0) {
                    totalExpenses = totalExpenses.add(balance);
                    credits.add(line(account, balance, "Close expense " + account.getAccountCode()));
                }
            }
        }

        BigDecimal netIncome = totalRevenue.subtract(totalExpenses);
        if (netIncome.compareTo(BigDecimal.ZERO) > 0) {
            credits.add(line(retainedEarnings, netIncome, "Close net income to retained earnings"));
        } else if (netIncome.compareTo(BigDecimal.ZERO) < 0) {
            debits.add(line(retainedEarnings, netIncome.abs(), "Close net loss to retained earnings"));
        }

        BigDecimal debitTotal = debits.stream().map(GeneralLedger::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal creditTotal = credits.stream().map(GeneralLedger::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debitTotal.compareTo(creditTotal) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Closing journal is not balanced");
        }

        List<GeneralLedgerDTO> posted = generalLedgerService.postYearEndClose(
                organizationId,
                request.getFiscalYear(),
                request.getToDate(),
                debits,
                credits,
                closedBy != null ? closedBy : request.getClosedBy());
        return YearEndCloseResponse.builder()
                .organizationId(organizationId)
                .fiscalYear(request.getFiscalYear())
                .totalRevenueClosed(totalRevenue)
                .totalExpensesClosed(totalExpenses)
                .netIncome(netIncome)
                .closingJournalId(posted.isEmpty() ? null : posted.get(0).getSourceId())
                .status("POSTED")
                .postedEntries(posted)
                .build();
    }

    private BigDecimal sum(List<GeneralLedger> entries, GeneralLedger.DebitCredit debitCredit) {
        return entries.stream()
                .filter(gl -> debitCredit.equals(gl.getDebitCredit()))
                .map(GeneralLedger::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private GeneralLedger line(ChartOfAccounts account, BigDecimal amount, String description) {
        return GeneralLedger.builder()
                .account(account)
                .amount(amount)
                .description(description)
                .build();
    }
}
