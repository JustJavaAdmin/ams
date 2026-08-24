package com.justjava.ams.cfo.repository;

import com.justjava.ams.cfo.entity.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    Optional<ApprovalRequest> findByEntityTypeAndEntityId(String entityType, Long entityId);
    Optional<ApprovalRequest> findByEntityTypeAndEntityIdAndStatus(String entityType, Long entityId, ApprovalRequest.ApprovalStatus status);
    List<ApprovalRequest> findByOrganizationIdAndStatus(Long organizationId, ApprovalRequest.ApprovalStatus status);
    List<ApprovalRequest> findByAssignedToUserId(Long userId);
}

