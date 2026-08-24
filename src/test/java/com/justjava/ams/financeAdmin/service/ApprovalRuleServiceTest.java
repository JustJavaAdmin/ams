package com.justjava.ams.financeAdmin.service;

import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.auditor.service.AuditLogService;
import com.justjava.ams.auditor.service.SecurityEventService;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.financeAdmin.dto.ApprovalDecisionDTO;
import com.justjava.ams.financeAdmin.dto.ApprovalEvaluationRequest;
import com.justjava.ams.financeAdmin.entity.ApprovalRule;
import com.justjava.ams.financeAdmin.entity.ModuleControl;
import com.justjava.ams.financeAdmin.repository.ApprovalRuleRepository;
import com.justjava.ams.financeAdmin.repository.ModuleControlRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalRuleServiceTest {

    @Mock
    private ApprovalRuleRepository approvalRuleRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private SecurityEventService securityEventService;

    @Mock
    private ModuleControlRepository moduleControlRepository;

    @InjectMocks
    private ApprovalRuleService approvalRuleService;

    @Test
    void evaluatesNonJournalApprovalRule() {
        ApprovalRule rule = ApprovalRule.builder()
                .id(10L)
                .ruleName("High value expense")
                .moduleType(ModuleControl.ModuleType.ACCOUNTS_PAYABLE)
                .transactionType("EXPENSE")
                .minAmount(new BigDecimal("1000.00"))
                .accountType(ChartOfAccounts.AccountType.EXPENSE)
                .requiredApprovals(2)
                .approverRole("CFO")
                .active(true)
                .build();

        when(approvalRuleRepository.findByOrganizationIdAndModuleTypeAndActiveTrueOrderByPriorityAscIdAsc(
                1L, ModuleControl.ModuleType.ACCOUNTS_PAYABLE)).thenReturn(List.of(rule));

        ApprovalDecisionDTO decision = approvalRuleService.evaluate(ApprovalEvaluationRequest.builder()
                .organizationId(1L)
                .moduleType(ModuleControl.ModuleType.ACCOUNTS_PAYABLE)
                .transactionType("EXPENSE")
                .amount(new BigDecimal("1500.00"))
                .accountTypes(Set.of(ChartOfAccounts.AccountType.EXPENSE))
                .build());

        assertThat(decision.getApprovalRequired()).isTrue();
        assertThat(decision.getApprovalRuleId()).isEqualTo(10L);
        assertThat(decision.getApprovalRuleName()).isEqualTo("High value expense");
        assertThat(decision.getRequiredApprovals()).isEqualTo(2);
        assertThat(decision.getApproverRole()).isEqualTo("CFO");
    }
}
