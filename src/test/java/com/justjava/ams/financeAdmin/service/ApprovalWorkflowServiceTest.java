package com.justjava.ams.financeAdmin.service;

import com.justjava.ams.cfo.entity.ApprovalRequest;
import com.justjava.ams.cfo.repository.ApprovalRequestRepository;
import com.justjava.ams.common.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalWorkflowServiceTest {

    @Mock
    private ApprovalRuleService approvalRuleService;

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private ApprovalWorkflowService approvalWorkflowService;

    @Test
    void requireApprovedRejectsPendingWorkflow() {
        when(approvalRequestRepository.findByEntityTypeAndEntityId("PaymentRun", 10L))
                .thenReturn(Optional.of(ApprovalRequest.builder()
                        .entityType("PaymentRun")
                        .entityId(10L)
                        .status(ApprovalRequest.ApprovalStatus.PENDING)
                        .build()));

        assertThatThrownBy(() -> approvalWorkflowService.requireApproved("PaymentRun", 10L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("approval is not complete");
    }

    @Test
    void rejectPendingMarksPendingRequestRejected() {
        ApprovalRequest request = ApprovalRequest.builder()
                .entityType("Budget")
                .entityId(20L)
                .status(ApprovalRequest.ApprovalStatus.PENDING)
                .build();
        when(approvalRequestRepository.findByEntityTypeAndEntityIdAndStatus(
                "Budget",
                20L,
                ApprovalRequest.ApprovalStatus.PENDING))
                .thenReturn(Optional.of(request));

        approvalWorkflowService.rejectPending("Budget", 20L, "Insufficient support");

        verify(approvalRequestRepository).save(request);
        org.assertj.core.api.Assertions.assertThat(request.getStatus()).isEqualTo(ApprovalRequest.ApprovalStatus.REJECTED);
        org.assertj.core.api.Assertions.assertThat(request.getRejectionReason()).isEqualTo("Insufficient support");
    }
}
