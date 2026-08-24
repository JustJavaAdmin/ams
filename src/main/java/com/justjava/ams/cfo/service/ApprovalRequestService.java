package com.justjava.ams.cfo.service;

import com.justjava.ams.cfo.dto.ApprovalRequestDTO;
import com.justjava.ams.cfo.entity.ApprovalRequest;
import com.justjava.ams.cfo.repository.ApprovalRequestRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ApprovalRequestService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final OrganizationRepository organizationRepository;

    public ApprovalRequestDTO createApprovalRequest(Long organizationId, ApprovalRequestDTO dto) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        ApprovalRequest request = ApprovalRequest.builder()
                .organization(organization)
                .entityType(dto.getEntityType())
                .entityId(dto.getEntityId())
                .moduleType(dto.getModuleType())
                .transactionType(dto.getTransactionType())
                .approvalRuleId(dto.getApprovalRuleId())
                .approvalRuleName(dto.getApprovalRuleName())
                .approverRole(dto.getApproverRole())
                .evaluatedAmount(dto.getEvaluatedAmount())
                .requiredApprovals(dto.getRequiredApprovals())
                .build();

        return mapToDTO(approvalRequestRepository.save(request));
    }

    public ApprovalRequestDTO getApprovalRequest(Long requestId) {
        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Approval request not found"));
        return mapToDTO(request);
    }

    public List<ApprovalRequestDTO> getPendingRequests(Long organizationId) {
        return approvalRequestRepository.findByOrganizationIdAndStatus(organizationId, ApprovalRequest.ApprovalStatus.PENDING)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<ApprovalRequestDTO> getRequestsAssignedToUser(Long userId) {
        return approvalRequestRepository.findByAssignedToUserId(userId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public ApprovalRequestDTO approveRequest(Long requestId, String approvalNotes) {
        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Approval request not found"));
        request.setStatus(ApprovalRequest.ApprovalStatus.APPROVED);
        request.setApprovalNotes(approvalNotes);
        request.setApprovedDate(LocalDate.now());
        return mapToDTO(approvalRequestRepository.save(request));
    }

    public ApprovalRequestDTO rejectRequest(Long requestId, String rejectionReason) {
        ApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Approval request not found"));
        request.setStatus(ApprovalRequest.ApprovalStatus.REJECTED);
        request.setRejectionReason(rejectionReason);
        return mapToDTO(approvalRequestRepository.save(request));
    }

    private ApprovalRequestDTO mapToDTO(ApprovalRequest request) {
        return ApprovalRequestDTO.builder()
                .id(request.getId())
                .organizationId(request.getOrganization().getId())
                .entityType(request.getEntityType())
                .entityId(request.getEntityId())
                .moduleType(request.getModuleType())
                .transactionType(request.getTransactionType())
                .approvalRuleId(request.getApprovalRuleId())
                .approvalRuleName(request.getApprovalRuleName())
                .approverRole(request.getApproverRole())
                .evaluatedAmount(request.getEvaluatedAmount())
                .status(request.getStatus().toString())
                .submittedByUserId(request.getSubmittedByUser() != null ? request.getSubmittedByUser().getId() : null)
                .submittedDate(request.getSubmittedDate())
                .assignedToUserId(request.getAssignedToUser() != null ? request.getAssignedToUser().getId() : null)
                .approvedByUserId(request.getApprovedByUser() != null ? request.getApprovedByUser().getId() : null)
                .approvedDate(request.getApprovedDate())
                .approvalNotes(request.getApprovalNotes())
                .rejectionReason(request.getRejectionReason())
                .approvalLevel(request.getApprovalLevel())
                .requiredApprovals(request.getRequiredApprovals())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .build();
    }
}

