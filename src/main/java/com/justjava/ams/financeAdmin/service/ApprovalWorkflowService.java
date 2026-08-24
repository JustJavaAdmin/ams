package com.justjava.ams.financeAdmin.service;

import com.justjava.ams.cfo.entity.ApprovalRequest;
import com.justjava.ams.cfo.repository.ApprovalRequestRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.financeAdmin.dto.ApprovalDecisionDTO;
import com.justjava.ams.financeAdmin.dto.ApprovalEvaluationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class ApprovalWorkflowService {

    private final ApprovalRuleService approvalRuleService;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public ApprovalDecisionDTO evaluate(ApprovalEvaluationRequest request) {
        return approvalRuleService.evaluate(request);
    }

    public ApprovalDecisionDTO submitForApproval(ApprovalEvaluationRequest request) {
        ApprovalDecisionDTO decision = approvalRuleService.evaluate(request);
        if (!Boolean.TRUE.equals(decision.getApprovalRequired())) {
            return decision;
        }

        Organization organization = organizationRepository.findById(request.getOrganizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        ApprovalRequest approvalRequest = approvalRequestRepository
                .findByEntityTypeAndEntityId(request.getEntityType(), request.getEntityId())
                .orElseGet(ApprovalRequest::new);

        approvalRequest.setOrganization(organization);
        approvalRequest.setEntityType(request.getEntityType());
        approvalRequest.setEntityId(request.getEntityId());
        approvalRequest.setModuleType(request.getModuleType().name());
        approvalRequest.setTransactionType(request.getTransactionType());
        approvalRequest.setStatus(ApprovalRequest.ApprovalStatus.PENDING);
        approvalRequest.setApprovalRuleId(decision.getApprovalRuleId());
        approvalRequest.setApprovalRuleName(decision.getApprovalRuleName());
        approvalRequest.setRequiredApprovals(decision.getRequiredApprovals());
        approvalRequest.setApproverRole(decision.getApproverRole());
        approvalRequest.setEvaluatedAmount(decision.getEvaluatedAmount());
        approvalRequest.setSubmittedDate(LocalDate.now());
        ApprovalRequest saved = approvalRequestRepository.save(approvalRequest);
        decision.setApprovalRequestId(saved.getId());

        return decision;
    }

    public ApprovalRequest approvePending(String entityType, Long entityId, String approvalNotes) {
        ApprovalRequest request = approvalRequestRepository
                .findByEntityTypeAndEntityIdAndStatus(entityType, entityId, ApprovalRequest.ApprovalStatus.PENDING)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No pending approval request found for " + entityType));
        request.setStatus(ApprovalRequest.ApprovalStatus.APPROVED);
        request.setApprovalNotes(approvalNotes);
        request.setApprovedDate(LocalDate.now());
        return approvalRequestRepository.save(request);
    }

    public ApprovalRequest rejectPending(String entityType, Long entityId, String rejectionReason) {
        ApprovalRequest request = approvalRequestRepository
                .findByEntityTypeAndEntityIdAndStatus(entityType, entityId, ApprovalRequest.ApprovalStatus.PENDING)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No pending approval request found for " + entityType));
        request.setStatus(ApprovalRequest.ApprovalStatus.REJECTED);
        request.setRejectionReason(rejectionReason);
        return approvalRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public void requireApproved(String entityType, Long entityId) {
        ApprovalRequest request = findLatest(entityType, entityId);
        if (request == null) {
            return;
        }
        if (!ApprovalRequest.ApprovalStatus.APPROVED.equals(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, entityType + " approval is not complete");
        }
    }

    @Transactional(readOnly = true)
    public ApprovalRequest findLatest(String entityType, Long entityId) {
        return approvalRequestRepository.findByEntityTypeAndEntityId(entityType, entityId).orElse(null);
    }
}
